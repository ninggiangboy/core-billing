package dev.ngb.core_billing.session.application.portal.service;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.session.api.portal.PortalSessions;
import dev.ngb.core_billing.session.api.portal.PortalVisitor;
import dev.ngb.core_billing.session.application.portal.command.ExchangeBillingSessionUseCase;
import dev.ngb.core_billing.session.application.portal.exception.InvalidPortalSessionException;
import dev.ngb.core_billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.core_billing.session.domain.portal.BillingSession;
import java.util.Optional;

/** Serves the portal-session operations at the HTTP edge. */
public class PortalSessionsService implements PortalSessions {

    private final UnitOfWork unitOfWork;
    private final BillingSessionRepository sessions;
    private final ExchangeBillingSessionUseCase exchange;
    private final SessionTokens tokens;
    private final Clock clock;

    public PortalSessionsService(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, ExchangeBillingSessionUseCase exchange, SessionTokens tokens, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.sessions = sessions;
        this.exchange = exchange;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    public Optional<PortalVisitor> resolve(String cookieToken) {
        if (cookieToken == null || cookieToken.isBlank()) {
            return Optional.empty();
        }
        return unitOfWork.transactional(() -> sessions.findByCookieTokenHash(tokens.hash(cookieToken))
                .filter(session -> session.isUsable(clock.now()))
                .map(PortalSessionsService::visitor));
    }

    @Override
    public ExchangedSession exchange(String exchangeToken, String requestId) {
        if (exchangeToken == null || exchangeToken.isBlank()) {
            throw new InvalidPortalSessionException();
        }
        ExchangeBillingSessionUseCase.Result result = exchange.execute(exchangeToken, requestId);
        return new ExchangedSession(visitor(result.session()), result.cookieToken(), result.csrfToken(), result.session().returnUrl());
    }

    private static PortalVisitor visitor(BillingSession session) {
        return new PortalVisitor(session.tenantId(), session.id().value(), session.customerId(), session.permissions(), session.expiresAt());
    }
}
