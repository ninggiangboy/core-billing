package io.corebilling.platform.application.user.port;

import io.corebilling.platform.api.error.ConflictException;
import io.corebilling.platform.domain.user.BillingUser;
import io.corebilling.platform.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** Reads see only the current scope's users, because the database enforces it. */
public interface UserRepository {

    /** @throws ConflictException when the email is already used in the scope */
    void add(BillingUser user);

    void update(BillingUser user);

    Optional<BillingUser> findById(UserId id);

    Optional<BillingUser> findByKeycloakSubject(String subject);

    Optional<BillingUser> findByEmail(String email);

    List<BillingUser> list();
}
