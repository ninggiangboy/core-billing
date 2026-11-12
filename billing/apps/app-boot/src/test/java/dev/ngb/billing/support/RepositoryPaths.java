package dev.ngb.billing.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the repository root and the billing build root from the working directory Gradle gives a test.
 * A test never counts {@code ..} segments, so moving a module between directories cannot silently point a
 * scan at the wrong place or make it find nothing.
 */
public final class RepositoryPaths {

    private static final Path ROOT = locateRoot();

    private RepositoryPaths() {}

    /** The repository root: the nearest ancestor holding both {@code contracts} and {@code billing}. */
    public static Path repositoryRoot() {
        return ROOT;
    }

    /** The billing build root, which holds {@code settings.gradle.kts} and every module directory. */
    public static Path billingRoot() {
        Path billing = ROOT.resolve("billing");
        if (!Files.isDirectory(billing)) {
            throw new IllegalStateException("billing directory not found under " + ROOT);
        }
        return billing;
    }

    private static Path locateRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("contracts")) && Files.isDirectory(dir.resolve("billing"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository root not found above " + Path.of("").toAbsolutePath());
    }
}
