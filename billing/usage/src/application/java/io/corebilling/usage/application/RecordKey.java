package io.corebilling.usage.application;

/** The shape of a usage idempotency key. */
final class RecordKey {

    private RecordKey() {}

    static void require(String key) {
        if (key == null || key.isBlank() || key.length() > 255 || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("the idempotency key must be 1-255 printable characters");
        }
    }
}
