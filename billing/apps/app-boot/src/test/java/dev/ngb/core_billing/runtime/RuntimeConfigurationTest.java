package dev.ngb.core_billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The profile files, read as shipped, own the deployment differences that a context test cannot see. */
class RuntimeConfigurationTest {

    @Test
    void apiAndWorkerNeverMigrateAndMigrateOwnsIt() {
        assertThat(value("application-api.yml", "spring", "flyway", "enabled")).isEqualTo(false);
        assertThat(value("application-worker.yml", "spring", "flyway", "enabled")).isEqualTo(false);
        assertThat(value("application-migrate.yml", "spring", "flyway", "enabled")).isEqualTo(true);
        assertThat(value("application-migrate.yml", "spring", "main", "web-application-type")).isEqualTo("none");
        assertThat(value("application-migrate.yml", "billing", "jobs", "enabled")).isEqualTo(false);
    }

    @Test
    void eachProfileSetsItsLockBudget() {
        assertThat((String) value("application-api.yml", "spring", "datasource", "hikari", "connection-init-sql"))
                .contains("lock_timeout = '30s'")
                .contains("idle_in_transaction_session_timeout = '60s'");
        assertThat((String) value("application-worker.yml", "spring", "datasource", "hikari", "connection-init-sql"))
                .contains("lock_timeout = '5s'")
                .contains("idle_in_transaction_session_timeout = '60s'");
        assertThat((String) value("application-migrate.yml", "spring", "flyway", "init-sql"))
                .contains("lock_timeout = '3s'");
    }

    @Test
    void eachProfileSizesItsOwnPools() {
        assertThat(value("application-api.yml", "spring", "datasource", "hikari", "maximum-pool-size")).isNotNull();
        assertThat(value("application-worker.yml", "spring", "datasource", "hikari", "maximum-pool-size")).isNotNull();
        assertThat(value("application-worker.yml", "spring", "task", "scheduling", "pool", "size")).isNotNull();
        assertThat(value("application-worker.yml", "spring", "task", "scheduling", "shutdown", "await-termination"))
                .as("a worker lets a running job finish")
                .isEqualTo(true);
    }

    @Test
    void theApiProfileUsesVirtualThreadsBehindAShortConnectionTimeout() {
        assertThat(value("application-api.yml", "spring", "threads", "virtual", "enabled"))
                .as("a wait for Stripe must not occupy a request thread")
                .isEqualTo(true);
        assertThat(value("application-api.yml", "spring", "datasource", "hikari", "connection-timeout"))
                .as("a request that cannot get a connection is refused in 3s, not queued for 30s")
                .isEqualTo(3000);
        assertThat(value("application-api.yml", "spring", "datasource", "hikari", "maximum-pool-size")).isNotNull();
        assertThat((String) value("application-api.yml", "spring", "datasource", "hikari", "connection-init-sql"))
                .contains("lock_timeout = '30s'");
    }

    @Test
    void theWorkerProfileStaysOnPlatformThreads() {
        assertThat(value("application-worker.yml", "spring", "threads"))
                .as("virtual threads would replace the four-thread scheduler the worker relies on")
                .isNull();
        assertThat(value("application-worker.yml", "spring", "task", "scheduling", "pool", "size")).isNotNull();
        assertThat(value("application-worker.yml", "billing", "jobs", "tenant-parallelism")).isNotNull();
    }

    @Test
    void bothServingProfilesExposeProbesAndShutDownGracefully() {
        for (String file : new String[] {"application-api.yml", "application-worker.yml"}) {
            assertThat(value(file, "management", "endpoint", "health", "probes", "enabled")).isEqualTo(true);
            assertThat(value(file, "server", "shutdown")).isEqualTo("graceful");
        }
    }

    @SuppressWarnings("unchecked")
    private static Object value(String file, String... path) {
        Object node = new Yaml().load(read(file));
        for (String key : path) {
            assertThat(node).as(file + " has no " + String.join(".", path)).isInstanceOf(Map.class);
            node = ((Map<String, Object>) node).get(key);
        }
        return node;
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of("src", "main", "resources", file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
