package com.npsoftdev.fixsimulator.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAuthServiceTest {

    private InMemoryUserRepository userRepo;
    private RoleRegistry           roleRegistry;
    private DefaultAuthService     service;

    @BeforeEach
    void setUp() {
        userRepo     = new InMemoryUserRepository();
        roleRegistry = new RoleRegistry();
        service      = new DefaultAuthService(userRepo, roleRegistry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User activeUser(String username, String plainPassword, String... roles) {
        return User.builder()
                .username(username)
                .passwordHash(DefaultAuthService.hashPassword(plainPassword))
                .roles(List.of(roles))
                .active(true)
                .build();
    }

    // ── authenticate ─────────────────────────────────────────────────────────

    @Test
    void authenticate_validCredentials_returnsUser() {
        userRepo.save(activeUser("alice", "password123", RoleRegistry.TESTER));

        Optional<User> result = service.authenticate("alice", "password123");

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().username());
    }

    @Test
    void authenticate_wrongPassword_returnsEmpty() {
        userRepo.save(activeUser("alice", "password123", RoleRegistry.TESTER));

        assertTrue(service.authenticate("alice", "wrongpass").isEmpty());
    }

    @Test
    void authenticate_unknownUser_returnsEmpty() {
        assertTrue(service.authenticate("nobody", "password123").isEmpty());
    }

    @Test
    void authenticate_inactiveUser_returnsEmpty() {
        User inactive = User.builder()
                .username("bob")
                .passwordHash(DefaultAuthService.hashPassword("pass1234"))
                .roles(List.of(RoleRegistry.TESTER))
                .active(false)
                .build();
        userRepo.save(inactive);

        assertTrue(service.authenticate("bob", "pass1234").isEmpty());
    }

    @Test
    void authenticate_nullUsername_returnsEmpty() {
        assertTrue(service.authenticate(null, "password").isEmpty());
    }

    @Test
    void authenticate_nullPassword_returnsEmpty() {
        userRepo.save(activeUser("alice", "password123", RoleRegistry.TESTER));
        assertTrue(service.authenticate("alice", null).isEmpty());
    }

    @Test
    void authenticate_noPasswordHash_returnsEmpty() {
        User noHash = User.builder()
                .username("nohash")
                .passwordHash(null)
                .roles(List.of(RoleRegistry.TESTER))
                .active(true)
                .build();
        userRepo.save(noHash);

        assertTrue(service.authenticate("nohash", "anything").isEmpty());
    }

    @Test
    void authenticate_trimmedUsername_matchesUser() {
        userRepo.save(activeUser("alice", "password123", RoleRegistry.TESTER));
        Optional<User> result = service.authenticate("  alice  ", "password123");
        assertTrue(result.isPresent());
    }

    // ── hasPermission ─────────────────────────────────────────────────────────

    @Test
    void hasPermission_adminHasManageUsersPermission() {
        User admin = activeUser("admin", "pass1234", RoleRegistry.ADMIN);
        assertTrue(service.hasPermission(admin, Permission.VIEW_MANAGE_USERS));
    }

    @Test
    void hasPermission_testerHasSendOrdersPermission() {
        User tester = activeUser("tester", "pass1234", RoleRegistry.TESTER);
        assertTrue(service.hasPermission(tester, Permission.VIEW_SEND_MANAGE_ORDERS));
    }

    @Test
    void hasPermission_adminLacksOrderPermission() {
        User admin = activeUser("admin", "pass1234", RoleRegistry.ADMIN);
        assertFalse(service.hasPermission(admin, Permission.VIEW_SEND_MANAGE_ORDERS));
    }

    // ── Session tracking ──────────────────────────────────────────────────────

    @Test
    void registerSession_incrementsCount() {
        assertEquals(0, service.getActiveSessionCount("alice"));
        service.registerSession("alice", "sess-1");
        assertEquals(1, service.getActiveSessionCount("alice"));
    }

    @Test
    void unregisterSession_decrementsCount() {
        service.registerSession("alice", "sess-1");
        service.registerSession("alice", "sess-2");
        service.unregisterSession("alice", "sess-1");
        assertEquals(1, service.getActiveSessionCount("alice"));
    }

    @Test
    void unregisterSessionById_removesSession() {
        service.registerSession("alice", "sess-1");
        service.unregisterSessionById("sess-1");
        assertEquals(0, service.getActiveSessionCount("alice"));
    }

    @Test
    void multipleUsers_sessionCountsAreIndependent() {
        service.registerSession("alice", "sess-a1");
        service.registerSession("alice", "sess-a2");
        service.registerSession("bob",   "sess-b1");

        assertEquals(2, service.getActiveSessionCount("alice"));
        assertEquals(1, service.getActiveSessionCount("bob"));
    }

    // ── canStartSession ───────────────────────────────────────────────────────

    @Test
    void canStartSession_unlimitedUser_alwaysTrue() {
        User unlimited = User.builder()
                .username("alice").passwordHash("h").roles(List.of(RoleRegistry.TESTER))
                .active(true).maxSessions(0).build();
        userRepo.save(unlimited);

        service.registerSession("alice", "s1");
        service.registerSession("alice", "s2");

        assertTrue(service.canStartSession("alice"));
    }

    @Test
    void canStartSession_limitedUser_falseWhenAtLimit() {
        User limited = User.builder()
                .username("bob").passwordHash("h").roles(List.of(RoleRegistry.TESTER))
                .active(true).maxSessions(1).build();
        userRepo.save(limited);

        service.registerSession("bob", "s1");

        assertFalse(service.canStartSession("bob"));
    }

    @Test
    void canStartSession_limitedUser_trueWhenBelowLimit() {
        User limited = User.builder()
                .username("bob").passwordHash("h").roles(List.of(RoleRegistry.TESTER))
                .active(true).maxSessions(2).build();
        userRepo.save(limited);

        service.registerSession("bob", "s1");

        assertTrue(service.canStartSession("bob"));
    }

    @Test
    void canStartSession_unknownUser_returnsFalse() {
        assertFalse(service.canStartSession("nobody"));
    }

    // ── hashPassword ─────────────────────────────────────────────────────────

    @Test
    void hashPassword_producesValidBcryptHash() {
        String hash = DefaultAuthService.hashPassword("mySecret");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"),
                "Expected BCrypt hash, got: " + hash);
    }

    @Test
    void hashPassword_twoCallsProduceDifferentHashes() {
        String h1 = DefaultAuthService.hashPassword("same");
        String h2 = DefaultAuthService.hashPassword("same");
        assertNotEquals(h1, h2, "BCrypt salts must differ");
    }

    @Test
    void hashPassword_usesCostFactor12() {
        String hash = DefaultAuthService.hashPassword("any");
        // BCrypt format: $2a$<cost>$... — cost must be exactly 12
        assertTrue(hash.startsWith("$2a$12$"),
                "BCrypt cost factor must be 12, got: " + hash);
    }

    // ── Minimal in-memory UserRepository for this test ─────────────────────

    static class InMemoryUserRepository implements UserRepository {
        private final java.util.concurrent.ConcurrentHashMap<String, User> map =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override public void save(User user) { map.put(user.username(), user); }
        @Override public Optional<User> findByUsername(String u) { return Optional.ofNullable(map.get(u)); }
        @Override public java.util.List<User> findAll() { return new java.util.ArrayList<>(map.values()); }
        @Override public void delete(String u) { map.remove(u); }
    }
}
