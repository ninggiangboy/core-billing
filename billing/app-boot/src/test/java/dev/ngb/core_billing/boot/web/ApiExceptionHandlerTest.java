package dev.ngb.core_billing.boot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void aDeadlockAnswersServiceUnavailableAndCanBeRetried() {
        ResponseEntity<ProblemDetail> response =
                handler.retryableLock(new PessimisticLockingFailureException("deadlock detected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void aLockTimeoutAnswersServiceUnavailableAndCanBeRetried() {
        ResponseEntity<ProblemDetail> response =
                handler.retryableLock(new CannotAcquireLockException("canceling statement due to lock timeout"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }
}
