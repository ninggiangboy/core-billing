package dev.ngb.billing.session.adapter.portal.persistence;

import static dev.ngb.billing_jooq.Tables.BILLING_SESSIONS;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import dev.ngb.billing.session.api.portal.PortalPermission;
import dev.ngb.billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.billing.session.domain.portal.BillingSession;
import dev.ngb.billing.session.domain.portal.BillingSessionId;
import dev.ngb.billing.session.domain.portal.SessionStatus;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing_jooq.tables.records.BillingSessionsRecord;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;

/** Persists Billing Sessions. Only token digests and permission codes are stored. */
public class JooqBillingSessionRepository implements BillingSessionRepository {

    private final DSLContext dsl;

    public JooqBillingSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(BillingSession session) {
        dsl.insertInto(BILLING_SESSIONS)
                .set(BILLING_SESSIONS.ID, session.id().value())
                .set(BILLING_SESSIONS.TENANT_ID, session.tenantId().value())
                .set(BILLING_SESSIONS.CUSTOMER_ID, session.customerId().value())
                .set(BILLING_SESSIONS.TENANT_SYSTEM_ID, session.tenantSystemId().value())
                .set(BILLING_SESSIONS.PERMISSIONS, codes(session))
                .set(BILLING_SESSIONS.RETURN_URL, session.returnUrl())
                .set(BILLING_SESSIONS.STATUS, session.status().name())
                .set(BILLING_SESSIONS.EXCHANGE_TOKEN_HASH, session.exchangeTokenHash())
                .set(BILLING_SESSIONS.COOKIE_TOKEN_HASH, session.cookieTokenHash())
                .set(BILLING_SESSIONS.CREATED_AT, session.createdAt())
                .set(BILLING_SESSIONS.EXPIRES_AT, session.expiresAt())
                .set(BILLING_SESSIONS.EXCHANGED_AT, session.exchangedAt())
                .set(BILLING_SESSIONS.REVOKED_AT, session.revokedAt())
                .execute();
    }

    @Override
    public void update(BillingSession session) {
        dsl.update(BILLING_SESSIONS)
                .set(BILLING_SESSIONS.STATUS, session.status().name())
                .set(BILLING_SESSIONS.COOKIE_TOKEN_HASH, session.cookieTokenHash())
                .set(BILLING_SESSIONS.EXCHANGED_AT, session.exchangedAt())
                .set(BILLING_SESSIONS.REVOKED_AT, session.revokedAt())
                .where(BILLING_SESSIONS.ID.eq(session.id().value()))
                .execute();
    }

    @Override
    public Optional<BillingSession> findById(BillingSessionId id) {
        return dsl.selectFrom(BILLING_SESSIONS)
                .where(BILLING_SESSIONS.ID.eq(id.value()))
                .fetchOptional(JooqBillingSessionRepository::toDomain);
    }

    @Override
    public Optional<BillingSession> findByExchangeTokenHash(String tokenHash) {
        return dsl.selectFrom(BILLING_SESSIONS)
                .where(BILLING_SESSIONS.EXCHANGE_TOKEN_HASH.eq(tokenHash))
                .fetchOptional(JooqBillingSessionRepository::toDomain);
    }

    @Override
    public Optional<BillingSession> findByCookieTokenHash(String tokenHash) {
        return dsl.selectFrom(BILLING_SESSIONS)
                .where(BILLING_SESSIONS.COOKIE_TOKEN_HASH.eq(tokenHash))
                .fetchOptional(JooqBillingSessionRepository::toDomain);
    }

    private static String[] codes(BillingSession session) {
        return session.permissions().stream().map(PortalPermission::code).sorted().toArray(String[]::new);
    }

    private static BillingSession toDomain(BillingSessionsRecord row) {
        Set<PortalPermission> permissions = Arrays.stream(row.getPermissions())
                .map(PortalPermission::fromCode)
                .collect(Collectors.toSet());
        return BillingSession.restore(
                new BillingSessionId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                new TenantSystemId(row.getTenantSystemId()),
                permissions,
                row.getReturnUrl(),
                row.getExchangeTokenHash(),
                row.getCreatedAt(),
                row.getExpiresAt(),
                SessionStatus.valueOf(row.getStatus()),
                row.getCookieTokenHash(),
                row.getExchangedAt(),
                row.getRevokedAt());
    }
}
