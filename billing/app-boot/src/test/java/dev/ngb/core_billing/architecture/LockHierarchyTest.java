package dev.ngb.core_billing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The lock hierarchy is documented in {@code docs/concurrency-and-locking.md}, and the one shape the rules forbid —
 * a blocking advisory lock — cannot appear in code or a migration without turning this test red.
 */
class LockHierarchyTest {

    /** The try forms are allowed; these two wait for the lock and are not. */
    private static final Pattern BLOCKING_ADVISORY_LOCK = Pattern.compile("pg_advisory_(xact_)?lock\\s*\\(");

    @Test
    void noBlockingAdvisoryLockAppearsInSourcesOrMigrations() {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of(".."))) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/"))
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                    .forEach(path -> {
                        try {
                            if (BLOCKING_ADVISORY_LOCK.matcher(Files.readString(path)).find()) {
                                offenders.add(path.toString());
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(offenders).as("only pg_try_advisory_lock is allowed").isEmpty();
    }

    @Test
    void theLockHierarchyHasADocument() {
        assertThat(Path.of("..", "..", "docs", "concurrency-and-locking.md")).exists();
    }
}
