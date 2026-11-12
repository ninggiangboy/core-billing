package archfixtures.customer.adapter.persistence;

import static dev.ngb.sampletenant_jooq.Tables.USERS;

import org.jooq.DSLContext;
import org.jooq.Query;

/** Violates module-uses-only-own-tables: a customer adapter reads the identity module's table. */
public class UsesAnotherModulesTable {

    Query read(DSLContext dsl) {
        return dsl.selectFrom(USERS);
    }
}
