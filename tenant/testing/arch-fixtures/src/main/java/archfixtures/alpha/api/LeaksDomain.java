package archfixtures.alpha.api;

import archfixtures.alpha.domain.Order;

/** Violates api isolation: the public api exposes a domain type. */
public interface LeaksDomain {
    Order find();
}
