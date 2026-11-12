package dev.ngb.tenant.identity.api.account;

import java.util.UUID;

/** What another module needs to know about an account, without seeing identity's domain. */
public record RegisteredAccount(UUID userId, String displayName, String email) {}
