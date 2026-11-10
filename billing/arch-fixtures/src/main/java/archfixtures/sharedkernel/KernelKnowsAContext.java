package archfixtures.sharedkernel;

import archfixtures.alpha.domain.Order;

/** Violates kernel independence: shared-kernel depends on a module. */
public class KernelKnowsAContext {
    Order order;
}
