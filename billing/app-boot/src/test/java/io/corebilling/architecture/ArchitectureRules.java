package io.corebilling.architecture;

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
import java.util.List;
import java.util.Set;

/**
 * The architecture rules, parameterised by root package so the same set runs against production
 * code and against the deliberately violating fixtures. ArchUnit owns layer, purity and import
 * rules; Spring Modulith owns module verification (see ModulithVerificationTest).
 *
 * <p>Package convention: {@code <root>.<context>.{domain,application,adapter,api}}. A context is
 * any direct sub-package of the root except the kernel and the wiring package.
 */
final class ArchitectureRules {

    record NamedRule(String id, ArchRule rule) {}

    private static final Set<String> NON_CONTEXT = Set.of("sharedkernel", "boot");
    private static final String JAVA = "java..";

    private ArchitectureRules() {}

    static List<NamedRule> all(String root) {
        return List.of(
                new NamedRule("layer-direction", layerDirection(root)),
                new NamedRule("inner-layer-purity", innerLayerPurity(root)),
                new NamedRule("no-cross-context-import", noCrossContextImport(root)),
                new NamedRule("persistence-types-in-adapter", persistenceTypesInAdapter(root)),
                new NamedRule("money-use-cases-use-unit-of-work", moneyUseCasesUseUnitOfWork(root)),
                new NamedRule("api-exposes-no-internals", apiExposesNoInternals(root)),
                new NamedRule("no-jpa", noJpa()),
                new NamedRule("shared-kernel-is-independent", sharedKernelIsIndependent(root)));
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

    /** domain and application know only the JDK and project code; no framework, no JPA, no Jackson. */
    private static ArchRule innerLayerPurity(String root) {
        return classes().that().resideInAnyPackage(root + ".*.domain..", root + ".*.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(JAVA, root + "..")
                .because("domain and application are plain Java");
    }

    private static ArchRule noCrossContextImport(String root) {
        return classes().should(notReachIntoOtherContexts(root))
                .because("a context may use another only through its api package");
    }

    private static ArchRule persistenceTypesInAdapter(String root) {
        return noClasses().that().resideOutsideOfPackages(root + ".*.adapter.persistence..", root + ".boot..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
                .because("row types and repositories live in adapter.persistence");
    }

    /** A "use case" is a class named *UseCase; every one in money must open the transaction itself. */
    private static ArchRule moneyUseCasesUseUnitOfWork(String root) {
        return classes().that().resideInAPackage(root + ".money.application..")
                .and().haveSimpleNameEndingWith("UseCase")
                .and().areNotInterfaces()
                .should().dependOnClassesThat().haveSimpleName("UnitOfWork")
                .because("the money path commits in one transaction opened by the use case");
    }

    /** The api package is the contract: it may name only the JDK, the kernel and other api packages. */
    private static ArchRule apiExposesNoInternals(String root) {
        GivenClassesConjunction api = classes().that().resideInAPackage(root + ".*.api..")
                .and().doNotHaveSimpleName("package-info");
        return api.should().onlyDependOnClassesThat()
                .resideInAnyPackage(JAVA, root + ".sharedkernel..", root + ".*.api..")
                .because("the api package must not leak domain or adapter types");
    }

    private static ArchRule noJpa() {
        return noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "javax.persistence..", "org.hibernate..", "org.springframework.data.jpa..")
                .because("persistence is Spring Data JDBC only");
    }

    private static ArchRule sharedKernelIsIndependent(String root) {
        return classes().that().resideInAPackage(root + ".sharedkernel..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(JAVA, root + ".sharedkernel..")
                .because("the kernel takes no module dependency");
    }

    private static ArchCondition<JavaClass> notReachIntoOtherContexts(String root) {
        return new ArchCondition<>("not depend on another context outside its api package") {
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
