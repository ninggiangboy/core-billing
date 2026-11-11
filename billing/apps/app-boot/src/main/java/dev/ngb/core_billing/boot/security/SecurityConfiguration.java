package dev.ngb.core_billing.boot.security;

import dev.ngb.core_billing.boot.portal.PortalCookieSettings;
import dev.ngb.core_billing.boot.web.PortalCorsConfigurationSource;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.error.AccessDeniedException;
import dev.ngb.core_billing.platform.api.actor.Authorizer;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.application.auth.service.Authentication;
import dev.ngb.core_billing.platform.domain.role.AuthorizationPolicy;
import dev.ngb.core_billing.platform.domain.credential.ApiKey;
import dev.ngb.core_billing.session.api.portal.CurrentPortalVisitor;
import dev.ngb.core_billing.session.api.portal.PortalAuthorizer;
import dev.ngb.core_billing.session.api.portal.PortalSessions;
import dev.ngb.core_billing.session.api.portal.PortalVisitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One filter chain, three possible principals: an OIDC bearer token (a Billing user), a Billing-issued API
 * key (a Tenant System) and, later, a Billing Session. The tenant scope was fixed by the hostname before this
 * chain runs; each principal is then validated against that scope.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            Authentication authentication,
            PortalSessions portalSessions,
            PortalCookieSettings portalCookieSettings,
            PortalCorsConfigurationSource portalCors,
            @Value("${billing.portal.enabled:true}") boolean portalEnabled) throws Exception {
        BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        // API keys are bearer tokens too; the OIDC filter must leave them to the API-key filter.
        BearerTokenResolver oidcOnly = request -> {
            String token = defaultResolver.resolve(request);
            return token != null && token.startsWith(ApiKey.PREFIX) ? null : token;
        };
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(portalCors))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Stripe calls these; the signature of the body is the proof, verified before anything is read.
                        .requestMatchers("/stripe/webhooks/**")
                        .permitAll()
                        // The portal exchanges its single-use token for a cookie here; there is no cookie yet.
                        .requestMatchers("/portal/api/session/exchange")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(oidcOnly)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtActorConverter(authentication))))
                .addFilterBefore(new ApiKeyAuthenticationFilter(authentication), BearerTokenAuthenticationFilter.class)
                .headers(Customizer.withDefaults());
        if (portalEnabled) {
            http.addFilterBefore(new PortalSessionAuthenticationFilter(portalSessions, portalCookieSettings), BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    CurrentActor currentActor() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof ActorAuthentication actorAuthentication) {
                return actorAuthentication.actor();
            }
            throw new IllegalStateException("the request is not authenticated");
        };
    }

    @Bean
    Authorizer authorizer(CurrentActor currentActor) {
        return permission -> {
            Actor actor = currentActor.get();
            if (!AuthorizationPolicy.permits(actor.permissions(), permission, actor.scope())) {
                throw new AccessDeniedException("missing permission " + permission.code());
            }
        };
    }

    @Bean
    PortalCookieSettings portalCookieSettings(@Value("${billing.portal.secure-cookie:true}") boolean secure) {
        return new PortalCookieSettings(secure);
    }

    @Bean
    PortalCorsConfigurationSource portalCorsConfigurationSource() {
        return new PortalCorsConfigurationSource();
    }

    @Bean
    CurrentPortalVisitor currentPortalVisitor() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof PortalSessionAuthentication portal) {
                return portal.visitor();
            }
            throw new IllegalStateException("the request has no portal session");
        };
    }

    @Bean
    PortalAuthorizer portalAuthorizer(CurrentPortalVisitor currentPortalVisitor) {
        return permission -> {
            PortalVisitor visitor = currentPortalVisitor.get();
            if (!visitor.permissions().contains(permission)) {
                throw new AccessDeniedException("missing portal permission " + permission.code());
            }
        };
    }
}
