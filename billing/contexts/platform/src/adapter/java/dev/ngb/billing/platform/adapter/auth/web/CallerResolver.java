package dev.ngb.billing.platform.adapter.auth.web;

import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.audit.Audit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Builds the {@link Audit} recorded in audit entries from the authenticated actor and the request id. */
public class CallerResolver {

    private final CurrentActor currentActor;

    public CallerResolver(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    public Audit current() {
        return Audit.of(currentActor.get(), suppliedRequestId());
    }

    private static String suppliedRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader("X-Request-Id");
        }
        return null;
    }
}
