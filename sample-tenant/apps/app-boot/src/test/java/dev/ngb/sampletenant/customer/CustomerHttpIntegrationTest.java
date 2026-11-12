package dev.ngb.sampletenant.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CustomerHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void servesTheMappedCustomerForTheCurrentAccount() {
        HttpWorld.Account account = world.registerViaApi();

        var me = world.body(world.call(HttpMethod.GET, account.token(), "/api/customers/me", null));

        assertThat(me.get("id").asString()).isNotBlank();
        assertThat(me.get("userId").asString()).isEqualTo(account.id());
        assertThat(me.get("billingCustomerId").asString()).startsWith("cus_");
        assertThat(me.get("displayName").asString()).isEqualTo("Customer");
        assertThat(me.get("email").asString()).isEqualTo(account.email());
        assertThat(me.get("status").asString()).isEqualTo("MAPPED");
        assertThat(me.get("createdAt").asString()).isNotBlank();
    }

    @Test
    void anAdministratorListsAndReadsCustomers() {
        String admin = world.adminToken();
        HttpWorld.Account account = world.registerViaApi();

        var list = world.body(world.call(HttpMethod.GET, admin, "/api/customers", null));
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsString("email")).contains(account.email());

        var mine = world.body(world.call(HttpMethod.GET, account.token(), "/api/customers/me", null));
        var byId = world.body(world.call(HttpMethod.GET, admin, "/api/customers/" + mine.get("id").asString(), null));
        assertThat(byId.get("id").asString()).isEqualTo(mine.get("id").asString());

        var retried = world.body(world.call(
                HttpMethod.POST, admin, "/api/customers/" + mine.get("id").asString() + "/mapping", null));
        assertThat(retried.get("status").asString()).isEqualTo("MAPPED");
    }

    @Test
    void aViewerCannotListCustomers() {
        HttpWorld.Account account = world.registerViaApi();

        world.requireStatus(world.call(HttpMethod.GET, account.token(), "/api/customers", null), 403);
    }

    @Test
    void anUnknownCustomerIsNotFound() {
        String admin = world.adminToken();

        world.requireStatus(world.call(HttpMethod.GET, admin, "/api/customers/" + UUID.randomUUID(), null), 404);
    }
}
