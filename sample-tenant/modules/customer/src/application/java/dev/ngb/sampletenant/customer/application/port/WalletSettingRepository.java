package dev.ngb.sampletenant.customer.application.port;

import dev.ngb.sampletenant.customer.domain.CustomerWalletSetting;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for the customer's chosen wallet mode. */
public interface WalletSettingRepository {

    Optional<CustomerWalletSetting> findByCustomerId(UUID customerId);

    void save(CustomerWalletSetting setting);
}
