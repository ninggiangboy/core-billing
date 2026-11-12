package dev.ngb.sampletenant.webhook.adapter.event.persistence;

import dev.ngb.sampletenant.webhook.application.event.port.CustomerPeriods;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCustomerPeriods implements CustomerPeriods {

    private final JdbcTemplate jdbc;

    public JdbcCustomerPeriods(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void extend(String billingCustomerId, Instant periodEnd, Instant now) {
        jdbc.update(
                """
                insert into customer_periods (billing_customer_id, period_end, updated_at)
                values (?, ?, ?)
                on conflict (billing_customer_id) do update
                    set period_end = greatest(customer_periods.period_end, excluded.period_end),
                        updated_at = excluded.updated_at
                """,
                billingCustomerId,
                Timestamp.from(periodEnd),
                Timestamp.from(now));
    }
}
