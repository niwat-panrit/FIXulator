package com.npsoftdev.fixsimulator.user;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link User} accounts.
 */
public interface UserRepository extends Serializable {

    /** Insert or replace by {@link User#username()}. */
    void save(User user);

    Optional<User> findByUsername(String username);

    /** All users ordered alphabetically by username. */
    List<User> findAll();

    /** No-op when absent. */
    void delete(String username);
}
