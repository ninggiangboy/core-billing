package io.corebilling.platform.application;

import io.corebilling.platform.domain.BillingUser;
import io.corebilling.platform.domain.UserId;
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
