package dev.ngb.billing.payment.application.stripe.service;

import dev.ngb.core.security.SecretCipher;
import dev.ngb.billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.billing.payment.domain.stripe.StripeEnvironment;

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
