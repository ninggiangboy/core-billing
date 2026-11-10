package dev.ngb.core_billing.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The committed contracts must equal what the code generates. After an intended API change run
 * {@code ./gradlew :app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true} and commit both files.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class OpenApiContractTest {

    private static final String CONTRACT = "contracts/openapi/billing.yaml";
    private static final String TENANT_CONTRACT = "contracts/openapi/billing-tenant-system.yaml";

    @Autowired MockMvc mvc;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final YAMLMapper yaml = YAMLMapper.builder().build();

    @Test
    void committedOpenApiDocumentMatchesTheGeneratedOne() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs.yaml").header("Host", "platform." + baseDomain))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(generated).startsWith("openapi:");
        Path root = repositoryRoot();
        Path file = root.resolve(CONTRACT);
        Path tenantFile = root.resolve(TENANT_CONTRACT);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            Files.writeString(tenantFile, tenantSystemSpec(generated), StandardCharsets.UTF_8);
        }

        assertThat(file).as(CONTRACT + " is missing; regenerate it with -Dopenapi.update=true").exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .as(CONTRACT + " is out of date; regenerate it with -Dopenapi.update=true and commit the result")
                .isEqualTo(generated);
        assertThat(tenantFile).as(TENANT_CONTRACT + " is missing; regenerate it with -Dopenapi.update=true").exists();
        assertThat(Files.readString(tenantFile, StandardCharsets.UTF_8))
                .as(TENANT_CONTRACT + " is out of date; regenerate it with -Dopenapi.update=true and commit the result")
                .isEqualTo(tenantSystemSpec(generated));
    }

    /**
     * The Tenant System's view of the API: the operations whose permission is grantable to a machine, with the
     * apiKey scheme, and nothing else. Both files come from the same generated document, so they cannot drift.
     */
    private String tenantSystemSpec(String generated) {
        ObjectNode tenant = (ObjectNode) yaml.readTree(generated);
        ObjectNode paths = (ObjectNode) tenant.get("paths");
        for (String path : new ArrayList<>(paths.propertyNames())) {
            ObjectNode operations = (ObjectNode) paths.get(path);
            boolean kept = false;
            for (String method : new ArrayList<>(operations.propertyNames())) {
                JsonNode callers = operations.get(method).get("x-allowed-callers");
                if (callers == null || !callers.isArray() || callers.valueStream().noneMatch(node -> "tenant-system".equals(node.asString()))) {
                    operations.remove(method);
                    continue;
                }
                ObjectNode operation = (ObjectNode) operations.get(method);
                ObjectNode apiKey = JsonNodeFactory.instance.objectNode();
                apiKey.putArray("apiKey");
                ArrayNode security = JsonNodeFactory.instance.arrayNode();
                security.add(apiKey);
                operation.set("security", security);
                kept = true;
            }
            if (!kept) {
                paths.remove(path);
            }
        }

        ObjectNode info = (ObjectNode) tenant.get("info");
        info.put("title", "Core Billing API - Tenant Systems");
        info.put("description", String.join("\n",
                "The API a Tenant System calls with the credential Billing issued to it.",
                "Base URL: https://{tenant-code}.{base-domain} - the hostname selects the Tenant.",
                "Authentication: `Authorization: Bearer cbk_<key-id>_<secret>`. The credential's scopes are the "
                        + "`x-required-permission` codes below; an operation outside those scopes answers 403.",
                "Send `Idempotency-Key` where the operation documents it.",
                "Every resource is limited to the Customers mapped to this Tenant System. A resource owned by another "
                        + "Tenant System in the same Tenant answers 404 exactly like a resource that does not exist.",
                "Operations not listed here are not available to a Tenant System."));

        ObjectNode server = JsonNodeFactory.instance.objectNode();
        server.put("url", "https://{tenant-code}.{base-domain}");
        ObjectNode variables = server.putObject("variables");
        variables.putObject("tenant-code").put("default", "tenant-a");
        variables.putObject("base-domain").put("default", "base-domain.com");
        ArrayNode servers = JsonNodeFactory.instance.arrayNode();
        servers.add(server);
        tenant.set("servers", servers);

        JsonNode schemes = tenant.at("/components/securitySchemes");
        if (schemes.isObject()) {
            ((ObjectNode) schemes).remove("oidc");
        }
        return yaml.writeValueAsString(tenant);
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("contracts")) && Files.isDirectory(dir.resolve("billing"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
