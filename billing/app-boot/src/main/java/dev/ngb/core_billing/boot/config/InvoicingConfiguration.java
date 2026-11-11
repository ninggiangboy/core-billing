package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.invoicing.adapter.messaging.InvoicingEventListener;
import dev.ngb.core_billing.invoicing.adapter.persistence.cycle.JdbcUnbilledLineRepository;
import dev.ngb.core_billing.invoicing.adapter.persistence.invoice.JdbcInvoiceRepository;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.cycle.command.CollectChargeUseCase;
import dev.ngb.core_billing.invoicing.application.cycle.command.CycleSettings;
import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.application.invoice.command.ApplyPaymentUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.InvoiceChargeUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.PayInvoiceUseCase;
import dev.ngb.core_billing.money.api.charge.ChargeLookup;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePayment;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import org.springframework.beans.factory.annotation.Value;
import dev.ngb.core_billing.invoicing.application.invoice.command.InvoiceIssuer;
import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the invoicing module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class InvoicingConfiguration {

    @Bean
    InvoiceRepository invoiceRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcInvoiceRepository(aggregates, jdbc);
    }

    @Bean
    InvoiceIssuer invoiceIssuer(InvoiceRepository invoices, EventPublisher events, IdGenerator ids, Clock clock) {
        return new InvoiceIssuer(invoices, events, ids, clock);
    }

    @Bean
    CycleSettings cycleSettings(
            @Value("${billing.invoicing.postpaid-cycle:MONTHLY}") BillingCycle cycle, @Value("${billing.invoicing.cycle-batch:100}") int batchSize) {
        return new CycleSettings(cycle, batchSize);
    }

    @Bean
    UnbilledLineRepository unbilledLineRepository(JdbcClient jdbc) {
        return new JdbcUnbilledLineRepository(jdbc);
    }

    @Bean
    CollectChargeUseCase collectChargeUseCase(
            UnitOfWork unitOfWork, EventInbox inbox, WalletLookup wallets, UnbilledLineRepository lines, CycleSettings settings, IdGenerator ids,
            Clock clock) {
        return new CollectChargeUseCase(unitOfWork, inbox, wallets, lines, settings, ids, clock);
    }

    @Bean
    CloseBillingCycleUseCase closeBillingCycleUseCase(
            UnitOfWork unitOfWork, UnbilledLineRepository lines, InvoiceIssuer issuer, CycleSettings settings, IdGenerator ids, Clock clock) {
        return new CloseBillingCycleUseCase(unitOfWork, lines, issuer, settings, ids, clock);
    }

    @Bean
    PayInvoiceUseCase payInvoiceUseCase(
            UnitOfWork unitOfWork, InvoiceRepository invoices, CustomerLookup customers, StartInvoicePayment payments, AuditLog auditLog,
            TenantContext tenantContext, Clock clock) {
        return new PayInvoiceUseCase(unitOfWork, invoices, customers, payments, auditLog, tenantContext, clock);
    }

    @Bean
    InvoiceChargeUseCase invoiceChargeUseCase(
            UnitOfWork unitOfWork, ChargeLookup charges, WalletLookup wallets, CustomerLookup customers, InvoiceRepository invoices,
            UnbilledLineRepository unbilled, InvoiceIssuer issuer, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new InvoiceChargeUseCase(unitOfWork, charges, wallets, customers, invoices, unbilled, issuer, auditLog, tenantContext, ids, clock);
    }

    @Bean
    ApplyPaymentUseCase applyPaymentUseCase(
            UnitOfWork unitOfWork, EventInbox inbox, InvoiceRepository invoices, CreditWallet credit, EventPublisher events, IdGenerator ids, Clock clock) {
        return new ApplyPaymentUseCase(unitOfWork, inbox, invoices, credit, events, ids, clock);
    }

    @Bean
    InvoicingEventListener invoicingEventListener(CollectChargeUseCase collectCharge, ApplyPaymentUseCase applyPayment) {
        return new InvoicingEventListener(collectCharge, applyPayment);
    }
}
