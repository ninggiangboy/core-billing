package archfixtures.alpha.domain;

import dev.ngb.billing_jooq.tables.records.CustomersRecord;

/** Violates persistence placement: a generated jOOQ type outside adapter.persistence. */
public class RowInDomain {
    CustomersRecord record;
}
