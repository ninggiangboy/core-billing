package dev.ngb.billing.money.adapter.wallet.persistence;

import static dev.ngb.billing_jooq.Tables.WALLETS;

import dev.ngb.billing.money.api.wallet.WalletMode;
import dev.ngb.billing.money.application.wallet.query.WalletReadRepository;
import dev.ngb.billing.money.application.wallet.query.WalletView;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing_jooq.tables.records.WalletsRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;

/** Selects wallets straight into the view; the aggregate is never built. */
public class JooqWalletReadRepository implements WalletReadRepository {

    private final DSLContext dsl;

    public JooqWalletReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WalletView> findById(WalletId id) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.ID.eq(id.value()))
                .fetchOptional(JooqWalletReadRepository::toView);
    }

    @Override
    public List<WalletView> listByCustomer(CustomerId customerId) {
        return dsl.selectFrom(WALLETS)
                .where(WALLETS.CUSTOMER_ID.eq(customerId.value()))
                .orderBy(WALLETS.CURRENCY)
                .fetch(JooqWalletReadRepository::toView);
    }

    private static WalletView toView(WalletsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return new WalletView(
                new WalletId(row.getId()),
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
