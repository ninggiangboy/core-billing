package archfixtures.shared;

import archfixtures.alpha.domain.Order;

/** Violates shared-core independence: the shared core depends on a module. */
public class SharedCoreKnowsAModule {
    Order order;
}
