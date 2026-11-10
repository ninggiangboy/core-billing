package dev.ngb.core_billing.webhook.adapter.messaging;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.webhook.application.delivery.port.EventBodyWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * The delivered document: {@code id}, {@code type}, {@code tenantId}, {@code occurredAt} and the event's own
 * {@code data}, whose shape is owned by the schema of that event type in {@code contracts/events}.
 */
public class JacksonEventBodyWriter implements EventBodyWriter {

    private final JsonMapper json;

    public JacksonEventBodyWriter(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String write(DomainEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", event.eventId().toString());
        document.put("type", event.type());
        document.put("tenantId", event.tenantId().toString());
        document.put("occurredAt", event.occurredAt().toString());
        document.put("data", event.payload());
        return json.writeValueAsString(document);
    }
}
