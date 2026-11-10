package io.corebilling.boot.config;

import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.money.adapter.persistence.JdbcChargeRepository;
import io.corebilling.money.adapter.persistence.JdbcLedgerRepository;
import io.corebilling.money.adapter.persistence.JdbcLedgerVerifier;
import io.corebilling.money.adapter.persistence.JdbcReservationRepository;
import io.corebilling.money.adapter.persistence.JdbcWalletRepository;
import io.corebilling.money.adapter.persistence.charge.JdbcChargeReadRepository;
import io.corebilling.money.adapter.persistence.ledger.JdbcLedgerReadRepository;
import io.corebilling.money.adapter.persistence.reservation.JdbcReservationReadRepository;
import io.corebilling.money.adapter.persistence.wallet.JdbcWalletReadRepository;
import io.corebilling.money.api.wallet.WalletLookup;
import io.corebilling.money.application.charge.port.ChargeRepository;
import io.corebilling.money.application.charge.query.ChargeReadRepository;
import io.corebilling.money.application.charge.command.ChargeUseCase;
import io.corebilling.money.application.wallet.command.CreditWalletUseCase;
import io.corebilling.money.application.wallet.command.DebitWalletUseCase;
import io.corebilling.money.application.reservation.command.ExpireReservationsUseCase;
import io.corebilling.money.application.ledger.port.LedgerRepository;
import io.corebilling.money.application.ledger.query.LedgerReadRepository;
import io.corebilling.money.application.ledger.command.LedgerVerifier;
import io.corebilling.money.application.wallet.command.OpenWalletUseCase;
import io.corebilling.money.application.reservation.command.ReleaseReservationUseCase;
import io.corebilling.money.application.reservation.port.ReservationRepository;
import io.corebilling.money.application.reservation.query.ReservationReadRepository;
import io.corebilling.money.application.reservation.command.ReserveBudgetUseCase;
import io.corebilling.money.application.ledger.command.ReverseEntryUseCase;
import io.corebilling.money.application.ledger.command.VerifyLedgerUseCase;
import io.corebilling.money.application.wallet.query.WalletLookupService;
import io.corebilling.money.application.wallet.query.WalletQueries;
import io.corebilling.money.application.wallet.port.WalletRepository;
import io.corebilling.money.application.wallet.query.WalletReadRepository;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wires the money module. Holds no business rules. The use cases implement the money api
 * (ReserveBudget, ChargeWallet, CreditWallet, DebitWallet), so other contexts inject those interfaces.
 */
@Configuration(proxyBeanMethods = false)
class MoneyConfiguration {

    @Bean
    WalletRepository walletRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcWalletRepository(aggregates, jdbc);
    }

    @Bean
    LedgerRepository ledgerRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcLedgerRepository(aggregates, jdbc);
    }

    @Bean
    ReservationRepository reservationRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcReservationRepository(aggregates, jdbc);
    }

    @Bean
    ChargeRepository chargeRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcChargeRepository(aggregates, jdbc);
    }

    @Bean
    WalletReadRepository walletReadRepository(JdbcClient jdbc) {
        return new JdbcWalletReadRepository(jdbc);
    }

    @Bean
    LedgerReadRepository ledgerReadRepository(JdbcClient jdbc) {
        return new JdbcLedgerReadRepository(jdbc);
    }

    @Bean
    ReservationReadRepository reservationReadRepository(JdbcClient jdbc) {
        return new JdbcReservationReadRepository(jdbc);
    }

    @Bean
    ChargeReadRepository chargeReadRepository(JdbcClient jdbc) {
        return new JdbcChargeReadRepository(jdbc);
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
    WalletLookup walletLookup(UnitOfWork unitOfWork, WalletReadRepository wallets) {
        return new WalletLookupService(unitOfWork, wallets);
    }

    @Bean
    WalletQueries walletQueries(
            UnitOfWork unitOfWork, WalletReadRepository wallets, LedgerReadRepository ledger, ReservationReadRepository reservations,
            ChargeReadRepository charges) {
        return new WalletQueries(unitOfWork, wallets, ledger, reservations, charges);
    }
}
