package dev.ngb.sampletenant.webhook.adapter.persistence;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.webhook.application.port.DebtBook;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDebtBook implements DebtBook {

    private final JdbcTemplate jdbc;
    private final IdGenerator ids;

    public JdbcDebtBook(JdbcTemplate jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void recordDebt(String billingCustomerId, long minorUnits, String currency, String reason, Instant occurredAt) {
        jdbc.update(
                "insert into customer_debts (id, billing_customer_id, minor_units, currency, reason, occurred_at) values (?, ?, ?, ?, ?, ?)",
                ids.newId(),
                billingCustomerId,
                minorUnits,
                currency,
                reason,
                Timestamp.from(occurredAt));
    }

    @Override
    public void clearOpenDebts(String billingCustomerId, Instant clearedAt) {
        jdbc.update(
                "update customer_debts set cleared_at = ? where billing_customer_id = ? and cleared_at is null",
                Timestamp.from(clearedAt),
                billingCustomerId);
    }
}
