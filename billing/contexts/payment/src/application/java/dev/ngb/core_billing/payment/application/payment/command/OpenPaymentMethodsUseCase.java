package dev.ngb.core_billing.payment.application.payment.command;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.customer.api.lookup.CustomerSummary;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.StripeCustomerRepository;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.application.stripe.service.ProviderAccess;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;

/**
 * Opens the Tenant's Stripe-hosted page where a Customer manages payment methods. A provider customer is created
 * lazily on the Tenant's configuration and reused afterwards. The provider call happens outside any transaction,
 * so no database lock is held across the network.
 */
public class OpenPaymentMethodsUseCase {

    public record Command(CustomerId customerId, String returnUrl) {}

    public record Result(String url) {}

    private record Prepared(StripeConfiguration configuration, String stripeCustomerId, String customerName) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final StripeCustomerRepository stripeCustomers;
    private final CustomerLookup customers;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final TenantContext tenantContext;
    private final Clock clock;

    public OpenPaymentMethodsUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, StripeCustomerRepository stripeCustomers, CustomerLookup customers,
            PaymentProvider provider, ProviderAccess access, TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.stripeCustomers = stripeCustomers;
        this.customers = customers;
        this.provider = provider;
        this.access = access;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public Result execute(Command command) {
        Prepared prepared = unitOfWork.transactional(() -> {
            StripeConfiguration configuration = configurations.findLive().orElseThrow(() -> new ConflictException("Stripe is not configured"));
            if (!configuration.canTakePayments()) {
                throw new ConflictException("Stripe is " + configuration.status().name().toLowerCase(java.util.Locale.ROOT) + " and cannot manage payment methods yet");
            }
            CustomerSummary customer = customers.find(command.customerId()).orElseThrow(() -> new NotFoundException("customer not found"));
            String existing = stripeCustomers.find(command.customerId(), configuration.id()).orElse(null);
            return new Prepared(configuration, existing, customer.displayName());
        });
        String stripeCustomerId = prepared.stripeCustomerId();
        if (stripeCustomerId == null) {
            String created = provider.createCustomer(access.of(prepared.configuration()), command.customerId().toString(),
                    prepared.customerName(), null, "payment-methods-customer-" + command.customerId() + "-" + prepared.configuration().id());
            unitOfWork.transactional(() -> stripeCustomers.addIfAbsent(
                    tenantContext.requireTenant(), command.customerId(), prepared.configuration().id(), created, clock.now()));
            stripeCustomerId = stripeCustomers.find(command.customerId(), prepared.configuration().id()).orElse(created);
        }
        return new Result(provider.createBillingPortalSession(access.of(prepared.configuration()), stripeCustomerId, command.returnUrl()));
    }
}
