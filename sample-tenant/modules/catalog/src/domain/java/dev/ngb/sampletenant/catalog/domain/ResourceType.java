package dev.ngb.sampletenant.catalog.domain;

/** The seven resources the reference tenant rents, each with the metric it reports to Billing. */
public enum ResourceType {
    COMPUTE("Compute Instance", PricingModel.TIME_BASED, "compute.vcpu_minute", "vCPU-minute"),
    STORAGE("Storage", PricingModel.USAGE_BASED, "storage.gb_month", "GB-month"),
    BANDWIDTH("Bandwidth", PricingModel.USAGE_BASED, "bandwidth.gb", "GB"),
    BACKGROUND_WORKLOAD("Background Workload", PricingModel.WORKLOAD_BASED, "workload.minute", "minute"),
    API_OPERATION("API Operation", PricingModel.EVENT_BASED, "api.operation", "operation"),
    MANAGED_SERVICE("Managed Service", PricingModel.SUBSCRIPTION, "service.month", "month"),
    CLOUD_PACKAGE("Cloud Package", PricingModel.HYBRID, "package.month", "month");

    private final String label;
    private final PricingModel pricingModel;
    private final String metric;
    private final String unit;

    ResourceType(String label, PricingModel pricingModel, String metric, String unit) {
        this.label = label;
        this.pricingModel = pricingModel;
        this.metric = metric;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public PricingModel pricingModel() {
        return pricingModel;
    }

    public String metric() {
        return metric;
    }

    public String unit() {
        return unit;
    }
}
