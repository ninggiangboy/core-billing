package dev.ngb.core_billing.pricing.application.metric.service;

import dev.ngb.core_billing.pricing.api.metric.MetricCatalog;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.Optional;
import dev.ngb.core_billing.pricing.application.metric.query.MetricReadRepository;

public class MetricCatalogService implements MetricCatalog {

    private final UnitOfWork unitOfWork;
    private final MetricReadRepository metrics;

    public MetricCatalogService(UnitOfWork unitOfWork, MetricReadRepository metrics) {
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
    }

    @Override
    public Optional<String> unitOf(String metric) {
        return unitOfWork.transactional(() -> metrics.unitOf(metric));
    }
}
