package dev.ngb.core_billing.money.adapter.persistence.wallet;

import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.application.wallet.query.WalletReadRepository;
import dev.ngb.core_billing.money.application.wallet.query.WalletView;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects wallets straight into the view; the aggregate is never built. */
public class JdbcWalletReadRepository implements WalletReadRepository {

    private static final String COLUMNS = "id, customer_id, currency, mode, credit_limit, balance, held, last_sequence, created_at";

    private record Row(
            UUID id,
            UUID customerId,
            String currency,
            String mode,
            long creditLimit,
            long balance,
            long held,
            long lastSequence,
            Instant createdAt) {

        WalletView toView() {
            Currency money = Currency.of(currency.strip());
            return new WalletView(
                    new WalletId(id),
                    new CustomerId(customerId),
                    money,
                    WalletMode.valueOf(mode),
                    Money.of(creditLimit, money),
                    Money.of(balance, money),
                    Money.of(held, money),
                    lastSequence,
                    createdAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcWalletReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WalletView> findById(WalletId id) {
        return jdbc.sql("select " + COLUMNS + " from wallets where id = ?").param(id.value()).query(Row.class).optional().map(Row::toView);
    }

    @Override
    public List<WalletView> listByCustomer(CustomerId customerId) {
        return jdbc.sql("select " + COLUMNS + " from wallets where customer_id = ? order by currency")
                .param(customerId.value())
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }
}
