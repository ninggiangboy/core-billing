package io.corebilling.platform.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is reachable without a signed-in actor because its caller proves itself another way,
 * for example a payment provider's webhook whose body is signed. The endpoint must verify that proof before
 * it acts; the security configuration lists the paths that skip authentication.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {}
