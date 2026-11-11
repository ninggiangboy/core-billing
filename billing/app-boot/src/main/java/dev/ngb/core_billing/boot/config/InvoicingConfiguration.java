package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.invoicing.adapter.persistence.invoice.JdbcInvoiceRepository;
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
}
