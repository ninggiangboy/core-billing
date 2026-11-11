package dev.ngb.core_billing.platform.application.user.port;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.user.BillingUserId;
import java.util.List;
import java.util.Optional;

/** Reads see only the current scope's users, because the database enforces it. */
public interface UserRepository {

    /** @throws ConflictException when the email is already used in the scope */
    void add(BillingUser user);

    void update(BillingUser user);

    Optional<BillingUser> findById(BillingUserId id);

    Optional<BillingUser> findByKeycloakSubject(String subject);

    Optional<BillingUser> findByEmail(String email);
}
