package dev.ngb.core_billing.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.wallet.CreditCommand;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.money.api.wallet.DebitCommand;
import dev.ngb.core_billing.money.api.wallet.DebitWallet;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.wallet.InsufficientFundsException;
import dev.ngb.core_billing.money.api.ledger.LedgerPostingResult;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.application.charge.command.ChargeUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ExpireReservationsUseCase;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.ledger.port.LedgerVerifier;
import dev.ngb.core_billing.money.application.reservation.command.ReleaseReservationUseCase;
import dev.ngb.core_billing.money.application.ledger.command.ReverseEntryUseCase;
import dev.ngb.core_billing.money.application.ledger.command.VerifyLedgerUseCase;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.wallet.query.WalletView;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.ngb.core_billing.platform.api.error.NotFoundException;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class MoneyIntegrationTest {

    @Autowired MoneyTestWorld worlds;
    @Autowired UnitOfWork unitOfWork;
    @Autowired CreditWallet creditWallet;
    @Autowired DebitWallet debitWallet;
    @Autowired ReserveBudget reserveBudget;
    @Autowired ChargeUseCase charge;
    @Autowired ReleaseReservationUseCase releaseReservation;
    @Autowired ExpireReservationsUseCase expireReservations;
    @Autowired ReverseEntryUseCase reverseEntry;
    @Autowired VerifyLedgerUseCase verifyLedger;
    @Autowired WalletQueries queries;
    @Autowired LedgerRepository ledger;
    @Autowired JdbcTemplate appJdbc;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private static Money usd(long minor) {
        return Money.of(minor, MoneyTestWorld.USD);
    }

    private ChargeResult charge(World w, long amount, String key) {
        return scopes.inTenant(w.tenant().id(), () -> charge.charge(new ChargeCommand(w.wallet().id(), null, usd(amount), null, "ref", key)));
    }

    private WalletView wallet(World w) {
        return scopes.inTenant(w.tenant().id(), () -> queries.wallet(w.wallet().id()));
    }

    private long count(String table, World w) {
        return owner.count("select count(*) from " + table + " where tenant_id = ?", w.tenant().id().value());
    }

    private long outbox(World w, String type) {
        return owner.count("select count(*) from outbox where tenant_id = ? and type = ?", w.tenant().id().value(), type);
    }

    @Test
    void walletLedgerAndOutboxCommitTogether() {
        World w = worlds.prepaid(10_000);

        assertThat(wallet(w).balance()).isEqualTo(usd(10_000));
        assertThat(count("ledger_entries", w)).isEqualTo(1);
        assertThat(outbox(w, "wallet.credited")).isEqualTo(1);
        var payload = owner.jdbc().queryForObject("select payload::text from outbox where tenant_id = ? and type = 'wallet.credited'", String.class, w.tenant().id().value());
        assertThat(payload).contains("\"minorUnits\": 10000").contains("\"currency\": \"USD\"").contains(w.wallet().id().toString());
    }

    @Test
    void aFailureAfterTheOutboxWriteRollsBackWalletLedgerChargeAndOutbox() {
        World w = worlds.prepaid(10_000);
        long ledgerBefore = count("ledger_entries", w);
        long outboxBefore = count("outbox", w);
        long chargesBefore = count("charges", w);

        assertThatThrownBy(() -> scopes.runInTenant(w.tenant().id(), () -> unitOfWork.transactional(() -> {
                    charge.charge(new ChargeCommand(w.wallet().id(), null, usd(400), null, "ref", "k-rollback"));
                    throw new IllegalStateException("injected failure after the outbox write");
                })))
                .hasMessageContaining("injected failure");

        WalletView after = wallet(w);
        assertThat(after.balance()).isEqualTo(usd(10_000));
        assertThat(after.lastSequence()).isEqualTo(1);
        assertThat(count("ledger_entries", w)).isEqualTo(ledgerBefore);
        assertThat(count("outbox", w)).isEqualTo(outboxBefore);
        assertThat(count("charges", w)).isEqualTo(chargesBefore);

        // The same key can be used afterwards because nothing of the failed attempt survived.
        assertThat(charge(w, 400, "k-rollback").status()).isEqualTo(ChargeStatus.SUCCEEDED);
    }

    @Test
    void theSameKeyTwiceWritesOneEntryAndReturnsTheSameResult() {
        World w = worlds.prepaid(10_000);

        ChargeResult first = charge(w, 1_500, "once");
        ChargeResult second = charge(w, 1_500, "once");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.ledgerEntryId()).isEqualTo(first.ledgerEntryId());
        assertThat(wallet(w).balance()).isEqualTo(usd(8_500));
        assertThat(count("ledger_entries", w)).isEqualTo(2);
        assertThat(count("charges", w)).isEqualTo(1);
        assertThat(outbox(w, "charge.succeeded")).isEqualTo(1);
        assertThatThrownBy(() -> charge(w, 1_501, "once")).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void creditsAndDebitsAreIdempotentToo() {
        World w = worlds.prepaid(1_000);
        CreditCommand credit = new CreditCommand(w.wallet().id(), usd(500), "topup:pi_1", "top-up", "topup-pi_1", "system/payment");
        LedgerPostingResult first = scopes.inTenant(w.tenant().id(), () -> creditWallet.credit(credit));
        LedgerPostingResult second = scopes.inTenant(w.tenant().id(), () -> creditWallet.credit(credit));

        assertThat(second.replayed()).isTrue();
        assertThat(second.entryId()).isEqualTo(first.entryId());
        assertThat(second.balanceAfter()).as("the replay reports the balance as of the original entry").isEqualTo(first.balanceAfter()).isEqualTo(usd(1_500));
        assertThat(wallet(w).balance()).isEqualTo(usd(1_500));

        DebitCommand debit = new DebitCommand(w.wallet().id(), usd(300), "refund:re_1", "refund", "refund-re_1", "system/payment");
        scopes.inTenant(w.tenant().id(), () -> debitWallet.debit(debit));
        assertThat(scopes.inTenant(w.tenant().id(), () -> debitWallet.debit(debit)).replayed()).isTrue();
        assertThat(wallet(w).balance()).isEqualTo(usd(1_200));
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> creditWallet.credit(
                        new CreditCommand(w.wallet().id(), usd(501), "topup:pi_1", null, "topup-pi_1", "system/payment"))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aDebitCannotDriveAPrepaidWalletNegative() {
        World w = worlds.prepaid(1_000);
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> debitWallet.debit(
                        new DebitCommand(w.wallet().id(), usd(1_001), "refund:x", null, "d-1", "system/payment"))))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet(w).balance()).isEqualTo(usd(1_000));
        assertThat(count("ledger_entries", w)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesProduceOneEntry() throws Exception {
        World w = worlds.prepaid(10_000);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<ChargeResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> charge(w, 700, "parallel-same-key"), pool));
            }
            Set<Object> ids = new HashSet<>();
            for (var future : futures) {
                ids.add(future.get(60, TimeUnit.SECONDS).id());
            }
            assertThat(ids).hasSize(1);
            assertThat(wallet(w).balance()).isEqualTo(usd(9_300));
            assertThat(count("ledger_entries", w)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentChargesNeverOverdrawAndKeepTheSequenceGapFree() throws Exception {
        World w = worlds.prepaid(10_000);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<CompletableFuture<ChargeResult>> futures = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                String key = "race-" + i;
                futures.add(CompletableFuture.supplyAsync(() -> charge(w, 300, key), pool));
            }
            long succeeded = 0;
            long failed = 0;
            for (var future : futures) {
                ChargeResult result = future.get(120, TimeUnit.SECONDS);
                if (result.status() == ChargeStatus.SUCCEEDED) {
                    succeeded++;
                } else {
                    failed++;
                    assertThat(result.failureReason()).isEqualTo("insufficient_funds");
                    assertThat(result.ledgerEntryId()).isNull();
                }
            }
            assertThat(succeeded).isEqualTo(33);
            assertThat(failed).isEqualTo(27);

            WalletView after = wallet(w);
            assertThat(after.balance()).isEqualTo(usd(100));
            assertThat(after.balance().isNegative()).isFalse();
            List<Long> sequences = owner.jdbc().queryForList("select seq from ledger_entries where wallet_id = ? order by seq", Long.class, w.wallet().id().value());
            assertThat(sequences).hasSize(34);
            for (int i = 0; i < sequences.size(); i++) {
                assertThat(sequences.get(i)).isEqualTo(i + 1L);
            }
            assertThat(after.lastSequence()).isEqualTo(34);
            assertThat(owner.count("select count(*) from charges where wallet_id = ? and status = 'FAILED'", w.wallet().id().value())).isEqualTo(27);
            assertThat(outbox(w, "charge.failed")).isEqualTo(27);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theLedgerRejectsUpdateAndDeleteFromTheApplicationAndTheOwner() {
        World w = worlds.prepaid(1_000);

        assertThatThrownBy(() -> scopes.runInTenant(w.tenant().id(), () -> unitOfWork.transactional(
                        () -> appJdbc.update("update ledger_entries set reason = 'edited' where wallet_id = ?", w.wallet().id().value()))))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> scopes.runInTenant(w.tenant().id(), () -> unitOfWork.transactional(
                        () -> appJdbc.update("delete from ledger_entries where wallet_id = ?", w.wallet().id().value()))))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");

        assertThatThrownBy(() -> owner.jdbc().update("update ledger_entries set amount = 1 where wallet_id = ?", w.wallet().id().value()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> owner.jdbc().update("delete from ledger_entries where wallet_id = ?", w.wallet().id().value()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> owner.jdbc().execute("truncate ledger_entries"))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThat(count("ledger_entries", w)).isEqualTo(1);
    }

    @Test
    void anEntryInAnotherCurrencyCannotBeInserted() {
        World w = worlds.prepaid(1_000);
        assertThatThrownBy(() -> owner.jdbc().update("""
                        insert into ledger_entries (id, tenant_id, wallet_id, currency, seq, type, amount, reference, actor, idempotency_key, created_at)
                        values (?, ?, ?, 'VND', 2, 'CREDIT', 500, 'sneaky', 'attacker', 'sneaky-key', now())
                        """, UUID.randomUUID(), w.tenant().id().value(), w.wallet().id().value()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("foreign key");
    }

    @Test
    void aWrongChargeIsReversedAndReplacedLeavingThreeLinkedEntries() {
        World w = worlds.prepaid(100_000);
        ChargeResult wrong = charge(w, 50_000, "wrong-price");
        assertThat(wallet(w).balance()).isEqualTo(usd(50_000));

        var result = scopes.inTenant(w.tenant().id(), () -> reverseEntry.execute(new ReverseEntryUseCase.Command(
                wrong.ledgerEntryId(), "wrong_price",
                new ReverseEntryUseCase.Replacement(EntryType.DEBIT, usd(30_000), "charge:corrected"), "user/admin")));

        List<LedgerEntryView> entries = scopes.inTenant(w.tenant().id(), () -> queries.ledger(w.wallet().id(), 0, 50));
        assertThat(entries).extracting(LedgerEntryView::type).containsExactly(EntryType.CREDIT, EntryType.DEBIT, EntryType.REVERSAL, EntryType.DEBIT);
        assertThat(entries).extracting(e -> e.amount().minorUnits()).containsExactly(100_000L, -50_000L, 50_000L, -30_000L);
        assertThat(entries.get(2).reversalOf()).isEqualTo(entries.get(1).id());
        assertThat(entries.get(1).amount().plus(entries.get(2).amount()).isZero()).as("an entry and its reversal sum to zero").isTrue();
        assertThat(entries.get(2).reason()).isEqualTo("wrong_price");
        assertThat(entries.get(2).actor()).isEqualTo("user/admin");
        assertThat(wallet(w).balance()).isEqualTo(usd(70_000));
        assertThat(result.replacement().amount()).isEqualTo(usd(-30_000));
        assertThat(outbox(w, "ledger.entry-reversed")).isEqualTo(1);

        var again = scopes.inTenant(w.tenant().id(), () -> reverseEntry.execute(new ReverseEntryUseCase.Command(
                wrong.ledgerEntryId(), "wrong_price", null, "user/admin")));
        assertThat(again.replayed()).isTrue();
        assertThat(again.reversal().id()).isEqualTo(result.reversal().id());
        assertThat(count("ledger_entries", w)).isEqualTo(4);

        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> reverseEntry.execute(new ReverseEntryUseCase.Command(
                        result.reversal().id(), "again", null, "user/admin"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> reverseEntry.execute(new ReverseEntryUseCase.Command(
                        entries.get(0).id(), " ", null, "user/admin"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reversingATopUpIsLimitedToTheUnspentBalance() {
        World w = worlds.prepaid(0);
        var topUp = scopes.inTenant(w.tenant().id(), () -> creditWallet.credit(new CreditCommand(
                w.wallet().id(), usd(5_000), "topup:pi_9", null, "topup-9", "system/payment")));
        charge(w, 4_000, "spend");

        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> reverseEntry.execute(
                        new ReverseEntryUseCase.Command(topUp.entryId(), "chargeback", null, "user/admin"))))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet(w).balance()).isEqualTo(usd(1_000));
        assertThat(count("ledger_entries", w)).as("the rejected reversal left nothing").isEqualTo(2);
    }

    @Test
    void reservationsHoldCaptureReleaseAndReplay() {
        World w = worlds.prepaid(1_000);
        ReserveBudgetCommand reserve = new ReserveBudgetCommand(w.wallet().id(), usd(600), Duration.ofMinutes(5), "vm-1", "res-1");

        ReservationResult held = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(reserve));
        ReservationResult replay = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(reserve));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.id()).isEqualTo(held.id());
        assertThat(count("reservations", w)).isEqualTo(1);
        assertThat(wallet(w).held()).isEqualTo(usd(600));
        assertThat(wallet(w).available()).isEqualTo(usd(400));
        assertThat(count("ledger_entries", w)).as("a hold writes no ledger entry").isEqualTo(1);
        assertThat(outbox(w, "reservation.created")).isEqualTo(1);
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                        new ReserveBudgetCommand(w.wallet().id(), usd(601), Duration.ofMinutes(5), "vm-1", "res-1"))))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                        new ReserveBudgetCommand(w.wallet().id(), usd(401), Duration.ofMinutes(5), "vm-2", "res-2"))))
                .isInstanceOf(InsufficientFundsException.class);

        ChargeResult captured = scopes.inTenant(w.tenant().id(),
                () -> charge.charge(new ChargeCommand(null, held.id(), usd(250), null, "vm-1 final", "cap-1")));
        assertThat(captured.status()).isEqualTo(ChargeStatus.SUCCEEDED);
        WalletView after = wallet(w);
        assertThat(after.balance()).isEqualTo(usd(750));
        assertThat(after.held()).as("the unused 350 went back with the rest of the hold").isEqualTo(usd(0));
        assertThat(scopes.inTenant(w.tenant().id(), () -> queries.reservation(held.id())).status()).isEqualTo(ReservationStatus.CAPTURED);
        assertThat(scopes.inTenant(w.tenant().id(), () -> charge.charge(new ChargeCommand(null, held.id(), usd(250), null, "vm-1 final", "cap-1"))).replayed()).isTrue();
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> charge.charge(new ChargeCommand(null, held.id(), usd(10), null, "again", "cap-2"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> releaseReservation.execute(held.id()))).isInstanceOf(ConflictException.class);

        ReservationResult second = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                new ReserveBudgetCommand(w.wallet().id(), usd(300), Duration.ofMinutes(5), "vm-3", "res-3")));
        scopes.inTenant(w.tenant().id(), () -> releaseReservation.execute(second.id()));
        assertThat(scopes.inTenant(w.tenant().id(), () -> releaseReservation.execute(second.id())).replayed()).isTrue();
        assertThat(wallet(w).held()).isEqualTo(usd(0));
        assertThat(outbox(w, "reservation.released")).isEqualTo(1);
    }

    @Test
    void aCaptureCannotExceedTheHoldAndAnExpiredHoldCannotBeCaptured() {
        World w = worlds.prepaid(1_000);
        ReservationResult held = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                new ReserveBudgetCommand(w.wallet().id(), usd(300), Duration.ofMinutes(5), "vm", "res-x")));
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(),
                        () -> charge.charge(new ChargeCommand(null, held.id(), usd(301), null, "too much", "cap-x"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet(w).held()).as("the rejected capture changed nothing").isEqualTo(usd(300));

        owner.jdbc().update("update reservations set expires_at = now() - interval '1 second', created_at = now() - interval '1 minute' where id = ?", held.id().value());
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(),
                        () -> charge.charge(new ChargeCommand(null, held.id(), usd(100), null, "late", "cap-late"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("expired");
    }

    @Test
    void theExpiryJobReleasesLapsedHoldsOnceAndSkipsBusyWallets() throws Exception {
        World w = worlds.prepaid(1_000);
        ReservationResult held = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                new ReserveBudgetCommand(w.wallet().id(), usd(400), Duration.ofMinutes(5), "vm", "res-exp")));
        ReservationResult live = scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                new ReserveBudgetCommand(w.wallet().id(), usd(100), Duration.ofHours(1), "vm2", "res-live")));
        owner.jdbc().update("update reservations set expires_at = now() - interval '1 second', created_at = now() - interval '1 minute' where id = ?", held.id().value());

        // While another transaction holds the wallet lock, the job skips it instead of waiting.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> scopes.runInTenant(w.tenant().id(), () -> unitOfWork.transactional(() -> {
                appJdbc.queryForList("select id from wallets where id = ? for update", w.wallet().id().value());
                locked.countDown();
                try {
                    finish.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })), pool);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(scopes.inTenant(w.tenant().id(), () -> expireReservations.execute(10))).as("busy wallet is skipped").isZero();
            finish.countDown();
            holder.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(scopes.inTenant(w.tenant().id(), () -> expireReservations.execute(10))).isEqualTo(1);
        assertThat(scopes.inTenant(w.tenant().id(), () -> expireReservations.execute(10))).as("nothing left to expire").isZero();
        assertThat(scopes.inTenant(w.tenant().id(), () -> queries.reservation(held.id())).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(scopes.inTenant(w.tenant().id(), () -> queries.reservation(live.id())).status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(wallet(w).held()).isEqualTo(usd(100));
        assertThat(outbox(w, "reservation.expired")).isEqualTo(1);
    }

    @Test
    void aPostpaidWalletSpendsUpToItsLimitThenRecordsFailuresAndPaysOffDebt() {
        World w = worlds.postpaid(5_000);

        assertThat(charge(w, 3_000, "p1").status()).isEqualTo(ChargeStatus.SUCCEEDED);
        assertThat(charge(w, 2_000, "p2").status()).isEqualTo(ChargeStatus.SUCCEEDED);
        ChargeResult over = charge(w, 1, "p3");
        assertThat(over.status()).isEqualTo(ChargeStatus.FAILED);
        assertThat(over.failureReason()).isEqualTo("insufficient_funds");

        WalletView debt = wallet(w);
        assertThat(debt.balance()).isEqualTo(usd(-5_000));
        assertThat(debt.outstanding()).isEqualTo(usd(5_000));
        assertThat(charge(w, 1, "p3").replayed()).as("the recorded failure is repeatable").isTrue();

        worlds.credit(w.tenant(), w.wallet(), 2_000);
        assertThat(wallet(w).outstanding()).isEqualTo(usd(3_000));
        assertThat(charge(w, 2_000, "p4").status()).isEqualTo(ChargeStatus.SUCCEEDED);
    }

    @Test
    void theRebuildJobFindsNoMismatchAfterRandomOperationsAndFindsATamperedProjection() {
        World w = worlds.prepaid(5_000);
        Random random = new Random(7);
        List<ReservationResult> open = new ArrayList<>();
        for (int step = 0; step < 80; step++) {
            final int i = step;
            long amount = 1 + random.nextInt(400);
            switch (random.nextInt(4)) {
                case 0 -> worlds.credit(w.tenant(), w.wallet(), amount);
                case 1 -> charge(w, amount, "rnd-" + i);
                case 2 -> {
                    try {
                        open.add(scopes.inTenant(w.tenant().id(), () -> reserveBudget.reserve(
                                new ReserveBudgetCommand(w.wallet().id(), usd(amount), Duration.ofHours(1), "r", "rres-" + i))));
                    } catch (InsufficientFundsException expected) {
                        // fine
                    }
                }
                default -> {
                    if (!open.isEmpty()) {
                        ReservationResult reservation = open.remove(random.nextInt(open.size()));
                        scopes.inTenant(w.tenant().id(), () -> releaseReservation.execute(reservation.id()));
                    }
                }
            }
        }
        assertThat(scopes.inTenant(w.tenant().id(), () -> verifyLedger.execute())).isEmpty();

        owner.jdbc().update("update wallets set balance = balance + 1 where id = ?", w.wallet().id().value());
        List<LedgerVerifier.Mismatch> mismatches = scopes.inTenant(w.tenant().id(), () -> verifyLedger.execute());

        assertThat(mismatches).singleElement().satisfies(m -> {
            assertThat(m.walletId()).isEqualTo(w.wallet().id());
            assertThat(m.balance() - m.ledgerBalance()).isEqualTo(1);
        });
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'ledger.mismatch'", w.tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select balance from wallets where id = ?", w.wallet().id().value()))
                .as("the job reports but never rewrites the projection")
                .isEqualTo(mismatches.getFirst().balance());
    }

    @Test
    void walletsAndLedgersOfOneTenantAreInvisibleToAnother() {
        World a = worlds.prepaid(1_000);
        World b = worlds.prepaid(2_000);

        assertThatThrownBy(() -> scopes.inTenant(b.tenant().id(), () -> queries.wallet(a.wallet().id())))
                .isInstanceOf(dev.ngb.core_billing.platform.api.error.NotFoundException.class);
        assertThatThrownBy(() -> scopes.inTenant(b.tenant().id(), () -> charge.charge(
                        new ChargeCommand(a.wallet().id(), null, usd(1), null, "x", "cross"))))
                .isInstanceOf(dev.ngb.core_billing.platform.api.error.NotFoundException.class);
        assertThat(scopes.inTenant(b.tenant().id(), () -> ledger.findByIdempotencyKey("anything"))).isEmpty();
        assertThat(wallet(a).balance()).isEqualTo(usd(1_000));
    }
}
