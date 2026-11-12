package dev.ngb.billing.invoicing.domain.invoice;

/** The number a Tenant sees on an invoice: a gap-free sequence per Tenant, assigned when the invoice is issued. */
public final class InvoiceNumber {

    private InvoiceNumber() {}

    public static String format(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("invoice numbers start at 1");
        }
        return String.format("INV-%06d", sequence);
    }
}
