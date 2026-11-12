package dev.ngb.sampletenant.customer.application.billing.command;

import dev.ngb.sampletenant.billing.api.client.BillingSessions;
import dev.ngb.sampletenant.billing.api.model.BillingSession;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.util.List;
import java.util.UUID;

/** Scenario 8: a mapped customer opens a Billing Session for top-up. */
public class OpenBillingSessionUseCase {

    private final CustomerDirectory customers;
    private final BillingSessions sessions;
    private final String defaultReturnUrl;

    public OpenBillingSessionUseCase(CustomerDirectory customers, BillingSessions sessions, String defaultReturnUrl) {
        this.customers = customers;
        this.sessions = sessions;
        this.defaultReturnUrl = defaultReturnUrl;
    }

    public BillingSession execute(UUID userId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
        CustomerDirectory.CustomerSnapshot customer = mapped(userId);
        String target = returnUrl == null || returnUrl.isBlank() ? defaultReturnUrl : returnUrl;
        if (target == null || target.isBlank()) {
            throw new BadRequestException("a return URL is required");
        }
        return sessions.create(customer.billingCustomerId(), target, permissions, expiresInSeconds);
    }

    private CustomerDirectory.CustomerSnapshot mapped(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = customers.byUserId(userId);
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        return customer;
    }
}
