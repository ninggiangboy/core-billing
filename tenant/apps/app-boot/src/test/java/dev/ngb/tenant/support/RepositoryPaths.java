package dev.ngb.tenant.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the repository root and the tenant build root from the working directory Gradle gives a
 * test. A test never counts {@code ..} segments, so moving a module between directories cannot silently
 * point a scan at the wrong place or make it find nothing.
 */
public final class RepositoryPaths {

    private static final Path ROOT = locateRoot();

    private RepositoryPaths() {}

    /** The repository root: the nearest ancestor holding both {@code contracts} and {@code tenant}. */
    public static Path repositoryRoot() {
        return ROOT;
    }

    /** The tenant build root, which holds {@code settings.gradle.kts} and every module directory. */
    public static Path sampleTenantRoot() {
        Path sampleTenant = ROOT.resolve("tenant");
        if (!Files.isDirectory(sampleTenant)) {
            throw new IllegalStateException("tenant directory not found under " + ROOT);
        }
        return sampleTenant;
    }

    private static Path locateRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("contracts")) && Files.isDirectory(dir.resolve("tenant"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository root not found above " + Path.of("").toAbsolutePath());
    }
}
