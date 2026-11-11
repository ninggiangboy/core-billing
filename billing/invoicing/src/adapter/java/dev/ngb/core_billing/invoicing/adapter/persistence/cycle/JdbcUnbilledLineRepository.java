package dev.ngb.core_billing.invoicing.adapter.persistence.cycle;

import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Unbilled lines are the invoice-line rows that have no invoice yet; the unique charge makes collection idempotent. */
public class JdbcUnbilledLineRepository implements UnbilledLineRepository {

    private static final String COLUMNS = "id, tenant_id, customer_id, wallet_id, currency, source, charge_id, subscription_id, description, amount, "
            + "period_start, period_end, cycle_start, cycle_end, incurred_at";

    private final JdbcClient jdbc;

    public JdbcUnbilledLineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(UnbilledLine line) {
        return jdbc.sql("""
                        insert into invoice_items (id, tenant_id, invoice_id, customer_id, wallet_id, currency, source, charge_id, subscription_id,
                                                   description, amount, period_start, period_end, cycle_start, cycle_end, incurred_at)
                        values (?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id, charge_id) do nothing
                        """)
                .params(line.item().id(), line.tenantId().value(), line.customerId().value(), line.walletId().value(), line.currency().code(),
                        line.item().source().name(), line.item().chargeId(), line.item().subscriptionId(), line.item().description(),
                        line.item().amount().minorUnits(), timestamp(line.item().periodStart()), timestamp(line.item().periodEnd()),
                        timestamp(line.cycleStart()), timestamp(line.cycleEnd()), timestamp(line.item().incurredAt()))
                .update() == 1;
    }

    @Override
    public Optional<UnbilledLine> lockByCharge(UUID chargeId) {
        return jdbc.sql("""
                        select %s
                        from invoice_items
                        where charge_id = ? and invoice_id is null
                        for update
                        """.formatted(COLUMNS)).param(chargeId)
                .query(UnbilledLineRow.class).optional().map(UnbilledLineRow::toDomain);
    }

    @Override
    public boolean periodInvoiced(WalletId walletId, Instant cycleStart) {
        return jdbc.sql("select exists (select 1 from invoices where wallet_id = ? and origin = 'POSTPAID_CYCLE' and period_start = ?)")
                .params(walletId.value(), Timestamp.from(cycleStart)).query(Boolean.class).single();
    }

    @Override
    public List<ClosedPeriod> closedPeriods(Instant now, int limit) {
        return jdbc.sql("""
                        select wallet_id, cycle_start, cycle_end from invoice_items
                        where invoice_id is null and cycle_end <= ?
                        group by wallet_id, cycle_start, cycle_end
                        order by cycle_end, wallet_id
                        limit ?
                        """)
                .params(Timestamp.from(now), limit)
                .query(PeriodRow.class).list().stream().map(PeriodRow::toPeriod).toList();
    }

    @Override
    public List<UnbilledLine> lockPeriod(WalletId walletId, Instant cycleStart) {
        return jdbc.sql("""
                        select %s
                        from invoice_items
                        where wallet_id = ? and cycle_start = ? and invoice_id is null
                        order by incurred_at, id
                        for update
                        """.formatted(COLUMNS))
                .params(walletId.value(), Timestamp.from(cycleStart))
                .query(UnbilledLineRow.class).list().stream().map(UnbilledLineRow::toDomain).toList();
    }

    private record PeriodRow(UUID walletId, Instant cycleStart, Instant cycleEnd) {
        ClosedPeriod toPeriod() {
            return new ClosedPeriod(new WalletId(walletId), cycleStart, cycleEnd);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
