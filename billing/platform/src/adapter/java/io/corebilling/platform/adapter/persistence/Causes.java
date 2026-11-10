package io.corebilling.platform.adapter.persistence;

public final class Causes {

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
}
