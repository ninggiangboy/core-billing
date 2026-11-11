package dev.ngb.core_billing.money.adapter.persistence;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.application.charge.port.ChargeRepository;
import dev.ngb.core_billing.money.domain.charge.Charge;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.Causes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DuplicateKeyException;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcChargeRepository implements ChargeRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, amount, source, status, failure_reason, reservation_id, "
            + "usage_ref, reference, idempotency_key, request_hash, created_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcChargeRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Charge c) {
        try {
            aggregates.insert(ChargeRow.from(c));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key was already used for a charge");
            }
            throw e;
        }
    }

    @Override
    public Optional<Charge> findById(ChargeId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), ChargeRow.class)).map(ChargeRow::toDomain);
    }

    @Override
    public Optional<Charge> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("""
                        select %s
                        from charges
                        where idempotency_key = ?
                        """.formatted(COLUMNS)).param(idempotencyKey).query(ChargeRow.class).optional()
                .map(ChargeRow::toDomain);
    }
}
