package dev.ngb.core_billing.support;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Loads the event schemas from contracts/events and validates event data against them. */
public final class EventSchemas {

    private static final String SUFFIX = ".schema.json";
    private static final Map<String, Schema> SCHEMAS = new TreeMap<>();

    static {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
        try (Stream<Path> files = Files.list(directory())) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).toList()) {
                String name = file.getFileName().toString();
                SCHEMAS.put(name.substring(0, name.length() - SUFFIX.length()),
                        registry.getSchema(Files.readString(file, StandardCharsets.UTF_8), InputFormat.JSON));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private EventSchemas() {}

    public static Path directory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("contracts/events")) && Files.isDirectory(dir.resolve("billing"))) {
                return dir.resolve("contracts/events");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("contracts/events not found");
    }

    public static Set<String> types() {
        return new TreeSet<>(SCHEMAS.keySet());
    }

    /** @return the violations of {@code dataJson} against the schema of {@code type}; empty when it conforms */
    public static List<String> validate(String type, String dataJson) {
        Schema schema = SCHEMAS.get(type);
        if (schema == null) {
            return List.of("no schema for event type " + type);
        }
        return schema.validate(dataJson, InputFormat.JSON).stream().map(Error::toString).toList();
    }
}
