package archfixtures.split.query;

import archfixtures.split.domain.Aggregate;

/** Violates query-side-uses-no-domain-aggregates: a read model depends on a domain aggregate. */
public class QueryReachesAggregate {
    Aggregate aggregate;
}
