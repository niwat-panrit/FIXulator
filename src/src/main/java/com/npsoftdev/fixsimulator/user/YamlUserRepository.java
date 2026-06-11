package com.npsoftdev.fixsimulator.user;

import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAML-backed {@link UserRepository}.
 *
 * <p>User accounts are persisted to {@code users.yaml} inside the configured
 * data directory. Passwords are stored as supplied (callers are responsible
 * for hashing before passing to {@link #save}).</p>
 *
 * <h3>YAML format</h3>
 * <pre>{@code
 * users:
 *   - username: alice
 *     displayName: Alice Smith
 *     role: ADMIN
 *     active: true
 *   - username: bob
 *     displayName: Bob Jones
 *     role: OPERATOR
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
        return User.builder()
                .username(d.username)
                .displayName(d.displayName)
                .passwordHash(d.passwordHash)
                .role(d.role != null ? d.role : "OPERATOR")
                .active(d.active == null || d.active)
                .build();
    }

    private static UserDto toDto(User u) {
        UserDto d      = new UserDto();
        d.username     = u.username();
        d.displayName  = u.displayName();
        d.passwordHash = u.passwordHash();
        d.role         = u.role();
        d.active       = u.isActive() ? null : false; // omit true (NON_NULL default = active)
        return d;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    static class UserListDto {
        public List<UserDto> users;
    }

    static class UserDto {
        public String  username;
        public String  displayName;
        public String  passwordHash;
        public String  role;
        /** Null means active = true (omitted in YAML for brevity). */
        public Boolean active;
    }
}
