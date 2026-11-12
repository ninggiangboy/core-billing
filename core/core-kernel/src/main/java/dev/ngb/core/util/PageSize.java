package dev.ngb.core.util;

/** The size of one page of a listing request. */
public final class PageSize {

    /** The largest page a caller can ask for; anything above it is lowered to it. */
    public static final int MAX = 200;

    private PageSize() {}

    /** @return {@code requested} brought into {@code [1, MAX]}, so a listing never fails on its page size */
    public static int of(int requested) {
        return Math.max(1, Math.min(requested, MAX));
    }
}
