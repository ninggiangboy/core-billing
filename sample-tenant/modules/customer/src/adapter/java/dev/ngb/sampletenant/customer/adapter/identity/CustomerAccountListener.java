package dev.ngb.sampletenant.customer.adapter.identity;

import dev.ngb.sampletenant.customer.application.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.application.RegisterCustomerUseCase;
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
