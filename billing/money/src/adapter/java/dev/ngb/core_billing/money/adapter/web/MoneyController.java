package dev.ngb.core_billing.money.adapter.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.application.charge.command.ChargeUseCase;
import dev.ngb.core_billing.money.application.wallet.command.OpenWalletUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ReleaseReservationUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ReserveBudgetUseCase;
import dev.ngb.core_billing.money.application.ledger.command.ReverseEntryUseCase;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.money.application.wallet.query.WalletView;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.reservation.query.ReservationView;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.MoneyDto;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/**
 * Wallets, reservations, charges and ledger corrections. Amounts are integers in minor units (cents, dong)
 * with an ISO 4217 currency code. Every money-moving POST needs an {@code Idempotency-Key}.
 */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class MoneyController {

    public record OpenWalletRequest(String customerId, String currency, String mode, Long creditLimit) {}

    public record WalletResponse(
            String id, String customerId, String currency, String mode, MoneyDto balance, MoneyDto held, MoneyDto available, MoneyDto creditLimit,
            MoneyDto outstanding, long lastSequence, Instant createdAt) {
        static WalletResponse of(Wallet w) {
            return new WalletResponse(w.id().toString(), w.customerId().toString(), w.currency().code(), w.mode().name(), MoneyDto.of(w.balance()),
                    MoneyDto.of(w.held()), MoneyDto.of(w.available()), MoneyDto.of(w.creditLimit()), MoneyDto.of(w.outstanding()), w.lastSequence(),
                    w.createdAt());
        }

        static WalletResponse ofView(WalletView w) {
            return new WalletResponse(w.id().toString(), w.customerId().toString(), w.currency().code(), w.mode().name(), MoneyDto.of(w.balance()),
                    MoneyDto.of(w.held()), MoneyDto.of(w.available()), MoneyDto.of(w.creditLimit()), MoneyDto.of(w.outstanding()), w.lastSequence(),
                    w.createdAt());
        }
    }

    public record LedgerEntryResponse(
            String id, long sequence, String type, MoneyDto amount, String reference, String reservationId, String chargeId, String reversalOf,
            String actor, String reason, Instant createdAt) {
        static LedgerEntryResponse of(LedgerEntry e) {
            return new LedgerEntryResponse(e.id().toString(), e.sequence(), e.type().name(), MoneyDto.of(e.amount()), e.source().reference(),
                    e.source().reservationId() == null ? null : e.source().reservationId().toString(),
                    e.source().chargeId() == null ? null : e.source().chargeId().toString(),
                    e.reversalOf() == null ? null : e.reversalOf().toString(), e.origin().actor(), e.origin().reason(), e.createdAt());
        }

        static LedgerEntryResponse ofView(LedgerEntryView e) {
            return new LedgerEntryResponse(e.id().toString(), e.sequence(), e.type().name(), MoneyDto.of(e.amount()), e.reference(),
                    e.reservationId() == null ? null : e.reservationId().toString(),
                    e.chargeId() == null ? null : e.chargeId().toString(),
                    e.reversalOf() == null ? null : e.reversalOf().toString(), e.actor(), e.reason(), e.createdAt());
        }
    }

    public record ReserveRequest(String walletId, long amount, String currency, Long ttlSeconds, String reference) {}

    public record ReservationResponse(
            String id, String walletId, String status, MoneyDto amount, MoneyDto captured, Instant expiresAt, String reference, Instant createdAt,
            Instant closedAt) {
        static ReservationResponse ofView(ReservationView r) {
            return new ReservationResponse(r.id().toString(), r.walletId().toString(), r.status().name(), MoneyDto.of(r.amount()),
                    MoneyDto.of(r.captured()), r.expiresAt(), r.reference(), r.createdAt(), r.closedAt());
        }
    }

    public record CaptureRequest(long amount, String currency, String reference) {}

    public record ChargeRequest(String walletId, long amount, String currency, String usageRef, String reference) {}

    public record ChargeResponse(
            String id, String status, String failureReason, MoneyDto amount, String ledgerEntryId, String reservationId, String usageRef,
            String reference) {
        static ChargeResponse ofView(ChargeResult result, ChargeView charge) {
            return new ChargeResponse(result.id().toString(), result.status().name(), result.failureReason(), MoneyDto.of(result.amount()),
                    result.ledgerEntryId() == null ? null : result.ledgerEntryId().toString(),
                    charge.reservationId() == null ? null : charge.reservationId().toString(), charge.usageRef(), charge.reference());
        }
    }

    public record ReplacementRequest(String type, long amount, String currency, String reference) {}

    public record ReverseRequest(String reason, ReplacementRequest replacement) {}

    public record ReversalResponse(LedgerEntryResponse reversal, LedgerEntryResponse replacement, boolean replayed) {}

    private final OpenWalletUseCase openWallet;
    private final ReserveBudgetUseCase reserve;
    private final ReleaseReservationUseCase release;
    private final ChargeUseCase charge;
    private final ReverseEntryUseCase reverse;
    private final WalletQueries queries;
    private final CurrentActor currentActor;

    public MoneyController(
            OpenWalletUseCase openWallet,
            ReserveBudgetUseCase reserve,
            ReleaseReservationUseCase release,
            ChargeUseCase charge,
            ReverseEntryUseCase reverse,
            WalletQueries queries,
            CurrentActor currentActor) {
        this.openWallet = openWallet;
        this.reserve = reserve;
        this.release = release;
        this.charge = charge;
        this.reverse = reverse;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @PostMapping("/wallets")
    @RequiresPermission(Permission.WALLET_MANAGE)
    public ResponseEntity<WalletResponse> openWallet(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody OpenWalletRequest request) {
        Currency currency = Currency.of(request.currency());
        WalletMode mode = WalletMode.valueOf(request.mode());
        Money limit = request.creditLimit() == null ? null : Money.of(request.creditLimit(), currency);
        OpenWalletUseCase.Result result = openWallet.execute(new OpenWalletUseCase.Command(
                CustomerId.of(request.customerId()), currency, mode, limit, Audit.of(currentActor.get(), requestId), currentActor.caller()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(WalletResponse.of(result.wallet()));
    }

    @GetMapping("/wallets")
    @RequiresPermission(Permission.WALLET_READ)
    public List<WalletResponse> wallets(@RequestParam String customerId) {
        return queries.walletsOf(CustomerId.of(customerId), currentActor.caller()).stream().map(WalletResponse::ofView).toList();
    }

    @GetMapping("/wallets/{id}")
    @RequiresPermission(Permission.WALLET_READ)
    public WalletResponse wallet(@PathVariable String id) {
        return WalletResponse.ofView(queries.wallet(WalletId.of(id), currentActor.caller()));
    }

    @GetMapping("/wallets/{id}/ledger")
    @RequiresPermission(Permission.WALLET_READ)
    public List<LedgerEntryResponse> ledger(
            @PathVariable String id, @RequestParam(defaultValue = "0") long afterSequence, @RequestParam(defaultValue = "50") int limit) {
        return queries.ledger(WalletId.of(id), afterSequence, limit, currentActor.caller()).stream().map(LedgerEntryResponse::ofView).toList();
    }

    @PostMapping("/reservations")
    @RequiresPermission(Permission.RESERVATION_MANAGE)
    public ResponseEntity<ReservationResponse> reserve(@RequestHeader("Idempotency-Key") String key, @RequestBody ReserveRequest request) {
        ReservationResult result = reserve.reserve(new ReserveBudgetCommand(
                WalletId.of(request.walletId()), new MoneyDto(request.amount(), request.currency()).toMoney(),
                Duration.ofSeconds(request.ttlSeconds() == null ? 900 : request.ttlSeconds()), request.reference(), key, currentActor.caller()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.ofView(queries.reservation(result.id(), currentActor.caller())));
    }

    @GetMapping("/reservations/{id}")
    @RequiresPermission(Permission.WALLET_READ)
    public ReservationResponse reservation(@PathVariable String id) {
        return ReservationResponse.ofView(queries.reservation(ReservationId.of(id), currentActor.caller()));
    }

    @PostMapping("/reservations/{id}/release")
    @RequiresPermission(Permission.RESERVATION_MANAGE)
    public ReservationResponse release(@PathVariable String id) {
        release.execute(ReservationId.of(id), currentActor.caller());
        return ReservationResponse.ofView(queries.reservation(ReservationId.of(id), currentActor.caller()));
    }

    /** Charges the actual cost against the reservation and releases whatever was not used. */
    @PostMapping("/reservations/{id}/capture")
    @RequiresPermission(Permission.CHARGE_CREATE)
    public ResponseEntity<ChargeResponse> capture(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody CaptureRequest request) {
        ChargeResult result = charge.charge(new ChargeCommand(
                null, ReservationId.of(id), new MoneyDto(request.amount(), request.currency()).toMoney(), null, request.reference(), key, currentActor.caller()));
        return chargeResponse(result, currentActor.caller());
    }

    @PostMapping("/charges")
    @RequiresPermission(Permission.CHARGE_CREATE)
    public ResponseEntity<ChargeResponse> charge(@RequestHeader("Idempotency-Key") String key, @RequestBody ChargeRequest request) {
        ChargeResult result = charge.charge(new ChargeCommand(
                WalletId.of(request.walletId()), null, new MoneyDto(request.amount(), request.currency()).toMoney(), request.usageRef(),
                request.reference(), key, currentActor.caller()));
        return chargeResponse(result, currentActor.caller());
    }

    @GetMapping("/charges/{id}")
    @RequiresPermission(Permission.WALLET_READ)
    public ChargeResponse getCharge(@PathVariable String id) {
        ChargeView found = queries.charge(ChargeId.of(id), currentActor.caller());
        return ChargeResponse.ofView(new ChargeResult(found.id(), found.status(), found.failureReason(), found.amount(), null, false), found);
    }

    @PostMapping("/ledger-entries/{id}/reverse")
    @RequiresPermission(Permission.LEDGER_REVERSE)
    public ReversalResponse reverse(@PathVariable String id, @RequestBody ReverseRequest request) {
        Actor actor = currentActor.get();
        ReverseEntryUseCase.Replacement replacement = null;
        if (request.replacement() != null) {
            replacement = new ReverseEntryUseCase.Replacement(
                    EntryType.valueOf(request.replacement().type()),
                    new MoneyDto(request.replacement().amount(), request.replacement().currency()).toMoney(), request.replacement().reference());
        }
        ReverseEntryUseCase.Result result = reverse.execute(
                new ReverseEntryUseCase.Command(LedgerEntryId.of(id), request.reason(), replacement, actor.auditName()));
        return new ReversalResponse(LedgerEntryResponse.of(result.reversal()),
                result.replacement() == null ? null : LedgerEntryResponse.of(result.replacement()), result.replayed());
    }

    /** 201 for a recorded charge; 402 when the wallet could not pay (the failure is recorded and repeatable). */
    private ResponseEntity<ChargeResponse> chargeResponse(ChargeResult result, UUID caller) {
        ChargeView stored = queries.charge(result.id(), caller);
        HttpStatus status = result.status() == ChargeStatus.FAILED ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ChargeResponse.ofView(result, stored));
    }
}
