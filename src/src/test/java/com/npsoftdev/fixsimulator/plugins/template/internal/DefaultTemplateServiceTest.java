package com.npsoftdev.fixsimulator.plugins.template.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageBuilder;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageTemplate;
import com.npsoftdev.fixsimulator.plugins.template.api.MessageDispatcher;
import com.npsoftdev.fixsimulator.plugins.template.api.MessageSnapshot;
import com.npsoftdev.fixsimulator.plugins.template.api.TemplateRepository;
import com.npsoftdev.fixsimulator.plugins.template.api.TemplateScope;

@ExtendWith(MockitoExtension.class)
class DefaultTemplateServiceTest {

    private static final SessionID SESSION_A = new SessionID("FIX.4.4", "SIM", "EXCH");
    private static final SessionID SESSION_B = new SessionID("FIX.4.4", "SIM", "BROKER");

    private static final String SESSION_A_STR = SESSION_A.toString();
    private static final String SESSION_B_STR = SESSION_B.toString();

    @Mock private TemplateRepository repository;
    @Mock private FixMessageBuilder   builder;
    @Mock private MessageDispatcher   dispatcher;

    private Map<String, SessionID> sessionIds;
    private DefaultTemplateService service;

    @BeforeEach
    void setUp() {
        sessionIds = Map.of(
                SESSION_A_STR, SESSION_A,
                SESSION_B_STR, SESSION_B
        );
        service = new DefaultTemplateService(repository, builder, dispatcher, sessionIds);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FixMessageTemplate template(String id, String name) {
        return FixMessageTemplate.builder()
                .id(id).name(name).msgType("D").build();
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    void send_dispatchesBuiltMessage() throws SessionNotFound {
        FixMessageTemplate tmpl = template("t1", "NewOrder");
        Message builtMsg = new Message();
        when(repository.findById("t1")).thenReturn(Optional.of(tmpl));
        when(builder.build(eq(tmpl), any(), eq(SESSION_A))).thenReturn(builtMsg);

        Message result = service.send(SESSION_A_STR, "t1", Map.of("symbol", "AAPL"));

        assertSame(builtMsg, result);
        verify(dispatcher).dispatch(builtMsg, SESSION_A);
    }

    @Test
    void send_unknownSession_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.send("FIX.4.4:UNKNOWN->NONE", "t1", Map.of()));
        assertTrue(ex.getMessage().contains("Unknown FIX session"));
    }

    @Test
    void send_unknownTemplate_throwsIllegalArgument() {
        when(repository.findById("no-such")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.send(SESSION_A_STR, "no-such", Map.of()));
        assertTrue(ex.getMessage().contains("Unknown template"));
    }

    @Test
    void send_sessionNotFound_wrappedInRuntimeException() throws SessionNotFound {
        FixMessageTemplate tmpl = template("t1", "NewOrder");
        Message builtMsg = new Message();
        when(repository.findById("t1")).thenReturn(Optional.of(tmpl));
        when(builder.build(any(), any(), any())).thenReturn(builtMsg);
        doThrow(new SessionNotFound("gone")).when(dispatcher).dispatch(any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.send(SESSION_A_STR, "t1", Map.of()));
        assertTrue(ex.getMessage().contains("FIX session not found"));
    }

    // ── findVisibleTo ─────────────────────────────────────────────────────────

    @Test
    void findVisibleTo_delegatesToRepository() {
        FixMessageTemplate tmpl = template("t1", "Global");
        when(repository.findVisibleTo(SESSION_A)).thenReturn(List.of(tmpl));

        List<FixMessageTemplate> result = service.findVisibleTo(SESSION_A_STR);

        assertEquals(1, result.size());
        assertSame(tmpl, result.get(0));
    }

    @Test
    void findVisibleTo_nullSessionId_returnsEmptyList() {
        List<FixMessageTemplate> result = service.findVisibleTo(null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void findVisibleTo_unknownSessionId_returnsEmptyList() {
        List<FixMessageTemplate> result = service.findVisibleTo("FIX.4.4:UNKNOWN->NONE");
        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
    }

    // ── captureFromMessage ────────────────────────────────────────────────────

    @Test
    void captureFromMessage_buildsTemplateFromSnapshot() {
        MessageSnapshot snap = new MessageSnapshot(
                "D", "FIX.4.4",
                Map.of(55, "AAPL"),  // header fields (symbol in header for test)
                Map.of(38, "100"),   // body fields
                Instant.now()
        );

        FixMessageTemplate captured = service.captureFromMessage(
                "cap1", "Captured Order", snap, TemplateScope.global());

        assertEquals("cap1",    captured.id());
        assertEquals("Captured Order", captured.name());
        assertEquals("D",       captured.msgType());
        assertEquals("FIX.4.4", captured.beginString());
        // Engine-owned tags should NOT be included; body fields should be present
        assertTrue(captured.fields().stream().anyMatch(f -> f.tag() == 38));
        // Engine-owned tag 8 from a snapshot's header should be skipped
        assertTrue(captured.fields().stream().noneMatch(f -> f.tag() == 8));
    }

    @Test
    void captureFromMessage_emptyBeginString_defaultsToFix44() {
        MessageSnapshot snap = new MessageSnapshot(
                "D", "", Map.of(), Map.of(55, "IBM"), Instant.now());

        FixMessageTemplate captured = service.captureFromMessage(
                "c1", "Test", snap, TemplateScope.global());

        assertEquals("FIX.4.4", captured.beginString());
    }

    @Test
    void captureFromMessage_engineOwnedHeaderTags_areSkipped() {
        // Tags 8, 9, 10, 34, 35, 49, 52, 56 are engine-owned
        MessageSnapshot snap = new MessageSnapshot(
                "D", "FIX.4.4",
                Map.of(8, "FIX.4.4", 49, "SIM", 56, "EXCH", 57, "DESK"),  // 57 is NOT engine-owned
                Map.of(55, "AAPL"),
                Instant.now());

        FixMessageTemplate captured = service.captureFromMessage(
                "c1", "Test", snap, TemplateScope.global());

        // Tag 57 (TargetSubID) should be included
        assertTrue(captured.fields().stream().anyMatch(f -> f.tag() == 57));
        // Engine-owned tags 8, 49, 56 must be excluded
        assertTrue(captured.fields().stream().noneMatch(f -> f.tag() == 8));
        assertTrue(captured.fields().stream().noneMatch(f -> f.tag() == 49));
        assertTrue(captured.fields().stream().noneMatch(f -> f.tag() == 56));
    }

    // ── Repository pass-throughs ──────────────────────────────────────────────

    @Test
    void findAll_delegatesToRepository() {
        FixMessageTemplate t1 = template("t1", "A");
        FixMessageTemplate t2 = template("t2", "B");
        when(repository.findAll()).thenReturn(List.of(t1, t2));

        List<FixMessageTemplate> result = service.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void findById_delegatesToRepository() {
        FixMessageTemplate tmpl = template("t1", "A");
        when(repository.findById("t1")).thenReturn(Optional.of(tmpl));

        assertTrue(service.findById("t1").isPresent());
    }

    @Test
    void save_delegatesToRepository() {
        FixMessageTemplate tmpl = template("t1", "A");
        service.save(tmpl);
        verify(repository).save(tmpl);
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete("t1");
        verify(repository).delete("t1");
    }
}
