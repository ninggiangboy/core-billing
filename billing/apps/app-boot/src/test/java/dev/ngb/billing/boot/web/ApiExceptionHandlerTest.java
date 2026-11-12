package dev.ngb.billing.boot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

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

    @Test
    void aConnectionPoolTimeoutAnswersServiceUnavailableWithoutLeakingThePool() {
        ResponseEntity<ProblemDetail> response = handler.noConnection(
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", new IllegalStateException("HikariPool-1 - Connection is not available, request timed out after 3000ms")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail())
                .as("the caller is told to retry, not shown the pool")
                .contains("Idempotency-Key")
                .doesNotContain("HikariPool")
                .doesNotContain("3000ms");
    }
}
