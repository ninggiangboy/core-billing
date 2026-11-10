package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.ChargeId;
import io.corebilling.money.api.ChargeSource;
import io.corebilling.money.api.ChargeStatus;
import io.corebilling.money.api.ReservationId;
import io.corebilling.money.application.ChargeRepository;
import io.corebilling.money.domain.Charge;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcChargeRepository implements ChargeRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, amount, source, status, failure_reason, reservation_id, "
            + "usage_ref, reference, idempotency_key, request_hash, created_at";

    private static final RowMapper<Charge> MAPPER = (rs, n) -> {
        UUID reservation = Rows.uuid(rs, "reservation_id");
        return Charge.restore(
                new ChargeId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new WalletId(Rows.uuid(rs, "wallet_id")),
                Money.of(rs.getLong("amount"), Currency.of(rs.getString("currency"))),
                ChargeSource.valueOf(rs.getString("source")),
                ChargeStatus.valueOf(rs.getString("status")),
                rs.getString("failure_reason"),
                reservation == null ? null : new ReservationId(reservation),
                rs.getString("usage_ref"),
                rs.getString("reference"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                Rows.instant(rs, "created_at"));
    };

    private final JdbcClient jdbc;

    public JdbcChargeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Charge c) {
        try {
            jdbc.sql("insert into charges (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(c.id().value(), c.tenantId().value(), c.walletId().value(), c.amount().currency().code(), c.amount().minorUnits(),
                            c.source().name(), c.status().name(), c.failureReason(), c.reservationId() == null ? null : c.reservationId().value(),
                            c.usageRef(), c.reference(), c.idempotencyKey(), c.requestHash(), Timestamp.from(c.createdAt()))
                    .update();
        } catch (RuntimeException e) {
            if (Rows.duplicate(e)) {
                throw new ConflictException("this idempotency key was already used for a charge");
            }
            throw e;
        }
    }

    @Override
    public Optional<Charge> findById(ChargeId id) {
        return jdbc.sql("select " + COLUMNS + " from charges where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Charge> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from charges where idempotency_key = ?").param(idempotencyKey).query(MAPPER).optional();
    }
}
