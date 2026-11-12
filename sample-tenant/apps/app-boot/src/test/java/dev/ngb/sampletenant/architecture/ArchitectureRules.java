package dev.ngb.sampletenant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The architecture rules, parameterised by root package so the same set runs against production
 * code and against the deliberately violating fixtures.
 *
 * <p>Package convention: {@code <root>.<module>.{domain,application,adapter,api}}. A module is any
 * direct sub-package of the root except the shared core and the wiring package.
 */
final class ArchitectureRules {

    record NamedRule(String id, ArchRule rule) {}

    private static final Set<String> NON_CONTEXT = Set.of("shared", "boot");

    /** The shared core lives in its own Gradle build; every layer may name it exactly where it may name itself. */
    static final String CORE = "dev.ngb.core";

    /** The JDK: java.* plus the javax.* packages that ship with it (for example javax.crypto). */
    private static final List<String> JDK = List.of("java..", "javax..");

    private ArchitectureRules() {}

    static List<NamedRule> all(String root) {
        return List.of(
                new NamedRule("layer-direction", layerDirection(root)),
                new NamedRule("inner-layer-purity", innerLayerPurity(root)),
                new NamedRule("no-cross-module-import", noCrossModuleImport(root)),
                new NamedRule("api-exposes-no-internals", apiExposesNoInternals(root)),
                new NamedRule("no-jpa", noJpa()),
                new NamedRule("shared-core-is-independent", sharedCoreIsIndependent(root)),
                new NamedRule("datasource-and-transactions-only-in-core-spring", datasourceOnlyInInfrastructure(root)));
    }

    /**
     * The rules that guard the shared core. They are separate from the application rules because the core is a
     * different build with a different vocabulary: it knows no module, no layer and no framework.
     *
     * @param core the core's root package, so the same rules run against the production code and the fixtures
     */
    static List<NamedRule> core(String core) {
        return List.of(
                new NamedRule("core-is-self-contained", coreIsSelfContained(core)),
                new NamedRule("core-spring-is-self-contained", coreSpringIsSelfContained(core)));
    }

    /** adapter -> application -> domain; never the other way. */
    private static ArchRule layerDirection(String root) {
        ArchRule domainStaysInside = noClasses().that().resideInAPackage(root + ".*.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(root + ".*.application..", root + ".*.adapter..")
                .because("domain must not depend on application or adapter");
        ArchRule applicationStaysInside = noClasses().that().resideInAPackage(root + ".*.application..")
                .should().dependOnClassesThat().resideInAPackage(root + ".*.adapter..")
                .because("application must not depend on adapter");
        return CompositeArchRule.of(domainStaysInside).and(applicationStaysInside);
    }

    /** domain and application know only the JDK, project code and the shared core; no framework, no JPA. */
    private static ArchRule innerLayerPurity(String root) {
        return classes().that().resideInAnyPackage(root + ".*.domain..", root + ".*.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(concat(JDK, root + "..", CORE + ".."))
                .because("domain and application are plain Java");
    }

    private static ArchRule noCrossModuleImport(String root) {
        return classes().should(notReachIntoOtherModules(root))
                .because("a module may use another only through its api package");
    }

    /** The api package is the contract: it may name only the JDK, the shared core and other api packages. */
    private static ArchRule apiExposesNoInternals(String root) {
        GivenClassesConjunction api = classes().that().resideInAPackage(root + ".*.api..")
                .and().doNotHaveSimpleName("package-info");
        return api.should().onlyDependOnClassesThat()
                .resideInAnyPackage(concat(JDK, root + ".shared..", CORE + "..", root + ".*.api.."))
                .because("the api package must not leak domain or adapter types");
    }

    private static ArchRule noJpa() {
        return noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "javax.persistence..", "org.hibernate..", "org.springframework.data.jpa..")
                .because("persistence is jOOQ only");
    }

    private static ArchRule sharedCoreIsIndependent(String root) {
        return classes().that().resideInAPackage(root + ".shared..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(concat(JDK, root + ".shared..", CORE + ".."))
                .because("the shared core takes no module dependency");
    }

    /**
     * Only the shared core's Spring module and the wiring package may open transactions or reach the datasource.
     * Anything else that touches the database runs outside a tenant-scoped transaction.
     */
    private static ArchRule datasourceOnlyInInfrastructure(String root) {
        return noClasses().that().resideOutsideOfPackages(CORE + ".spring..", root + ".boot..")
                .should().dependOnClassesThat().haveFullyQualifiedName("javax.sql.DataSource")
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.transaction..", "org.springframework.jdbc.datasource..")
                .because("the shared core's UnitOfWork is the only owner of transactions and the tenant setting");
    }

    /**
     * The shared core is shared by more than one application build, so it may name only the JDK and itself. The
     * Spring implementation module also sees the Spring types its implementations are built from; neither may
     * reach back into an application.
     */
    private static ArchRule coreIsSelfContained(String core) {
        return classes().that().resideInAnyPackage(core + "..")
                .and().resideOutsideOfPackage(core + ".spring..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(concat(JDK, core + ".."))
                .because("the core is shared by every application build and depends on nothing of theirs");
    }

    private static ArchRule coreSpringIsSelfContained(String core) {
        return classes().that().resideInAPackage(core + ".spring..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(concat(JDK, core + "..", "org.springframework.."))
                .because("the core's Spring implementation sees the JDK, the core and Spring only");
    }

    private static ArchCondition<JavaClass> notReachIntoOtherModules(String root) {
        return new ArchCondition<>("not depend on another module outside its api package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = contextOf(root, origin.getPackageName());
                if (from == null) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    String to = contextOf(root, targetPackage);
                    if (to != null && !to.equals(from) && !isApi(root, to, targetPackage)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String[] concat(List<String> prefix, String... rest) {
        List<String> all = new ArrayList<>(prefix);
        all.addAll(List.of(rest));
        return all.toArray(String[]::new);
    }

    private static String contextOf(String root, String packageName) {
        String prefix = root + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        String first = dot < 0 ? rest : rest.substring(0, dot);
        return NON_CONTEXT.contains(first) ? null : first;
    }

    private static boolean isApi(String root, String context, String packageName) {
        String api = root + "." + context + ".api";
        return packageName.equals(api) || packageName.startsWith(api + ".");
    }
}
