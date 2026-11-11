package dev.ngb.core_billing.architecture;

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
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * The architecture rules, parameterised by root package so the same set runs against production
 * code and against the deliberately violating fixtures. ArchUnit owns layer, purity, layout,
 * persistence and read/write rules; Spring Modulith owns module verification (see
 * ModulithVerificationTest).
 *
 * <p>Package convention: {@code <root>.<context>.{domain,application,adapter,api}}. A context is
 * any direct sub-package of the root except the kernel and the wiring package. Inside a context,
 * packages are grouped by feature and then by role: {@code command} (use cases), {@code query} (read side),
 * {@code port} (outbound interfaces), {@code service} (collaborators of use cases) and {@code exception}.
 *
 * <p>Every context is checked; there is no exemption list.
 */
final class ArchitectureRules {

    record NamedRule(String id, ArchRule rule) {}

    private static final Set<String> NON_CONTEXT = Set.of("sharedkernel", "boot");

    /** Which context owns each generated table; boot owns the tables every context shares. */
    private static final Map<String, String> TABLE_OWNER = new HashMap<>();

    static {
        for (String table : List.of("Customers", "CustomerMappings")) {
            TABLE_OWNER.put(table, "customer");
        }
        TABLE_OWNER.put("UsageEvents", "usage");
        for (String table : List.of("WebhookEndpoints", "WebhookDeliveries", "WebhookDeliveryAttempts")) {
            TABLE_OWNER.put(table, "webhook");
        }
        for (String table : List.of(
                "ApiCredentials", "Roles", "RolePermissions", "Permissions", "BillingUsers", "RoleAssignments", "UserPermissions", "Tenants",
                "TenantDomains", "TenantSystems")) {
            TABLE_OWNER.put(table, "platform");
        }
        for (String table : List.of("PricingPlans", "PricingPlanVersions", "PricingMetrics", "UsageRatings", "UsageCharges")) {
            TABLE_OWNER.put(table, "pricing");
        }
        for (String table : List.of(
                "Payments", "PaymentAttempts", "Refunds", "StripeConfigurations", "StripeEvents", "StripeEventAttempts", "StripeIncidents",
                "StripeRoutes")) {
            TABLE_OWNER.put(table, "payment");
        }
        for (String table : List.of("Invoices", "InvoiceItems", "InvoicePayments", "InvoiceCounters", "Subscriptions", "SubscriptionEvents")) {
            TABLE_OWNER.put(table, "invoicing");
        }
        for (String table : List.of("Wallets", "LedgerEntries", "Reservations", "Charges")) {
            TABLE_OWNER.put(table, "money");
        }
        for (String table : List.of("Outbox", "EventInbox", "IdempotencyKeys", "AuditLog", "JobLeases", "JobRuns", "RateLimitBuckets")) {
            TABLE_OWNER.put(table, "boot");
        }
    }
    private static final String JAVA = "java..";
    private static final List<String> LAYERS = List.of("domain", "application", "adapter", "api");
    private static final List<String> READ_METHOD_PREFIXES = List.of("list", "page", "search");

    /** Interfaces in application whose name ends in one of these are outbound (write-side) ports. */
    private static final Set<String> OUTBOUND_PORT_SUFFIXES = Set.of(
            "Repository", "Port", "Provider", "Access", "Sender", "Signer", "Cipher", "Writer", "Store", "Keys", "Limiter",
            "Units", "Registry", "Verifier");

    /** The role packages of the application layer; every application class sits in exactly one of them. */
    private static final Set<String> APPLICATION_ROLES = Set.of("command", "query", "port", "service", "exception");

    /**
     * Domain types a read model may name besides enums. Read models are shaped for the caller, so they
     * use primitives where they can; this list is kept short and reviewed whenever it grows.
     */
    private static final Set<String> ALLOWED_QUERY_DOMAIN_TYPES = Set.of();

    private ArchitectureRules() {}

