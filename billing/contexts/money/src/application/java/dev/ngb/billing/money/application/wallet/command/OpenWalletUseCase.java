package dev.ngb.billing.money.application.wallet.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.customer.api.lookup.CustomerLookup;
import dev.ngb.billing.customer.api.lookup.CustomerSummary;
import dev.ngb.billing.money.api.wallet.WalletMode;
import dev.ngb.billing.money.application.wallet.port.WalletRepository;
import dev.ngb.billing.money.domain.wallet.Wallet;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import java.util.UUID;

/**
 * Opens the wallet of a customer in one currency. Asking again with the same settings returns the existing
 * wallet; asking with different settings is a conflict, because a wallet's currency and mode never change.
 */
public class OpenWalletUseCase {

    public record Command(CustomerId customerId, Currency currency, WalletMode mode, Money creditLimit, Audit audit, UUID caller) {
        public Command(CustomerId customerId, Currency currency, WalletMode mode, Money creditLimit, Audit audit) {
            this(customerId, currency, mode, creditLimit, audit, null);
        }
    }

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
            CustomerSummary customer = customers.findFor(command.customerId(), command.caller())
                    .orElseThrow(() -> new NotFoundException("customer not found"));
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
            auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "wallet.opened", "wallet/" + wallet.id(), command.audit().requestId(), clock.now()));
            return new Result(wallet, true);
        });
    }
}
