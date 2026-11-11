package dev.ngb.core_billing.money.adapter.reservation.web;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.application.reservation.command.ReleaseReservationUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ReserveBudgetUseCase;
import dev.ngb.core_billing.money.application.reservation.query.ReservationView;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Duration;
import java.time.Instant;
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

/** Holds part of a wallet's balance for a later charge. Every money-moving POST needs an {@code Idempotency-Key}. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    public record ReserveRequest(String walletId, long amount, String currency, Long ttlSeconds, String reference) {}

    public record ReservationResponse(
            String id, String walletId, String status, MoneyDto amount, MoneyDto captured, Instant expiresAt, String reference, Instant createdAt,
            Instant closedAt) {
        static ReservationResponse ofView(ReservationView r) {
            return new ReservationResponse(r.id().toString(), r.walletId().toString(), r.status().name(), MoneyDto.of(r.amount()),
                    MoneyDto.of(r.captured()), r.expiresAt(), r.reference(), r.createdAt(), r.closedAt());
        }
    }

    private final ReserveBudgetUseCase reserve;
    private final ReleaseReservationUseCase release;
    private final WalletQueries queries;
    private final CurrentActor currentActor;

    public ReservationController(
            ReserveBudgetUseCase reserve, ReleaseReservationUseCase release, WalletQueries queries, CurrentActor currentActor) {
        this.reserve = reserve;
        this.release = release;
        this.queries = queries;
        this.currentActor = currentActor;
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
}
