package dev.ngb.billing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.support.RepositoryPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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

    /** Every bounded context, so a scan that reaches none of them fails instead of passing empty. */
    private static final Set<String> CONTEXTS = Set.of(
            "platform", "customer", "money", "pricing", "usage", "invoicing", "payment", "webhook");

    @Test
    void noBlockingAdvisoryLockAppearsInSourcesOrMigrations() {
        List<String> offenders = new ArrayList<>();
        Set<String> modules = new TreeSet<>();
        List<Path> sources = sources();
        assertThat(sources).as("the scan must reach every module's sources").isNotEmpty();

        for (Path path : sources) {
            modules.add(moduleName(path));
            try {
                if (BLOCKING_ADVISORY_LOCK.matcher(Files.readString(path)).find()) {
                    offenders.add(path.toString());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        assertThat(modules).as("modules the scan covered").containsAll(CONTEXTS);
        assertThat(offenders).as("only pg_try_advisory_lock is allowed").isEmpty();
    }

    @Test
    void theLockHierarchyHasADocument() {
        assertThat(RepositoryPaths.repositoryRoot().resolve("docs/concurrency-and-locking.md")).exists();
    }

    /** The module a file belongs to: the directory above {@code src}, wherever its group puts it. */
    private static String moduleName(Path file) {
        for (Path part = file; part != null; part = part.getParent()) {
            if (part.getFileName().toString().equals("src")) {
                return part.getParent().getFileName().toString();
            }
        }
        throw new IllegalStateException("no src directory above " + file);
    }

    /**
     * Every hand-written source file of every module, across all four layer source sets plus the conventional
     * {@code src/main}. Generated output under {@code build/} is never read.
     */
    private static List<Path> sources() {
        List<Path> files = new ArrayList<>();
        for (Path module : modules(RepositoryPaths.billingRoot())) {
            Path src = module.resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                files.addAll(walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                        .filter(path -> !path.toString().contains("/build/"))
                        .filter(path -> !path.toString().contains("/src/test/"))
                        .toList());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    /** Every directory holding a {@code src}, so a module grouped one or more levels down is still found. */
    private static List<Path> modules(Path root) {
        List<Path> modules = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 2)) {
            for (Path dir : walk.filter(Files::isDirectory).toList()) {
                if (!dir.equals(root) && Files.isDirectory(dir.resolve("src"))) {
                    modules.add(dir);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return modules;
    }
}
