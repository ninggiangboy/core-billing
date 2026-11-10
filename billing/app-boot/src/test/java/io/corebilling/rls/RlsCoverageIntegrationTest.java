package io.corebilling.rls;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.Application;
import io.corebilling.support.OwnerDatabase;
import io.corebilling.support.PostgresContainerConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Guards the schema as it grows: a later migration that adds a tenant-scoped table and forgets
 * enable_tenant_rls fails here, and the application role can never be given ownership or bypass.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class RlsCoverageIntegrationTest {

    /** Platform-owned tables that carry a nullable tenant_id but hold no tenant-scoped data. */
    private static final Set<String> PLATFORM_OWNED = Set.of("tenant_domains");

    @Autowired OwnerDatabase owner;

    @Test
    void everyTenantScopedTableForcesRowLevelSecurity() {
        List<String> unprotected = owner.jdbc().queryForList("""
                select c.relname
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r'
                  and exists (select 1 from pg_attribute a
                              where a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped)
                  and not (c.relrowsecurity and c.relforcerowsecurity)
                """, String.class);
        assertThat(unprotected).allMatch(PLATFORM_OWNED::contains);
        assertThat(unprotected).containsExactlyInAnyOrderElementsOf(PLATFORM_OWNED);
    }

    @Test
    void applicationRoleOwnsNothingAndCannotBypassRowLevelSecurity() {
        assertThat(owner.count("select count(*) from pg_roles where rolname = 'corebilling_app'"
                        + " and not rolsuper and not rolbypassrls and not rolcreaterole and not rolcreatedb"))
                .isEqualTo(1);
        assertThat(owner.count("select count(*) from pg_tables where schemaname = 'public' and tableowner = 'corebilling_app'"))
                .isZero();
    }

    @Test
    void auditLogIsAppendOnlyForTheApplicationRole() {
        assertThat(owner.count("select count(*) from information_schema.role_table_grants"
                        + " where grantee = 'corebilling_app' and table_name = 'audit_log'"
                        + " and privilege_type in ('UPDATE', 'DELETE')"))
                .isZero();
    }
}
