package dev.ngb.sampletenant.boot.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SystemConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
