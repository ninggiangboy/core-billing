package dev.ngb.billing.payment.api.invoice;

import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.Objects;
import java.util.UUID;

/**
 * @param idempotencyKey one operation within the tenant: repeating it returns the same payment, and reusing it for a
 *     different invoice or amount is a conflict
 * @param description what the customer sees on the hosted page
 */
public record StartInvoicePaymentCommand(
        UUID invoiceId, WalletId walletId, CustomerId customerId, Money amount, String description, String idempotencyKey) {

    public StartInvoicePaymentCommand {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
