package io.corebilling.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The architecture rules, parameterised by root package so the same set runs against production
 * code and against the deliberately violating fixtures. ArchUnit owns layer, purity, layout,
 * persistence and read/write rules; Spring Modulith owns module verification (see
 * ModulithVerificationTest).
 *
 * <p>Package convention: {@code <root>.<context>.{domain,application,adapter,api}}. A context is
 * any direct sub-package of the root except the kernel and the wiring package. Inside a context,
 * packages are grouped by feature and then by role: {@code command}, {@code query}, {@code port}.
 *
 * <p>The three {@link ContextSets} let a refactor turn the new rules on one module at a time: a
 * context appears in a set only once it has adopted that part of the target architecture.
 */
final class ArchitectureRules {

    record NamedRule(String id, ArchRule rule) {}

    /** Contexts that have adopted each part of the target architecture. */
    record ContextSets(Set<String> layout, Set<String> persistence, Set<String> readWrite) {

        static ContextSets none() {
            return new ContextSets(Set.of(), Set.of(), Set.of());
        }

        static ContextSets every(Set<String> contexts) {
            return new ContextSets(contexts, contexts, contexts);
        }
    }

    private static final Set<String> NON_CONTEXT = Set.of("sharedkernel", "boot");
    private static final String JAVA = "java..";
    private static final List<String> LAYERS = List.of("domain", "application", "adapter", "api");
    private static final List<String> READ_METHOD_PREFIXES = List.of("list", "page", "search");

    /** Interfaces in application whose name ends in one of these are outbound (write-side) ports. */
    private static final Set<String> OUTBOUND_PORT_SUFFIXES = Set.of(
            "Repository", "Port", "Provider", "Access", "Sender", "Signer", "Cipher", "Writer", "Store", "Keys", "Limiter",
            "Units", "Registry");

    /**
     * Domain types a read model may name besides enums. Read models are shaped for the caller, so they
     * use primitives where they can; this list is kept short and reviewed whenever it grows.
     */
    private static final Set<String> ALLOWED_QUERY_DOMAIN_TYPES = Set.of();

    private ArchitectureRules() {}

