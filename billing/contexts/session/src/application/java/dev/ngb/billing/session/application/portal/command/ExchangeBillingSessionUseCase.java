package dev.ngb.billing.session.application.portal.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.session.application.portal.exception.InvalidPortalSessionException;
import dev.ngb.billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.billing.session.application.portal.service.SessionTokens;
import dev.ngb.billing.session.domain.portal.BillingSession;
import java.time.Instant;

/**
 * Consumes the single-use token a Tenant System handed to the browser and binds the session to a fresh cookie
 * token. The exchange token cannot be used again.
 */
public class ExchangeBillingSessionUseCase {

    public record Result(BillingSession session, String cookieToken, String csrfToken) {}

    private final UnitOfWork unitOfWork;
    private final BillingSessionRepository sessions;
    private final SessionTokens tokens;
    private final AuditLog auditLog;
    private final Clock clock;

    public ExchangeBillingSessionUseCase(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, SessionTokens tokens, AuditLog auditLog, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.sessions = sessions;
        this.tokens = tokens;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Result execute(String exchangeToken, String requestId) {
        if (exchangeToken == null || exchangeToken.isBlank()) {
            throw new InvalidPortalSessionException();
        }
        return unitOfWork.transactional(() -> {
            BillingSession session = sessions.findByExchangeTokenHash(tokens.hash(exchangeToken))
                    .orElseThrow(InvalidPortalSessionException::new);
            Instant now = clock.now();
            if (!session.isUsable(now) || session.isExchanged()) {
                throw new InvalidPortalSessionException();
            }
            String cookieToken = tokens.newToken();
            String csrfToken = tokens.newToken();
            session.exchange(tokens.hash(cookieToken), now);
            sessions.update(session);
            auditLog.record(AuditEntry.tenant(session.tenantId(), "portal-session/" + session.id(), "session.consumed",
                    "billing-session/" + session.id(), requestId, now));
            return new Result(session, cookieToken, csrfToken);
        });
    }
}
