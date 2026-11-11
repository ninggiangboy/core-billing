package dev.ngb.core_billing.invoicing.application.messaging.service;

/**
 * The names invoicing consumes outbox events under. A name is part of the inbox key
 * {@code (tenant_id, consumer, event_id)}: it must never change once released, and two consumers must never share
 * one, because the second would skip every event the first has already seen.
 */
public final class InvoicingConsumers {

    /** The cycle job that turns charges into unbilled lines. */
    public static final String CYCLE = "invoicing.cycle";

    /** The consumer that applies payment results to invoices. */
    public static final String PAYMENT = "invoicing.payment";

    private InvoicingConsumers() {}
}
