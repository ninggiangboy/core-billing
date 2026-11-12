package dev.ngb.core.util;

/** Shapes text that is stored in a column with a bounded width. */
public final class Text {

    private Text() {}

    /** @return {@code text}, shortened to {@code max} characters, or null when {@code text} is null */
    public static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
