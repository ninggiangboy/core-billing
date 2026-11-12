package dev.ngb.sampletenant.resource.application.order.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Estimate;
import dev.ngb.sampletenant.billing.api.model.PricingPlan;
import dev.ngb.sampletenant.billing.api.model.ReserveResult;
import dev.ngb.sampletenant.billing.api.model.SubscriptionDetail;
import dev.ngb.sampletenant.billing.api.model.UsageInput;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.resource.application.order.port.OrderQueueRepository;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scenarios 2, 3 and 4: fetch the Billing plan, estimate the cost, check the wallet, reserve the
 * estimate, and only then create the order and its queue row. A refused reservation leaves no order.
 */
public class CreateOrderUseCase {

    public record Command(UUID userId, UUID planId, String quantity, Long events) {}

    private final CatalogLookup catalog;
    private final CustomerDirectory customers;
    private final BillingPricing pricing;
    private final BillingSubscriptions subscriptions;
    private final BillingWallets wallets;
    private final OrderRepository orders;
    private final OrderQueueRepository queue;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration reservationTtl;

    public CreateOrderUseCase(
            CatalogLookup catalog,
            CustomerDirectory customers,
            BillingPricing pricing,
            BillingSubscriptions subscriptions,
            BillingWallets wallets,
            OrderRepository orders,
            OrderQueueRepository queue,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids,
            Duration reservationTtl) {
        this.catalog = catalog;
        this.customers = customers;
        this.pricing = pricing;
        this.subscriptions = subscriptions;
        this.wallets = wallets;
        this.orders = orders;
        this.queue = queue;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.ids = ids;
        this.reservationTtl = reservationTtl;
    }

    public Order execute(Command command) {
        CatalogLookup.PlanSnapshot plan = catalog.get(command.planId());
        if (!plan.billable()) {
            throw new BadRequestException("this plan is not bound to a Billing plan yet");
        }
        CustomerDirectory.CustomerSnapshot customer = customers.byUserId(command.userId());
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        PricingPlan billingPlan = pricing.plan(plan.billingPlanId());
        if ("RETIRED".equals(billingPlan.status())) {
            throw new BadRequestException("this Billing plan is retired");
        }
        String currency = billingPlan.currency();
        Wallet wallet = wallets.wallets(customer.billingCustomerId()).stream()
                .filter(candidate -> currency.equals(candidate.currency()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("no " + currency + " wallet for this customer"));

        String quantity = command.quantity() == null || command.quantity().isBlank() ? "1" : command.quantity();
        Long events = eventCount(plan, quantity, command.events());
        Instant now = clock.now();
        String reference = "order:" + customer.id() + ":" + UUID.randomUUID();
        String reservationId = null;
        String subscriptionId = null;
        long amount = 0;

        if (subscriptionPlan(plan)) {
            SubscriptionDetail started = subscriptions.subscribe(
                    "subscription:" + reference, wallet.id(), plan.billingPlanId());
            subscriptionId = started.subscription().id();
        } else {
            Estimate estimate = pricing.estimate(new BillingPricing.EstimateRequest(
                    plan.billingPlanId(), null, now, usage(plan, quantity, events)));
            amount = estimate.amount() == null ? 0 : estimate.amount().amount();
            if (amount > 0) {
                ReserveResult reserved = pricing.reserve(
                        "reserve:" + reference,
                        new BillingPricing.ReserveRequest(
                                wallet.id(), amount, currency, reservationTtl.toSeconds(), reference));
                reservationId = reserved.reservation().id();
            }
        }

        Order order = Order.queued(
                ids,
                customer.id(),
                customer.billingCustomerId(),
                wallet.id(),
                plan.id(),
                plan.billingPlanId(),
                plan.pricingModel(),
                plan.metric(),
                quantity,
                plan.unit(),
                events,
                amount,
                currency,
                reservationId,
                subscriptionId,
                now);
        unitOfWork.transactional(() -> {
            orders.save(order);
            queue.enqueue(order.id(), order.createdAt());
            return null;
        });
        return order;
    }

    private static boolean subscriptionPlan(CatalogLookup.PlanSnapshot plan) {
        return "SUBSCRIPTION".equals(plan.pricingModel()) || "HYBRID".equals(plan.pricingModel());
    }

    private static List<UsageInput> usage(CatalogLookup.PlanSnapshot plan, String quantity, Long events) {
        if ("SUBSCRIPTION".equals(plan.pricingModel())) {
            return List.of();
        }
        return List.of(new UsageInput(plan.metric(), quantity, events));
    }

    private static Long eventCount(CatalogLookup.PlanSnapshot plan, String quantity, Long events) {
        if (!"EVENT_BASED".equals(plan.pricingModel())) {
            return events;
        }
        if (events != null) {
            return events;
        }
        try {
            return Long.parseLong(quantity);
        } catch (NumberFormatException e) {
            throw new BadRequestException("an event-based order needs a whole quantity");
        }
    }
}
