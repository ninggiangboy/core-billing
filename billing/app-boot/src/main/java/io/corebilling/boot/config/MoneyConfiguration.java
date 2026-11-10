package io.corebilling.boot.config;

import io.corebilling.customer.api.CustomerLookup;
import io.corebilling.money.adapter.persistence.JdbcChargeRepository;
import io.corebilling.money.adapter.persistence.JdbcLedgerRepository;
import io.corebilling.money.adapter.persistence.JdbcLedgerVerifier;
import io.corebilling.money.adapter.persistence.JdbcReservationRepository;
import io.corebilling.money.adapter.persistence.JdbcWalletRepository;
import io.corebilling.money.api.WalletLookup;
import io.corebilling.money.application.ChargeRepository;
import io.corebilling.money.application.ChargeUseCase;
import io.corebilling.money.application.CreditWalletUseCase;
import io.corebilling.money.application.DebitWalletUseCase;
import io.corebilling.money.application.ExpireReservationsUseCase;
import io.corebilling.money.application.LedgerRepository;
import io.corebilling.money.application.LedgerVerifier;
import io.corebilling.money.application.OpenWalletUseCase;
import io.corebilling.money.application.ReleaseReservationUseCase;
import io.corebilling.money.application.ReservationRepository;
import io.corebilling.money.application.ReserveBudgetUseCase;
import io.corebilling.money.application.ReverseEntryUseCase;
import io.corebilling.money.application.VerifyLedgerUseCase;
import io.corebilling.money.application.WalletLookupService;
import io.corebilling.money.application.WalletQueries;
import io.corebilling.money.application.WalletRepository;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wires the money module. Holds no business rules. The use cases implement the money api
 * (ReserveBudget, ChargeWallet, CreditWallet, DebitWallet), so other contexts inject those interfaces.
 */
@Configuration(proxyBeanMethods = false)
class MoneyConfiguration {

    @Bean
    WalletRepository walletRepository(JdbcClient jdbc) {
        return new JdbcWalletRepository(jdbc);
    }

    @Bean
    LedgerRepository ledgerRepository(JdbcClient jdbc) {
        return new JdbcLedgerRepository(jdbc);
    }

    @Bean
    ReservationRepository reservationRepository(JdbcClient jdbc) {
        return new JdbcReservationRepository(jdbc);
    }

    @Bean
    ChargeRepository chargeRepository(JdbcClient jdbc) {
        return new JdbcChargeRepository(jdbc);
    }

    @Bean
    LedgerVerifier ledgerVerifier(JdbcClient jdbc) {
        return new JdbcLedgerVerifier(jdbc);
    }

    @Bean
    OpenWalletUseCase openWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, CustomerLookup customers, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        return new OpenWalletUseCase(unitOfWork, wallets, customers, auditLog, tenantContext, ids, clock);
    }

    @Bean
    CreditWalletUseCase creditWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        return new CreditWalletUseCase(unitOfWork, wallets, ledger, events, ids, clock);
    }

    @Bean
    DebitWalletUseCase debitWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        return new DebitWalletUseCase(unitOfWork, wallets, ledger, events, ids, clock);
    }

    @Bean
    ReserveBudgetUseCase reserveBudgetUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        return new ReserveBudgetUseCase(unitOfWork, wallets, reservations, events, ids, clock);
    }

    @Bean
    ChargeUseCase chargeUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, ChargeRepository charges, LedgerRepository ledger,
            EventPublisher events, IdGenerator ids, Clock clock) {
        return new ChargeUseCase(unitOfWork, wallets, reservations, charges, ledger, events, ids, clock);
    }

    @Bean
    ReleaseReservationUseCase releaseReservationUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        return new ReleaseReservationUseCase(unitOfWork, wallets, reservations, events, ids, clock);
    }

    @Bean
    ExpireReservationsUseCase expireReservationsUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        return new ExpireReservationsUseCase(unitOfWork, wallets, reservations, events, ids, clock);
    }

    @Bean
    ReverseEntryUseCase reverseEntryUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        return new ReverseEntryUseCase(unitOfWork, wallets, ledger, events, ids, clock);
    }

    @Bean
    VerifyLedgerUseCase verifyLedgerUseCase(
            UnitOfWork unitOfWork, LedgerVerifier verifier, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new VerifyLedgerUseCase(unitOfWork, verifier, auditLog, tenantContext, clock);
    }

    @Bean
    WalletLookup walletLookup(UnitOfWork unitOfWork, WalletRepository wallets) {
        return new WalletLookupService(unitOfWork, wallets);
    }

    @Bean
    WalletQueries walletQueries(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, ReservationRepository reservations, ChargeRepository charges) {
        return new WalletQueries(unitOfWork, wallets, ledger, reservations, charges);
    }
}
