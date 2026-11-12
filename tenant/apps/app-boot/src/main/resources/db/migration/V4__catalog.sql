CREATE TABLE catalog_plans (
    id             uuid         PRIMARY KEY,
    code           varchar(80)  NOT NULL,
    name           varchar(200) NOT NULL,
    resource_type  varchar(40)  NOT NULL,
    pricing_model  varchar(40)  NOT NULL,
    description    varchar(500),
    billing_plan_id varchar(64),
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX catalog_plans_code_key ON catalog_plans (code);

INSERT INTO catalog_plans (id, code, name, resource_type, pricing_model, description) VALUES
    (gen_random_uuid(), 'compute-standard', 'Compute Instance', 'COMPUTE', 'TIME_BASED', 'Virtual machines billed per started vCPU-minute.'),
    (gen_random_uuid(), 'storage-standard', 'Storage', 'STORAGE', 'USAGE_BASED', 'Block and object storage billed per GB-month.'),
    (gen_random_uuid(), 'bandwidth-standard', 'Bandwidth', 'BANDWIDTH', 'USAGE_BASED', 'Egress billed per GB transferred.'),
    (gen_random_uuid(), 'workload-standard', 'Background Workload', 'BACKGROUND_WORKLOAD', 'WORKLOAD_BASED', 'Batch executions billed per start plus work consumed.'),
    (gen_random_uuid(), 'api-operation-standard', 'API Operation', 'API_OPERATION', 'EVENT_BASED', 'API calls billed per operation.'),
    (gen_random_uuid(), 'managed-service-standard', 'Managed Service', 'MANAGED_SERVICE', 'SUBSCRIPTION', 'A managed service billed once per cycle.'),
    (gen_random_uuid(), 'cloud-package-standard', 'Cloud Package', 'CLOUD_PACKAGE', 'HYBRID', 'A package base billed per cycle plus usage.');
