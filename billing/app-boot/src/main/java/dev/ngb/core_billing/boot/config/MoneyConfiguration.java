package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.adapter.persistence.JdbcChargeRepository;
import dev.ngb.core_billing.money.adapter.persistence.JdbcLedgerRepository;
import dev.ngb.core_billing.money.adapter.persistence.JdbcLedgerVerifier;
import dev.ngb.core_billing.money.adapter.persistence.JdbcReservationRepository;
import dev.ngb.core_billing.money.adapter.persistence.JdbcWalletRepository;
import dev.ngb.core_billing.money.adapter.persistence.charge.JdbcChargeReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.ledger.JdbcLedgerReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.reservation.JdbcReservationReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.wallet.JdbcWalletReadRepository;
import dev.ngb.core_billing.money.api.charge.ChargeLookup;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.application.charge.port.ChargeRepository;
import dev.ngb.core_billing.money.application.charge.query.ChargeLookupService;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;
import dev.ngb.core_billing.money.application.charge.command.ChargeUseCase;
import dev.ngb.core_billing.money.application.wallet.command.CreditWalletUseCase;
import dev.ngb.core_billing.money.application.wallet.command.DebitWalletUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ExpireReservationsUseCase;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.ledger.query.LedgerReadRepository;
import dev.ngb.core_billing.money.application.ledger.command.LedgerVerifier;
import dev.ngb.core_billing.money.application.wallet.command.OpenWalletUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ReleaseReservationUseCase;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;
import dev.ngb.core_billing.money.application.reservation.query.ReservationReadRepository;
import dev.ngb.core_billing.money.application.reservation.command.ReserveBudgetUseCase;
import dev.ngb.core_billing.money.application.ledger.command.ReverseEntryUseCase;
import dev.ngb.core_billing.money.application.ledger.command.VerifyLedgerUseCase;
import dev.ngb.core_billing.money.application.wallet.query.WalletLookupService;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;
import dev.ngb.core_billing.money.application.wallet.query.WalletReadRepository;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
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
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, CustomerLookup customers,
            IdGenerator ids, Clock clock) {
        return new ReserveBudgetUseCase(unitOfWork, wallets, reservations, events, customers, ids, clock);
    }

    @Bean
    ChargeUseCase chargeUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, ChargeRepository charges, LedgerRepository ledger,
            EventPublisher events, CustomerLookup customers, IdGenerator ids, Clock clock) {
        return new ChargeUseCase(unitOfWork, wallets, reservations, charges, ledger, events, customers, ids, clock);
    }

    @Bean
    ReleaseReservationUseCase releaseReservationUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, CustomerLookup customers,
            IdGenerator ids, Clock clock) {
        return new ReleaseReservationUseCase(unitOfWork, wallets, reservations, events, customers, ids, clock);
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
    ChargeLookup chargeLookup(UnitOfWork unitOfWork, ChargeReadRepository charges) {
        return new ChargeLookupService(unitOfWork, charges);
    }

    @Bean
    WalletLookup walletLookup(UnitOfWork unitOfWork, WalletReadRepository wallets) {
        return new WalletLookupService(unitOfWork, wallets);
    }

    @Bean
    WalletQueries walletQueries(
            UnitOfWork unitOfWork, WalletReadRepository wallets, LedgerReadRepository ledger, ReservationReadRepository reservations,
            ChargeReadRepository charges, CustomerLookup customers) {
        return new WalletQueries(unitOfWork, wallets, ledger, reservations, charges, customers);
    }
}
