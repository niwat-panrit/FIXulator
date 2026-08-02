package com.npsoftdev.fixsimulator.plugins.template.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.SessionID;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageTemplate;
import com.npsoftdev.fixsimulator.plugins.template.api.TemplateScope;

class InMemoryTemplateRepositoryTest {

    private InMemoryTemplateRepository repo;

    private static final SessionID SESSION_A = new SessionID("FIX.4.4", "SIM", "EXCH");
    private static final SessionID SESSION_B = new SessionID("FIX.4.4", "SIM", "BROKER");

    @BeforeEach
    void setUp() {
        repo = new InMemoryTemplateRepository();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FixMessageTemplate template(String id, String name) {
        return FixMessageTemplate.builder()
                .id(id).name(name).msgType("D")
                .build();
    }

    private FixMessageTemplate templateWithPriority(String id, String name, int priority) {
        return FixMessageTemplate.builder()
                .id(id).name(name).msgType("D").priority(priority)
                .build();
    }

    private FixMessageTemplate sessionTemplate(String id, String name, String sessionId) {
        return FixMessageTemplate.builder()
                .id(id).name(name).msgType("D")
                .scope(TemplateScope.session(sessionId))
                .build();
    }

    private FixMessageTemplate protectedTemplate(String id, String name) {
        return FixMessageTemplate.builder()
                .id(id).name(name).msgType("D").deletionProtected(true)
                .build();
    }

    // ── save / findById ───────────────────────────────────────────────────────

    @Test
    void save_and_findById_returnsTemplate() {
        repo.save(template("t1", "Template One"));
        Optional<FixMessageTemplate> found = repo.findById("t1");
        assertTrue(found.isPresent());
        assertEquals("Template One", found.get().name());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertTrue(repo.findById("nonexistent").isEmpty());
    }

    @Test
    void save_overwritesExistingTemplate() {
        repo.save(template("t1", "Original"));
        repo.save(FixMessageTemplate.builder().id("t1").name("Updated").msgType("G").build());
        assertEquals("Updated", repo.findById("t1").get().name());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_emptyRepo_returnsEmptyList() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_sortsByPriorityAscending() {
        repo.save(templateWithPriority("t1", "Alpha", 200));
        repo.save(templateWithPriority("t2", "Beta",   50));
        repo.save(templateWithPriority("t3", "Gamma", 100));

        List<FixMessageTemplate> all = repo.findAll();
        assertEquals(3, all.size());
        assertEquals("t2", all.get(0).id()); // priority 50
        assertEquals("t3", all.get(1).id()); // priority 100
        assertEquals("t1", all.get(2).id()); // priority 200
    }

    @Test
    void findAll_samePriority_sortsByNameAlphabetically() {
        repo.save(templateWithPriority("t1", "Zebra",  100));
        repo.save(templateWithPriority("t2", "Apple",  100));
        repo.save(templateWithPriority("t3", "Mango",  100));

        List<FixMessageTemplate> all = repo.findAll();
        assertEquals("Apple", all.get(0).name());
        assertEquals("Mango", all.get(1).name());
        assertEquals("Zebra", all.get(2).name());
    }

    // ── findVisibleTo ─────────────────────────────────────────────────────────

    @Test
    void findVisibleTo_globalTemplates_visibleToAllSessions() {
        repo.save(template("g1", "Global One"));
        repo.save(template("g2", "Global Two"));

        List<FixMessageTemplate> forA = repo.findVisibleTo(SESSION_A);
        List<FixMessageTemplate> forB = repo.findVisibleTo(SESSION_B);

        assertEquals(2, forA.size());
        assertEquals(2, forB.size());
    }

    @Test
    void findVisibleTo_sessionScoped_onlyVisibleToMatchingSession() {
        repo.save(sessionTemplate("s1", "Session A only", SESSION_A.toString()));
        repo.save(template("g1", "Global"));

        List<FixMessageTemplate> forA = repo.findVisibleTo(SESSION_A);
        List<FixMessageTemplate> forB = repo.findVisibleTo(SESSION_B);

        assertEquals(2, forA.size());
        assertEquals(1, forB.size());
        assertEquals("Global", forB.get(0).name());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesTemplate() {
        repo.save(template("t1", "To Delete"));
        repo.delete("t1");
        assertTrue(repo.findById("t1").isEmpty());
    }

    @Test
    void delete_unknownId_doesNotThrow() {
        assertDoesNotThrow(() -> repo.delete("no-such-id"));
    }

    @Test
    void delete_deletionProtected_throwsIllegalStateException() {
        repo.save(protectedTemplate("builtin", "Built-In"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> repo.delete("builtin"));
        assertTrue(ex.getMessage().contains("Built-In"));
    }

    @Test
    void delete_doesNotAffectOtherTemplates() {
        repo.save(template("t1", "Keep"));
        repo.save(template("t2", "Delete Me"));
        repo.delete("t2");

        assertTrue(repo.findById("t1").isPresent());
        assertTrue(repo.findById("t2").isEmpty());
        assertEquals(1, repo.findAll().size());
    }
}
