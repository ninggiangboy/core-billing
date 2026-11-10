package io.corebilling.boot.security;

import io.corebilling.platform.api.actor.Actor;
import io.corebilling.platform.application.auth.Authentication;
import io.corebilling.platform.application.tenant.command.TenantMismatchException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

/**
 * Links a validated token to a Billing user. Signature, issuer, audience and expiry were already checked by
 * the decoder; this adds the two Billing rules: the token's tenant must be the tenant the hostname
 * resolved to (403 otherwise), and its subject must be an active user of that scope (401 otherwise).
 */
class JwtActorConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String TENANT_CLAIM = "tenant_code";

    private final Authentication authentication;

    JwtActorConverter(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Actor actor = authentication.authenticateUser(jwt.getSubject(), jwt.getClaimAsString(TENANT_CLAIM))
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new BearerTokenError(BearerTokenErrorCodes.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "the user is not active here", null)));
            return new ActorAuthentication(actor);
        } catch (TenantMismatchException mismatch) {
            throw new OAuth2AuthenticationException(
                    new BearerTokenError(BearerTokenErrorCodes.INSUFFICIENT_SCOPE, HttpStatus.FORBIDDEN, "the token belongs to another tenant", null));
        }
    }
}
