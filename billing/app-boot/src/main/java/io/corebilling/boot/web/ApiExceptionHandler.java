package io.corebilling.boot.web;

import io.corebilling.payment.application.stripe.command.InvalidSignatureException;
import io.corebilling.payment.application.stripe.command.ProviderRejectedException;
import io.corebilling.payment.application.stripe.command.ProviderUnavailableException;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.money.api.InsufficientFundsException;
import io.corebilling.platform.api.AccessDeniedException;
import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.platform.application.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns application errors into RFC 9457 problem responses. Unexpected errors reveal nothing about the cause. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ConflictException.class, IdempotencyConflictException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** A reference to something missing in this tenant, or a clash with existing data. Details stay in the log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        LOG.warn("data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request refers to something that does not exist here, or clashes with existing data.");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, "The wallet cannot cover this amount.");
    }

    /** The body did not match its signature. Says nothing about why, so a caller cannot probe for the secret. */
    @ExceptionHandler(InvalidSignatureException.class)
    ProblemDetail invalidSignature(InvalidSignatureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The signature could not be verified.");
    }

    @ExceptionHandler(ProviderRejectedException.class)
    ProblemDetail providerRejected(ProviderRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    /** The payment provider is unreachable; the outcome is unknown, so repeating the request with the same Idempotency-Key is safe. */
    @ExceptionHandler(ProviderUnavailableException.class)
    ProblemDetail providerUnavailable(ProviderUnavailableException e) {
        LOG.warn("payment provider unavailable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The payment provider could not be reached. Repeat the request with the same Idempotency-Key.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to do this.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail rateLimited(RateLimitExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many requests.");
    }

    /** Money arithmetic never wraps; an amount that does not fit in 64-bit minor units is a bad request. */
    @ExceptionHandler(ArithmeticException.class)
    ProblemDetail outOfRange(ArithmeticException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The amount is out of range.");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, ServletRequestBindingException.class})
    ProblemDetail badRequest(Exception e) {
        String detail = e instanceof IllegalArgumentException
                ? e.getMessage()
                : e instanceof ServletRequestBindingException ? "A required header or parameter is missing." : "The request body is not valid.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        LOG.error("unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
    }
}
