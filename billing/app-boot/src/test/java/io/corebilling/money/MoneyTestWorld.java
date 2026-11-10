package io.corebilling.money;

import io.corebilling.customer.application.AuditContext;
import io.corebilling.customer.application.CreateCustomerUseCase;
import io.corebilling.money.api.CreditCommand;
import io.corebilling.money.api.CreditWallet;
import io.corebilling.money.api.WalletMode;
import io.corebilling.money.application.OpenWalletUseCase;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.domain.Tenant;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.support.ScopeTestSupport;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds tenants with a customer and a wallet for the money tests. */
@Component
public class MoneyTestWorld {

    public static final Currency USD = Currency.of("USD");
    public static final Currency VND = Currency.of("VND");

    public record World(Tenant tenant, CustomerId customerId, Wallet wallet) {}

    private final ScopeTestSupport scopes;
    private final CreateCustomerUseCase createCustomer;
    private final OpenWalletUseCase openWallet;
    private final CreditWallet creditWallet;

    MoneyTestWorld(ScopeTestSupport scopes, CreateCustomerUseCase createCustomer, OpenWalletUseCase openWallet, CreditWallet creditWallet) {
        this.scopes = scopes;
        this.createCustomer = createCustomer;
        this.openWallet = openWallet;
        this.creditWallet = creditWallet;
    }

    public World prepaid(long initialBalance) {
        return world(WalletMode.PREPAID, 0, initialBalance);
    }

    public World postpaid(long creditLimit) {
        return world(WalletMode.POSTPAID, creditLimit, 0);
    }

    private World world(WalletMode mode, long limit, long initialBalance) {
        Tenant tenant = scopes.newTenant();
        CustomerId customerId = scopes.inTenant(tenant.id(), () -> createCustomer.execute(new CreateCustomerUseCase.Command(
                null, null, "Customer " + UUID.randomUUID(), null, UUID.randomUUID().toString(), new AuditContext("user/test", "req"))).customer().id());
        Wallet wallet = scopes.inTenant(tenant.id(), () -> openWallet.execute(new OpenWalletUseCase.Command(
                customerId, USD, mode, mode == WalletMode.POSTPAID ? Money.of(limit, USD) : null, "user/test", "req")).wallet());
        if (initialBalance > 0) {
            credit(tenant, wallet, initialBalance);
        }
        return new World(tenant, customerId, wallet);
    }

    public void credit(Tenant tenant, Wallet wallet, long amount) {
        scopes.inTenant(tenant.id(), () -> creditWallet.credit(new CreditCommand(
                wallet.id(), Money.of(amount, wallet.currency()), "topup:" + UUID.randomUUID(), null, "credit-" + UUID.randomUUID(), "system/test")));
    }
}
