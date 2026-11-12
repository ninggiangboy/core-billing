package archfixtures.sharedcore;

import archfixtures.alpha.domain.Order;

/** Violates core-is-self-contained: the shared core reaches into an application's module. */
public class CoreKnowsAnApplication {
    Order order;
}
