package dev.ngb.sampletenant.customer.adapter.identity;

import dev.ngb.sampletenant.customer.application.RegisterCustomerUseCase;
import dev.ngb.sampletenant.identity.application.port.AccountListener;
import dev.ngb.sampletenant.identity.domain.User;

/** Scenario 1: registration maps the new local account to a Billing Customer. */
public class CustomerAccountListener implements AccountListener {

    private final RegisterCustomerUseCase register;

    public CustomerAccountListener(RegisterCustomerUseCase register) {
        this.register = register;
    }

    @Override
    public void registered(User user) {
        register.register(user.id(), user.displayName(), user.email());
    }
}
