package dev.ngb.core_billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RepositoryPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The jOOQ tables and records in {@code jooq-schema} are generated from the Flyway migrations and committed, so a
 * normal build needs no database. This test migrates a throwaway PostgreSQL, generates again and fails when the
 * committed code is out of date. After adding or changing a migration, run
 * {@code ./gradlew :app-boot:test --tests '*JooqSchemaTest' -Djooq.update=true} and commit the result.
 */
class JooqSchemaTest {

    private static final String PACKAGE = "dev.ngb.core_billing_jooq";
    private static final Path COMMITTED = RepositoryPaths.billingRoot()
            .resolve(Path.of("libs", "jooq-schema", "src", "main", "java", PACKAGE.replace('.', '/')));

    @Test
    void committedSchemaMatchesTheMigrations() throws IOException {
        Path generated = Files.createTempDirectory("jooq-schema");
        try {
            generateFromMigrations(generated);
            Path generatedPackage = generated.resolve(PACKAGE.replace('.', '/'));
            if (Boolean.getBoolean("jooq.update")) {
                replace(COMMITTED, generatedPackage);
                return;
            }
            Map<String, String> expected = read(generatedPackage);
            Map<String, String> actual = read(COMMITTED);
            assertThat(actual.keySet())
                    .as("files in jooq-schema; regenerate with -Djooq.update=true and commit the result")
                    .containsExactlyElementsOf(expected.keySet());
            expected.forEach((file, content) -> assertThat(actual.get(file))
                    .as(file + " is out of date; regenerate with -Djooq.update=true and commit the result")
                    .isEqualTo(content));
        } finally {
            delete(generated);
        }
    }

    private static void generateFromMigrations(Path target) {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(PostgresContainerConfig.IMAGE)) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("appDbPassword", "schema-generation"))
                    .load()
                    .migrate();
            GenerationTool.generate(configuration(postgres, target));
        } catch (Exception e) {
            throw new IllegalStateException("could not generate the jOOQ schema", e);
        }
    }

    private static Configuration configuration(PostgreSQLContainer postgres, Path target) {
        return new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.getJdbcUrl())
                        .withUser(postgres.getUsername())
                        .withPassword(postgres.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema("public")
                                .withExcludes("flyway_schema_history")
                                // The domain speaks Instant, not OffsetDateTime.
                                .withForcedTypes(new ForcedType()
                                        .withName("INSTANT")
                                        .withIncludeTypes("(?i:TIMESTAMPTZ|TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE)")))
                        .withGenerate(new Generate()
                                .withRecords(true)
                                .withPojos(false)
                                .withDaos(false)
                                .withInterfaces(false)
                                .withRoutines(false)
                                .withSequences(false)
                                .withIndexes(false)
                                .withDeprecated(false)
                                .withGeneratedAnnotation(false)
                                .withFluentSetters(true)
                                .withJavaTimeTypes(true))
                        .withTarget(new Target().withPackageName(PACKAGE).withDirectory(target.toString())));
    }

    private static Map<String, String> read(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.exists(root)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString(), Files.readString(file));
            }
        }
        return files;
    }

    private static void replace(Path committed, Path generated) throws IOException {
        delete(committed);
        try (Stream<Path> walk = Files.walk(generated)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path destination = committed.resolve(generated.relativize(file).toString());
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            }
        }
    }

    private static void delete(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
