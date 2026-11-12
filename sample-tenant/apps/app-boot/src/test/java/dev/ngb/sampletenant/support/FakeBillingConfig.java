package dev.ngb.sampletenant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces every Billing client bean with one in-memory fake; no test ever calls the network. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeBillingConfig {

    private final FakeBilling billing = new FakeBilling();

    /**
     * The fake implements every Billing api interface, so a single primary bean replaces all of the
     * client beans the application configuration declares.
     */
    @Bean
    @Primary
    FakeBilling fakeBilling() {
        return billing;
    }
}