    static List<NamedRule> all(String root, ContextSets sets) {
        return List.of(
                new NamedRule("layer-direction", layerDirection(root)),
                new NamedRule("inner-layer-purity", innerLayerPurity(root)),
                new NamedRule("no-cross-context-import", noCrossContextImport(root)),
                new NamedRule("persistence-types-in-adapter", persistenceTypesInAdapter(root)),
                new NamedRule("money-use-cases-use-unit-of-work", moneyUseCasesUseUnitOfWork(root)),
                new NamedRule("api-exposes-no-internals", apiExposesNoInternals(root)),
                new NamedRule("ledger-repository-is-append-only", ledgerRepositoryIsAppendOnly()),
                new NamedRule("datasource-and-transactions-only-in-infrastructure", datasourceOnlyInInfrastructure(root)),
                new NamedRule("outbound-http-only-through-the-guarded-sender", outboundHttpOnlyThroughTheGuardedSender(root)),
                new NamedRule("stripe-types-stay-in-the-stripe-adapter", stripeTypesStayInTheStripeAdapter(root)),
                new NamedRule("no-jpa", noJpa()),
                new NamedRule("shared-kernel-is-independent", sharedKernelIsIndependent(root)),
                new NamedRule("role-package-not-layer-root", rolePackageNotLayerRoot(root, sets.layout())),
                new NamedRule("use-cases-and-queries-in-command-or-query", useCasesAndQueriesInCommandOrQuery(root, sets.layout())),
                new NamedRule("ports-in-port-or-query", portsInPortOrQuery(root, sets.layout())),
                new NamedRule("table-rows-in-adapter-persistence", tableRowsInAdapterPersistence(root, sets.layout())),
                new NamedRule("persistence-maps-without-row-mapper", persistenceMapsWithoutRowMapper(root, sets.persistence())),
                new NamedRule("write-side-does-not-depend-on-query", writeSideDoesNotDependOnQuery(root, sets.readWrite())),
                new NamedRule("read-side-does-not-depend-on-write", readSideDoesNotDependOnWrite(root, sets.readWrite())),
                new NamedRule("query-side-uses-no-domain-aggregates", querySideUsesNoDomainAggregates(root, sets.readWrite())),
                new NamedRule("write-ports-serve-no-reads", writePortsServeNoReads(root, sets.readWrite())));
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

    /**
     * Only the unit of work may open transactions or reach the datasource. Anything else that touches the
     * database runs outside a tenant-scoped transaction, where row-level security returns no rows.
     */
    private static ArchRule datasourceOnlyInInfrastructure(String root) {
        return noClasses().that().resideOutsideOfPackage(root + ".boot.infrastructure..")
                .should().dependOnClassesThat().haveFullyQualifiedName("javax.sql.DataSource")
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.transaction..", "org.springframework.jdbc.datasource..")
                .because("the UnitOfWork is the only owner of transactions and the tenant setting");
    }

    /** The ledger's repository port can append and read; a method that changes or removes an entry cannot exist. */
    private static ArchRule ledgerRepositoryIsAppendOnly() {
        return noMethods().that().areDeclaredInClassesThat().haveSimpleName("LedgerRepository")
                .should().haveNameMatching("(update|delete|remove|save|set|merge|upsert|truncate).*")
                .because("the ledger is append-only: corrections are reversal entries, never edits");
    }

    /**
     * Webhook URLs are chosen by Tenants, so every call to one must go through the sender whose resolver refuses
     * non-public addresses. Any other HTTP client inside the webhook module would bypass that guard.
     */
    private static ArchRule outboundHttpOnlyThroughTheGuardedSender(String root) {
        return noClasses().that().resideInAPackage(root + ".webhook..")
                .and().resideOutsideOfPackage(root + ".webhook.adapter.http..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.net.http..", "org.apache.hc..", "org.springframework.web.client..", "org.springframework.http.client..",
                        "org.springframework.web.reactive.function.client..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.URL")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.HttpURLConnection")
                .because("a Tenant-chosen destination must only be called through the guarded sender");
    }

    /**
     * Stripe's SDK types may be named only by the Stripe adapter. Everything else talks to the PaymentProvider
     * port, which is what keeps a second provider a matter of one adapter and keeps API drift in one place.
     */
    private static ArchRule stripeTypesStayInTheStripeAdapter(String root) {
        return noClasses().that().resideOutsideOfPackage(root + ".payment.adapter.stripe..")
                .should().dependOnClassesThat().resideInAPackage("com.stripe..")
                .because("Stripe's types belong to the Stripe adapter; the rest of the system sees the PaymentProvider port");
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

    /**
     * Inside a converted context no class sits directly in a layer root package: every package is grouped by
     * feature and then by role, so a reader sees one job per package. {@code package-info} declarations stay.
     */
    private static ArchRule rolePackageNotLayerRoot(String root, Set<String> contexts) {
        List<String> roots = new ArrayList<>();
        for (String context : contexts) {
            for (String layer : LAYERS) {
                roots.add(root + "." + context + "." + layer);
            }
        }
        return classes().that().resideInAnyPackage(packagesOrPlaceholder(root, roots))
                .and().doNotHaveSimpleName("package-info")
                .should(never("must not sit directly in a layer root package: group it under a feature and role package"))
                .because("a converted context groups packages by feature and then by role");
    }

    /** A use case belongs to the command side and a query class to the query side. */
    private static ArchRule useCasesAndQueriesInCommandOrQuery(String root, Set<String> contexts) {
        return classes().that().resideInAnyPackage(packagesOrPlaceholder(root, applicationPackages(root, contexts)))
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
    private static ArchRule portsInPortOrQuery(String root, Set<String> contexts) {
        return classes().that().resideInAnyPackage(packagesOrPlaceholder(root, applicationPackages(root, contexts)))
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

    /** A Spring Data row is an adapter detail and lives next to its write repository. */
    private static ArchRule tableRowsInAdapterPersistence(String root, Set<String> contexts) {
        List<String> persistence = persistencePackages(root, contexts);
        return classes().that().areAnnotatedWith(Table.class)
                .and().resideInAnyPackage(packagesOrPlaceholder(root, contextPackages(root, contexts)))
                .should().resideInAnyPackage(packagesOrPlaceholder(root, persistence))
                .because("a @Table row lives in adapter.persistence next to the repository that uses it");
    }

    /**
     * Persistence maps result sets through {@code query(Class)} into a Row or a view. A RowMapper would let a
     * result set flow straight into a domain type again, which is the shape this refactor removes.
     */
    private static ArchRule persistenceMapsWithoutRowMapper(String root, Set<String> contexts) {
        return noClasses().that().resideInAnyPackage(packagesOrPlaceholder(root, persistencePackages(root, contexts)))
                .should().callMethodWhere(new DescribedPredicate<>("call JdbcClient.query(RowMapper)") {
                    @Override
                    public boolean test(JavaMethodCall call) {
                        return queriesWithARowMapper(call);
                    }
                })
                .because("persistence maps through query(Class); a RowMapper could map straight into a domain type")
                .allowEmptyShould(true);
    }

    /** The command side never reads through the query side; a use case that must read decides with its own port. */
    private static ArchRule writeSideDoesNotDependOnQuery(String root, Set<String> contexts) {
        return noClasses().that().resideInAnyPackage(packagesOrPlaceholder(root, rolePackages(root, contexts, "command", "port")))
                .should().dependOnClassesThat().resideInAnyPackage(packagesOrPlaceholder(root, rolePackages(root, contexts, "query")))
                .because("the write side decides with its own ports and never through a read model")
                .allowEmptyShould(true);
    }

    /**
     * The query side names views and read ports only. It never reaches into the command side and never depends
     * on a write port, so a read cannot accidentally drive a write.
     */
    private static ArchRule readSideDoesNotDependOnWrite(String root, Set<String> contexts) {
        List<String> queryPackages = rolePackages(root, contexts, "query");
        List<String> writePackages = rolePackages(root, contexts, "command", "port");
        ArchRule byPackage = noClasses().that().resideInAnyPackage(packagesOrPlaceholder(root, queryPackages))
                .should().dependOnClassesThat().resideInAnyPackage(packagesOrPlaceholder(root, writePackages))
                .because("a query class reads through a read port and never through the write side");
        ArchRule byName = noClasses().that().resideInAnyPackage(packagesOrPlaceholder(root, contextPackages(root, contexts)))
                .and().haveSimpleNameStartingWith("Jdbc").and().haveSimpleNameEndingWith("ReadRepository")
                .should().dependOnClassesThat().resideInAnyPackage(packagesOrPlaceholder(root, writePackages))
                .because("a read repository selects into a view and never into the write side")
                .allowEmptyShould(true);
        return CompositeArchRule.of(byPackage).and(byName).allowEmptyShould(true);
    }

    /**
     * A read model is never a domain aggregate. Should a read need a value object or an enum, the explicit
     * allowlist records it for review instead of letting the domain leak in silently.
     */
    private static ArchRule querySideUsesNoDomainAggregates(String root, Set<String> contexts) {
        List<String> domainPackages = new ArrayList<>();
        for (String context : contexts) {
            domainPackages.add(root + "." + context + ".domain");
        }
        return classes().that().resideInAnyPackage(packagesOrPlaceholder(root, rolePackages(root, contexts, "query")))
                .should(new ArchCondition<>("not use a domain aggregate") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            if (isInAny(target.getPackageName(), domainPackages, true)
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
    private static ArchRule writePortsServeNoReads(String root, Set<String> contexts) {
        return noMethods().that().areDeclaredInClassesThat()
                .resideInAnyPackage(packagesOrPlaceholder(root, rolePackages(root, contexts, "port")))
                .and().areDeclaredInClassesThat().areInterfaces()
                .should().haveNameMatching("(list|page|search).*")
                .because("the write side never serves reads; a list, page or search method belongs to a read port")
                .allowEmptyShould(true);
    }

    private static boolean queriesWithARowMapper(JavaMethodCall call) {
        if (!call.getTarget().getName().equals("query")) {
            return false;
        }
        if (!call.getTarget().getOwner().isAssignableTo(JdbcClient.StatementSpec.class)) {
            return false;
        }
        return Arrays.stream(call.getTarget().getRawParameterTypes().toArray(JavaClass[]::new))
                .anyMatch(type -> type.isAssignableTo(RowMapper.class));
    }

    private static boolean isOutboundPort(String simpleName) {
        return OUTBOUND_PORT_SUFFIXES.stream().anyMatch(simpleName::endsWith);
    }

    private static List<String> contextPackages(String root, Set<String> contexts) {
        List<String> packages = new ArrayList<>();
        for (String context : contexts) {
            packages.add(root + "." + context + "..");
        }
        return packages;
    }

    private static List<String> applicationPackages(String root, Set<String> contexts) {
        List<String> packages = new ArrayList<>();
        for (String context : contexts) {
            packages.add(root + "." + context + ".application..");
        }
        return packages;
    }

    private static List<String> persistencePackages(String root, Set<String> contexts) {
        List<String> packages = new ArrayList<>();
        for (String context : contexts) {
            packages.add(root + "." + context + ".adapter.persistence..");
        }
        return packages;
    }

    private static List<String> rolePackages(String root, Set<String> contexts, String... roles) {
        List<String> packages = new ArrayList<>();
        for (String context : contexts) {
            for (String role : roles) {
                packages.add(root + "." + context + ".." + role + "..");
            }
        }
        return packages;
    }

    private static String[] packagesOrPlaceholder(String root, List<String> packages) {
        if (packages.isEmpty()) {
            return new String[] {root + ".__not_converted__"};
        }
        return new LinkedHashSet<>(packages).toArray(String[]::new);
    }

    private static boolean hasSegment(String packageName, String segment) {
        for (String part : packageName.split("\\.")) {
            if (part.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInAny(String packageName, List<String> packages, boolean includeSubPackages) {
        for (String candidate : packages) {
            if (packageName.equals(candidate) || (includeSubPackages && packageName.startsWith(candidate + "."))) {
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
