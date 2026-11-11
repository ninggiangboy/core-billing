package dev.ngb.sampletenant.customer.adapter.web;

import dev.ngb.sampletenant.customer.application.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.domain.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.WalletMode;
import dev.ngb.sampletenant.identity.api.AuthenticatedUser;
import dev.ngb.sampletenant.identity.api.Permission;
import dev.ngb.sampletenant.identity.api.RequiresPermission;
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
