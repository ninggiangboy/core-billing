package dev.ngb.billing.platform.application.auth.port;

/** Creates and removes identities in the identity provider (Keycloak). It never stores roles or permissions. */
public interface IdentityProviderPort {

    /**
     * @param username tenant-scoped login name, {@code <tenant-code>:<email>}
     * @param tenantCode value of the {@code tenant_code} claim the identity's tokens will carry
     * @param temporaryPassword optional; when present the user must change it at first login
     */
    record NewIdentity(String username, String email, String displayName, String tenantCode, String temporaryPassword) {
        @Override
        public String toString() {
            return "NewIdentity[username=" + username + "]";
        }
    }

    /** @return the OIDC subject of the new identity */
    String createIdentity(NewIdentity identity);

    void deleteIdentity(String subject);
}
