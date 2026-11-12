package dev.ngb.tenant.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves the rules have teeth: the same rule set, pointed at the deliberately violating classes in
 * the arch-fixtures module, must report a violation for every rule. A rule that is removed,
 * disabled or weakened until it passes on the fixtures turns this test red.
 */
class ArchitectureMetaTest {

    private static final String FIXTURE_ROOT = "archfixtures";

    /** The core's root package as the fixtures spell it, so the core rules can be pointed at them too. */
    private static final String FIXTURE_CORE = "archfixtures.sharedcore";

    /** Each rule and the fixture class that must be named in its failure report. */
    private static final Map<String, String> CULPRIT_BY_RULE_ID = new LinkedHashMap<>();

    static {
        CULPRIT_BY_RULE_ID.put("layer-direction", "DomainReachesApplication");
        CULPRIT_BY_RULE_ID.put("inner-layer-purity", "DomainUsesSpring");
        CULPRIT_BY_RULE_ID.put("no-cross-module-import", "ReachesIntoBeta");
        CULPRIT_BY_RULE_ID.put("api-exposes-no-internals", "LeaksDomain");
        CULPRIT_BY_RULE_ID.put("no-jpa", "JpaEntity");
        CULPRIT_BY_RULE_ID.put("shared-core-is-independent", "SharedCoreKnowsAModule");
        CULPRIT_BY_RULE_ID.put("datasource-and-transactions-only-in-core-spring", "TouchesDataSource");
        CULPRIT_BY_RULE_ID.put("role-package-not-layer-root", "StrayRoleClass");
        CULPRIT_BY_RULE_ID.put("use-cases-and-queries-in-command-or-query", "MisplacedUseCase");
        CULPRIT_BY_RULE_ID.put("ports-in-port-or-query", "MisplacedReadRepository");
        CULPRIT_BY_RULE_ID.put("application-classes-in-a-role-package", "StrayFeatureRootClass");
        CULPRIT_BY_RULE_ID.put("command-package-holds-only-use-cases", "HelperInCommand");
        CULPRIT_BY_RULE_ID.put("port-package-holds-no-implementations", "ImplementationInPort");
        CULPRIT_BY_RULE_ID.put("adapter-classes-in-feature-then-technology", "TechnologyFirstController");
        CULPRIT_BY_RULE_ID.put("api-implementations-are-services", "MisnamedLookupImplementation");
        CULPRIT_BY_RULE_ID.put("web-does-not-depend-on-persistence", "WebReachesPersistence");
        CULPRIT_BY_RULE_ID.put("persistence-types-in-adapter", "RowInDomain");
        CULPRIT_BY_RULE_ID.put("generated-records-in-adapter-persistence", "TableInApplication");
        CULPRIT_BY_RULE_ID.put("module-uses-only-own-tables", "UsesAnotherModulesTable");
        CULPRIT_BY_RULE_ID.put("write-side-does-not-depend-on-query", "CommandReachesQuery");
        CULPRIT_BY_RULE_ID.put("read-side-does-not-depend-on-write", "QueryReachesWritePort");
        CULPRIT_BY_RULE_ID.put("query-side-uses-no-domain-aggregates", "QueryReachesAggregate");
        CULPRIT_BY_RULE_ID.put("write-ports-serve-no-reads", "WritePort");
        CULPRIT_BY_RULE_ID.put("repository-pair-shares-a-package", "JooqSplitReadRepository");
    }

    private static final Map<String, String> CULPRIT_BY_CORE_RULE_ID = Map.of(
            "core-is-self-contained", "CoreKnowsAnApplication",
            "core-spring-is-self-contained", "CoreSpringKnowsAnApplication");

    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
        assertThat(fixtures).isNotEmpty();
        assertThat(fixtures.contain(FIXTURE_CORE + ".CoreKnowsAnApplication")).isTrue();
    }

    static Stream<ArchitectureRules.NamedRule> rules() {
        return ArchitectureRules.all(FIXTURE_ROOT).stream();
    }

    /** The core rules, pointed at fixtures that spell the core's package the way the fixtures spell an application. */
    static Stream<ArchitectureRules.NamedRule> coreRules() {
        return ArchitectureRules.core(FIXTURE_CORE).stream();
    }

    @Test
    void ruleSetIsExactlyTheExpectedOne() {
        assertThat(ArchitectureRules.all(FIXTURE_ROOT))
                .extracting(ArchitectureRules.NamedRule::id)
                .containsExactlyElementsOf(CULPRIT_BY_RULE_ID.keySet());
    }

    @ParameterizedTest(name = "{0} fails on its fixture")
    @MethodSource("rules")
    void everyRuleReportsAViolationOnFixtures(ArchitectureRules.NamedRule rule) {
        assertThatThrownBy(() -> rule.rule().check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(CULPRIT_BY_RULE_ID.get(rule.id()));
    }

    @Test
    void coreRuleSetIsExactlyTheExpectedOne() {
        assertThat(ArchitectureRules.core(FIXTURE_CORE))
                .extracting(ArchitectureRules.NamedRule::id)
                .containsExactlyElementsOf(CULPRIT_BY_CORE_RULE_ID.keySet().stream().sorted().toList());
    }

    @ParameterizedTest(name = "{0} fails on its core fixture")
    @MethodSource("coreRules")
    void everyCoreRuleReportsAViolationOnFixtures(ArchitectureRules.NamedRule rule) {
        assertThatThrownBy(() -> rule.rule().check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(CULPRIT_BY_CORE_RULE_ID.get(rule.id()));
    }
}
