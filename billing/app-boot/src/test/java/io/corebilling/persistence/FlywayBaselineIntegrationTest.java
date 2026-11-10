package io.corebilling.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.Application;
import io.corebilling.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class FlywayBaselineIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesBaselineMigrationToRealPostgres() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '0'", Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}
