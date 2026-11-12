package dev.ngb.tenant.boot.jobs;

import dev.ngb.tenant.execution.application.queue.command.ExecuteOrdersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drains the execution queue in bounded batches. Switch it off to stop running orders. */
@Component
@ConditionalOnProperty(name = "tenant.execution.enabled", havingValue = "true", matchIfMissing = true)
class ExecutionJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionJob.class);

    private final ExecuteOrdersUseCase execute;
    private final int batchSize;

    ExecutionJob(ExecuteOrdersUseCase execute, @Value("${tenant.execution.batch-size:10}") int batchSize) {
        this.execute = execute;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${tenant.execution.poll-delay:PT2S}")
    public void tick() {
        ExecuteOrdersUseCase.Report report = execute.run(batchSize);
        if (report.claimed() > 0) {
            LOG.info(
                    "execution: claimed {}, completed {}, failed {}, retried {}",
                    report.claimed(),
                    report.completed(),
                    report.failed(),
                    report.retried());
        }
    }
}
