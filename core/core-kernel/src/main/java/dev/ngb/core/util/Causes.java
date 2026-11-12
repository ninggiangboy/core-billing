package dev.ngb.core.util;

import java.sql.SQLException;
import java.util.Set;

/**
 * Walks the cause chain of a throwable, so a repository can recognise the driver exception that a
 * constraint violation surfaces as without depending on a framework type.
 */
public final class Causes {

    /** PostgreSQL: a deadlock was detected or the statement gave up waiting for a lock. */
    private static final Set<String> RETRYABLE_SQL_STATES = Set.of("40P01", "55P03");

    /** PostgreSQL: a unique constraint was violated. */
    private static final String UNIQUE_VIOLATION = "23505";

    private Causes() {}

    /** True when {@code type} appears anywhere in the cause chain of {@code throwable}. */
    public static boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * True when a {@code SQLException} in the chain is a unique violation ({@code 23505}) whose message names
     * {@code constraint} in double quotes, as PostgreSQL reports it.
     */
    public static boolean violates(Throwable throwable, String constraint) {
        String quoted = "\"" + constraint + "\"";
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(quoted)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * True when the failure is a lock wait that timed out ({@code 55P03}) or a deadlock ({@code 40P01}). Such a
     * failure says nothing about the work itself: the row was not changed, so a job must retry it on its next tick
     * instead of counting a failed attempt.
     */
    public static boolean isRetryableContention(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && RETRYABLE_SQL_STATES.contains(sql.getSQLState())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
