package dev.ngb.tenant.customer.adapter.wallet.web;

import dev.ngb.tenant.customer.application.wallet.command.ManageWalletSettingUseCase;
import dev.ngb.tenant.customer.domain.wallet.CustomerWalletSetting;
import dev.ngb.tenant.customer.domain.wallet.WalletMode;
import dev.ngb.tenant.identity.api.security.AuthenticatedUser;
import dev.ngb.tenant.identity.api.security.Permission;
import dev.ngb.tenant.identity.api.security.RequiresPermission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scenario 13: the customer selects Prepaid or Postpaid and Billing opens that wallet. */
@RestController
@RequestMapping("/api/customers/me/wallet-settings")
public class WalletSettingController {

    public record UpdateRequest(WalletMode mode, String currency, Long creditLimit) {}

    public record SettingResponse(
            UUID customerId, String mode, String currency, Long creditLimit, String walletId, Instant updatedAt) {
        static SettingResponse of(CustomerWalletSetting setting) {
            return new SettingResponse(
                    setting.customerId(),
                    setting.walletMode().name(),
                    setting.currency(),
                    setting.creditLimit(),
                    setting.walletId(),
                    setting.updatedAt());
        }
    }

    private final ManageWalletSettingUseCase settings;

    public WalletSettingController(ManageWalletSettingUseCase settings) {
        this.settings = settings;
    }

    @GetMapping
    @RequiresPermission(Permission.BILLING_READ)
    public SettingResponse current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return SettingResponse.of(settings.current(principal.id()));
    }

    @PutMapping
    @RequiresPermission(Permission.CUSTOMER_MANAGE)
    public SettingResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody UpdateRequest request) {
        return SettingResponse.of(
                settings.update(principal.id(), request.mode(), request.currency(), request.creditLimit()));
    }
}
