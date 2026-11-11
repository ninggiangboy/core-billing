package dev.ngb.core_billing.money.adapter.charge.persistence;

import static dev.ngb.core_billing_jooq.Tables.CHARGES;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeSource;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.ChargesRecord;
import java.util.Optional;
import org.jooq.DSLContext;

/** Selects a charge straight into the view; the aggregate is never built. */
public class JooqChargeReadRepository implements ChargeReadRepository {

    private final DSLContext dsl;

    public JooqChargeReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ChargeView> findById(ChargeId id) {
        return dsl.selectFrom(CHARGES)
                .where(CHARGES.ID.eq(id.value()))
                .fetchOptional(JooqChargeReadRepository::toView);
    }

    private static ChargeView toView(ChargesRecord row) {
        return new ChargeView(
                new ChargeId(row.getId()),
                new WalletId(row.getWalletId()),
                ChargeStatus.valueOf(row.getStatus()),
                row.getFailureReason(),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                row.getReservationId() == null ? null : new ReservationId(row.getReservationId()),
                row.getUsageRef(),
                row.getReference(),
                ChargeSource.valueOf(row.getSource()),
                row.getCreatedAt());
    }
}
