package io.corebilling.boot.web;

import io.corebilling.platform.api.Authenticated;
import io.corebilling.platform.api.Authorizer;
import io.corebilling.platform.api.PublicEndpoint;
import io.corebilling.platform.api.RequiresPermission;
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

    PermissionInterceptor(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isInfrastructure(method)) {
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
