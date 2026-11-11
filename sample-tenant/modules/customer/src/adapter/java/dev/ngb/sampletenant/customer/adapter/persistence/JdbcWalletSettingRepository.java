package dev.ngb.sampletenant.customer.adapter.persistence;

import dev.ngb.sampletenant.customer.application.port.WalletSettingRepository;
import dev.ngb.sampletenant.customer.domain.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.WalletMode;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** One row per customer; the upsert keeps an explicit update idempotent. */
public class JdbcWalletSettingRepository implements WalletSettingRepository {

    private final JdbcTemplate jdbc;

    public JdbcWalletSettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CustomerWalletSetting> findByCustomerId(UUID customerId) {
        return jdbc.query(
                        "select * from customer_wallet_settings where customer_id = ?",
                        (rs, row) -> new CustomerWalletSetting(
                                rs.getObject("customer_id", UUID.class),
                                WalletMode.valueOf(rs.getString("wallet_mode")),
                                rs.getString("currency"),
                                rs.getObject("credit_limit", Long.class),
                                rs.getString("wallet_id"),
                                rs.getTimestamp("updated_at").toInstant()),
                        customerId)
                .stream()
                .findFirst();
    }

    @Override
    public void save(CustomerWalletSetting setting) {
        jdbc.update(
                """
                insert into customer_wallet_settings
                    (customer_id, wallet_mode, currency, credit_limit, wallet_id, updated_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (customer_id) do update
                    set wallet_mode = excluded.wallet_mode,
                        currency = excluded.currency,
                        credit_limit = excluded.credit_limit,
                        wallet_id = excluded.wallet_id,
                        updated_at = excluded.updated_at
                """,
                setting.customerId(),
                setting.walletMode().name(),
                setting.currency(),
                setting.creditLimit(),
                setting.walletId(),
                Timestamp.from(setting.updatedAt()));
    }
}
