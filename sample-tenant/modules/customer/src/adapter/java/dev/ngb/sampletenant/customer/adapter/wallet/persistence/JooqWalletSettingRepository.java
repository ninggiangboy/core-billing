package dev.ngb.sampletenant.customer.adapter.wallet.persistence;

import static dev.ngb.sampletenant_jooq.Tables.CUSTOMER_WALLET_SETTINGS;

import dev.ngb.sampletenant.customer.application.wallet.port.WalletSettingRepository;
import dev.ngb.sampletenant.customer.domain.wallet.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.wallet.WalletMode;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** One row per customer; the upsert keeps an explicit update idempotent. */
public class JooqWalletSettingRepository implements WalletSettingRepository {

    private final DSLContext dsl;

    public JooqWalletSettingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<CustomerWalletSetting> findByCustomerId(UUID customerId) {
        return dsl.selectFrom(CUSTOMER_WALLET_SETTINGS)
                .where(CUSTOMER_WALLET_SETTINGS.CUSTOMER_ID.eq(customerId))
                .fetchOptional()
                .map(row -> new CustomerWalletSetting(
                        row.getCustomerId(),
                        WalletMode.valueOf(row.getWalletMode()),
                        row.getCurrency(),
                        row.getCreditLimit(),
                        row.getWalletId(),
                        row.getUpdatedAt()));
    }

    @Override
    public void save(CustomerWalletSetting setting) {
        dsl.insertInto(CUSTOMER_WALLET_SETTINGS)
                .columns(
                        CUSTOMER_WALLET_SETTINGS.CUSTOMER_ID,
                        CUSTOMER_WALLET_SETTINGS.WALLET_MODE,
                        CUSTOMER_WALLET_SETTINGS.CURRENCY,
                        CUSTOMER_WALLET_SETTINGS.CREDIT_LIMIT,
                        CUSTOMER_WALLET_SETTINGS.WALLET_ID,
                        CUSTOMER_WALLET_SETTINGS.UPDATED_AT)
                .values(
                        setting.customerId(),
                        setting.walletMode().name(),
                        setting.currency(),
                        setting.creditLimit(),
                        setting.walletId(),
                        setting.updatedAt())
                .onConflict(CUSTOMER_WALLET_SETTINGS.CUSTOMER_ID)
                .doUpdate()
                .set(CUSTOMER_WALLET_SETTINGS.WALLET_MODE, DSL.excluded(CUSTOMER_WALLET_SETTINGS.WALLET_MODE))
                .set(CUSTOMER_WALLET_SETTINGS.CURRENCY, DSL.excluded(CUSTOMER_WALLET_SETTINGS.CURRENCY))
                .set(CUSTOMER_WALLET_SETTINGS.CREDIT_LIMIT, DSL.excluded(CUSTOMER_WALLET_SETTINGS.CREDIT_LIMIT))
                .set(CUSTOMER_WALLET_SETTINGS.WALLET_ID, DSL.excluded(CUSTOMER_WALLET_SETTINGS.WALLET_ID))
                .set(CUSTOMER_WALLET_SETTINGS.UPDATED_AT, DSL.excluded(CUSTOMER_WALLET_SETTINGS.UPDATED_AT))
                .execute();
    }
}
