package io.corebilling.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs every architecture rule against the production code. */
class ArchitectureTest {

    static final String ROOT = "io.corebilling";

    // A context joins one of these sets only once it has adopted that part of the target architecture.
    private static final Set<String> LAYOUT = Set.of("customer");
    private static final Set<String> PERSISTENCE = Set.of();
    private static final Set<String> READ_WRITE = Set.of();

    private static JavaClasses production;

    @BeforeAll
    static void importProduction() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        // Guards against an import that silently matches nothing and makes every rule pass.
        assertThat(production.contain("io.corebilling.sharedkernel.Money")).isTrue();
    }

    @org.junit.jupiter.api.Test
    void theMoneyUseCaseRuleHasSubjects() {
        long useCases = production.stream()
                .filter(c -> c.getPackageName().startsWith(ROOT + ".money.application") && c.getSimpleName().endsWith("UseCase") && !c.isInterface())
                .count();
        assertThat(useCases).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(8);
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
                .filter(c -> c.getPackageName().startsWith(ROOT + ".payment.adapter.stripe"))
                .count();
        assertThat(users).as("a rule that matches no class passes vacuously").isGreaterThanOrEqualTo(1);
    }

    static Stream<ArchitectureRules.NamedRule> rules() {
        return ArchitectureRules.all(ROOT, new ArchitectureRules.ContextSets(LAYOUT, PERSISTENCE, READ_WRITE)).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void productionCodeObeysRule(ArchitectureRules.NamedRule rule) {
        rule.rule().check(production);
    }
}
