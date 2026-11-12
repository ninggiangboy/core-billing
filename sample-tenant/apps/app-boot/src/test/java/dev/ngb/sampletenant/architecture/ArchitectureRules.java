package dev.ngb.sampletenant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final List<String> LAYERS = List.of("domain", "application", "adapter", "api");

    /** The role packages of the application layer; every application class sits in exactly one of them. */
    private static final Set<String> APPLICATION_ROLES = Set.of("command", "query", "port", "service", "exception");

    /** Interfaces in application whose name ends in one of these are outbound (write-side) ports. */
    private static final Set<String> OUTBOUND_PORT_SUFFIXES = Set.of(
            "Repository", "Port", "Provider", "Access", "Sender", "Signer", "Cipher", "Writer", "Store", "Keys", "Limiter",
            "Units", "Registry", "Verifier");

    /** Which module owns each generated table; execution owns no table (decision D1 gives execution_queue to resource). */
    private static final Map<String, String> TABLE_OWNER = new HashMap<>();

    static {
        for (String table : List.of("Users", "UserSessions")) {
            TABLE_OWNER.put(table, "identity");
        }
        for (String table : List.of("Customers", "CustomerWalletSettings")) {
            TABLE_OWNER.put(table, "customer");
        }
        TABLE_OWNER.put("CatalogPlans", "catalog");
        TABLE_OWNER.put("UsageRecords", "usage");
        for (String table : List.of("Orders", "ExecutionQueue")) {
            TABLE_OWNER.put(table, "resource");
        }
        for (String table : List.of("WebhookEvents", "CustomerDebts", "CustomerPeriods")) {
            TABLE_OWNER.put(table, "webhook");
        }
    }

    /** Domain types a read model may name besides enums. Read models use primitives where they can. */
    private static final Set<String> ALLOWED_QUERY_DOMAIN_TYPES = Set.of();

    private ArchitectureRules() {}

    static List<NamedRule> all(String root) {
        return List.of(
                new NamedRule("layer-direction", layerDirection(root)),
                new NamedRule("inner-layer-purity", innerLayerPurity(root)),
                new NamedRule("no-cross-module-import", noCrossModuleImport(root)),
                new NamedRule("api-exposes-no-internals", apiExposesNoInternals(root)),
                new NamedRule("no-jpa", noJpa()),
                new NamedRule("shared-core-is-independent", sharedCoreIsIndependent(root)),
                new NamedRule("datasource-and-transactions-only-in-core-spring", datasourceOnlyInInfrastructure(root)),
                new NamedRule("role-package-not-layer-root", rolePackageNotLayerRoot(root)),
                new NamedRule("use-cases-and-queries-in-command-or-query", useCasesAndQueriesInCommandOrQuery(root)),
                new NamedRule("ports-in-port-or-query", portsInPortOrQuery(root)),
                new NamedRule("application-classes-in-a-role-package", applicationClassesInARolePackage(root)),
                new NamedRule("command-package-holds-only-use-cases", commandPackageHoldsOnlyUseCases(root)),
                new NamedRule("port-package-holds-no-implementations", portPackageHoldsNoImplementations(root)),
                new NamedRule("adapter-classes-in-feature-then-technology", adapterClassesInFeatureThenTechnology(root)),
                new NamedRule("api-implementations-are-services", apiImplementationsAreServices(root)),
                new NamedRule("web-does-not-depend-on-persistence", webDoesNotDependOnPersistence(root)),
                new NamedRule("persistence-types-in-adapter", persistenceTypesInAdapter(root)),
                new NamedRule("generated-records-in-adapter-persistence", generatedRecordsInAdapterPersistence(root)),
                new NamedRule("module-uses-only-own-tables", moduleUsesOnlyItsOwnTables(root)),
                new NamedRule("write-side-does-not-depend-on-query", writeSideDoesNotDependOnQuery(root)),
                new NamedRule("read-side-does-not-depend-on-write", readSideDoesNotDependOnWrite(root)),
                new NamedRule("query-side-uses-no-domain-aggregates", querySideUsesNoDomainAggregates(root)),
                new NamedRule("write-ports-serve-no-reads", writePortsServeNoReads(root)),
                new NamedRule("repository-pair-shares-a-package", repositoryPairSharesAPackage(root)));
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

    /**
     * Inside a module no class sits directly in a layer root package: every package is grouped by feature
     * and then by role, so a reader sees one job per package. {@code package-info} declarations stay.
     */
    private static ArchRule rolePackageNotLayerRoot(String root) {
        List<String> roots = new ArrayList<>();
        for (String layer : LAYERS) {
            roots.add(root + ".*." + layer);
        }
        return classes().that().resideInAnyPackage(roots.toArray(String[]::new))
                .and().doNotHaveSimpleName("package-info")
                .should(never("must not sit directly in a layer root package: group it under a feature and role package"))
                .because("a module groups packages by feature and then by role")
                .allowEmptyShould(true);
    }

    /** A use case belongs to the command side and a query class to the query side. */
    private static ArchRule useCasesAndQueriesInCommandOrQuery(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .should(new ArchCondition<>("be in a command or query package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String name = item.getSimpleName();
                        String packageName = item.getPackageName();
                        if (name.endsWith("UseCase") && !hasSegment(packageName, "command")) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is a use case but does not live in a ..command.. package"));
                        }
                        if (name.endsWith("Queries") && !hasSegment(packageName, "query")) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is a query class but does not live in a ..query.. package"));
                        }
                    }
                })
                .because("the command side owns use cases and the query side owns query classes");
    }

    /** Outbound write ports live in port/, read ports in query/. A read port is named *ReadRepository. */
    private static ArchRule portsInPortOrQuery(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .and().areInterfaces()
                .should(new ArchCondition<>("be in a port or query package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String name = item.getSimpleName();
                        String packageName = item.getPackageName();
                        if (name.endsWith("ReadRepository")) {
                            if (!hasSegment(packageName, "query")) {
                                events.add(SimpleConditionEvent.violated(item,
                                        item.getFullName() + " is a read port but does not live in a ..query.. package"));
                            }
                        } else if (isOutboundPort(name) && !hasSegment(packageName, "port")) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is a write port but does not live in a ..port.. package"));
                        }
                    }
                })
                .because("write ports live with the command side and read ports with the query side");
    }

    /**
     * Every application class sits in a command, query, port, service or exception package below its feature, so
     * a reader can tell a use case from a helper, a port and an error by the package alone.
     */
    private static ArchRule applicationClassesInARolePackage(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .and().doNotHaveSimpleName("package-info")
                .and(areTopLevel())
                .should(new ArchCondition<>("sit in a command, query, port, service or exception package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean inRole = false;
                        for (String role : APPLICATION_ROLES) {
                            inRole |= hasSegment(item.getPackageName(), role);
                        }
                        if (!inRole) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is not in a command, query, port, service or exception package"));
                        }
                    }
                })
                .because("an application package has one job: use cases, read side, ports, services or errors");
    }

    /** The command package holds use cases only; collaborators of a use case live in service. */
    private static ArchRule commandPackageHoldsOnlyUseCases(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .and().doNotHaveSimpleName("package-info")
                .and(areTopLevel())
                .should(new ArchCondition<>("be a *UseCase when it sits in a command package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (hasSegment(item.getPackageName(), "command") && !item.getSimpleName().endsWith("UseCase")) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is in a command package but is not named *UseCase"));
                        }
                    }
                })
                .because("a command package holds use cases; a helper belongs in a service package");
    }

    /** A port package declares interfaces and their data carriers; an implementation belongs to the adapter. */
    private static ArchRule portPackageHoldsNoImplementations(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .and().doNotHaveSimpleName("package-info")
                .and(areTopLevel())
                .should(new ArchCondition<>("be an interface, record or enum when it sits in a port package") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (hasSegment(item.getPackageName(), "port")
                                && !(item.isInterface() || item.isRecord() || item.isEnum())) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is a class in a port package; ports are interfaces"));
                        }
                    }
                })
                .because("a port package declares what the application needs; a service class belongs in service");
    }

    /** An adapter class sits in a feature package and then a technology package: adapter.&lt;feature&gt;.&lt;technology&gt;. */
    private static ArchRule adapterClassesInFeatureThenTechnology(String root) {
        return classes().that().resideInAnyPackage(root + ".*.adapter..")
                .and().doNotHaveSimpleName("package-info")
                .and(areTopLevel())
                .should(new ArchCondition<>("sit in adapter.<feature>.<technology>") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String packageName = item.getPackageName();
                        int adapter = packageName.indexOf(".adapter");
                        String below = adapter < 0 ? "" : packageName.substring(adapter + ".adapter".length());
                        int segments = below.isEmpty() ? 0 : below.substring(1).split("\\.").length;
                        if (segments < 2) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is not in a feature package and then a technology package below adapter"));
                        }
                    }
                })
                .because("an adapter is grouped by feature first and by technology second");
    }

    /**
     * A class that implements a public api interface for other modules, other than a use case, is the
     * {@code <Interface>Service} of its feature and lives in the service package.
     */
    private static ArchRule apiImplementationsAreServices(String root) {
        return classes().that().resideInAnyPackage(root + ".*.application..")
                .and(areTopLevel())
                .and().areNotInterfaces()
                .and().haveSimpleNameNotEndingWith("UseCase")
                .should(new ArchCondition<>("be <Interface>Service in a service package when it implements an api interface") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaClass api : item.getAllRawInterfaces()) {
                            if (!api.getPackageName().startsWith(root + ".") || !isApiPackage(root, api.getPackageName())) {
                                continue;
                            }
                            String expected = api.getSimpleName() + "Service";
                            if (!item.getSimpleName().equals(expected) || !hasSegment(item.getPackageName(), "service")) {
                                events.add(SimpleConditionEvent.violated(item, item.getFullName() + " implements the api interface "
                                        + api.getSimpleName() + " but is not " + expected + " in a service package"));
                            }
                        }
                    }
                })
                .because("one convention for the class that serves another module: <Interface>Service in service");
    }

    /** A controller reaches the data through the application layer; a shared wire format lives in its own package. */
    private static ArchRule webDoesNotDependOnPersistence(String root) {
        return noClasses().that().resideInAnyPackage(root + ".*.adapter..web..")
                .should().dependOnClassesThat().resideInAnyPackage(root + ".*.adapter..persistence..")
                .because("web and persistence are separate technologies; a shape both use belongs in its own package")
                .allowEmptyShould(true);
    }

    private static ArchRule persistenceTypesInAdapter(String root) {
        return noClasses().that().resideOutsideOfPackages(root + ".*.adapter..persistence..", root + ".boot..")
                .should().dependOnClassesThat().resideInAPackage("dev.ngb.sampletenant_jooq..")
                .because("generated jOOQ types live in an adapter persistence package");
    }

    /** A generated jOOQ record is an adapter detail and lives next to the repository that uses it. */
    private static ArchRule generatedRecordsInAdapterPersistence(String root) {
        return noClasses().that().resideOutsideOfPackages(root + ".*.adapter..persistence..", root + ".boot..")
                .should().dependOnClassesThat().resideInAPackage("dev.ngb.sampletenant_jooq.tables.records..")
                .because("a generated record lives in an adapter persistence package next to the repository that uses it");
    }

    /** A module's adapters use only the tables it owns. */
    private static ArchRule moduleUsesOnlyItsOwnTables(String root) {
        return classes().that().resideInAnyPackage(root + ".*.adapter..", root + ".boot..")
                .should(new ArchCondition<>("use only the tables its own module owns") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String context = owningContextOf(root, item.getPackageName());
                        if (context == null) {
                            return;
                        }
                        for (var access : item.getFieldAccessesFromSelf()) {
                            if (!"dev.ngb.sampletenant_jooq.Tables".equals(access.getTarget().getOwner().getName())) {
                                continue;
                            }
                            String table = tableName(access.getTarget().getName());
                            String owner = TABLE_OWNER.get(table);
                            if (owner != null && !owner.equals(context)) {
                                events.add(SimpleConditionEvent.violated(item,
                                        item.getFullName() + " uses " + table + ", owned by " + owner));
                            }
                        }
                    }
                })
                .because("each module persists only its own tables");
    }

    /** The command side never reads through the query side; a use case that must read decides with its own port. */
    private static ArchRule writeSideDoesNotDependOnQuery(String root) {
        return noClasses().that().resideInAnyPackage(root + ".*..command..", root + ".*..port..")
                .should().dependOnClassesThat().resideInAnyPackage(root + ".*..query..")
                .because("the write side decides with its own ports and never through a read model")
                .allowEmptyShould(true);
    }

    /**
     * The query side names views and read ports only. It never reaches into the command side and never depends
     * on a write port, so a read cannot accidentally drive a write.
     */
    private static ArchRule readSideDoesNotDependOnWrite(String root) {
        ArchRule byPackage = noClasses().that().resideInAnyPackage(root + ".*..query..")
                .should().dependOnClassesThat().resideInAnyPackage(root + ".*..command..", root + ".*..port..")
                .because("a query class reads through a read port and never through the write side")
                .allowEmptyShould(true);
        ArchRule byName = noClasses().that().resideInAnyPackage(root + "..")
                .and().haveSimpleNameStartingWith("Jooq").and().haveSimpleNameEndingWith("ReadRepository")
                .should().dependOnClassesThat().resideInAnyPackage(root + ".*..command..", root + ".*..port..")
                .because("a read repository selects into a view and never into the write side")
                .allowEmptyShould(true);
        return CompositeArchRule.of(byPackage).and(byName).allowEmptyShould(true);
    }

    /**
     * A read model is never a domain aggregate. Should a read need a value object, the explicit allowlist
     * records it for review instead of letting the domain leak in silently.
     */
    private static ArchRule querySideUsesNoDomainAggregates(String root) {
        return classes().that().resideInAnyPackage(root + ".*..query..")
                .should(new ArchCondition<>("not use a domain aggregate") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            if (isDomainPackage(root, target.getPackageName())
                                    && !target.isEnum()
                                    && !ALLOWED_QUERY_DOMAIN_TYPES.contains(target.getFullName())) {
                                events.add(SimpleConditionEvent.violated(item,
                                        item.getFullName() + " depends on domain type " + target.getFullName()));
                            }
                        }
                    }
                })
                .because("a read model is shaped for its caller and is never a domain aggregate");
    }

    /** The write side never serves reads: a port has no list, page or search method. */
    private static ArchRule writePortsServeNoReads(String root) {
        return noMethods().that().areDeclaredInClassesThat().resideInAnyPackage(root + ".*..port..")
                .and().areDeclaredInClassesThat().areInterfaces()
                .should().haveNameMatching("(list|page|search).*")
                .because("the write side never serves reads; a list, page or search method belongs to a read port")
                .allowEmptyShould(true);
    }

    /** A feature's write repository and read repository sit side by side. */
    private static ArchRule repositoryPairSharesAPackage(String root) {
        return classes().that().resideInAnyPackage(root + ".*.adapter..persistence..")
                .and().haveSimpleNameStartingWith("Jooq").and().haveSimpleNameEndingWith("Repository")
                .should(new ArchCondition<>("sit in the same package as its feature's write repository") {
                    private final Map<String, JavaClass> byContextAndName = new HashMap<>();

                    @Override
                    public void init(Collection<JavaClass> allObjectsToTest) {
                        for (JavaClass candidate : allObjectsToTest) {
                            byContextAndName.put(
                                    owningContextOf(root, candidate.getPackageName()) + "/" + candidate.getSimpleName(),
                                    candidate);
                        }
                    }

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String name = item.getSimpleName();
                        if (!name.endsWith("ReadRepository")) {
                            return;
                        }
                        String writeName = name.substring(0, name.length() - "ReadRepository".length()) + "Repository";
                        JavaClass write =
                                byContextAndName.get(owningContextOf(root, item.getPackageName()) + "/" + writeName);
                        if (write != null && !write.getPackageName().equals(item.getPackageName())) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is not in the same package as " + write.getFullName()));
                        }
                    }
                })
                .because("the write and read repository of one feature share a package")
                .allowEmptyShould(true);
    }

    private static boolean isDomainPackage(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return false;
        }
        return hasSegment(packageName.substring(root.length() + 1), "domain");
    }

    private static String owningContextOf(String root, String packageName) {
        String prefix = root + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static String tableName(String constant) {
        StringBuilder name = new StringBuilder();
        for (String part : constant.split("_")) {
            name.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return name.toString();
    }

    private static boolean isApiPackage(String root, String packageName) {
        String rest = packageName.substring(root.length() + 1);
        int dot = rest.indexOf('.');
        return dot > 0 && (rest.substring(dot + 1).equals("api") || rest.substring(dot + 1).startsWith("api."));
    }

    private static DescribedPredicate<JavaClass> areTopLevel() {
        return DescribedPredicate.describe("top-level", javaClass -> javaClass.getEnclosingClass().isEmpty());
    }

    private static boolean isOutboundPort(String simpleName) {
        return OUTBOUND_PORT_SUFFIXES.stream().anyMatch(simpleName::endsWith);
    }

    private static boolean hasSegment(String packageName, String segment) {
        for (String part : packageName.split("\\.")) {
            if (part.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static ArchCondition<JavaClass> never(String reason) {
        return new ArchCondition<>("not exist: " + reason) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                events.add(SimpleConditionEvent.violated(item, item.getFullName() + " " + reason));
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
