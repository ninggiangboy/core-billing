package io.corebilling.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs every architecture rule against the production code. */
class ArchitectureTest {

    static final String ROOT = "io.corebilling";

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
                .filter(c -> c.getPackageName().equals(ROOT + ".money.application") && c.getSimpleName().endsWith("UseCase") && !c.isInterface())
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

    static Stream<ArchitectureRules.NamedRule> rules() {
        return ArchitectureRules.all(ROOT).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void productionCodeObeysRule(ArchitectureRules.NamedRule rule) {
        rule.rule().check(production);
    }
}
