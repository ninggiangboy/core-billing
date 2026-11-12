package archfixtures.layout.application;

import dev.ngb.tenant_jooq.tables.records.CustomersRecord;

/** Violates generated-records-in-adapter-persistence: a generated record outside adapter.persistence. */
public class TableInApplication {
    CustomersRecord record;
}
