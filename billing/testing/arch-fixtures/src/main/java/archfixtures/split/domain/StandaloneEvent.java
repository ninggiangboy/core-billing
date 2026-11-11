package archfixtures.split.domain;

import dev.ngb.core_billing.sharedkernel.DomainEvent;

/** Violates domain-events-live-in-an-events-holder: a domain event that is not nested in an *Events class. */
public abstract class StandaloneEvent implements DomainEvent {}
