package dev.ngb.core_billing.money.adapter.charge.persistence;

import static dev.ngb.core_billing_jooq.Tables.CHARGES;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeSource;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.port.ChargeRepository;
import dev.ngb.core_billing.money.domain.charge.Charge;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.util.Causes;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.ChargesRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqChargeRepository implements ChargeRepository {

    private final DSLContext dsl;

    public JooqChargeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Charge c) {
        try {
            dsl.insertInto(CHARGES)
                    .set(CHARGES.ID, c.id().value())
                    .set(CHARGES.TENANT_ID, c.tenantId().value())
                    .set(CHARGES.WALLET_ID, c.walletId().value())
                    .set(CHARGES.CURRENCY, c.amount().currency().code())
                    .set(CHARGES.AMOUNT, c.amount().minorUnits())
                    .set(CHARGES.SOURCE, c.source().name())
                    .set(CHARGES.STATUS, c.status().name())
                    .set(CHARGES.FAILURE_REASON, c.failureReason())
                    .set(CHARGES.RESERVATION_ID, c.reservationId() == null ? null : c.reservationId().value())
                    .set(CHARGES.USAGE_REF, c.usageRef())
                    .set(CHARGES.REFERENCE, c.reference())
                    .set(CHARGES.IDEMPOTENCY_KEY, c.idempotencyKey())
                    .set(CHARGES.REQUEST_HASH, c.requestHash())
                    .set(CHARGES.CREATED_AT, c.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key was already used for a charge");
            }
            throw e;
        }
    }

    @Override
    public Optional<Charge> findById(ChargeId id) {
        return dsl.selectFrom(CHARGES)
                .where(CHARGES.ID.eq(id.value()))
                .fetchOptional(JooqChargeRepository::toDomain);
    }

    @Override
    public Optional<Charge> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(CHARGES)
                .where(CHARGES.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(JooqChargeRepository::toDomain);
    }

    private static Charge toDomain(ChargesRecord row) {
        return Charge.restore(
                new ChargeId(row.getId()),
                new TenantId(row.getTenantId()),
                new WalletId(row.getWalletId()),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                ChargeSource.valueOf(row.getSource()),
                ChargeStatus.valueOf(row.getStatus()),
                row.getFailureReason(),
                row.getReservationId() == null ? null : new ReservationId(row.getReservationId()),
                row.getUsageRef(),
                row.getReference(),
                row.getIdempotencyKey(),
                row.getRequestHash(),
                row.getCreatedAt());
    }
}
