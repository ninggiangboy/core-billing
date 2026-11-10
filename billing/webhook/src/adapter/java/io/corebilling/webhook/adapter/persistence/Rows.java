package io.corebilling.webhook.adapter.persistence;

/** Small helper for values that need shaping before they are stored. */
final class Rows {

    private Rows() {}

    static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
