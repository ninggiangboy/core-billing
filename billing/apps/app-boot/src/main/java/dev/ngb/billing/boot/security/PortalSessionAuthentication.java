package dev.ngb.billing.boot.security;

import dev.ngb.billing.session.api.portal.PortalVisitor;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** A Customer Portal visitor as a Spring Security authentication. */
final class PortalSessionAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient PortalVisitor visitor;

    PortalSessionAuthentication(PortalVisitor visitor) {
        super(List.of());
        this.visitor = visitor;
        setAuthenticated(true);
    }

    PortalVisitor visitor() {
        return visitor;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return visitor;
    }
}