    static List<NamedRule> all(String root) {
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
                new NamedRule("role-package-not-layer-root", rolePackageNotLayerRoot(root)),
                new NamedRule("use-cases-and-queries-in-command-or-query", useCasesAndQueriesInCommandOrQuery(root)),
                new NamedRule("ports-in-port-or-query", portsInPortOrQuery(root)),
                new NamedRule("generated-records-in-adapter-persistence", generatedRecordsInAdapterPersistence(root)),
                new NamedRule("context-uses-only-own-tables", contextUsesOnlyItsOwnTables(root)),
                new NamedRule("write-side-does-not-depend-on-query", writeSideDoesNotDependOnQuery(root)),
                new NamedRule("read-side-does-not-depend-on-write", readSideDoesNotDependOnWrite(root)),
                new NamedRule("query-side-uses-no-domain-aggregates", querySideUsesNoDomainAggregates(root)),
                new NamedRule("write-ports-serve-no-reads", writePortsServeNoReads(root)),
                new NamedRule("application-classes-in-a-role-package", applicationClassesInARolePackage(root)),
                new NamedRule("command-package-holds-only-use-cases", commandPackageHoldsOnlyUseCases(root)),
                new NamedRule("port-package-holds-no-implementations", portPackageHoldsNoImplementations(root)),
                new NamedRule("repository-pair-shares-a-package", repositoryPairSharesAPackage(root)));
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
                .should().dependOnClassesThat().resideInAPackage("dev.ngb.core_billing_jooq..")
                .because("generated jOOQ types live in adapter.persistence");
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
                .because("persistence is jOOQ only");
    }

    private static ArchRule sharedKernelIsIndependent(String root) {
        return classes().that().resideInAPackage(root + ".sharedkernel..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(JAVA, root + ".sharedkernel..")
                .because("the kernel takes no module dependency");
    }

    /**
     * Inside a context no class sits directly in a layer root package: every package is grouped by feature
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
                .because("a context groups packages by feature and then by role");
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

    /** A generated jOOQ record is an adapter detail and lives next to the repository that uses it. */
    private static ArchRule generatedRecordsInAdapterPersistence(String root) {
        return noClasses().that().resideOutsideOfPackages(root + ".*.adapter.persistence..", root + ".boot..")
                .should().dependOnClassesThat().resideInAPackage("dev.ngb.core_billing_jooq.tables.records..")
                .because("a generated record lives in adapter.persistence next to the repository that uses it");
    }

    /**
     * A context's adapters use only the tables it owns. The outbox, inbox, idempotency keys and audit log are
     * shared infrastructure and belong to boot.
     */
    private static ArchRule contextUsesOnlyItsOwnTables(String root) {
        return classes().that().resideInAnyPackage(root + ".*.adapter..", root + ".boot..")
                .should(new ArchCondition<>("use only the tables its own context owns") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String context = owningContextOf(root, item.getPackageName());
                        if (context == null) {
                            return;
                        }
                        for (var access : item.getFieldAccessesFromSelf()) {
                            if (!"dev.ngb.core_billing_jooq.Tables".equals(access.getTarget().getOwner().getName())) {
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
                .because("each context persists only its own tables");
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
     * A read model is never a domain aggregate. Should a read need a value object or an enum, the explicit
     * allowlist records it for review instead of letting the domain leak in silently.
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

    /** A feature's write repository and read repository sit side by side, so one package holds one feature's persistence. */
    private static ArchRule repositoryPairSharesAPackage(String root) {
        return classes().that().resideInAnyPackage(root + ".*.adapter.persistence..")
                .and().haveSimpleNameStartingWith("Jooq").and().haveSimpleNameEndingWith("Repository")
                .should(new ArchCondition<>("sit in the same package as its feature's write repository") {
                    private final Map<String, JavaClass> byContextAndName = new HashMap<>();

                    @Override
                    public void init(Collection<JavaClass> allObjectsToTest) {
                        for (JavaClass candidate : allObjectsToTest) {
                            byContextAndName.put(owningContextOf(root, candidate.getPackageName()) + "/" + candidate.getSimpleName(), candidate);
                        }
                    }

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        String name = item.getSimpleName();
                        if (!name.endsWith("ReadRepository")) {
                            return;
                        }
                        String writeName = name.substring(0, name.length() - "ReadRepository".length()) + "Repository";
                        JavaClass write = byContextAndName.get(owningContextOf(root, item.getPackageName()) + "/" + writeName);
                        if (write != null && !write.getPackageName().equals(item.getPackageName())) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getFullName() + " is not in the same package as " + write.getFullName()));
                        }
                    }
                })
                .because("the write and read repository of one feature share a package")
                .allowEmptyShould(true);
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

    private static boolean isDomainPackage(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return false;
        }
        return hasSegment(packageName.substring(root.length() + 1), "domain");
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
