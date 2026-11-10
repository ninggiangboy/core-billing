package io.corebilling.money;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.money.api.charge.ChargeSource;
import io.corebilling.money.api.charge.ChargeStatus;
import io.corebilling.money.api.wallet.InsufficientFundsException;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.api.wallet.WalletMode;
import io.corebilling.money.domain.charge.ChargeService;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.money.domain.reservation.Reservation;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.corebilling.money.api.ledger.LedgerEntryId;

/**
 * Property-style tests: thousands of random operations on a wallet, with the invariants checked after every
 * single step against a reference model that only knows the ledger entries.
 */
class MoneyInvariantsTest {

    private static final Currency USD = Currency.of("USD");
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static Money usd(long minor) {
        return Money.of(minor, USD);
    }

    private static final class Simulation {
        final Random random;
        final Wallet wallet;
        final WalletMode mode;
        final long limit;
        final List<LedgerEntry> ledger = new ArrayList<>();
        final List<Reservation> active = new ArrayList<>();
        final ChargeService charges = new ChargeService(UUID::randomUUID);
        Instant now = NOW;

        Simulation(long seed, WalletMode mode) {
            this.random = new Random(seed);
            this.mode = mode;
            this.limit = mode == WalletMode.POSTPAID ? 1 + random.nextInt(5000) : 0;
            this.wallet = Wallet.open(new WalletId(UUID.randomUUID()), TENANT, new CustomerId(UUID.randomUUID()), USD, mode, usd(limit), NOW);
        }

        void step() {
            long amount = 1 + random.nextInt(1500);
            switch (random.nextInt(7)) {
                case 0 -> credit(amount);
                case 1 -> debit(amount);
                case 2 -> reserve(amount);
                case 3 -> captureSome();
                case 4 -> releaseSome();
                case 5 -> standaloneCharge(amount);
                default -> now = now.plusSeconds(random.nextInt(30));
            }
        }

        void credit(long amount) {
            long seq = wallet.post(usd(amount));
            ledger.add(LedgerEntry.credit(new io.corebilling.money.api.ledger.LedgerEntryId(UUID.randomUUID()), TENANT, wallet.id(), seq, usd(amount),
                    LedgerEntry.Source.of("topup"), new LedgerEntry.Origin("sim", null, UUID.randomUUID().toString()), now));
        }

        void debit(long amount) {
            try {
                long seq = wallet.post(usd(-amount));
                ledger.add(LedgerEntry.debit(new io.corebilling.money.api.ledger.LedgerEntryId(UUID.randomUUID()), TENANT, wallet.id(), seq, usd(amount),
                        LedgerEntry.Source.of("debit"), new LedgerEntry.Origin("sim", null, UUID.randomUUID().toString()), now));
            } catch (InsufficientFundsException expected) {
                // rejected: nothing may have changed
            }
        }

        void reserve(long amount) {
            try {
                active.add(Reservation.hold(new ReservationId(UUID.randomUUID()), wallet, usd(amount), now.plusSeconds(1 + random.nextInt(120)),
                        null, UUID.randomUUID().toString(), "h", now));
            } catch (InsufficientFundsException expected) {
                // rejected: the hold must not exist
            }
        }

        void captureSome() {
            if (active.isEmpty()) {
                return;
            }
            Reservation reservation = active.remove(random.nextInt(active.size()));
            if (reservation.isExpired(now)) {
                reservation.expire(wallet, now);
                return;
            }
            long cost = 1 + random.nextInt((int) reservation.amount().minorUnits());
            ChargeService.Outcome outcome = charges.charge(new ChargeService.Request(
                    wallet, reservation, usd(cost), ChargeSource.RESERVATION, null, null, UUID.randomUUID().toString(), "h", "sim", now));
            assertThat(outcome.charge().status()).isEqualTo(ChargeStatus.SUCCEEDED);
            ledger.add(outcome.entry());
        }

        void releaseSome() {
            if (!active.isEmpty()) {
                Reservation reservation = active.remove(random.nextInt(active.size()));
                if (reservation.isExpired(now)) {
                    reservation.expire(wallet, now);
                } else {
                    reservation.release(wallet, now);
                }
            }
        }

        void standaloneCharge(long amount) {
            ChargeService.Outcome outcome = charges.charge(new ChargeService.Request(
                    wallet, null, usd(amount), ChargeSource.STANDALONE, null, null, UUID.randomUUID().toString(), "h", "sim", now));
            if (outcome.entry() != null) {
                ledger.add(outcome.entry());
            }
        }

        void assertInvariants() {
            long sum = ledger.stream().mapToLong(e -> e.amount().minorUnits()).sum();
            assertThat(wallet.balance().minorUnits()).as("balance is the sum of the ledger").isEqualTo(sum);
            assertThat(wallet.lastSequence()).as("sequence is gap-free").isEqualTo(ledger.size());
            for (int i = 0; i < ledger.size(); i++) {
                assertThat(ledger.get(i).sequence()).isEqualTo(i + 1);
            }
            long heldSum = active.stream().mapToLong(r -> r.amount().minorUnits()).sum();
            assertThat(wallet.held().minorUnits()).as("held is exactly the open reservations").isEqualTo(heldSum);
            assertThat(wallet.held().isNegative()).isFalse();
            assertThat(wallet.balance().minus(wallet.held()).minorUnits())
                    .as("available never drops below minus the credit limit")
                    .isGreaterThanOrEqualTo(-limit);
            if (mode == WalletMode.PREPAID) {
                assertThat(wallet.available().isNegative()).as("a prepaid wallet is never overdrawn").isFalse();
                assertThat(wallet.balance().isNegative()).isFalse();
                assertThat(wallet.held().isGreaterThan(wallet.balance())).as("held never exceeds the balance").isFalse();
            } else {
                assertThat(wallet.outstanding().minorUnits()).isLessThanOrEqualTo(limit);
            }
        }
    }

    @Test
    void prepaidWalletsKeepEveryInvariantUnderRandomOperations() {
        for (long seed = 0; seed < 60; seed++) {
            Simulation simulation = new Simulation(seed, WalletMode.PREPAID);
            for (int i = 0; i < 400; i++) {
                simulation.step();
                simulation.assertInvariants();
            }
        }
    }

    @Test
    void postpaidWalletsKeepEveryInvariantUnderRandomOperations() {
        for (long seed = 1000; seed < 1060; seed++) {
            Simulation simulation = new Simulation(seed, WalletMode.POSTPAID);
            for (int i = 0; i < 400; i++) {
                simulation.step();
                simulation.assertInvariants();
            }
        }
    }
}
