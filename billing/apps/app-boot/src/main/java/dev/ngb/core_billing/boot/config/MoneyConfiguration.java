package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.adapter.persistence.JooqChargeRepository;
import dev.ngb.core_billing.money.adapter.persistence.JooqLedgerRepository;
import dev.ngb.core_billing.money.adapter.persistence.JooqLedgerVerifier;
import dev.ngb.core_billing.money.adapter.persistence.JooqReservationRepository;
import dev.ngb.core_billing.money.adapter.persistence.JooqWalletRepository;
import dev.ngb.core_billing.money.adapter.persistence.charge.JooqChargeReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.ledger.JooqLedgerReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.reservation.JooqReservationReadRepository;
import dev.ngb.core_billing.money.adapter.persistence.wallet.JooqWalletReadRepository;
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
import org.jooq.DSLContext;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the money module. Holds no business rules. The use cases implement the money api
 * (ReserveBudget, ChargeWallet, CreditWallet, DebitWallet), so other contexts inject those interfaces.
 */
@Configuration(proxyBeanMethods = false)
class MoneyConfiguration {

    @Bean
    WalletRepository walletRepository(DSLContext dsl) {
        return new JooqWalletRepository(dsl);
    }

    @Bean
    LedgerRepository ledgerRepository(DSLContext dsl) {
        return new JooqLedgerRepository(dsl);
    }

    @Bean
    ReservationRepository reservationRepository(DSLContext dsl) {
        return new JooqReservationRepository(dsl);
    }

    @Bean
    ChargeRepository chargeRepository(DSLContext dsl) {
        return new JooqChargeRepository(dsl);
    }

    @Bean
    WalletReadRepository walletReadRepository(DSLContext dsl) {
        return new JooqWalletReadRepository(dsl);
    }

    @Bean
    LedgerReadRepository ledgerReadRepository(DSLContext dsl) {
        return new JooqLedgerReadRepository(dsl);
    }

    @Bean
    ReservationReadRepository reservationReadRepository(DSLContext dsl) {
        return new JooqReservationReadRepository(dsl);
    }

    @Bean
    ChargeReadRepository chargeReadRepository(DSLContext dsl) {
        return new JooqChargeReadRepository(dsl);
    }

    @Bean
    LedgerVerifier ledgerVerifier(DSLContext dsl) {
        return new JooqLedgerVerifier(dsl);
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
