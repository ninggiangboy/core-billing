package io.corebilling.pricing.application;

import io.corebilling.pricing.api.MetricCatalog;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Optional;

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
