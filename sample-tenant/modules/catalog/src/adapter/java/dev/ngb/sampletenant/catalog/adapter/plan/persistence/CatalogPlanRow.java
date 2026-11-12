package dev.ngb.sampletenant.catalog.adapter.plan.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("catalog_plans")
record CatalogPlanRow(
        @Id UUID id,
        String code,
        String name,
        String resourceType,
        String pricingModel,
        String description,
        String billingPlanId,
        boolean active,
        Instant createdAt) {}
