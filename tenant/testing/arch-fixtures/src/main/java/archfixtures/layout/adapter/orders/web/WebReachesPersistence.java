package archfixtures.layout.adapter.orders.web;

import archfixtures.layout.adapter.orders.persistence.OrderStore;

/** Violates web-does-not-depend-on-persistence: a web class names a persistence class. */
public class WebReachesPersistence {
    OrderStore store;
}
