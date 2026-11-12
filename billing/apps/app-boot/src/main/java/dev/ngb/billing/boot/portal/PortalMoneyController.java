package dev.ngb.billing.boot.portal;

import dev.ngb.billing.money.application.charge.query.ChargeView;
import dev.ngb.billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.billing.money.application.wallet.query.WalletView;
import dev.ngb.billing.payment.application.payment.command.CreateTopUpUseCase;
import dev.ngb.billing.payment.domain.payment.Payment;
import dev.ngb.billing.payment.domain.payment.PaymentStatus;
import dev.ngb.billing.platform.api.idempotency.RepeatableCreate;
import dev.ngb.billing.session.api.portal.PortalPermission;
import dev.ngb.billing.session.api.portal.RequiresPortalPermission;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.money.MoneyDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The customer's wallets, balance, ledger, charges and top-ups, always scoped to the session's customer. */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api")
public class PortalMoneyController {

    public record WalletResponse(
            String id, String customerId, String currency, String mode, MoneyDto balance, MoneyDto held, MoneyDto available, MoneyDto creditLimit,
            MoneyDto outstanding, long lastSequence, Instant createdAt) {
        static WalletResponse of(WalletView wallet) {
            return new WalletResponse(wallet.id().toString(), wallet.customerId().toString(), wallet.currency().code(), wallet.mode().name(),
                    MoneyDto.of(wallet.balance()), MoneyDto.of(wallet.held()), MoneyDto.of(wallet.available()), MoneyDto.of(wallet.creditLimit()),
                    MoneyDto.of(wallet.outstanding()), wallet.lastSequence(), wallet.createdAt());
        }
    }

    public record LedgerEntryResponse(
            String id, long sequence, String type, MoneyDto amount, String reference, String reservationId, String chargeId, String reversalOf,
            String actor, String reason, Instant createdAt) {
        static LedgerEntryResponse of(LedgerEntryView entry) {
            return new LedgerEntryResponse(entry.id().toString(), entry.sequence(), entry.type().name(), MoneyDto.of(entry.amount()), entry.reference(),
                    entry.reservationId() == null ? null : entry.reservationId().toString(),
                    entry.chargeId() == null ? null : entry.chargeId().toString(),
                    entry.reversalOf() == null ? null : entry.reversalOf().toString(), entry.actor(), entry.reason(), entry.createdAt());
        }
    }

    public record ChargeResponse(
            String id, String walletId, String status, String failureReason, MoneyDto amount, String reservationId, String usageRef,
            String reference, String source, Instant createdAt) {
        static ChargeResponse of(ChargeView charge) {
            return new ChargeResponse(charge.id().toString(), charge.walletId().toString(), charge.status().name(), charge.failureReason(),
                    MoneyDto.of(charge.amount()), charge.reservationId() == null ? null : charge.reservationId().toString(), charge.usageRef(),
                    charge.reference(), charge.source().name(), charge.createdAt());
        }
    }

    public record TopUpRequest(String walletId, long amount, String currency) {}

    public record TopUpResponse(String id, String status, MoneyDto amount, String checkoutUrl) {
        static TopUpResponse of(Payment payment) {
            String url = payment.status() == PaymentStatus.REQUIRES_ACTION ? payment.checkoutUrl() : null;
            return new TopUpResponse(payment.id().toString(), payment.status().name(), MoneyDto.of(payment.amount()), url);
        }
    }

    private final PortalReadModel portal;

    public PortalMoneyController(PortalReadModel portal) {
        this.portal = portal;
    }

    @GetMapping("/wallets")
    @RequiresPortalPermission(PortalPermission.WALLET_VIEW)
    public List<WalletResponse> wallets() {
        return portal.wallets().stream().map(WalletResponse::of).toList();
    }

    @GetMapping("/wallets/{id}")
    @RequiresPortalPermission(PortalPermission.WALLET_VIEW)
    public WalletResponse wallet(@PathVariable String id) {
        return WalletResponse.of(portal.wallet(id));
    }

    @GetMapping("/wallets/{id}/ledger")
    @RequiresPortalPermission(PortalPermission.LEDGER_VIEW)
    public List<LedgerEntryResponse> ledger(
            @PathVariable String id, @RequestParam(defaultValue = "0") long afterSequence, @RequestParam(defaultValue = "50") int limit) {
        return portal.ledger(id, afterSequence, limit).stream().map(LedgerEntryResponse::of).toList();
    }

    /** Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/charges")
    @RequiresPortalPermission(PortalPermission.CHARGE_VIEW)
    public List<ChargeResponse> charges(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.charges(before == null ? null : UUID.fromString(before), limit).stream().map(ChargeResponse::of).toList();
    }

    /** Starts a top-up through the Tenant's Stripe configuration; the wallet is credited only by the verified event. */
    @PostMapping("/top-ups")
    @RepeatableCreate
    @RequiresPortalPermission(PortalPermission.TOP_UP)
    public ResponseEntity<TopUpResponse> topUp(
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody TopUpRequest request) {
        if (request.walletId() == null || request.currency() == null) {
            throw new IllegalArgumentException("walletId and currency are required");
        }
        CreateTopUpUseCase.Result result = portal.topUp(request.walletId(), Money.of(request.amount(), Currency.of(request.currency())), key, requestId);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(TopUpResponse.of(result.payment()));
    }
}
