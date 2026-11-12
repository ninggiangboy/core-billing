package dev.ngb.billing.boot.web;

import dev.ngb.billing.platform.api.actor.Authenticated;
import dev.ngb.billing.platform.api.actor.Authorizer;
import dev.ngb.billing.platform.api.actor.PublicEndpoint;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.session.api.portal.PortalAuthorizer;
import dev.ngb.billing.session.api.portal.RequiresPortalPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the permission an endpoint declares. A controller method that declares neither a permission nor
 * {@link Authenticated} (or {@link PublicEndpoint}) fails closed with a server error instead of being served, so no endpoint can be
 * exposed by forgetting an annotation.
 */
class PermissionInterceptor implements HandlerInterceptor {

    private final Authorizer authorizer;
    private final PortalAuthorizer portalAuthorizer;

    PermissionInterceptor(Authorizer authorizer, PortalAuthorizer portalAuthorizer) {
        this.authorizer = authorizer;
        this.portalAuthorizer = portalAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isInfrastructure(method)) {
            return true;
        }
        RequiresPortalPermission portal = method.getMethodAnnotation(RequiresPortalPermission.class);
        if (portal != null) {
            portalAuthorizer.require(portal.value());
            return true;
        }
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required != null) {
            authorizer.require(required.value());
            return true;
        }
        if (method.hasMethodAnnotation(Authenticated.class) || method.hasMethodAnnotation(PublicEndpoint.class)) {
            return true;
        }
        throw new IllegalStateException("endpoint declares no access rule: " + method.getShortLogMessage());
    }

    private static boolean isInfrastructure(HandlerMethod method) {
        String type = method.getBeanType().getName();
        return type.startsWith("org.springdoc.") || type.startsWith("org.springframework.");
    }
}
