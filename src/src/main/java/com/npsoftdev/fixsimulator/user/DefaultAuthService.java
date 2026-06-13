package com.npsoftdev.fixsimulator.user;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link AuthService} implementation.
 *
 * <ul>
 *   <li>Password verification uses BCrypt.</li>
 *   <li>Active sessions are tracked in memory; slots freed on explicit sign-out
 *       or via {@link #unregisterSessionById} (called on session expiry).</li>
 * </ul>
 */
public class DefaultAuthService implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthService.class);

    private final UserRepository userRepository;
    private final RoleRegistry   roleRegistry;

    /** username → set of active Wicket session IDs */
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();
    /** Wicket session ID → username (reverse index for expiry cleanup) */
    private final Map<String, String>      sessionToUser  = new ConcurrentHashMap<>();

    public DefaultAuthService(UserRepository userRepository, RoleRegistry roleRegistry) {
        this.userRepository = userRepository;
        this.roleRegistry   = roleRegistry;
    }

    // ── AuthService ───────────────────────────────────────────────────────────

    @Override
    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        Optional<User> opt = userRepository.findByUsername(username.trim());
        if (opt.isEmpty()) return Optional.empty();
        User user = opt.get();
        if (!user.isActive()) return Optional.empty();
        if (user.passwordHash() == null) return Optional.empty();
        try {
            if (!BCrypt.checkpw(password, user.passwordHash())) return Optional.empty();
        } catch (Exception e) {
            log.warn("BCrypt check failed for user {}: {}", username, e.getMessage());
            return Optional.empty();
        }
        return Optional.of(user);
    }

    @Override
    public boolean hasPermission(User user, Permission permission) {
        return roleRegistry.hasPermission(user, permission);
    }

    @Override
    public List<String> getRoleNames() {
        return roleRegistry.getRoleNames();
    }

    @Override
    public boolean canStartSession(String username) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) return false;
        int max = opt.get().maxSessions();
        if (max <= 0) return true;
        return getActiveSessionCount(username) < max;
    }

    @Override
    public void registerSession(String username, String sessionId) {
        if (username == null || sessionId == null) return;
        sessionToUser.put(sessionId, username);
        userToSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("Session registered: user={} sessionId={}", username, sessionId);
    }

    @Override
    public void unregisterSession(String username, String sessionId) {
        if (username == null || sessionId == null) return;
        sessionToUser.remove(sessionId);
        Set<String> sessions = userToSessions.get(username);
        if (sessions != null) sessions.remove(sessionId);
        log.debug("Session unregistered: user={} sessionId={}", username, sessionId);
    }

    @Override
    public void unregisterSessionById(String sessionId) {
        if (sessionId == null) return;
        String username = sessionToUser.remove(sessionId);
        if (username != null) {
            Set<String> sessions = userToSessions.get(username);
            if (sessions != null) sessions.remove(sessionId);
            log.debug("Session expired: user={} sessionId={}", username, sessionId);
        }
    }

    @Override
    public int getActiveSessionCount(String username) {
        Set<String> sessions = userToSessions.get(username);
        return sessions == null ? 0 : sessions.size();
    }

    // ── Password utilities ────────────────────────────────────────────────────

    /** Hashes a plaintext password with BCrypt. */
    public static String hashPassword(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }
}
