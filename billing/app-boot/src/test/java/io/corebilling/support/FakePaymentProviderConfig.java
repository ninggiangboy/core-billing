package io.corebilling.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Takes precedence over the Stripe adapter (it is {@code @Primary} for the {@code PaymentProvider} port) with {@link FakePaymentProvider} for tests that must not (and need not) reach Stripe. */
@TestConfiguration(proxyBeanMethods = false)
public class FakePaymentProviderConfig {

    @Bean
    @Primary
    FakePaymentProvider fakePaymentProvider() {
        return new FakePaymentProvider();
    }
}
