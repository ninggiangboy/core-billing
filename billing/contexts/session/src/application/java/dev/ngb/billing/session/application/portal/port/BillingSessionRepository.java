package dev.ngb.billing.session.application.portal.port;

import dev.ngb.billing.session.domain.portal.BillingSession;
import dev.ngb.billing.session.domain.portal.BillingSessionId;
import java.util.Optional;

/** Persists Billing Sessions of the current tenant. Rows are never deleted, so revocation and expiry stay auditable. */
public interface BillingSessionRepository {

    void add(BillingSession session);

    void update(BillingSession session);

    Optional<BillingSession> findById(BillingSessionId id);

    Optional<BillingSession> findByExchangeTokenHash(String tokenHash);

    Optional<BillingSession> findByCookieTokenHash(String tokenHash);
}
