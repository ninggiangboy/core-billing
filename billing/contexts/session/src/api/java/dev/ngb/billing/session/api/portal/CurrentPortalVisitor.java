package dev.ngb.billing.session.api.portal;

/** The portal visitor of the current request. */
public interface CurrentPortalVisitor {

    /** @throws IllegalStateException when the request has no portal session */
    PortalVisitor get();
}
