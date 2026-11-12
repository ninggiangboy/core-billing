package dev.ngb.sampletenant.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.wallet.port.WalletSettingRepository;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.customer.domain.wallet.CustomerWalletSetting;
import dev.ngb.sampletenant.customer.domain.wallet.WalletMode;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqWalletSettingRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    WalletSettingRepository settings;

    @Autowired
    CustomerRepository customers;

    @Autowired
    IdGenerator ids;

    private UUID customerId() {
        String email = "wallet-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        Customer customer = customers.findByUserId(user.id()).orElseThrow();
        return customer.id();
    }

    @Test
    void roundTripsAndUpsertsTheSetting() {
        UUID customerId = customerId();
        settings.save(new CustomerWalletSetting(customerId, WalletMode.PREPAID, "USD", null, "wal_1", NOW));

        CustomerWalletSetting loaded = settings.findByCustomerId(customerId).orElseThrow();
        assertThat(loaded.walletMode()).isEqualTo(WalletMode.PREPAID);
        assertThat(loaded.walletId()).isEqualTo("wal_1");

        settings.save(new CustomerWalletSetting(customerId, WalletMode.POSTPAID, "EUR", 50_000L, "wal_2", NOW));

        CustomerWalletSetting updated = settings.findByCustomerId(customerId).orElseThrow();
        assertThat(updated.walletMode()).isEqualTo(WalletMode.POSTPAID);
        assertThat(updated.currency()).isEqualTo("EUR");
        assertThat(updated.creditLimit()).isEqualTo(50_000L);
        assertThat(updated.walletId()).isEqualTo("wal_2");
    }
}
