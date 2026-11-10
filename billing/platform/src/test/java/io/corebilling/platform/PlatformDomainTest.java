package io.corebilling.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.platform.domain.tenant.DomainKind;
import io.corebilling.platform.domain.tenant.Hostname;
import io.corebilling.platform.domain.tenant.Tenant;
import io.corebilling.platform.domain.tenant.TenantCode;
import io.corebilling.platform.domain.tenant.TenantDomain;
import io.corebilling.platform.domain.tenant.TenantStatus;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Test
    void tenantCodeAcceptsUrlSafeLowercaseCodes() {
        assertThat(new TenantCode("acme-corp").value()).isEqualTo("acme-corp");
        assertThat(new TenantCode("a1b").value()).isEqualTo("a1b");
    }

    @Test
    void tenantCodeRejectsUnsafeOrReservedCodes() {
        for (String bad : new String[] {"ab", "Acme", "1acme", "acme-", "acme_corp", "acme.corp", "platform", "x".repeat(41)}) {
            assertThatThrownBy(() -> TenantCode.forNewTenant(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void hostnameIsParsedFromAHostHeader() {
        assertThat(Hostname.fromHostHeader(" Acme.Example.COM:8443 ").value()).isEqualTo("acme.example.com");
        assertThat(Hostname.fromHostHeader("acme.example.com.").value()).isEqualTo("acme.example.com");
    }

    @Test
    void hostnameRejectsMalformedValues() {
        for (String bad : new String[] {"", "-a.com", "a..com", "a b.com", "bad_host", "a.com/path"}) {
            assertThatThrownBy(() -> Hostname.fromHostHeader(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void tenantStartsActiveAndDeactivationIsIdempotent() {
        Tenant tenant = Tenant.create(new TenantId(UUID.randomUUID()), new TenantCode("acme"), "  Acme  ", NOW);
        assertThat(tenant.isActive()).isTrue();
        assertThat(tenant.name()).isEqualTo("Acme");

        tenant.deactivate();
        tenant.deactivate();

        assertThat(tenant.status()).isEqualTo(TenantStatus.DEACTIVATED);
        assertThat(tenant.isActive()).isFalse();
    }

    @Test
    void tenantNameMustBePresent() {
        assertThatThrownBy(() -> Tenant.create(new TenantId(UUID.randomUUID()), new TenantCode("acme"), " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void platformDomainsHaveNoTenantAndEveryOtherKindHasOne() {
        Hostname host = new Hostname("platform.example.com");
        TenantDomain platform = TenantDomain.platform(UUID.randomUUID(), host, NOW);
        assertThat(platform.tenantId()).isEmpty();
        assertThat(platform.kind()).isEqualTo(DomainKind.PLATFORM);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        assertThat(TenantDomain.forTenant(UUID.randomUUID(), tenantId, host, DomainKind.TENANT_SUBDOMAIN, NOW).tenantId())
                .contains(tenantId);
        assertThatThrownBy(() -> TenantDomain.restore(UUID.randomUUID(), null, host, DomainKind.TENANT_SUBDOMAIN, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDomain.restore(UUID.randomUUID(), tenantId, host, DomainKind.PLATFORM, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
