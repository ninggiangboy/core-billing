package io.corebilling.pricing.application.rating.command;

import io.corebilling.money.api.ChargeCommand;
import io.corebilling.money.api.ChargeResult;
import io.corebilling.money.api.ChargeStatus;
import io.corebilling.money.api.ChargeWallet;
import io.corebilling.money.api.WalletLookup;
import io.corebilling.money.api.WalletSummary;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.pricing.domain.metric.Measure;
import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.pricing.domain.plan.PlanVersion;
import io.corebilling.pricing.domain.plan.PricingEvents;
import io.corebilling.pricing.domain.plan.PricingPlan;
import io.corebilling.pricing.domain.rating.Quote;
import io.corebilling.pricing.domain.rating.RatingId;
import io.corebilling.pricing.domain.rating.RatingStatus;
import io.corebilling.pricing.domain.rating.UsageRating;
import io.corebilling.pricing.domain.metric.UsageSummary;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import io.corebilling.usage.api.usage.UsageLookup;
import io.corebilling.usage.api.usage.UsageRecord;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.corebilling.pricing.application.plan.port.PlanRepository;
import io.corebilling.pricing.application.rating.port.RatingRepository;

/**
 * Prices the usage a customer accumulated in a period and charges the wallet, exactly once per usage event.
 *
 * <p>Idempotency comes from the data, not from a header: the money charge is keyed by a digest of the usage event
 * ids, and a link row per usage event ({@code usage_charges}, unique on the event) records that it is accounted
 * for. Repeating the request finds nothing left to rate and returns the earlier rating. A charge that fails for
 * lack of funds is recorded but links nothing, so after a top-up the same request rates the same events again
 * under a new attempt number. The rating, the links, the charge, the ledger entry and the events all commit in
 * one transaction.
 */
public class ChargeFromUsageUseCase {

    /** Above this many events a window must be split, to keep one transaction and one breakdown reasonable. */
    public static final int MAX_EVENTS = 10_000;

    /** The period is half-open: {@code from} included, {@code to} excluded. {@code version} defaults to the one in force at the end of the period. */
    public record Command(CustomerId customerId, WalletId walletId, PlanId planId, Integer version, Instant from, Instant to) {}

    public enum Outcome { CHARGED, FAILED, NO_CHARGE, NO_USAGE }

    /** {@code rating} is null only for {@link Outcome#NO_USAGE}. {@code replayed} is true when the events were already accounted for. */
    public record Result(Outcome outcome, UsageRating rating, boolean replayed) {}

