package dev.ngb.sampletenant.catalog.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.catalog.domain.plan.PricingModel;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import org.junit.jupiter.api.Test;

class ResourceTypeTest {

    @Test
    void definesTheSevenReferenceResourcesWithTheirPricingModels() {
        assertThat(ResourceType.values()).hasSize(7);
        assertThat(ResourceType.COMPUTE.pricingModel()).isEqualTo(PricingModel.TIME_BASED);
        assertThat(ResourceType.STORAGE.pricingModel()).isEqualTo(PricingModel.USAGE_BASED);
        assertThat(ResourceType.BANDWIDTH.pricingModel()).isEqualTo(PricingModel.USAGE_BASED);
        assertThat(ResourceType.BACKGROUND_WORKLOAD.pricingModel()).isEqualTo(PricingModel.WORKLOAD_BASED);
        assertThat(ResourceType.API_OPERATION.pricingModel()).isEqualTo(PricingModel.EVENT_BASED);
        assertThat(ResourceType.MANAGED_SERVICE.pricingModel()).isEqualTo(PricingModel.SUBSCRIPTION);
        assertThat(ResourceType.CLOUD_PACKAGE.pricingModel()).isEqualTo(PricingModel.HYBRID);
    }

    @Test
    void everyTypeNamesTheMetricItReports() {
        for (ResourceType type : ResourceType.values()) {
            assertThat(type.metric()).isNotBlank();
            assertThat(type.unit()).isNotBlank();
        }
    }
}
