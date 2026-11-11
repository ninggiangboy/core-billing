package dev.ngb.core_billing.boot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs exist only in the {@code worker} profile (and in the all-in-one default used by dev and test).
 * The {@code api} profile serves HTTP and runs nothing on a timer; {@code migrate} only migrates.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!api & !migrate")
public class SchedulingConfiguration {}
