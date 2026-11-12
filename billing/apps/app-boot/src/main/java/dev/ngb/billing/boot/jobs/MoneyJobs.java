package dev.ngb.billing.boot.jobs;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.billing.boot.infrastructure.coordination.JobLeases;
import dev.ngb.billing.boot.infrastructure.coordination.JobRuns;
import dev.ngb.billing.money.application.ledger.command.VerifyLedgerUseCase;
import dev.ngb.billing.money.application.ledger.port.LedgerVerifier;
import dev.ngb.billing.money.application.reservation.command.ExpireReservationsUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled money maintenance: releasing expired holds, and checking every wallet's projection against its
 * ledger. A mismatch is counted in {@code billing.ledger.mismatch} and audited; nothing is repaired automatically.
 *
 * <p>The verification is a singleton: a lease makes one worker run it, and a per-day run record per tenant makes a
 * second run the same day a no-op, so the mismatch counter cannot count the same run twice. Neither the lease nor
 * the run record is held while wallets are read. It is scheduled as a short fixed delay rather than one daily
 * firing, so a worker that dies mid-round hands the remaining tenants to the next tick of another worker, and a
 * tenant whose verification failed releases its claim and is tried again on a later tick. A claim left unfinished
 * by a dead process is taken over once it is older than the lease TTL, so that tenant does not lose its day.
 */
@Profile("worker")
@Component
@ConditionalOnProperty(name = "billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MoneyJobs {

    private static final Logger LOG = LoggerFactory.getLogger(MoneyJobs.class);
    private static final String VERIFY_LEASE = "verify-ledger";
    private static final String VERIFY_RUN = "verify-ledger";
    private static final Duration VERIFY_TTL = Duration.ofMinutes(30);

    private final TenantSweep sweep;
    private final ExpireReservationsUseCase expireReservations;
    private final VerifyLedgerUseCase verifyLedger;
    private final JobLeases leases;
    private final JobRuns runs;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int batchSize;
    private final LocalTime verifyNotBefore;

    MoneyJobs(
            TenantSweep sweep,
            ExpireReservationsUseCase expireReservations,
            VerifyLedgerUseCase verifyLedger,
            JobLeases leases,
            JobRuns runs,
            Clock clock,
            MeterRegistry meters,
            @Value("${billing.jobs.expire-reservations-batch:200}") int batchSize,
            @Value("${billing.jobs.verify-ledger-not-before:03:00}") LocalTime verifyNotBefore) {
        this.sweep = sweep;
        this.expireReservations = expireReservations;
        this.verifyLedger = verifyLedger;
        this.leases = leases;
        this.runs = runs;
        this.clock = clock;
        this.meters = meters;
        this.batchSize = batchSize;
        this.verifyNotBefore = verifyNotBefore;
    }

    @Scheduled(fixedDelayString = "${billing.jobs.expire-reservations-delay:PT30S}")
    public void expireReservations() {
        sweep.forEachActiveTenant(tenant -> {
            int expired = expireReservations.execute(batchSize);
            if (expired > 0) {
                LOG.info("expired {} reservations for tenant {}", expired, tenant);
            }
        });
    }

    @Scheduled(
            fixedDelayString = "${billing.jobs.verify-ledger-delay:PT10M}",
            initialDelayString = "${billing.jobs.verify-ledger-initial-delay:PT0S}")
    public void verifyLedgers() {
        if (LocalTime.ofInstant(clock.now(), ZoneOffset.UTC).isBefore(verifyNotBefore)) {
            return;
        }
        if (!leases.tryAcquire(VERIFY_LEASE, VERIFY_TTL)) {
            LOG.info("ledger verification is already running on another worker");
            return;
        }
        try {
            String runKey = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).toString();
            sweep.forEachActiveTenant(tenant -> verifyTenant(runKey, tenant));
        } finally {
            leases.release(VERIFY_LEASE);
        }
    }

    private void verifyTenant(String runKey, TenantId tenant) {
        if (!leases.renew(VERIFY_LEASE, VERIFY_TTL)) {
            LOG.warn("ledger verification lost its lease before tenant {}", tenant);
            return;
        }
        if (!runs.claim(VERIFY_RUN, runKey, tenant.value(), VERIFY_TTL)) {
            return;
        }
        try {
            List<LedgerVerifier.Mismatch> mismatches = verifyLedger.execute();
            if (!mismatches.isEmpty()) {
                meters.counter("billing.ledger.mismatch").increment(mismatches.size());
                LOG.error("ledger mismatch for tenant {}: {} wallets disagree with their entries", tenant, mismatches.size());
            }
            runs.complete(VERIFY_RUN, runKey, tenant.value());
        } catch (RuntimeException failure) {
            runs.release(VERIFY_RUN, runKey, tenant.value());
            throw failure;
        }
    }
}
