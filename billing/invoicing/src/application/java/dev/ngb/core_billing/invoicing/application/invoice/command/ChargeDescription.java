package dev.ngb.core_billing.invoicing.application.invoice.command;

import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;

/** The line text of a charge, the same whether it is collected for a cycle or invoiced on its own. */
public final class ChargeDescription {

    private ChargeDescription() {}

    /** @param source USAGE, RESERVATION or STANDALONE, as the money context names it */
    public static String of(String source, String reference) {
        String text = switch (source) {
            case "USAGE" -> "Usage" + (reference == null ? "" : " (" + reference + ")");
            case "RESERVATION" -> "Charge from a reservation" + (reference == null ? "" : " (" + reference + ")");
            default -> reference == null || reference.isBlank() ? "Charge" : reference;
        };
        return text.length() > InvoiceItem.MAX_DESCRIPTION ? text.substring(0, InvoiceItem.MAX_DESCRIPTION) : text;
    }
}
