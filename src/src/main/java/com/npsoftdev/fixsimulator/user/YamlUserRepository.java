package com.npsoftdev.fixsimulator.user;

import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAML-backed {@link UserRepository}.
 *
 * <p>User accounts are persisted to {@code users.yaml} inside the configured
 * data directory.  The {@code role} (singular) field from the old schema is
 * automatically migrated to {@code roles} (list) on load.</p>
 *
 * <h3>YAML format</h3>
 * <pre>{@code
 * users:
 *   - username: alice
 *     displayName: Alice Smith
 *     email: alice@example.com
 *     roles: [Admin, Tester]
 *     maxSessions: 0
 *     active: true
 * }</pre>
 */
public class YamlUserRepository implements UserRepository {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(YamlUserRepository.class);
    private static final String FILENAME = "users.yaml";

    private final YamlPersistenceService yaml;
    private final Map<String, User> byUsername = new LinkedHashMap<>();

    public YamlUserRepository(YamlPersistenceService yaml) {
        this.yaml = yaml;
        load();
    }

    // ── UserRepository ────────────────────────────────────────────────────────

    @Override
    public synchronized void save(User user) {
        byUsername.put(user.username(), user);
        persist();
    }

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    @Override
    public synchronized List<User> findAll() {
        List<User> all = new ArrayList<>(byUsername.values());
        all.sort(Comparator.comparing(User::username));
        return all;
    }

    @Override
    public synchronized void delete(String username) {
        byUsername.remove(username);
        persist();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void load() {
        if (!yaml.exists(FILENAME)) return;
        try {
            UserListDto dto = yaml.load(FILENAME, UserListDto.class);
            if (dto.users != null) {
                for (UserDto u : dto.users) {
                    byUsername.put(u.username, fromDto(u));
                }
            }
            log.info("Loaded {} user(s) from {}", byUsername.size(), FILENAME);
        } catch (IOException e) {
            log.error("Failed to load {}; starting with empty user repository", FILENAME, e);
        }
    }

    private void persist() {
        UserListDto dto = new UserListDto();
        dto.users = new ArrayList<>();
        for (User u : findAll()) dto.users.add(toDto(u));
        try {
            yaml.save(FILENAME, dto);
        } catch (IOException e) {
            log.error("Failed to save users to {}", FILENAME, e);
        }
    }

    private static User fromDto(UserDto d) {
        // Migrate old single-role field to roles list
        List<String> roles;
        if (d.roles != null && !d.roles.isEmpty()) {
            roles = d.roles;
        } else if (d.role != null && !d.role.isBlank()) {
            roles = List.of(d.role);
        } else {
            roles = Collections.emptyList();
        }

        return User.builder()
                .username(d.username)
                .displayName(d.displayName)
                .passwordHash(d.passwordHash)
                .email(d.email)
                .roles(roles)
                .active(d.active == null || d.active)
                .maxSessions(d.maxSessions != null ? d.maxSessions : 0)
                .timezone(d.timezone)
                .build();
    }

    private static UserDto toDto(User u) {
        UserDto d        = new UserDto();
        d.username       = u.username();
        d.displayName    = u.displayName();
        d.passwordHash   = u.passwordHash();
        d.email          = u.email();
        d.roles          = u.roles().isEmpty() ? null : new ArrayList<>(u.roles());
        d.active         = u.isActive() ? null : false;  // omit true (cleaner YAML)
        d.maxSessions    = u.maxSessions() == 0 ? null : u.maxSessions();
        d.timezone       = u.timezone();
        return d;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    static class UserListDto {
        public List<UserDto> users;
    }

    static class UserDto {
        public String       username;
        public String       displayName;
        public String       passwordHash;
        public String       email;
        public List<String> roles;
        /** Legacy single-role field — migrated to {@code roles} on load. */
        public String       role;
        /** Null means active = true (omitted in YAML for brevity). */
        public Boolean      active;
        /** Null means 0 = unlimited. */
        public Integer      maxSessions;
        /** IANA timezone ID, e.g. "Asia/Bangkok". Null means UTC. */
        public String       timezone;
    }
}
