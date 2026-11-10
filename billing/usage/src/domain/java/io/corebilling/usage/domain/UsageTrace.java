package io.corebilling.usage.domain;

import io.corebilling.platform.api.TenantSystemId;

/** Where a usage event came from: the tenant system that reported it, its resource, and its own event id. */
public record UsageTrace(TenantSystemId tenantSystemId, String resourceRef, String sourceEventId) {

    public static final int MAX_LENGTH = 255;

    public UsageTrace {
        resourceRef = clean(resourceRef, "resourceRef");
        sourceEventId = clean(sourceEventId, "sourceEventId");
    }

    private static String clean(String text, String name) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(name + " is limited to " + MAX_LENGTH + " characters");
        }
        return text.strip();
    }
}