    /** Another transaction rated the same usage first; the caller re-reads and reports its result. */
    private static final class RatedConcurrently extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RatedConcurrently() {
            super("rated concurrently", null, false, false);
        }
    }

    private final UnitOfWork unitOfWork;
    private final PlanRepository plans;
    private final RatingRepository ratings;
    private final UsageLookup usage;
    private final ChargeWallet money;
    private final WalletLookup wallets;
    private final EventPublisher events;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public ChargeFromUsageUseCase(
            UnitOfWork unitOfWork, PlanRepository plans, RatingRepository ratings, UsageLookup usage, ChargeWallet money, WalletLookup wallets,
            EventPublisher events, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.plans = plans;
        this.ratings = ratings;
        this.usage = usage;
        this.money = money;
        this.wallets = wallets;
        this.events = events;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        if (!command.from().isBefore(command.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        try {
            return unitOfWork.transactional(() -> rate(command));
        } catch (RatedConcurrently first) {
            try {
                return unitOfWork.transactional(() -> rate(command));
            } catch (RatedConcurrently second) {
                throw new ConflictException("the usage in this period is being rated by another request; try again");
            }
        }
    }

    private Result rate(Command c) {
        WalletSummary wallet = wallets.find(c.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
        if (!wallet.customerId().equals(c.customerId())) {
            throw new IllegalArgumentException("the wallet belongs to another customer");
        }
        PricingPlan plan = plans.findById(c.planId()).orElseThrow(() -> new NotFoundException("pricing plan not found"));
        if (!wallet.currency().equals(plan.currency())) {
            throw new IllegalArgumentException("the wallet is in " + wallet.currency() + " but the plan is priced in " + plan.currency());
        }
        // A retired plan can still be charged: usage that was recorded under it must be billed.
        PlanVersion version = PlanSelection.choose(plan, c.version(), c.to().minusNanos(1));
        if (version.rule().metrics().isEmpty()) {
            throw new IllegalArgumentException("this plan has no metered usage; a subscription is charged per billing cycle");
        }

        List<UsageRecord> records = usage.find(c.customerId(), version.rule().metrics().keySet(), c.from(), c.to(), MAX_EVENTS + 1);
        if (records.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("more than " + MAX_EVENTS + " usage events in this period; rate a shorter period");
        }
        if (records.isEmpty()) {
            return new Result(Outcome.NO_USAGE, null, false);
        }
        Map<UUID, RatingId> accounted = ratings.ratedAmong(records.stream().map(r -> r.id().value()).toList());
        List<UsageRecord> open = records.stream().filter(r -> !accounted.containsKey(r.id().value())).toList();
        if (open.isEmpty()) {
            UsageRating earlier = ratings.findById(accounted.get(records.get(0).id().value())).orElseThrow(() -> new IllegalStateException("rating vanished"));
            return new Result(outcome(earlier), earlier, true);
        }

        Quote quote = version.quote(plan.currency(), summarize(open));
        String setKey = setKey(open);
        int attempt = ratings.failedAttempts(setKey) + 1;
        RatingId ratingId = new RatingId(ids.newId());
        Instant now = clock.now();
        Money amount = quote.amount();

        RatingStatus status = RatingStatus.NO_CHARGE;
        UUID chargeId = null;
        String failure = null;
        if (amount.isPositive()) {
            ChargeResult charge = money.charge(new ChargeCommand(
                    c.walletId(), null, amount, "usage-set:" + setKey, "plan:" + plan.code() + "@v" + version.number(), "rating:" + setKey + ":" + attempt));
            chargeId = charge.id().value();
            status = charge.status() == ChargeStatus.FAILED ? RatingStatus.FAILED : RatingStatus.CHARGED;
            failure = charge.status() == ChargeStatus.FAILED ? charge.failureReason() : null;
        }
        UsageRating rating = new UsageRating(ratingId, tenantContext.requireTenant(), c.customerId(), c.walletId(), plan.id(), version.number(), setKey, attempt,
                c.from(), c.to(), open.size(), amount, status, chargeId, failure, quote.lines(), now);
        if (!ratings.addIfAbsent(rating)) {
            throw new RatedConcurrently();
        }
        if (rating.consumesUsage()) {
            if (ratings.link(ratingId, open.stream().map(r -> r.id().value()).toList()) != open.size()) {
                throw new RatedConcurrently();
            }
            events.publish(new PricingEvents.UsageRated(ids.newId(), rating, now));
        }
        return new Result(outcome(rating), rating, false);
    }

    private static Outcome outcome(UsageRating rating) {
        return switch (rating.status()) {
            case CHARGED -> Outcome.CHARGED;
            case FAILED -> Outcome.FAILED;
            case NO_CHARGE -> Outcome.NO_CHARGE;
        };
    }

    private static UsageSummary summarize(List<UsageRecord> records) {
        Map<String, BigDecimal> quantities = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (UsageRecord record : records) {
            quantities.merge(record.metric(), record.quantity(), BigDecimal::add);
            counts.merge(record.metric(), 1L, Long::sum);
        }
        Map<String, Measure> byMetric = new HashMap<>();
        quantities.forEach((metric, quantity) -> byMetric.put(metric, new Measure(quantity, counts.get(metric))));
        return new UsageSummary(byMetric);
    }

    /** A digest of the sorted usage event ids: the same events always give the same key. */
    static String setKey(List<UsageRecord> records) {
        List<String> sorted = new ArrayList<>(records.stream().map(r -> r.id().toString()).toList());
        sorted.sort(Comparator.naturalOrder());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join(",", sorted).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
