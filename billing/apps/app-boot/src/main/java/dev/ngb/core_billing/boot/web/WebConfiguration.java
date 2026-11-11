package dev.ngb.core_billing.boot.web;

import dev.ngb.core_billing.platform.adapter.auth.web.CallerResolver;
import dev.ngb.core_billing.platform.api.actor.Authenticated;
import dev.ngb.core_billing.platform.api.actor.Authorizer;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.actor.PublicEndpoint;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.idempotency.RepeatableCreate;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebConfiguration implements WebMvcConfigurer {

    private final Authorizer authorizer;

    WebConfiguration(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor(authorizer));
    }

    @Bean
    CallerResolver callerResolver(CurrentActor currentActor) {
        return new CallerResolver(currentActor);
    }

    @Bean
    OpenAPI billingOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Core Billing API").version("v1"))
                .servers(List.of(new Server().url("/").description("Any Billing hostname: platform.<base-domain> or <tenant-code>.<base-domain>")))
                .components(new Components()
                        .addSecuritySchemes("oidc", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Billing User signed in through Keycloak"))
                        .addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("cbk_<key-id>_<secret>")
                                .description("Tenant System credential issued by Billing")));
    }

    /** Documents the permission each operation requires, straight from the annotation that enforces it. */
    @Bean
    OperationCustomizer requiredPermission() {
        return (operation, handlerMethod) -> {
            RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
            if (required != null) {
                operation.addExtension("x-required-permission", required.value().code());
            }
            return operation;
        };
    }

    /**
     * Documents both outcomes of a repeatable create. A handler that returns {@code ResponseEntity} is generated with a
     * single 200, so the body schema found there is listed under 201 for a new resource and under 200 for a repeat.
     */
    @Bean
    OperationCustomizer repeatableCreates() {
        return (operation, handlerMethod) -> {
            RepeatableCreate create = handlerMethod.getMethodAnnotation(RepeatableCreate.class);
            if (create == null || operation.getResponses() == null) {
                return operation;
            }
            ApiResponse generated = operation.getResponses().get("200");
            Content body = generated == null ? null : generated.getContent();
            ApiResponses responses = new ApiResponses()
                    .addApiResponse("201", new ApiResponse().description("Created").content(body))
                    .addApiResponse("200", new ApiResponse().description(create.repeated()).content(body));
            operation.getResponses().forEach((code, response) -> {
                if (!responses.containsKey(code)) {
                    responses.addApiResponse(code, response);
                }
            });
            operation.setResponses(responses);
            return operation;
        };
    }

    /** Documents which principal may call each operation, derived from the same Permission the interceptor enforces. */
    @Bean
    OperationCustomizer allowedCallers() {
        return (operation, handlerMethod) -> {
            RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
            if (required != null) {
                List<String> callers = new java.util.ArrayList<>();
                if (required.value().validIn(PermissionScope.PLATFORM)) {
                    callers.add("platform-user");
                }
                if (required.value().validIn(PermissionScope.TENANT)) {
                    callers.add("tenant-user");
                }
                if (required.value().grantableToTenantSystem()) {
                    callers.add("tenant-system");
                }
                operation.addExtension("x-allowed-callers", callers);
            } else if (handlerMethod.hasMethodAnnotation(Authenticated.class)) {
                operation.addExtension("x-allowed-callers", List.of("platform-user", "tenant-user", "tenant-system"));
            } else if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class)) {
                operation.addExtension("x-allowed-callers", List.of("public"));
            }
            return operation;
        };
    }
}
