package dev.ngb.core_billing.money.adapter.reservation.persistence;

import static dev.ngb.core_billing_jooq.Tables.RESERVATIONS;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.ReservationsRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqReservationRepository implements ReservationRepository {

    private final DSLContext dsl;

    public JooqReservationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Reservation r) {
        try {
            dsl.insertInto(RESERVATIONS)
                    .set(RESERVATIONS.ID, r.id().value())
                    .set(RESERVATIONS.TENANT_ID, r.tenantId().value())
                    .set(RESERVATIONS.WALLET_ID, r.walletId().value())
                    .set(RESERVATIONS.CURRENCY, r.amount().currency().code())
                    .set(RESERVATIONS.AMOUNT, r.amount().minorUnits())
                    .set(RESERVATIONS.CAPTURED_AMOUNT, r.captured().minorUnits())
                    .set(RESERVATIONS.STATUS, r.status().name())
                    .set(RESERVATIONS.EXPIRES_AT, r.expiresAt())
                    .set(RESERVATIONS.CREATED_AT, r.createdAt())
                    .set(RESERVATIONS.CLOSED_AT, r.closedAt())
                    .set(RESERVATIONS.REFERENCE, r.reference())
                    .set(RESERVATIONS.IDEMPOTENCY_KEY, r.idempotencyKey())
                    .set(RESERVATIONS.REQUEST_HASH, r.requestHash())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key was already used for a reservation");
            }
            throw e;
        }
    }

    @Override
    public void update(Reservation r) {
        dsl.update(RESERVATIONS)
                .set(RESERVATIONS.CURRENCY, r.amount().currency().code())
                .set(RESERVATIONS.AMOUNT, r.amount().minorUnits())
                .set(RESERVATIONS.CAPTURED_AMOUNT, r.captured().minorUnits())
                .set(RESERVATIONS.STATUS, r.status().name())
                .set(RESERVATIONS.EXPIRES_AT, r.expiresAt())
                .set(RESERVATIONS.CLOSED_AT, r.closedAt())
                .set(RESERVATIONS.REFERENCE, r.reference())
                .set(RESERVATIONS.IDEMPOTENCY_KEY, r.idempotencyKey())
                .set(RESERVATIONS.REQUEST_HASH, r.requestHash())
                .where(RESERVATIONS.ID.eq(r.id().value()))
                .execute();
    }

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return dsl.selectFrom(RESERVATIONS)
                .where(RESERVATIONS.ID.eq(id.value()))
                .fetchOptional(JooqReservationRepository::toDomain);
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(RESERVATIONS)
                .where(RESERVATIONS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(JooqReservationRepository::toDomain);
    }

    @Override
    public List<ReservationId> findExpired(Instant now, int limit) {
        return dsl.select(RESERVATIONS.ID)
                .from(RESERVATIONS)
                .where(RESERVATIONS.STATUS.eq("ACTIVE"))
                .and(RESERVATIONS.EXPIRES_AT.le(now))
                .orderBy(RESERVATIONS.EXPIRES_AT)
                .limit(limit)
                .fetch(RESERVATIONS.ID)
                .stream()
                .map(ReservationId::new)
                .toList();
    }

    private static Reservation toDomain(ReservationsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return Reservation.restore(
                new ReservationId(row.getId()),
                new TenantId(row.getTenantId()),
                new WalletId(row.getWalletId()),
                Money.of(row.getAmount(), money),
                Money.of(row.getCapturedAmount(), money),
                ReservationStatus.valueOf(row.getStatus()),
                row.getExpiresAt(),
                row.getCreatedAt(),
                row.getClosedAt(),
                row.getReference(),
                row.getIdempotencyKey(),
                row.getRequestHash());
    }
}
