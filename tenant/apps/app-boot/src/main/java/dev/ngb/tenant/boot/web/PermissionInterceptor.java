package dev.ngb.tenant.boot.web;

import dev.ngb.tenant.identity.api.security.AuthenticatedUser;
import dev.ngb.tenant.identity.api.security.RequiresPermission;
import dev.ngb.tenant.shared.error.ForbiddenException;
import dev.ngb.tenant.shared.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RequiresPermission} on the authenticated business user. */
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            required = method.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("authentication is required");
        }
        if (!user.permits(required.value())) {
            throw new ForbiddenException("missing permission " + required.value().code());
        }
        return true;
    }
}
