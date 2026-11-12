package archfixtures.sharedcore.spring;

import archfixtures.alpha.domain.Order;

/** Violates core-spring-is-self-contained: the core's Spring module reaches into an application's module. */
public class CoreSpringKnowsAnApplication {
    Order order;
}
