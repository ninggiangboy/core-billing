package dev.ngb.core_billing.money.adapter.persistence;

/** Small helper for the one JDBC failure the write side classifies. */
final class Rows {

    private Rows() {}

    static boolean duplicate(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.dao.DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
