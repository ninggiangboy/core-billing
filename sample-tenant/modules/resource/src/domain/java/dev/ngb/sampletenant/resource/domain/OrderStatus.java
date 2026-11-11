package dev.ngb.sampletenant.resource.domain;

/** The lifecycle of a resource order. Only an order whose reservation succeeded is queued. */
public enum OrderStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    SUSPENDED,
    CANCELED
}
