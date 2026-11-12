package dev.ngb.billing.webhook.domain.endpoint;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a Tenant wants its events sent, and which ones. The signing secret is held only as ciphertext; the
 * plaintext exists once, in the response that creates or rotates it. {@code tenantSystemId} names the Tenant
 * System that registered it, or is null when a Tenant User owns it.
 */
public final class WebhookEndpoint {

    /** Subscribes to every event type. */
    public static final String ALL_EVENTS = "*";

    private final WebhookEndpointId id;
    private final TenantId tenantId;
    private final TenantSystemId tenantSystemId;
    private String url;
    private String description;
    private Set<String> eventTypes;
    private EndpointStatus status;
    private String keyId;
    private String secretCiphertext;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant secretRotatedAt;

    private WebhookEndpoint(
            WebhookEndpointId id, TenantId tenantId, TenantSystemId tenantSystemId, String url, String description, Set<String> eventTypes,
            EndpointStatus status, String keyId, String secretCiphertext, Instant createdAt, Instant updatedAt, Instant secretRotatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.tenantSystemId = tenantSystemId;
        this.url = Objects.requireNonNull(url, "url");
        this.description = description;
        this.eventTypes = normalize(eventTypes);
        this.status = Objects.requireNonNull(status, "status");
        this.keyId = Objects.requireNonNull(keyId, "keyId");
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext, "secretCiphertext");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.secretRotatedAt = secretRotatedAt;
    }

    public static WebhookEndpoint create(
            WebhookEndpointId id, TenantId tenantId, String url, String description, Collection<String> eventTypes, String keyId,
            String secretCiphertext, Instant now) {
        return create(id, tenantId, null, url, description, eventTypes, keyId, secretCiphertext, now);
    }

    public static WebhookEndpoint create(
            WebhookEndpointId id, TenantId tenantId, TenantSystemId tenantSystemId, String url, String description, Collection<String> eventTypes,
            String keyId, String secretCiphertext, Instant now) {
        return new WebhookEndpoint(id, tenantId, tenantSystemId, url, cleanDescription(description), Set.copyOf(eventTypes), EndpointStatus.ACTIVE, keyId,
                secretCiphertext, now, now, null);
    }

    public static WebhookEndpoint restore(
            WebhookEndpointId id, TenantId tenantId, String url, String description, Collection<String> eventTypes, EndpointStatus status,
            String keyId, String secretCiphertext, Instant createdAt, Instant updatedAt, Instant secretRotatedAt) {
        return restore(id, tenantId, null, url, description, eventTypes, status, keyId, secretCiphertext, createdAt, updatedAt, secretRotatedAt);
    }

    public static WebhookEndpoint restore(
            WebhookEndpointId id, TenantId tenantId, TenantSystemId tenantSystemId, String url, String description, Collection<String> eventTypes,
            EndpointStatus status, String keyId, String secretCiphertext, Instant createdAt, Instant updatedAt, Instant secretRotatedAt) {
        return new WebhookEndpoint(id, tenantId, tenantSystemId, url, description, Set.copyOf(eventTypes), status, keyId, secretCiphertext, createdAt,
                updatedAt, secretRotatedAt);
    }

    public void reconfigure(String newUrl, String newDescription, Collection<String> newEventTypes, Instant now) {
        this.url = Objects.requireNonNull(newUrl, "url");
        this.description = cleanDescription(newDescription);
        this.eventTypes = normalize(Set.copyOf(newEventTypes));
        this.updatedAt = now;
    }

    public void changeStatus(EndpointStatus newStatus, Instant now) {
        this.status = Objects.requireNonNull(newStatus, "status");
        this.updatedAt = now;
    }

    public void rotateSecret(String newKeyId, String newCiphertext, Instant now) {
        this.keyId = Objects.requireNonNull(newKeyId, "keyId");
        this.secretCiphertext = Objects.requireNonNull(newCiphertext, "secretCiphertext");
        this.secretRotatedAt = now;
        this.updatedAt = now;
    }

    public boolean subscribesTo(String eventType) {
        return status == EndpointStatus.ACTIVE && (eventTypes.contains(ALL_EVENTS) || eventTypes.contains(eventType));
    }

    private static Set<String> normalize(Set<String> types) {
        if (types.isEmpty()) {
            throw new IllegalArgumentException("subscribe to at least one event type");
        }
        return java.util.Collections.unmodifiableSet(new TreeSet<>(types));
    }

    private static String cleanDescription(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() > 500) {
            throw new IllegalArgumentException("the description is limited to 500 characters");
        }
        return text.strip();
    }

    public WebhookEndpointId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    /** The Tenant System that owns this endpoint, or null when a Tenant User owns it. */
    public TenantSystemId tenantSystemId() {
        return tenantSystemId;
    }

    public String url() {
        return url;
    }

    public String description() {
        return description;
    }

    public Set<String> eventTypes() {
        return eventTypes;
    }

    public EndpointStatus status() {
        return status;
    }

    public String keyId() {
        return keyId;
    }

    public String secretCiphertext() {
        return secretCiphertext;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant secretRotatedAt() {
        return secretRotatedAt;
    }
}
