package dev.ngb.core_billing.platform.api.actor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission an endpoint needs. A request handler with neither this nor
 * {@link Authenticated} is rejected at startup of the test suite, so no endpoint is open by accident.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    Permission value();
}
