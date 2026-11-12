package dev.ngb.core_billing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

/** Runs every architecture rule against the production code. */
class ArchitectureTest {

    static final String ROOT = "dev.ngb.core_billing";

    private static JavaClasses production;
    private static JavaClasses core;

    @BeforeAll
    static void importProduction() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        core = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.CORE);
        // Guards against an import that silently matches nothing and makes every rule pass.
        assertThat(production.contain("dev.ngb.core_billing.sharedkernel.money.Money")).isTrue();
        assertThat(core.contain("dev.ngb.core.runtime.UnitOfWork")).isTrue();
        assertThat(core.contain("dev.ngb.core.spring.runtime.SpringUnitOfWork")).isTrue();
    }

    @org.junit.jupiter.api.Test
    void theMoneyUseCaseRuleHasSubjects() {
        long useCases = production.stream()
                .filter(c -> c.getPackageName().startsWith(ROOT + ".money.application") && c.getSimpleName().endsWith("UseCase") && !c.isInterface())
                .count();
        assertThat(useCases).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(8);
    }

    @org.junit.jupiter.api.Test
    void theDomainEventRuleHasSubjects() {
        long events = production.stream()
                .filter(c -> c.getPackageName().contains(".domain.") && !c.isInterface())
                .filter(c -> c.getAllRawInterfaces().stream().anyMatch(i -> i.getSimpleName().equals("DomainEvent")))
                .count();
        assertThat(events).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(10);
    }

    /**
     * What makes a new pricing model a change inside pricing only: nothing that moves or records money, usage or
     * deliveries knows the pricing context exists.
     */
    @org.junit.jupiter.api.Test
    void moneyUsageAndWebhookDoNotKnowPricing() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ROOT + ".money..", ROOT + ".usage..", ROOT + ".webhook..", ROOT + ".customer..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".pricing..")
                .because("pricing sits above money and usage; the arrow never points back")
                .check(production);
    }

    /**
     * Payments credit and debit wallets through the money api and announce through the outbox; nothing underneath
     * knows a payment provider exists, which is what keeps a second provider a change inside payment only.
     */
    @org.junit.jupiter.api.Test
    void contextsBelowPaymentDoNotKnowPayment() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ROOT + ".money..", ROOT + ".usage..", ROOT + ".webhook..", ROOT + ".customer..", ROOT + ".pricing..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".payment..")
                .because("payment sits above money; the arrow never points back")
                .check(production);
    }

    /** The Stripe rule must have something to guard: the adapter really does use the SDK. */
    @org.junit.jupiter.api.Test
    void theStripeAdapterIsWhereTheSdkIsUsed() {
        long users = production.stream()
                .filter(c -> c.getDirectDependenciesFromSelf().stream().anyMatch(d -> d.getTargetClass().getPackageName().startsWith("com.stripe")))
                .filter(c -> c.getPackageName().startsWith(ROOT + ".payment.adapter.stripe.provider"))
                .count();
        assertThat(users).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void theHttpEdgeRuleHasSubjects() {
        long controllers = production.stream().filter(c -> c.isAnnotatedWith(RestController.class)).count();
        assertThat(controllers).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(22);
    }

    @org.junit.jupiter.api.Test
    void theJobRuleHasSubjects() {
        assertThat(scheduledClasses()).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(6);
        assertThat(jobClasses()).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(7);
    }

    /**
     * Why the runtime split is declared positively: a controller without the api profile is registered in every
     * co-located runtime, which is how a worker ends up serving HTTP.
     */
    @org.junit.jupiter.api.Test
    void everyRestControllerServesTheApiRuntime() {
        assertThat(production.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .filter(c -> !serves(c, "api"))
                .map(JavaClass::getName)
                .toList())
                .as("a controller outside the api runtime is registered in every co-located runtime")
                .isEmpty();
    }

    /** The other half: a scheduled class without the worker profile fires in a process that also serves HTTP. */
    @org.junit.jupiter.api.Test
    void everyScheduledClassRunsInTheWorkerRuntime() {
        assertThat(production.stream()
                .filter(ArchitectureTest::schedulesSomething)
                .filter(c -> !serves(c, "worker"))
                .map(JavaClass::getName)
                .toList())
                .as("a scheduled class outside the worker runtime fires in every co-located runtime")
                .isEmpty();
    }

    /** Covers what schedules nothing itself: the sweep the jobs fan out through is owned by the worker too. */
    @org.junit.jupiter.api.Test
    void everyClassInTheJobsPackageRunsInTheWorkerRuntime() {
        assertThat(production.stream()
                .filter(c -> c.getPackageName().startsWith(ROOT + ".boot.jobs"))
                .filter(c -> !serves(c, "worker"))
                .map(JavaClass::getName)
                .toList())
                .as("a class in the jobs package outside the worker runtime is registered in every co-located runtime")
                .isEmpty();
    }

    private static long scheduledClasses() {
        return production.stream().filter(ArchitectureTest::schedulesSomething).count();
    }

    private static long jobClasses() {
        return production.stream().filter(c -> c.getPackageName().startsWith(ROOT + ".boot.jobs")).count();
    }

    private static boolean schedulesSomething(JavaClass type) {
        return type.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(Scheduled.class));
    }

    private static boolean serves(JavaClass type, String runtime) {
        return type.isAnnotatedWith(Profile.class)
                && List.of(type.getAnnotationOfType(Profile.class).value()).contains(runtime);
    }

    static Stream<ArchitectureRules.NamedRule> rules() {
        return ArchitectureRules.all(ROOT).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void productionCodeObeysRule(ArchitectureRules.NamedRule rule) {
        rule.rule().check(production);
    }

    static Stream<ArchitectureRules.NamedRule> coreRules() {
        return ArchitectureRules.core(ArchitectureRules.CORE).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coreRules")
    void coreObeysRule(ArchitectureRules.NamedRule rule) {
        rule.rule().check(core);
    }
}
