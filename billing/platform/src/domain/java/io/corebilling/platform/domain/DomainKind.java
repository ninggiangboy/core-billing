package io.corebilling.platform.domain;

/** What a hostname resolves to. This enum is the seam where custom domains can be added later. */
public enum DomainKind {
    /** Platform Administration; belongs to no Tenant. */
    PLATFORM,
    /** A Tenant's subdomain of the base domain. */
    TENANT_SUBDOMAIN
}
