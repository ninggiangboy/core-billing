package dev.ngb.billing.money;

import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.customer.application.customer.command.CreateCustomerUseCase;
import dev.ngb.billing.money.api.wallet.CreditCommand;
import dev.ngb.billing.money.api.wallet.CreditWallet;
import dev.ngb.billing.money.api.wallet.WalletMode;
import dev.ngb.billing.money.application.wallet.command.OpenWalletUseCase;
import dev.ngb.billing.money.domain.wallet.Wallet;
import dev.ngb.billing.platform.domain.tenant.Tenant;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.support.ScopeTestSupport;
import java.util.UUID;
import org.springframework.stereotype.Component;
import dev.ngb.billing.customer.domain.customer.Customer;

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

    /** A second prepaid wallet in the same tenant, owned by another customer, for cross-wallet races. */
    public World anotherWallet(World base, long initialBalance) {
        Tenant tenant = base.tenant();
        CustomerId customerId = scopes.inTenant(tenant.id(), () -> createCustomer.execute(new CreateCustomerUseCase.Command(
                null, null, "Customer " + UUID.randomUUID(), null, UUID.randomUUID().toString(), new Audit("user/test", "req"))).customer().id());
        Wallet wallet = scopes.inTenant(tenant.id(), () -> openWallet.execute(new OpenWalletUseCase.Command(
                customerId, USD, WalletMode.PREPAID, null, new Audit("user/test", "req"))).wallet());
        credit(tenant, wallet, initialBalance);
        return new World(tenant, customerId, wallet);
    }

    private World world(WalletMode mode, long limit, long initialBalance) {
        Tenant tenant = scopes.newTenant();
        CustomerId customerId = scopes.inTenant(tenant.id(), () -> createCustomer.execute(new CreateCustomerUseCase.Command(
                null, null, "Customer " + UUID.randomUUID(), null, UUID.randomUUID().toString(), new Audit("user/test", "req"))).customer().id());
        Wallet wallet = scopes.inTenant(tenant.id(), () -> openWallet.execute(new OpenWalletUseCase.Command(
                customerId, USD, mode, mode == WalletMode.POSTPAID ? Money.of(limit, USD) : null, new Audit("user/test", "req"))).wallet());
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
