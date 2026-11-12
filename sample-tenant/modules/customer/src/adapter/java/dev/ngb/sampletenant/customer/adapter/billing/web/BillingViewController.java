package dev.ngb.sampletenant.customer.adapter.billing.web;

import dev.ngb.sampletenant.billing.api.model.BillingSession;
import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.application.billing.command.OpenBillingSessionUseCase;
import dev.ngb.sampletenant.customer.application.billing.command.TopUpUseCase;
import dev.ngb.sampletenant.customer.application.billing.query.CustomerBillingQueries;
import dev.ngb.sampletenant.identity.api.security.AuthenticatedUser;
import dev.ngb.sampletenant.identity.api.security.Permission;
import dev.ngb.sampletenant.identity.api.security.RequiresPermission;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal redirect and the billing views the tenant renders itself. */
@RestController
@RequestMapping("/api/billing")
public class BillingViewController {

    public record SessionRequest(String returnUrl, List<String> permissions, Long expiresInSeconds) {}

    public record TopUpRequest(String walletId, long amount, String currency) {}

    private final OpenBillingSessionUseCase openSession;
    private final TopUpUseCase topUp;
    private final CustomerBillingQueries queries;

    public BillingViewController(
            OpenBillingSessionUseCase openSession, TopUpUseCase topUp, CustomerBillingQueries queries) {
        this.openSession = openSession;
        this.topUp = topUp;
        this.queries = queries;
    }

    @PostMapping("/session")
    @RequiresPermission(Permission.BILLING_READ)
    public BillingSession session(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody SessionRequest request) {
        return openSession.execute(
                principal.id(), request.returnUrl(), request.permissions(), request.expiresInSeconds());
    }

    @GetMapping("/wallets")
    @RequiresPermission(Permission.BILLING_READ)
    public List<Wallet> wallets(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queries.wallets(principal.id());
    }

    @GetMapping("/invoices")
    @RequiresPermission(Permission.BILLING_READ)
    public List<Invoice> invoices(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queries.invoices(principal.id());
    }

    @GetMapping("/payments")
    @RequiresPermission(Permission.BILLING_READ)
    public List<Payment> payments(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queries.payments(principal.id());
    }

    @PostMapping("/top-ups")
    @RequiresPermission(Permission.TOPUP_CREATE)
    public Payment topUp(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TopUpRequest request) {
        return topUp.execute(principal.id(), request.walletId(), request.amount(), request.currency(), idempotencyKey);
    }
}
