package archfixtures.customer.adapter.persistence;

import static dev.ngb.core_billing_jooq.Tables.USAGE_EVENTS;

import org.jooq.DSLContext;
import org.jooq.Query;

/** Violates context-uses-only-own-tables: a customer adapter reads the usage context's table. */
public class UsesAnotherContextsTable {

    Query read(DSLContext dsl) {
        return dsl.selectFrom(USAGE_EVENTS);
    }
}
