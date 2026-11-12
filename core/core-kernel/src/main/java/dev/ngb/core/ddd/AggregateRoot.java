package dev.ngb.core.ddd;

import dev.ngb.core.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The entry point of an aggregate: a consistency boundary that decides what changes. An aggregate may record the
 * events its changes raise; the caller takes them out in one go and decides how to publish them, so the aggregate
 * itself knows nothing about outboxes or transports.
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final List<DomainEvent> events = new ArrayList<>();

    protected AggregateRoot(ID id) {
        super(id);
    }

    /** Records an event that the next {@link #pullEvents()} hands to the caller. */
    protected void register(DomainEvent event) {
        events.add(event);
    }

    /** @return the events registered since the last call, and forgets them */
    public final List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
