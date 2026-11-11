package dev.ngb.core_billing.platform.adapter.web;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.application.tenant.command.AddTenantDomainUseCase;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.application.tenant.command.DeactivateTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.ProvisionTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.query.TenantDomainView;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import dev.ngb.core_billing.platform.application.tenant.query.TenantView;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant lifecycle and hostnames. Platform scope only: the permissions involved exist nowhere else. */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    public record CreateTenantRequest(String code, String name, String adminEmail, String adminDisplayName, String adminTemporaryPassword) {
        @Override
        public String toString() {
            return "CreateTenantRequest[code=" + code + "]";
        }
    }

    public record AddDomainRequest(String hostname) {}

    public record TenantResponse(String id, String code, String name, String status, Instant createdAt) {
        static TenantResponse of(Tenant tenant) {
            return new TenantResponse(tenant.id().toString(), tenant.code().value(), tenant.name(), tenant.status().name(), tenant.createdAt());
        }

        static TenantResponse ofView(TenantView tenant) {
            return new TenantResponse(tenant.id().toString(), tenant.code(), tenant.name(), tenant.status().name(), tenant.createdAt());
        }
    }

    public record DomainResponse(String id, String hostname, String kind, Instant createdAt) {
        static DomainResponse of(TenantDomain domain) {
            return new DomainResponse(domain.id().toString(), domain.hostname().value(), domain.kind().name(), domain.createdAt());
        }

        static DomainResponse ofView(TenantDomainView domain) {
            return new DomainResponse(domain.id().toString(), domain.hostname(), domain.kind().name(), domain.createdAt());
        }
    }

    private final ProvisionTenantUseCase provision;
    private final DeactivateTenantUseCase deactivate;
    private final AddTenantDomainUseCase addDomain;
    private final TenantQueries queries;
    private final CallerResolver callers;

    public TenantController(
            ProvisionTenantUseCase provision,
            DeactivateTenantUseCase deactivate,
            AddTenantDomainUseCase addDomain,
            TenantQueries queries,
            CallerResolver callers) {
        this.provision = provision;
        this.deactivate = deactivate;
        this.addDomain = addDomain;
        this.queries = queries;
        this.callers = callers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TENANT_CREATE)
    public TenantResponse create(@RequestBody CreateTenantRequest request) {
        Audit caller = callers.current();
        return TenantResponse.of(provision.execute(new ProvisionTenantUseCase.Command(
                request.code(), request.name(), request.adminEmail(), request.adminDisplayName(),
                request.adminTemporaryPassword(), caller.actor(), caller.requestId())));
    }

    @GetMapping
    @RequiresPermission(Permission.TENANT_READ)
    public List<TenantResponse> list() {
        return queries.list().stream().map(TenantResponse::ofView).toList();
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.TENANT_READ)
    public TenantResponse get(@PathVariable String id) {
        return TenantResponse.ofView(queries.get(TenantId.of(id).value()));
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(Permission.TENANT_DEACTIVATE)
    public TenantResponse deactivate(@PathVariable String id) {
        Audit caller = callers.current();
        queries.get(TenantId.of(id).value());
        return TenantResponse.of(deactivate.execute(new DeactivateTenantUseCase.Command(TenantId.of(id), caller.actor(), caller.requestId())));
    }

    @GetMapping("/{id}/domains")
    @RequiresPermission(Permission.TENANT_READ)
    public List<DomainResponse> domains(@PathVariable String id) {
        return queries.domains(TenantId.of(id).value()).stream().map(DomainResponse::ofView).toList();
    }

    @PostMapping("/{id}/domains")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TENANT_DOMAIN_MANAGE)
    public DomainResponse addDomain(@PathVariable String id, @RequestBody AddDomainRequest request) {
        Audit caller = callers.current();
        queries.get(TenantId.of(id).value());
        return DomainResponse.of(addDomain.execute(
                new AddTenantDomainUseCase.Command(TenantId.of(id), request.hostname(), caller.actor(), caller.requestId())));
    }
}
