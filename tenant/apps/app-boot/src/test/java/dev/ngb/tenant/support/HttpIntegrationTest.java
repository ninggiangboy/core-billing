package dev.ngb.tenant.support;

import dev.ngb.tenant.identity.application.user.command.RegisterUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Base for the HTTP integration tests: a real context, a migrated PostgreSQL and a fake Billing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, FakeBillingConfig.class})
public abstract class HttpIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RegisterUserUseCase registerUser;

    @Autowired
    @Qualifier("fakeBilling")
    protected FakeBilling billing;

    protected HttpWorld world;

    @BeforeEach
    void createWorld() {
        world = new HttpWorld(mvc, registerUser);
    }
}
