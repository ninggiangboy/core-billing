package dev.ngb.billing.boot.config;

import java.sql.SQLException;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The {@code migrate} step: Flyway as the table owner, then exit. A migration that cannot take its lock is retried,
 * because {@code ALTER TABLE} waits behind a long transaction and, once queued, blocks every new query behind it.
 * The datasource profile keeps {@code lock_timeout} short so that wait ends instead of piling up.
 */
@Configuration(proxyBeanMethods = false)
@Profile("migrate")
class MigrateConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MigrateConfiguration.class);
    private static final int MAX_ATTEMPTS = 5;

    @Bean
    FlywayMigrationStrategy retryingMigrations() {
        return flyway -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    flyway.migrate();
                    return;
                } catch (FlywayException failure) {
                    if (attempt >= MAX_ATTEMPTS || !lockTimeout(failure)) {
                        throw failure;
                    }
                    LOG.warn("migration could not take its lock (attempt {} of {}), retrying", attempt, MAX_ATTEMPTS);
                    pause(attempt);
                }
            }
        };
    }

    /** A migrate step has nothing left to serve once the schema is current. */
    @Bean
    ApplicationRunner exitAfterMigration(
            ConfigurableApplicationContext context, @Value("${billing.migrate.exit:true}") boolean exit) {
        return args -> {
            if (!exit) {
                return;
            }
            int code = SpringApplication.exit(context, () -> 0);
            System.exit(code);
        };
    }

    private static boolean lockTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("lock timeout")) {
                return true;
            }
        }
        return false;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(attempt * 1_000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to retry a migration", interrupted);
        }
    }
}
