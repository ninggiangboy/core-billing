package io.corebilling.platform.adapter.web;

import io.corebilling.platform.api.actor.CurrentActor;
import io.corebilling.platform.application.auth.Caller;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Builds the {@link Caller} recorded in audit entries from the authenticated actor and the request id. */
public class CallerResolver {

    private static final int MAX_REQUEST_ID = 64;

    private final CurrentActor currentActor;

    public CallerResolver(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    public Caller current() {
        return new Caller(currentActor.get().auditName(), requestId());
    }

    private static String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String supplied = request.getHeader("X-Request-Id");
            if (supplied != null && !supplied.isBlank() && supplied.length() <= MAX_REQUEST_ID && supplied.chars().allMatch(c -> c > 32 && c < 127)) {
                return supplied;
            }
        }
        return UUID.randomUUID().toString();
    }
}
