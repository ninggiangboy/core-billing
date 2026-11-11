package dev.ngb.core_billing.money.adapter.persistence;

import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import org.springframework.dao.DuplicateKeyException;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWalletRepository implements WalletRepository {

    private static final String COLUMNS =
            "id, tenant_id, customer_id, currency, mode, credit_limit, balance, held, last_sequence, created_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcWalletRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Wallet wallet) {
        try {
            aggregates.insert(WalletRow.from(wallet));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("the customer already has a wallet in this currency");
            }
            throw e;
        }
    }

    @Override
    public void update(Wallet wallet) {
        aggregates.update(WalletRow.from(wallet));
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), WalletRow.class)).map(WalletRow::toDomain);
    }

    @Override
    public Optional<Wallet> lockById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ? for update").param(id.value()).query(WalletRow.class).optional()
                .map(WalletRow::toDomain);
    }

    @Override
    public Optional<Wallet> tryLockById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ? for update skip locked").param(id.value()).query(WalletRow.class).optional()
                .map(WalletRow::toDomain);
    }

    @Override
    public Optional<Wallet> findByCustomer(CustomerId customerId, Currency currency) {
        return jdbc.sql("select " + COLUMNS + " from wallets where customer_id = ? and currency = ?")
                .params(customerId.value(), currency.code())
                .query(WalletRow.class)
                .optional()
                .map(WalletRow::toDomain);
    }
}
