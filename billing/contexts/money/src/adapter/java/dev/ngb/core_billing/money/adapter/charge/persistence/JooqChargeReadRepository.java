package dev.ngb.core_billing.money.adapter.charge.persistence;

import static dev.ngb.core_billing_jooq.Tables.CHARGES;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeSource;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.ChargesRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
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

    @Override
    public List<ChargeView> list(List<UUID> walletIds, UUID before, int limit) {
        if (walletIds.isEmpty()) {
            return List.of();
        }
        Condition filter = CHARGES.WALLET_ID.in(walletIds)
                .and(before == null ? noCondition() : CHARGES.ID.lt(before));
        return dsl.selectFrom(CHARGES)
                .where(filter)
                .orderBy(CHARGES.ID.desc())
                .limit(limit)
                .fetch(JooqChargeReadRepository::toView);
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
