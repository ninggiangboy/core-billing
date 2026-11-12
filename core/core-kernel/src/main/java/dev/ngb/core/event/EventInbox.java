package dev.ngb.core.event;

/**
 * Remembers which events a consumer has already handled, so the at-least-once relay never makes a consumer act
 * twice. It must be called inside the unit of work that carries the consumer's own writes, so the mark and the
 * effect commit or roll back together.
 */
public interface EventInbox {

    /**
     * @return true the first time {@code consumer} sees {@code event}; false when it was handled before
     */
    boolean firstTime(String consumer, DomainEvent event);
}
