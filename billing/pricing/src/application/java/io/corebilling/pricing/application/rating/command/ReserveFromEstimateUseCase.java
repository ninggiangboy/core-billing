package io.corebilling.pricing.application.rating.command;

import io.corebilling.money.api.reservation.ReservationResult;
import io.corebilling.money.api.reservation.ReserveBudget;
import io.corebilling.money.api.reservation.ReserveBudgetCommand;
import io.corebilling.money.api.wallet.WalletLookup;
import io.corebilling.money.api.wallet.WalletSummary;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.pricing.domain.metric.Measure;
import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Holds the estimated cost of a resource on the customer's wallet before the resource runs. */
public class ReserveFromEstimateUseCase {

    public record Command(
            WalletId walletId, PlanId planId, Integer version, Instant at, Map<String, Measure> usage, Duration ttl, String reference, String idempotencyKey) {}

    public record Result(EstimateCostUseCase.Estimate estimate, ReservationResult reservation) {}

    private final UnitOfWork unitOfWork;
    private final EstimateCostUseCase estimates;
    private final ReserveBudget reserve;
    private final WalletLookup wallets;

    public ReserveFromEstimateUseCase(UnitOfWork unitOfWork, EstimateCostUseCase estimates, ReserveBudget reserve, WalletLookup wallets) {
        this.unitOfWork = unitOfWork;
        this.estimates = estimates;
        this.reserve = reserve;
        this.wallets = wallets;
    }

    /** @throws io.corebilling.money.api.wallet.InsufficientFundsException when the wallet cannot cover the estimate */
    public Result reserve(Command command) {
        return unitOfWork.transactional(() -> {
            WalletSummary wallet = wallets.find(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            EstimateCostUseCase.Estimate estimate = estimates.estimate(new EstimateCostUseCase.Request(command.planId(), command.version(), command.at(), command.usage()));
            if (!wallet.currency().equals(estimate.plan().currency())) {
                throw new IllegalArgumentException("the wallet is in " + wallet.currency() + " but the plan is priced in " + estimate.plan().currency());
            }
            if (!estimate.quote().amount().isPositive()) {
                throw new IllegalArgumentException("the estimate is zero, so there is nothing to reserve");
            }
            ReservationResult reservation = reserve.reserve(new ReserveBudgetCommand(
                    command.walletId(), estimate.quote().amount(), command.ttl(), command.reference(), command.idempotencyKey()));
            return new Result(estimate, reservation);
        });
    }
}
