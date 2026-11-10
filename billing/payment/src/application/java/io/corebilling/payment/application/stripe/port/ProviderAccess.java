package io.corebilling.payment.application.stripe.port;

import io.corebilling.payment.domain.stripe.IntegrationMode;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.payment.domain.stripe.StripeEnvironment;
import io.corebilling.payment.application.payment.command.PaymentSettings;
import io.corebilling.payment.application.payment.port.PaymentProvider;

/** Works out which credentials a call for a configuration uses: the Tenant's own key, or the Platform's key acting on the connected account. */
public class ProviderAccess {

    private final SecretCipher cipher;
    private final PaymentSettings settings;

    public ProviderAccess(SecretCipher cipher, PaymentSettings settings) {
        this.cipher = cipher;
        this.settings = settings;
    }

    public PaymentProvider.Access of(StripeConfiguration configuration) {
        if (configuration.mode() == IntegrationMode.DIRECT) {
            return new PaymentProvider.Access(cipher.decrypt(configuration.secretKeyCiphertext(), configuration.id().toString()), null);
        }
        return new PaymentProvider.Access(platformKey(), configuration.accountId());
    }

    public String platformKey() {
        return settings.connectSettings()
                .orElseThrow(() -> new IllegalStateException("Stripe Connect is not configured for this deployment"))
                .platformSecretKey();
    }

    public StripeEnvironment platformEnvironment() {
        return settings.connectSettings().orElseThrow(() -> new IllegalStateException("Stripe Connect is not configured for this deployment"))
                .environment();
    }
}
