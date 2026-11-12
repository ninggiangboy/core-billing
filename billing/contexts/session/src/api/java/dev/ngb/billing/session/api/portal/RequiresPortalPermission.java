package dev.ngb.billing.session.api.portal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the session permission a portal endpoint needs, enforced by the same interceptor as every other endpoint. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPortalPermission {

    PortalPermission value();
}
