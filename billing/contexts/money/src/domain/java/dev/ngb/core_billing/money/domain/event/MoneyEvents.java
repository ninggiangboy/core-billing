package dev.ngb.core_billing.money.domain.event;

import dev.ngb.core_billing.money.api.event.MoneyEventTypes;
import dev.ngb.core_billing.sharedkernel.event.DomainEvent;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import dev.ngb.core_billing.money.domain.charge.Charge;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.money.domain.reservation.Reservation;

/**
 * Everything the money path announces. Each event is written to the outbox in the same transaction as the
 * change it reports. Payloads are the integration contract: ids, amounts in minor units and currency codes,
 * never personal data.
 */
public final class MoneyEvents {

    private MoneyEvents() {}

    private static Map<String, Object> money(Money money) {
        return Map.of("minorUnits", money.minorUnits(), "currency", money.currency().code());
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** Money was added to a wallet. */
    public record WalletCredited(UUID eventId, LedgerEntry entry, Money balanceAfter, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "wallet.credited";

        @Override
        public TenantId tenantId() {
            return entry.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "wallet/" + entry.walletId();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("walletId", entry.walletId().toString(), "entryId", entry.id().toString(), "sequence", entry.sequence(),
                    "amount", money(entry.amount()), "balanceAfter", money(balanceAfter), "reference", entry.source().reference());
        }
    }

    /** Money was taken from a wallet outside a charge. */
    public record WalletDebited(UUID eventId, LedgerEntry entry, Money balanceAfter, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "wallet.debited";

        @Override
        public TenantId tenantId() {
            return entry.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "wallet/" + entry.walletId();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("walletId", entry.walletId().toString(), "entryId", entry.id().toString(), "sequence", entry.sequence(),
                    "amount", money(entry.amount().negate()), "balanceAfter", money(balanceAfter), "reference", entry.source().reference());
        }
    }

    public record ReservationCreated(UUID eventId, Reservation reservation, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "reservation.created";

        @Override
        public TenantId tenantId() {
            return reservation.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "reservation/" + reservation.id();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("reservationId", reservation.id().toString(), "walletId", reservation.walletId().toString(),
                    "amount", money(reservation.amount()), "expiresAt", reservation.expiresAt().toString(), "reference", reservation.reference());
        }
    }

    /** A reservation ended without a capture: released by the caller, or expired. */
    public record ReservationReleased(UUID eventId, Reservation reservation, boolean expired, Instant occurredAt) implements DomainEvent {
        public static final String RELEASED = "reservation.released";
        public static final String EXPIRED = "reservation.expired";

        @Override
        public TenantId tenantId() {
            return reservation.tenantId();
        }

        @Override
        public String type() {
            return expired ? EXPIRED : RELEASED;
        }

        @Override
        public String aggregate() {
            return "reservation/" + reservation.id();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("reservationId", reservation.id().toString(), "walletId", reservation.walletId().toString(),
                    "amount", money(reservation.amount()), "reference", reservation.reference());
        }
    }

    public record ChargeSucceeded(UUID eventId, Charge charge, LedgerEntry entry, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = MoneyEventTypes.CHARGE_SUCCEEDED;

        @Override
        public TenantId tenantId() {
            return charge.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "charge/" + charge.id();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("chargeId", charge.id().toString(), "walletId", charge.walletId().toString(), "entryId", entry.id().toString(),
                    "amount", money(charge.amount()), "source", charge.source().name(), "usageRef", charge.usageRef(),
                    "reservationId", charge.reservationId() == null ? null : charge.reservationId().toString(), "reference", charge.reference());
        }
    }

    public record ChargeFailed(UUID eventId, Charge charge, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "charge.failed";

        @Override
        public TenantId tenantId() {
            return charge.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "charge/" + charge.id();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("chargeId", charge.id().toString(), "walletId", charge.walletId().toString(), "amount", money(charge.amount()),
                    "source", charge.source().name(), "usageRef", charge.usageRef(), "reason", charge.failureReason(), "reference", charge.reference());
        }
    }

    /** An earlier entry was corrected. Invoicing reacts to this; the original entry is never edited. */
    public record EntryReversed(UUID eventId, LedgerEntry reversal, LedgerEntry original, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "ledger.entry-reversed";

        @Override
        public TenantId tenantId() {
            return reversal.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "wallet/" + reversal.walletId();
        }

        @Override
        public Map<String, Object> payload() {
            return MoneyEvents.payload("walletId", reversal.walletId().toString(), "originalEntryId", original.id().toString(),
                    "reversalEntryId", reversal.id().toString(), "amount", money(reversal.amount()), "reason", reversal.origin().reason(),
                    "chargeId", original.source().chargeId() == null ? null : original.source().chargeId().toString());
        }
    }
}
