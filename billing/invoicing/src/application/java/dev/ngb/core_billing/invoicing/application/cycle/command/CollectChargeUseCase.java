package dev.ngb.core_billing.invoicing.application.cycle.command;

import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.invoicing.application.invoice.command.ChargeLineFactory;
import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code charge.succeeded}: a charge on a Postpaid wallet becomes an unbilled line of the calendar period
 * it was incurred in. A charge on a Prepaid wallet is ignored, because the wallet already paid it. The inbox and the
 * unique line per charge make a repeated event a no-op.
 */
public class CollectChargeUseCase {

    public static final String CONSUMER = "invoicing.cycle";
    public static final String EVENT_TYPE = "charge.succeeded";

    private final UnitOfWork unitOfWork;
    private final EventInbox inbox;
    private final WalletLookup wallets;
    private final UnbilledLineRepository lines;
    private final ChargeLineFactory lineFactory;
    private final CycleSettings settings;
    private final Clock clock;

    public CollectChargeUseCase(
            UnitOfWork unitOfWork, EventInbox inbox, WalletLookup wallets, UnbilledLineRepository lines, ChargeLineFactory lineFactory, CycleSettings settings,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.inbox = inbox;
        this.wallets = wallets;
        this.lines = lines;
        this.lineFactory = lineFactory;
        this.settings = settings;
        this.clock = clock;
    }

    /** @return true when a line was added */
    public boolean handle(DomainEvent event) {
        if (!EVENT_TYPE.equals(event.type())) {
            return false;
        }
        return unitOfWork.transactional(() -> {
            if (!inbox.firstTime(CONSUMER, event)) {
                return false;
            }
            Map<String, Object> data = event.payload();
            WalletSummary wallet = wallets.find(WalletId.of((String) data.get("walletId"))).orElse(null);
            if (wallet == null || wallet.mode() != WalletMode.POSTPAID) {
                return false;
            }
            Instant now = clock.now();
            Instant start = settings.cycle().calendarStart(event.occurredAt());
            if (lines.periodInvoiced(wallet.id(), start)) {
                // The period was billed before this event reached us; the line belongs to the period that is open now.
                start = settings.cycle().calendarStart(now);
            }
            InvoiceItem item = lineFactory.lineFor(UUID.fromString((String) data.get("chargeId")), (String) data.get("source"), (String) data.get("reference"),
                    amount(data, wallet.currency()), event.occurredAt());
            return lines.add(new UnbilledLine(event.tenantId(), wallet.customerId(), wallet.id(), item, start, settings.cycle().calendarEnd(start)));
        });
    }

    @SuppressWarnings("unchecked")
    private static Money amount(Map<String, Object> data, Currency walletCurrency) {
        Map<String, Object> amount = (Map<String, Object>) data.get("amount");
        Money money = Money.of(((Number) amount.get("minorUnits")).longValue(), Currency.of((String) amount.get("currency")));
        if (!money.currency().equals(walletCurrency)) {
            throw new IllegalStateException("a charge is in the currency of its wallet");
        }
        return money;
    }
}
