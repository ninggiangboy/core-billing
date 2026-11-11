package dev.ngb.core_billing.money.adapter.wallet.persistence;

import static dev.ngb.core_billing_jooq.Tables.WALLETS;

import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.util.Causes;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.WalletsRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqWalletRepository implements WalletRepository {

    private final DSLContext dsl;

    public JooqWalletRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Wallet wallet) {
        try {
            dsl.insertInto(WALLETS)
                    .set(WALLETS.ID, wallet.id().value())
                    .set(WALLETS.TENANT_ID, wallet.tenantId().value())
                    .set(WALLETS.CUSTOMER_ID, wallet.customerId().value())
                    .set(WALLETS.CURRENCY, wallet.currency().code())
                    .set(WALLETS.MODE, wallet.mode().name())
                    .set(WALLETS.CREDIT_LIMIT, wallet.creditLimit().minorUnits())
                    .set(WALLETS.BALANCE, wallet.balance().minorUnits())
                    .set(WALLETS.HELD, wallet.held().minorUnits())
                    .set(WALLETS.LAST_SEQUENCE, wallet.lastSequence())
                    .set(WALLETS.CREATED_AT, wallet.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("the customer already has a wallet in this currency");
            }
            throw e;
        }
    }

    @Override
    public void update(Wallet wallet) {
        dsl.update(WALLETS)
                .set(WALLETS.CUSTOMER_ID, wallet.customerId().value())
                .set(WALLETS.CURRENCY, wallet.currency().code())
                .set(WALLETS.MODE, wallet.mode().name())
                .set(WALLETS.CREDIT_LIMIT, wallet.creditLimit().minorUnits())
                .set(WALLETS.BALANCE, wallet.balance().minorUnits())
                .set(WALLETS.HELD, wallet.held().minorUnits())
                .set(WALLETS.LAST_SEQUENCE, wallet.lastSequence())
                .where(WALLETS.ID.eq(wallet.id().value()))
                .execute();
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.ID.eq(id.value()))
                .fetchOptional(JooqWalletRepository::toDomain);
    }

    @Override
    public Optional<Wallet> lockById(WalletId id) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(JooqWalletRepository::toDomain);
    }

    @Override
    public Optional<Wallet> tryLockById(WalletId id) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.ID.eq(id.value()))
                .forUpdate()
                .skipLocked()
                .fetchOptional(JooqWalletRepository::toDomain);
    }

    @Override
    public Optional<Wallet> findByCustomer(CustomerId customerId, Currency currency) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.CUSTOMER_ID.eq(customerId.value()))
                .and(WALLETS.CURRENCY.eq(currency.code()))
                .fetchOptional(JooqWalletRepository::toDomain);
    }

    private static Wallet toDomain(WalletsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return Wallet.restore(
                new WalletId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                money,
                WalletMode.valueOf(row.getMode()),
                Money.of(row.getCreditLimit(), money),
                Money.of(row.getBalance(), money),
                Money.of(row.getHeld(), money),
                row.getLastSequence(),
                row.getCreatedAt());
    }
}
