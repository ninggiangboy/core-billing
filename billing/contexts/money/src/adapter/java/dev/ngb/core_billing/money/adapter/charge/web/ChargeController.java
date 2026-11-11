package dev.ngb.core_billing.money.adapter.charge.web;

import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.command.ChargeUseCase;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Charges a wallet directly or by capturing a reservation. Every money-moving POST needs an {@code Idempotency-Key}. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class ChargeController {

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

    private final ChargeUseCase charge;
    private final WalletQueries queries;
    private final CurrentActor currentActor;

    public ChargeController(ChargeUseCase charge, WalletQueries queries, CurrentActor currentActor) {
        this.charge = charge;
        this.queries = queries;
        this.currentActor = currentActor;
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

    /**
     * 201 for a new recorded charge, 200 when a replayed key returns the earlier charge, and 402 when the wallet
     * could not pay (the failure is recorded and repeatable).
     */
    private ResponseEntity<ChargeResponse> chargeResponse(ChargeResult result, UUID caller) {
        ChargeView stored = queries.charge(result.id(), caller);
        HttpStatus status = result.status() == ChargeStatus.FAILED
                ? HttpStatus.PAYMENT_REQUIRED
                : result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ChargeResponse.ofView(result, stored));
    }
}
