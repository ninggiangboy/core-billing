package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.WalletMode;
import io.corebilling.money.application.WalletRepository;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWalletRepository implements WalletRepository {

    private static final String COLUMNS =
            "id, tenant_id, customer_id, currency, mode, credit_limit, balance, held, last_sequence, created_at";

    private static final RowMapper<Wallet> MAPPER = (rs, n) -> {
        Currency currency = Currency.of(rs.getString("currency"));
        return Wallet.restore(
                new WalletId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new CustomerId(Rows.uuid(rs, "customer_id")),
                currency,
                WalletMode.valueOf(rs.getString("mode")),
                Money.of(rs.getLong("credit_limit"), currency),
                Money.of(rs.getLong("balance"), currency),
                Money.of(rs.getLong("held"), currency),
                rs.getLong("last_sequence"),
                Rows.instant(rs, "created_at"));
    };

    private final JdbcClient jdbc;

    public JdbcWalletRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Wallet wallet) {
        try {
            jdbc.sql("insert into wallets (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(wallet.id().value(), wallet.tenantId().value(), wallet.customerId().value(), wallet.currency().code(),
                            wallet.mode().name(), wallet.creditLimit().minorUnits(), wallet.balance().minorUnits(),
                            wallet.held().minorUnits(), wallet.lastSequence(), Timestamp.from(wallet.createdAt()))
                    .update();
        } catch (RuntimeException e) {
            if (Rows.duplicate(e)) {
                throw new ConflictException("the customer already has a wallet in this currency");
            }
            throw e;
        }
    }

    @Override
    public void update(Wallet wallet) {
        int updated = jdbc.sql("update wallets set balance = ?, held = ?, last_sequence = ? where id = ?")
                .params(wallet.balance().minorUnits(), wallet.held().minorUnits(), wallet.lastSequence(), wallet.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("wallet " + wallet.id() + " was not updated");
        }
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Wallet> lockById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ? for update").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Wallet> tryLockById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ? for update skip locked").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Wallet> findByCustomer(CustomerId customerId, Currency currency) {
        return jdbc.sql("select " + COLUMNS + " from wallets where customer_id = ? and currency = ?")
                .params(customerId.value(), currency.code()).query(MAPPER).optional();
    }

    @Override
    public List<Wallet> listByCustomer(CustomerId customerId) {
        return jdbc.sql("select " + COLUMNS + " from wallets where customer_id = ? order by currency").param(customerId.value()).query(MAPPER).list();
    }
}
