package dev.ngb.core_billing.pricing.application.metric.query;

import dev.ngb.core_billing.pricing.api.metric.MetricCatalog;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.Optional;

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
