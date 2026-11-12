package dev.ngb.billing.session.api.portal;

/** Answers whether the current portal session carries a permission. */
public interface PortalAuthorizer {

    /** @throws dev.ngb.billing.platform.api.error.AccessDeniedException when the permission is absent */
    void require(PortalPermission permission);
}
