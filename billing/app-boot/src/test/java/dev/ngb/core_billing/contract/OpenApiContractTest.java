package dev.ngb.core_billing.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The committed contract must equal what the code generates. After an intended API change run
 * {@code ./gradlew :app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true} and commit the file.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class OpenApiContractTest {

    private static final String CONTRACT = "contracts/openapi/billing.yaml";

    @Autowired MockMvc mvc;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    @Test
    void committedOpenApiDocumentMatchesTheGeneratedOne() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs.yaml").header("Host", "platform." + baseDomain))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(generated).startsWith("openapi:");
        Path file = repositoryRoot().resolve(CONTRACT);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, generated, StandardCharsets.UTF_8);
        }

        assertThat(file).as(CONTRACT + " is missing; regenerate it with -Dopenapi.update=true").exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .as(CONTRACT + " is out of date; regenerate it with -Dopenapi.update=true and commit the result")
                .isEqualTo(generated);
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
