package dev.ngb.core_billing.platform.api.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is safe to repeat: it answers 201 when the request creates the resource and 200 when an
 * earlier identical request already did, returning that resource. The API document lists both outcomes with the
 * same body.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RepeatableCreate {

    /** What a 200 means for this endpoint. */
    String repeated() default "An earlier identical request already created it; that resource is returned";
}
