package dev.ngb.sampletenant.customer.application.wallet.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.wallet.port.WalletSettingRepository;
import dev.ngb.sampletenant.customer.domain.wallet.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.wallet.WalletMode;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.ConflictException;
import dev.ngb.sampletenant.shared.error.UpstreamUnavailableException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Scenario 13: the customer's setting selects the wallet mode on Billing's side. Opening the same
 * customer and currency again returns Billing's existing wallet, so an explicit update is safe.
 */
public class ManageWalletSettingUseCase {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final CustomerDirectory customers;
    private final WalletSettingRepository settings;
    private final BillingWallets wallets;
    private final Clock clock;

    public ManageWalletSettingUseCase(
            CustomerDirectory customers, WalletSettingRepository settings, BillingWallets wallets, Clock clock) {
        this.customers = customers;
        this.settings = settings;
        this.wallets = wallets;
        this.clock = clock;
    }

    public CustomerWalletSetting current(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = mapped(userId);
        return settings.findByCustomerId(customer.id())
                .orElseGet(() -> CustomerWalletSetting.defaults(customer.id()));
    }

    public CustomerWalletSetting update(UUID userId, WalletMode mode, String currency, Long creditLimit) {
        CustomerDirectory.CustomerSnapshot customer = mapped(userId);
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(normalized).matches()) {
            throw new BadRequestException("currency must be an ISO 4217 code");
        }
        if (mode == null) {
            throw new BadRequestException("a wallet mode is required");
        }
        if (creditLimit != null && creditLimit < 0) {
            throw new BadRequestException("a credit limit cannot be negative");
        }
        Long limit = mode == WalletMode.POSTPAID ? creditLimit : null;
        Wallet wallet = wallets.open(
                "wallet:" + customer.billingCustomerId() + ":" + normalized,
                new BillingWallets.OpenWallet(customer.billingCustomerId(), normalized, mode.name(), limit));
        CustomerWalletSetting setting = new CustomerWalletSetting(
                customer.id(), mode, normalized, limit, wallet.id(), clock.now());
        settings.save(setting);
        return setting;
    }

    public void ensureDefaultWallet(UUID userId) {
        try {
            CustomerDirectory.CustomerSnapshot customer = customers.byUserId(userId);
            if (!customer.mapped() || settings.findByCustomerId(customer.id()).isPresent()) {
                return;
            }
            update(userId, WalletMode.PREPAID, "USD", null);
        } catch (UpstreamUnavailableException | ConflictException | BadRequestException e) {
            // Registration still succeeds; an explicit wallet-settings update opens it later.
        }
    }

    private CustomerDirectory.CustomerSnapshot mapped(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = customers.byUserId(userId);
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        return customer;
    }
}
