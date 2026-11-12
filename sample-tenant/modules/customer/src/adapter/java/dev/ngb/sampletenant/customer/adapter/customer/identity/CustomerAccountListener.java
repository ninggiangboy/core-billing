package dev.ngb.sampletenant.customer.adapter.customer.identity;

import dev.ngb.sampletenant.customer.application.wallet.command.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.application.customer.command.RegisterCustomerUseCase;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import dev.ngb.sampletenant.identity.api.account.RegisteredAccount;

/** Scenario 1: registration maps the new local account to a Billing Customer. */
public class CustomerAccountListener implements AccountListener {

    private final RegisterCustomerUseCase register;
    private final ManageWalletSettingUseCase walletSettings;

    public CustomerAccountListener(RegisterCustomerUseCase register, ManageWalletSettingUseCase walletSettings) {
        this.register = register;
        this.walletSettings = walletSettings;
    }

    @Override
    public void registered(RegisteredAccount account) {
        register.register(account.userId(), account.displayName(), account.email());
        walletSettings.ensureDefaultWallet(account.userId());
    }
}
