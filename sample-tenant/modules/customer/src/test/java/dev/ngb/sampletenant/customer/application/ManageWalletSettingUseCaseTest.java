package dev.ngb.sampletenant.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Amount;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.application.port.WalletSettingRepository;
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.customer.domain.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.WalletMode;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageWalletSettingUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    private final Fakes.Customers customers = new Fakes.Customers();
    private final InMemorySettings settings = new InMemorySettings();
    private final FakeWallets wallets = new FakeWallets();
    private final ManageWalletSettingUseCase useCase = new ManageWalletSettingUseCase(
            new CustomerQueries(customers), settings, wallets, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theDefaultSettingIsPrepaidInUsd() {
        mapped();

        CustomerWalletSetting setting = useCase.current(USER);

        assertThat(setting.walletMode()).isEqualTo(WalletMode.PREPAID);
        assertThat(setting.currency()).isEqualTo("USD");
        assertThat(setting.walletId()).isNull();
    }

    @Test
    void choosingPostpaidOpensThatWalletOnBilling() {
        mapped();

        CustomerWalletSetting setting = useCase.update(USER, WalletMode.POSTPAID, "usd", 50_000L);

        assertThat(setting.walletMode()).isEqualTo(WalletMode.POSTPAID);
        assertThat(setting.currency()).isEqualTo("USD");
        assertThat(setting.creditLimit()).isEqualTo(50_000L);
        assertThat(setting.walletId()).isEqualTo("wal_1");
        assertThat(wallets.last.mode()).isEqualTo("POSTPAID");
        assertThat(wallets.last.creditLimit()).isEqualTo(50_000L);
        assertThat(settings.byCustomer).containsKey(setting.customerId());
    }

    @Test
    void prepaidDropsAnyCreditLimit() {
        mapped();

        CustomerWalletSetting setting = useCase.update(USER, WalletMode.PREPAID, "USD", 50_000L);

        assertThat(setting.creditLimit()).isNull();
        assertThat(wallets.last.creditLimit()).isNull();
    }

    @Test
    void refusesAnInvalidCurrency() {
        mapped();

        assertThatThrownBy(() -> useCase.update(USER, WalletMode.PREPAID, "dollars", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAnUnmappedCustomer() {
        customers.save(Customer.pending(USER, "Ada", "ada@example.com", NOW));

        assertThatThrownBy(() -> useCase.update(USER, WalletMode.PREPAID, "USD", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void defaultWalletIsOpenedOnlyOnce() {
        mapped();

        useCase.ensureDefaultWallet(USER);
        useCase.ensureDefaultWallet(USER);

        assertThat(wallets.opened).hasSize(1);
    }

    private void mapped() {
        customers.save(Customer.pending(USER, "Ada", "ada@example.com", NOW).mapped("cus_1"));
    }

    private static final class InMemorySettings implements WalletSettingRepository {
        private final Map<UUID, CustomerWalletSetting> byCustomer = new LinkedHashMap<>();

        @Override
        public Optional<CustomerWalletSetting> findByCustomerId(UUID customerId) {
            return Optional.ofNullable(byCustomer.get(customerId));
        }

        @Override
        public void save(CustomerWalletSetting setting) {
            byCustomer.put(setting.customerId(), setting);
        }
    }

    private static final class FakeWallets implements BillingWallets {
        private final List<OpenWallet> opened = new java.util.ArrayList<>();
        private OpenWallet last;

        @Override
        public List<Wallet> wallets(String customerId) {
            return List.of(walletOf(customerId));
        }

        @Override
        public Wallet wallet(String id) {
            return walletOf("cus_1");
        }

        @Override
        public Wallet open(String idempotencyKey, OpenWallet request) {
            opened.add(request);
            last = request;
            return walletOf(request.customerId());
        }

        private static Wallet walletOf(String customerId) {
            return new Wallet(
                    "wal_1", customerId, "USD", "PREPAID", new Amount(0, "USD"), new Amount(0, "USD"),
                    new Amount(0, "USD"), new Amount(0, "USD"), new Amount(0, "USD"), 0L, NOW);
        }
    }
}
