package io.corebilling.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.money.api.ChargeSource;
import io.corebilling.money.api.ChargeStatus;
import io.corebilling.money.api.EntryType;
import io.corebilling.money.api.InsufficientFundsException;
import io.corebilling.money.api.LedgerEntryId;
import io.corebilling.money.api.ReservationId;
import io.corebilling.money.api.ReservationStatus;
import io.corebilling.money.api.WalletMode;
import io.corebilling.money.domain.ChargeService;
import io.corebilling.money.domain.LedgerEntry;
import io.corebilling.money.domain.Reservation;
import io.corebilling.money.domain.Wallet;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MoneyDomainTest {

    private static final Currency USD = Currency.of("USD");
    private static final Currency VND = Currency.of("VND");
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private final ChargeService charges = new ChargeService(UUID::randomUUID);

    private static Money usd(long minor) {
        return Money.of(minor, USD);
    }

    private static Wallet prepaid() {
        return Wallet.open(new WalletId(UUID.randomUUID()), TENANT, new CustomerId(UUID.randomUUID()), USD, WalletMode.PREPAID, null, NOW);
    }

    private static Wallet postpaid(long limit) {
        return Wallet.open(new WalletId(UUID.randomUUID()), TENANT, new CustomerId(UUID.randomUUID()), USD, WalletMode.POSTPAID, usd(limit), NOW);
    }

    private static Reservation hold(Wallet wallet, long amount, Instant expiresAt) {
        return Reservation.hold(new ReservationId(UUID.randomUUID()), wallet, usd(amount), expiresAt, "run-1", UUID.randomUUID().toString(), "h", NOW);
    }

    private ChargeService.Request request(Wallet wallet, Reservation reservation, long amount) {
        return new ChargeService.Request(wallet, reservation, usd(amount), reservation == null ? ChargeSource.STANDALONE : ChargeSource.RESERVATION,
                null, null, UUID.randomUUID().toString(), "h", "user/test", NOW);
    }

    @Test
    void aPrepaidWalletSpendsOnlyWhatItHas() {
        Wallet wallet = prepaid();
        assertThat(wallet.canSpend(usd(1))).isFalse();
        assertThatThrownBy(() -> wallet.post(usd(-1))).isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.post(usd(1000))).isEqualTo(1);
        assertThat(wallet.post(usd(-400))).isEqualTo(2);
        assertThat(wallet.balance()).isEqualTo(usd(600));
        assertThatThrownBy(() -> wallet.post(usd(-601))).isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.balance()).as("a rejected debit changes nothing").isEqualTo(usd(600));
        assertThat(wallet.lastSequence()).isEqualTo(2);
    }

    @Test
    void holdsReduceWhatCanBeSpentWithoutTouchingTheBalance() {
        Wallet wallet = prepaid();
        wallet.post(usd(1000));
        wallet.hold(usd(700));

        assertThat(wallet.balance()).isEqualTo(usd(1000));
        assertThat(wallet.available()).isEqualTo(usd(300));
        assertThat(wallet.canSpend(usd(301))).isFalse();
        assertThatThrownBy(() -> wallet.hold(usd(301))).isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.lastSequence()).as("a hold writes no ledger entry").isEqualTo(1);
    }

    @Test
    void aPostpaidWalletSpendsUpToItsLimitAndTracksDebt() {
        Wallet wallet = postpaid(5000);
        wallet.post(usd(-3000));
        assertThat(wallet.outstanding()).isEqualTo(usd(3000));
        assertThat(wallet.canSpend(usd(2000))).isTrue();
        assertThat(wallet.canSpend(usd(2001))).isFalse();
        assertThatThrownBy(() -> wallet.post(usd(-2001))).isInstanceOf(InsufficientFundsException.class);

        wallet.post(usd(1200));
        assertThat(wallet.outstanding()).as("a payment reduces the debt").isEqualTo(usd(1800));
        wallet.post(usd(2000));
        assertThat(wallet.outstanding()).isEqualTo(usd(0));
        assertThat(wallet.balance()).isEqualTo(usd(200));
    }

    @Test
    void aPrepaidWalletCannotHaveACreditLimit() {
        assertThatThrownBy(() -> Wallet.open(new WalletId(UUID.randomUUID()), TENANT, new CustomerId(UUID.randomUUID()), USD,
                        WalletMode.PREPAID, usd(1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWalletRejectsAnotherCurrencyEverywhere() {
        Wallet wallet = prepaid();
        Money dong = Money.of(10, VND);
        assertThatThrownBy(() -> wallet.post(dong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.hold(dong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.canSpend(dong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> charges.charge(new ChargeService.Request(wallet, null, dong, ChargeSource.STANDALONE, null, null, "k", "h", "a", NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capturingChargesTheActualCostAndReleasesTheRemainder() {
        Wallet wallet = prepaid();
        wallet.post(usd(1000));
        Reservation reservation = hold(wallet, 600, NOW.plusSeconds(60));

        ChargeService.Outcome outcome = charges.charge(request(wallet, reservation, 250));

        assertThat(outcome.charge().status()).isEqualTo(ChargeStatus.SUCCEEDED);
        assertThat(outcome.entry().type()).isEqualTo(EntryType.DEBIT);
        assertThat(outcome.entry().amount()).isEqualTo(usd(-250));
        assertThat(outcome.entry().sequence()).isEqualTo(2);
        assertThat(wallet.balance()).isEqualTo(usd(750));
        assertThat(wallet.held()).as("the whole hold, including the unused 350, is returned").isEqualTo(usd(0));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CAPTURED);
        assertThat(reservation.captured()).isEqualTo(usd(250));
    }

    @Test
    void aCaptureCannotExceedTheHoldOrComeTwiceOrAfterExpiry() {
        Wallet wallet = prepaid();
        wallet.post(usd(1000));
        Reservation reservation = hold(wallet, 300, NOW.plusSeconds(60));

        assertThatThrownBy(() -> charges.charge(request(wallet, reservation, 301))).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet.held()).as("a rejected capture holds on to the hold").isEqualTo(usd(300));
        charges.charge(request(wallet, reservation, 300));
        assertThatThrownBy(() -> charges.charge(request(wallet, reservation, 1))).isInstanceOf(IllegalStateException.class);

        Reservation late = hold(wallet, 100, NOW.plusSeconds(1));
        ChargeService.Request afterExpiry = new ChargeService.Request(wallet, late, usd(50), ChargeSource.RESERVATION, null, null, "k", "h", "a", NOW.plusSeconds(2));
        assertThatThrownBy(() -> charges.charge(afterExpiry)).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
    }

    @Test
    void releaseAndExpiryReturnTheHoldExactlyOnce() {
        Wallet wallet = prepaid();
        wallet.post(usd(1000));
        Reservation released = hold(wallet, 400, NOW.plusSeconds(60));
        released.release(wallet, NOW);
        assertThat(wallet.held()).isEqualTo(usd(0));
        assertThatThrownBy(() -> released.release(wallet, NOW)).isInstanceOf(IllegalStateException.class);

        Reservation expiring = hold(wallet, 400, NOW.plusSeconds(10));
        assertThatThrownBy(() -> expiring.expire(wallet, NOW.plusSeconds(9))).isInstanceOf(IllegalStateException.class);
        expiring.expire(wallet, NOW.plusSeconds(10));
        assertThat(expiring.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(wallet.held()).isEqualTo(usd(0));
        assertThatThrownBy(() -> expiring.expire(wallet, NOW.plusSeconds(11))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aChargeBeyondTheBalanceIsRecordedAsFailedAndMovesNothing() {
        Wallet wallet = prepaid();
        wallet.post(usd(100));

        ChargeService.Outcome outcome = charges.charge(request(wallet, null, 101));

        assertThat(outcome.charge().status()).isEqualTo(ChargeStatus.FAILED);
        assertThat(outcome.charge().failureReason()).isEqualTo(ChargeService.INSUFFICIENT_FUNDS);
        assertThat(outcome.entry()).isNull();
        assertThat(wallet.balance()).isEqualTo(usd(100));
        assertThat(wallet.lastSequence()).isEqualTo(1);
    }

    @Test
    void ledgerEntriesMatchTheirTypeAndSign() {
        TenantId tenant = TENANT;
        WalletId walletId = new WalletId(UUID.randomUUID());
        LedgerEntry.Source source = LedgerEntry.Source.of("topup:1");
        LedgerEntry.Origin origin = new LedgerEntry.Origin("system/payment", null, "k1");

        assertThat(LedgerEntry.credit(new LedgerEntryId(UUID.randomUUID()), tenant, walletId, 1, usd(500), source, origin, NOW).amount()).isEqualTo(usd(500));
        assertThat(LedgerEntry.debit(new LedgerEntryId(UUID.randomUUID()), tenant, walletId, 2, usd(500), source, origin, NOW).amount()).isEqualTo(usd(-500));
        assertThatThrownBy(() -> LedgerEntry.credit(new LedgerEntryId(UUID.randomUUID()), tenant, walletId, 1, usd(-5), source, origin, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerEntry.credit(new LedgerEntryId(UUID.randomUUID()), tenant, walletId, 0, usd(5), source, origin, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerEntry.credit(new LedgerEntryId(UUID.randomUUID()), tenant, walletId, 1, usd(0), source, origin, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aReversalNegatesTheOriginalAndNeedsAReason() {
        WalletId walletId = new WalletId(UUID.randomUUID());
        LedgerEntry wrong = LedgerEntry.debit(new LedgerEntryId(UUID.randomUUID()), TENANT, walletId, 1, usd(50_000),
                LedgerEntry.Source.of("charge:ch_999"), new LedgerEntry.Origin("user/a", null, "k1"), NOW);

        LedgerEntry reversal = LedgerEntry.reversalOf(wrong, new LedgerEntryId(UUID.randomUUID()), 2,
                new LedgerEntry.Origin("user/admin", "wrong_price", "reversal:" + wrong.id()), NOW);

        assertThat(reversal.type()).isEqualTo(EntryType.REVERSAL);
        assertThat(reversal.amount()).isEqualTo(usd(50_000));
        assertThat(reversal.reversalOf()).isEqualTo(wrong.id());
        assertThat(reversal.walletId()).isEqualTo(wrong.walletId());
        assertThat(wrong.amount().plus(reversal.amount()).isZero()).isTrue();

        assertThatThrownBy(() -> LedgerEntry.reversalOf(wrong, new LedgerEntryId(UUID.randomUUID()), 3,
                        new LedgerEntry.Origin("user/admin", " ", "k"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerEntry.reversalOf(reversal, new LedgerEntryId(UUID.randomUUID()), 3,
                        new LedgerEntry.Origin("user/admin", "again", "k"), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
