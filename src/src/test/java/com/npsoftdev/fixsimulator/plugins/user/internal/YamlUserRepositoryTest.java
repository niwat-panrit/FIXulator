package com.npsoftdev.fixsimulator.plugins.user.internal;

import com.npsoftdev.fixsimulator.plugins.persistence.api.YamlPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.plugins.user.api.RoleRegistry;
import com.npsoftdev.fixsimulator.plugins.user.api.User;

class YamlUserRepositoryTest {

    @TempDir
    Path tempDir;

    private YamlPersistenceService yaml;
    private YamlUserRepository     repo;

    @BeforeEach
    void setUp() {
        yaml = new YamlPersistenceService(tempDir);
        repo = new YamlUserRepository(yaml);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(String username, String... roles) {
        return User.builder()
                .username(username)
                .displayName(username.toUpperCase())
                .passwordHash("$2a$hashed")
                .email(username + "@example.com")
                .roles(List.of(roles))
                .active(true)
                .maxSessions(0)
                .build();
    }

    // ── Basic CRUD ────────────────────────────────────────────────────────────

    @Test
    void save_and_findByUsername_returnsUser() {
        repo.save(user("alice", RoleRegistry.TESTER));

        assertTrue(repo.findByUsername("alice").isPresent());
        assertEquals("alice", repo.findByUsername("alice").get().username());
    }

    @Test
    void findByUsername_unknown_returnsEmpty() {
        assertTrue(repo.findByUsername("nobody").isEmpty());
    }

    @Test
    void save_overwritesExistingUser() {
        repo.save(user("alice", RoleRegistry.TESTER));

        User updated = User.builder()
                .username("alice").email("new@example.com")
                .roles(List.of(RoleRegistry.ADMIN)).active(true).build();
        repo.save(updated);

        User found = repo.findByUsername("alice").get();
        assertEquals("new@example.com", found.email());
        assertTrue(found.hasRole(RoleRegistry.ADMIN));
    }

    @Test
    void delete_removesUser() {
        repo.save(user("alice", RoleRegistry.TESTER));
        repo.delete("alice");
        assertTrue(repo.findByUsername("alice").isEmpty());
    }

    @Test
    void delete_unknownUser_doesNotThrow() {
        assertDoesNotThrow(() -> repo.delete("nobody"));
    }

    @Test
    void findAll_sortedAlphabetically() {
        repo.save(user("charlie", RoleRegistry.TESTER));
        repo.save(user("alice",   RoleRegistry.ADMIN));
        repo.save(user("bob",     RoleRegistry.TESTER));

        List<User> all = repo.findAll();
        assertEquals("alice",   all.get(0).username());
        assertEquals("bob",     all.get(1).username());
        assertEquals("charlie", all.get(2).username());
    }

    // ── Persistence across repository instances ───────────────────────────────

    @Test
    void save_persistsToYamlFile() {
        repo.save(user("alice", RoleRegistry.ADMIN));

        // New repository instance loading from the same directory
        YamlUserRepository repo2 = new YamlUserRepository(yaml);
        assertTrue(repo2.findByUsername("alice").isPresent());
        assertTrue(repo2.findByUsername("alice").get().hasRole(RoleRegistry.ADMIN));
    }

    @Test
    void delete_persistsToYamlFile() {
        repo.save(user("alice", RoleRegistry.TESTER));
        repo.save(user("bob",   RoleRegistry.TESTER));
        repo.delete("alice");

        YamlUserRepository repo2 = new YamlUserRepository(yaml);
        assertTrue(repo2.findByUsername("alice").isEmpty());
        assertTrue(repo2.findByUsername("bob").isPresent());
    }

    // ── Field round-trip ──────────────────────────────────────────────────────

    @Test
    void timezone_savedAndLoaded() {
        User u = User.builder()
                .username("tz-user").roles(List.of(RoleRegistry.TESTER))
                .active(true).timezone("Asia/Bangkok").build();
        repo.save(u);

        YamlUserRepository repo2 = new YamlUserRepository(yaml);
        String tz = repo2.findByUsername("tz-user").get().timezone();
        assertEquals("Asia/Bangkok", tz);
    }

    @Test
    void maxSessions_savedAndLoaded() {
        User u = User.builder()
                .username("limited").roles(List.of(RoleRegistry.TESTER))
                .active(true).maxSessions(3).build();
        repo.save(u);

        YamlUserRepository repo2 = new YamlUserRepository(yaml);
        assertEquals(3, repo2.findByUsername("limited").get().maxSessions());
    }

    @Test
    void inactiveUser_savedAndLoaded() {
        User u = User.builder()
                .username("disabled").roles(List.of(RoleRegistry.TESTER))
                .active(false).build();
        repo.save(u);

        YamlUserRepository repo2 = new YamlUserRepository(yaml);
        assertFalse(repo2.findByUsername("disabled").get().isActive());
    }

    // ── Legacy role migration ─────────────────────────────────────────────────

    @Test
    void load_legacySingleRoleField_migratedToRolesList() throws Exception {
        // Write YAML using the old single-role schema
        String legacyYaml =
                "users:\n" +
                "  - username: legacy\n" +
                "    passwordHash: $2a$legacyhash\n" +
                "    role: Tester\n" +
                "    active: true\n";
        Files.writeString(tempDir.resolve("users.yaml"), legacyYaml);

        YamlUserRepository loaded = new YamlUserRepository(yaml);
        User legacy = loaded.findByUsername("legacy").orElseThrow();
        assertTrue(legacy.hasRole(RoleRegistry.TESTER),
                "Legacy 'role' field should be migrated to 'roles' list");
    }

    // ── No file at startup ────────────────────────────────────────────────────

    @Test
    void newRepo_noFile_startsEmpty() {
        assertTrue(repo.findAll().isEmpty());
    }
}
