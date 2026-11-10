package io.corebilling.pricing.application.metric.query;

import io.corebilling.pricing.api.metric.MetricCatalog;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Optional;
import io.corebilling.pricing.application.metric.port.MetricRegistry;

public class MetricCatalogService implements MetricCatalog {

    private final UnitOfWork unitOfWork;
    private final MetricRegistry metrics;

    public MetricCatalogService(UnitOfWork unitOfWork, MetricRegistry metrics) {
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
    }

    @Override
    public Optional<String> unitOf(String metric) {
        return unitOfWork.transactional(() -> metrics.unitOf(metric));
    }
}
