package dev.ngb.tenant.identity.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void administratorHoldsEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertThat(Role.ADMIN.permits(permission)).isTrue();
        }
    }

    @Test
    void viewerCannotCreateAnOrder() {
        assertThat(Role.VIEWER.permits(Permission.ORDER_READ)).isTrue();
        assertThat(Role.VIEWER.permits(Permission.ORDER_CREATE)).isFalse();
    }

    @Test
    void operatorCanOrderAndTopUpButCannotManageUsers() {
        assertThat(Role.OPERATOR.permits(Permission.ORDER_CREATE)).isTrue();
        assertThat(Role.OPERATOR.permits(Permission.TOPUP_CREATE)).isTrue();
        assertThat(Role.OPERATOR.permits(Permission.USER_MANAGE)).isFalse();
    }
}
