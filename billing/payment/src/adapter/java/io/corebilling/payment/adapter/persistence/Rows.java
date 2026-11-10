package io.corebilling.payment.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;

/** Small helper for writing a nullable timestamp. */
final class Rows {

    private Rows() {}

    static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
