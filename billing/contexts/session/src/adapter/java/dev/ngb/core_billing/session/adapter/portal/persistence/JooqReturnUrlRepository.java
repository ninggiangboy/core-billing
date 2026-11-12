package dev.ngb.core_billing.session.adapter.portal.persistence;

import static dev.ngb.core_billing_jooq.Tables.PORTAL_RETURN_URLS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.session.application.portal.port.ReturnUrlRepository;
import java.util.Set;
import org.jooq.DSLContext;

/** Reads and replaces the current tenant's return-URL allowlist. Row-level security scopes every statement. */
public class JooqReturnUrlRepository implements ReturnUrlRepository {

    private final DSLContext dsl;

    public JooqReturnUrlRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<String> allowed() {
        return Set.copyOf(dsl.select(PORTAL_RETURN_URLS.URL)
                .from(PORTAL_RETURN_URLS)
                .fetch(PORTAL_RETURN_URLS.URL));
    }

    @Override
    public void replace(TenantId tenantId, Set<String> urls) {
        dsl.deleteFrom(PORTAL_RETURN_URLS).execute();
        if (urls.isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(PORTAL_RETURN_URLS, PORTAL_RETURN_URLS.TENANT_ID, PORTAL_RETURN_URLS.URL);
        for (String url : urls) {
            insert = insert.values(tenantId.value(), url);
        }
        insert.execute();
    }
}
