package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.money.application.reservation.command.ExpireReservationsUseCase;
import dev.ngb.core_billing.money.application.ledger.command.LedgerVerifier;
import dev.ngb.core_billing.money.application.ledger.command.VerifyLedgerUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled money maintenance: releasing expired holds, and checking every wallet's projection against its
 * ledger. A mismatch is counted in {@code billing.ledger.mismatch} and audited; nothing is repaired automatically.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MoneyJobs {

    private static final Logger LOG = LoggerFactory.getLogger(MoneyJobs.class);

    private final TenantSweep sweep;
    private final ExpireReservationsUseCase expireReservations;
    private final VerifyLedgerUseCase verifyLedger;
    private final MeterRegistry meters;
    private final int batchSize;

    MoneyJobs(
            TenantSweep sweep,
            ExpireReservationsUseCase expireReservations,
            VerifyLedgerUseCase verifyLedger,
            MeterRegistry meters,
            @Value("${billing.jobs.expire-reservations-batch:200}") int batchSize) {
        this.sweep = sweep;
        this.expireReservations = expireReservations;
        this.verifyLedger = verifyLedger;
        this.meters = meters;
        this.batchSize = batchSize;
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

    @Scheduled(cron = "${billing.jobs.verify-ledger-cron:0 0 3 * * *}")
    public void verifyLedgers() {
        sweep.forEachActiveTenant(tenant -> {
            List<LedgerVerifier.Mismatch> mismatches = verifyLedger.execute();
            if (!mismatches.isEmpty()) {
                meters.counter("billing.ledger.mismatch").increment(mismatches.size());
                LOG.error("ledger mismatch for tenant {}: {} wallets disagree with their entries", tenant, mismatches.size());
            }
        });
    }
}
