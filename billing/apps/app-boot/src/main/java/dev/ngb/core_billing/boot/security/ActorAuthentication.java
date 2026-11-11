package dev.ngb.core_billing.boot.security;

import dev.ngb.core_billing.platform.api.actor.Actor;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** The authenticated {@link Actor} as a Spring Security authentication. Authorisation uses the actor's permissions, not authorities. */
final class ActorAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient Actor actor;

    ActorAuthentication(Actor actor) {
        super(List.of());
        this.actor = actor;
        setAuthenticated(true);
    }

    Actor actor() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return actor.auditName();
    }
}
