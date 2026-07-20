package com.npsoftdev.fixsimulator.user;

import java.util.Optional;

/**
 * Manages persistent remember-me tokens so that users remain signed in after
 * an application restart.
 *
 * <p>A token is a cryptographically random string stored both in a browser
 * cookie and in the server-side token store (backed by a YAML file).  On every
 * request the {@link com.npsoftdev.fixsimulator.FixSimulatorApplication}
 * request-cycle listener resolves the cookie against this store and
 * automatically signs in the associated user when the token is valid.</p>
 */
public interface RememberMeService {

    /**
     * Generates a new random token for {@code username}, persists it, and
     * returns the raw token string to be written into the browser cookie.
     */
    String createToken(String username);

    /**
     * Validates {@code token} and returns the associated username when the
     * token exists and has not expired.
     *
     * @return the username, or empty if the token is unknown or expired
     */
    Optional<String> resolveToken(String token);

    /**
     * Removes the token, typically called on explicit sign-out so that the
     * cookie cannot be replayed after the user has logged out.
     */
    void deleteToken(String token);

    /** Removes all tokens whose expiry timestamp has already passed. */
    void purgeExpired();
}
