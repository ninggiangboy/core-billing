package dev.ngb.billing.money.adapter.wallet.web;

import dev.ngb.billing.money.api.wallet.WalletMode;
import dev.ngb.billing.money.application.wallet.command.OpenWalletUseCase;
import dev.ngb.billing.money.application.wallet.query.WalletQueries;
import dev.ngb.billing.money.application.wallet.query.WalletView;
import dev.ngb.billing.money.domain.wallet.Wallet;
import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.money.MoneyDto;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.List;
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

/** Opens and reads wallets. Amounts are integers in minor units (cents, dong) with an ISO 4217 currency code. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class WalletController {

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

    private final OpenWalletUseCase openWallet;
    private final WalletQueries queries;
    private final CurrentActor currentActor;

    public WalletController(OpenWalletUseCase openWallet, WalletQueries queries, CurrentActor currentActor) {
        this.openWallet = openWallet;
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
}
