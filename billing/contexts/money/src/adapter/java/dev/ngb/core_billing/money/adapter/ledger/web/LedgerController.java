package dev.ngb.core_billing.money.adapter.ledger.web;

import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.application.ledger.command.ReverseEntryUseCase;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reads a wallet's ledger and corrects it with reversal entries; the ledger itself is append-only. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class LedgerController {

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

    public record ReplacementRequest(String type, long amount, String currency, String reference) {}

    public record ReverseRequest(String reason, ReplacementRequest replacement) {}

    public record ReversalResponse(LedgerEntryResponse reversal, LedgerEntryResponse replacement, boolean replayed) {}

    private final ReverseEntryUseCase reverse;
    private final WalletQueries queries;
    private final CurrentActor currentActor;

    public LedgerController(ReverseEntryUseCase reverse, WalletQueries queries, CurrentActor currentActor) {
        this.reverse = reverse;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/wallets/{id}/ledger")
    @RequiresPermission(Permission.WALLET_READ)
    public List<LedgerEntryResponse> ledger(
            @PathVariable String id, @RequestParam(defaultValue = "0") long afterSequence, @RequestParam(defaultValue = "50") int limit) {
        return queries.ledger(WalletId.of(id), afterSequence, limit, currentActor.caller()).stream().map(LedgerEntryResponse::ofView).toList();
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
}
