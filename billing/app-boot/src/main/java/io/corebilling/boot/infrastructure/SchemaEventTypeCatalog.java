package io.corebilling.boot.infrastructure;

import io.corebilling.webhook.application.EventTypeCatalog;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The event types are the schemas in {@code contracts/events}, packaged on the classpath under {@code events/}:
 * the file {@code charge.succeeded.schema.json} defines the type {@code charge.succeeded}.
 */
public class SchemaEventTypeCatalog implements EventTypeCatalog {

    private static final String SUFFIX = ".schema.json";

    private final Set<String> types;

    public SchemaEventTypeCatalog() {
        Set<String> found = new TreeSet<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:events/*" + SUFFIX)) {
                String name = resource.getFilename();
                if (name != null && name.endsWith(SUFFIX)) {
                    found.add(name.substring(0, name.length() - SUFFIX.length()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read the event schemas", e);
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("no event schemas were found on the classpath under events/");
        }
        this.types = Set.copyOf(found);
    }

    @Override
    public Set<String> eventTypes() {
        return types;
    }
}
