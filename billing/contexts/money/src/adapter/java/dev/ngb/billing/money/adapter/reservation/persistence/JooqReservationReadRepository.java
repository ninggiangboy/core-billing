package dev.ngb.billing.money.adapter.reservation.persistence;

import static dev.ngb.billing_jooq.Tables.RESERVATIONS;

import dev.ngb.billing.money.api.reservation.ReservationId;
import dev.ngb.billing.money.api.reservation.ReservationStatus;
import dev.ngb.billing.money.application.reservation.query.ReservationReadRepository;
import dev.ngb.billing.money.application.reservation.query.ReservationView;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing_jooq.tables.records.ReservationsRecord;
import java.util.Optional;
import org.jooq.DSLContext;

/** Selects a reservation straight into the view; the aggregate is never built. */
public class JooqReservationReadRepository implements ReservationReadRepository {

    private final DSLContext dsl;

    public JooqReservationReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ReservationView> findById(ReservationId id) {
        return dsl.selectFrom(RESERVATIONS)
                .where(RESERVATIONS.ID.eq(id.value()))
                .fetchOptional(JooqReservationReadRepository::toView);
    }

    private static ReservationView toView(ReservationsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return new ReservationView(
                new ReservationId(row.getId()),
                new WalletId(row.getWalletId()),
                ReservationStatus.valueOf(row.getStatus()),
                Money.of(row.getAmount(), money),
                Money.of(row.getCapturedAmount(), money),
                row.getExpiresAt(),
                row.getReference(),
                row.getCreatedAt(),
                row.getClosedAt());
    }
}
