package dev.ngb.core_billing.pricing.application.rating.command;

import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.pricing.domain.metric.Measure;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Holds the estimated cost of a resource on the customer's wallet before the resource runs. */
public class ReserveFromEstimateUseCase {

    public record Command(
            WalletId walletId, PlanId planId, Integer version, Instant at, Map<String, Measure> usage, Duration ttl, String reference,
            String idempotencyKey, UUID caller) {
        public Command(
                WalletId walletId, PlanId planId, Integer version, Instant at, Map<String, Measure> usage, Duration ttl, String reference,
                String idempotencyKey) {
            this(walletId, planId, version, at, usage, ttl, reference, idempotencyKey, null);
        }
    }

    public record Result(EstimateCostUseCase.Estimate estimate, ReservationResult reservation) {}

    private final UnitOfWork unitOfWork;
    private final EstimateCostUseCase estimates;
    private final ReserveBudget reserve;
    private final WalletLookup wallets;
    private final CustomerLookup customers;

    public ReserveFromEstimateUseCase(
            UnitOfWork unitOfWork, EstimateCostUseCase estimates, ReserveBudget reserve, WalletLookup wallets, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.estimates = estimates;
        this.reserve = reserve;
        this.wallets = wallets;
        this.customers = customers;
    }

    /** @throws dev.ngb.core_billing.money.api.wallet.InsufficientFundsException when the wallet cannot cover the estimate */
    public Result reserve(Command command) {
        return unitOfWork.transactional(() -> {
            WalletSummary wallet = wallets.find(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            customers.findFor(wallet.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("wallet not found"));
            EstimateCostUseCase.Estimate estimate = estimates.estimate(new EstimateCostUseCase.Request(command.planId(), command.version(), command.at(), command.usage()));
            if (!wallet.currency().equals(estimate.plan().currency())) {
                throw new IllegalArgumentException("the wallet is in %s but the plan is priced in %s".formatted(wallet.currency(), estimate.plan().currency()));
            }
            if (!estimate.quote().amount().isPositive()) {
                throw new IllegalArgumentException("the estimate is zero, so there is nothing to reserve");
            }
            ReservationResult reservation = reserve.reserve(new ReserveBudgetCommand(
                    command.walletId(), estimate.quote().amount(), command.ttl(), command.reference(), command.idempotencyKey(), command.caller()));
            return new Result(estimate, reservation);
        });
    }
}
