package io.corebilling.payment.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Small helpers for reading and writing columns that JDBC handles awkwardly. */
final class Rows {

    private Rows() {}

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
