package io.corebilling.architecture;

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

    /** Each rule and the fixture class that must be named in its failure report. */
    private static final Map<String, String> CULPRIT_BY_RULE_ID = new LinkedHashMap<>();

    static {
        CULPRIT_BY_RULE_ID.put("layer-direction", "DomainReachesApplication");
        CULPRIT_BY_RULE_ID.put("inner-layer-purity", "DomainUsesSpring");
        CULPRIT_BY_RULE_ID.put("no-cross-context-import", "ReachesIntoBeta");
        CULPRIT_BY_RULE_ID.put("persistence-types-in-adapter", "RowInDomain");
        CULPRIT_BY_RULE_ID.put("money-use-cases-use-unit-of-work", "ChargeUseCase");
        CULPRIT_BY_RULE_ID.put("api-exposes-no-internals", "LeaksDomain");
        CULPRIT_BY_RULE_ID.put("ledger-repository-is-append-only", "LedgerRepository");
        CULPRIT_BY_RULE_ID.put("datasource-and-transactions-only-in-infrastructure", "TouchesDataSource");
        CULPRIT_BY_RULE_ID.put("outbound-http-only-through-the-guarded-sender", "OpensItsOwnHttpConnection");
        CULPRIT_BY_RULE_ID.put("stripe-types-stay-in-the-stripe-adapter", "UsesStripeDirectly");
        CULPRIT_BY_RULE_ID.put("no-jpa", "JpaEntity");
        CULPRIT_BY_RULE_ID.put("shared-kernel-is-independent", "KernelKnowsAContext");
        CULPRIT_BY_RULE_ID.put("role-package-not-layer-root", "StrayRoleClass");
        CULPRIT_BY_RULE_ID.put("use-cases-and-queries-in-command-or-query", "MisplacedUseCase");
        CULPRIT_BY_RULE_ID.put("ports-in-port-or-query", "MisplacedReadRepository");
        CULPRIT_BY_RULE_ID.put("table-rows-in-adapter-persistence", "TableInApplication");
        CULPRIT_BY_RULE_ID.put("persistence-maps-without-row-mapper", "DomainRowMapper");
        CULPRIT_BY_RULE_ID.put("write-side-does-not-depend-on-query", "CommandReachesQuery");
        CULPRIT_BY_RULE_ID.put("read-side-does-not-depend-on-write", "QueryReachesWritePort");
        CULPRIT_BY_RULE_ID.put("query-side-uses-no-domain-aggregates", "QueryReachesAggregate");
        CULPRIT_BY_RULE_ID.put("write-ports-serve-no-reads", "WritePort");
    }

    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
        assertThat(fixtures).isNotEmpty();
    }

    static Stream<ArchitectureRules.NamedRule> rules() {
        return ArchitectureRules.all(FIXTURE_ROOT).stream();
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
}
