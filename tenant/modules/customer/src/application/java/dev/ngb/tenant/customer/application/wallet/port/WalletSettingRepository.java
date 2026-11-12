package dev.ngb.tenant.customer.application.wallet.port;

import dev.ngb.tenant.customer.domain.wallet.CustomerWalletSetting;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for the customer's chosen wallet mode. */
public interface WalletSettingRepository {

    Optional<CustomerWalletSetting> findByCustomerId(UUID customerId);

    void save(CustomerWalletSetting setting);
}
