package io.corebilling.boot.security;

import io.corebilling.platform.api.actor.Actor;
import io.corebilling.platform.api.error.AccessDeniedException;
import io.corebilling.platform.api.actor.Authorizer;
import io.corebilling.platform.api.actor.CurrentActor;
import io.corebilling.platform.application.auth.query.Authentication;
import io.corebilling.platform.domain.role.AuthorizationPolicy;
import io.corebilling.platform.domain.credential.ApiKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Authentication authentication) throws Exception {
        BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        // API keys are bearer tokens too; the OIDC filter must leave them to the API-key filter.
        BearerTokenResolver oidcOnly = request -> {
            String token = defaultResolver.resolve(request);
            return token != null && token.startsWith(ApiKey.PREFIX) ? null : token;
        };
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Stripe calls these; the signature of the body is the proof, verified before anything is read.
                        .requestMatchers("/stripe/webhooks/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(oidcOnly)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtActorConverter(authentication))))
                .addFilterBefore(new ApiKeyAuthenticationFilter(authentication), BearerTokenAuthenticationFilter.class)
                .headers(Customizer.withDefaults());
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
}
