package io.corebilling.money.application;

import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.customer.api.lookup.CustomerSummary;
import io.corebilling.money.api.WalletMode;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;

/**
 * Opens the wallet of a customer in one currency. Asking again with the same settings returns the existing
 * wallet; asking with different settings is a conflict, because a wallet's currency and mode never change.
 */
public class OpenWalletUseCase {

    public record Command(CustomerId customerId, Currency currency, WalletMode mode, Money creditLimit, String actor, String requestId) {}

    /** {@code created} is false when the wallet already existed with these settings. */
    public record Result(Wallet wallet, boolean created) {}

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final CustomerLookup customers;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public OpenWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, CustomerLookup customers, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.customers = customers;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            CustomerSummary customer = customers.find(command.customerId()).orElseThrow(() -> new NotFoundException("customer not found"));
            if (!customer.active()) {
                throw new ConflictException("the customer is deactivated");
            }
            Money limit = command.creditLimit() == null ? Money.zero(command.currency()) : command.creditLimit();
            var existing = wallets.findByCustomer(command.customerId(), command.currency());
            if (existing.isPresent()) {
                Wallet wallet = existing.get();
                if (wallet.mode() != command.mode() || !wallet.creditLimit().equals(limit)) {
                    throw new ConflictException("the customer already has a " + command.currency() + " wallet with different settings");
                }
                return new Result(wallet, false);
            }
            Wallet wallet = Wallet.open(new WalletId(ids.newId()), tenantId, command.customerId(), command.currency(), command.mode(), limit, clock.now());
            wallets.add(wallet);
            auditLog.record(AuditEntry.tenant(tenantId, command.actor(), "wallet.opened", "wallet/" + wallet.id(), command.requestId(), clock.now()));
            return new Result(wallet, true);
        });
    }
}
