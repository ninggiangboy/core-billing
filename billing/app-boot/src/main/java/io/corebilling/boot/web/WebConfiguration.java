package io.corebilling.boot.web;

import io.corebilling.platform.adapter.web.CallerResolver;
import io.corebilling.platform.api.Authorizer;
import io.corebilling.platform.api.CurrentActor;
import io.corebilling.platform.api.RequiresPermission;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
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
}
