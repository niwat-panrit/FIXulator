package com.npsoftdev.fixsimulator.plugins.connection.internal;

import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest;
import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.SessionActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.Message;
import quickfix.SessionID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.npsoftdev.fixsimulator.plugins.connection.api.SessionFacade;

@ExtendWith(MockitoExtension.class)
class GatewayConnectionServiceTest {

    @Mock
    private SessionFacade facade;

    private Map<String, SessionID> sessionIDs;
    private GatewayConnectionService service;

    private static final SessionID SID_A = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");
    private static final SessionID SID_B = new SessionID("FIX.4.4", "SIMULATOR", "BROKER");

    @BeforeEach
    void setUp() {
        sessionIDs = new ConcurrentHashMap<>();
        service    = new GatewayConnectionService(sessionIDs, facade, null);
    }

    // ── onCreate / listSessionIds ─────────────────────────────────────────────

    @Test
    void onCreate_sessionAppearsInList() {
        service.onSessionCreated(SID_A);
        assertTrue(service.listSessionIds().contains(SID_A.toString()));
    }

    @Test
    void onCreate_multipleSessionsAllAppear() {
        service.onSessionCreated(SID_A);
        service.onSessionCreated(SID_B);
        assertEquals(2, service.listSessionIds().size());
    }

    // ── getSessionName ────────────────────────────────────────────────────────

    @Test
    void getSessionName_knownSession_returnsSenderToTarget() {
        service.onSessionCreated(SID_A);
        String name = service.getSessionName(SID_A.toString());
        assertTrue(name.contains("SIMULATOR"), "name should include sender comp ID");
        assertTrue(name.contains("EXCHANGE"),  "name should include target comp ID");
    }

    @Test
    void getSessionName_unknownSession_returnsSessionIdString() {
        String result = service.getSessionName("UNKNOWN");
        assertEquals("UNKNOWN", result);
    }

    // ── getStatus state machine ───────────────────────────────────────────────

    @Test
    void getStatus_unknownSession_returnsUnknown() {
        assertEquals("UNKNOWN", service.getStatus(SID_A.toString()));
    }

    @Test
    void getStatus_afterOnCreate_returnsCreated() {
        service.onSessionCreated(SID_A);
        assertEquals("CREATED", service.getStatus(SID_A.toString()));
    }

    @Test
    void getStatus_afterOnLogon_returnsConnected() {
        service.onSessionCreated(SID_A);
        service.onLogon(SID_A);
        assertEquals("CONNECTED", service.getStatus(SID_A.toString()));
    }

    @Test
    void getStatus_afterOnLogout_returnsDisconnected() {
        service.onSessionCreated(SID_A);
        service.onLogon(SID_A);
        service.onLogout(SID_A);
        assertEquals("DISCONNECTED", service.getStatus(SID_A.toString()));
    }

    @Test
    void onLogon_withoutPriorOnCreate_stillTransitionsToConnected() {
        // QuickFIX/J may call onLogon before onCreate in some edge cases
        service.onLogon(SID_A);
        assertEquals("CONNECTED", service.getStatus(SID_A.toString()));
    }

    @Test
    void onLogout_withoutPriorOnCreate_isNoOp() {
        assertDoesNotThrow(() -> service.onLogout(SID_A));
    }

    // ── connect / disconnect ──────────────────────────────────────────────────

    @Test
    void connect_knownSession_delegatesToFacade() {
        sessionIDs.put(SID_A.toString(), SID_A);
        service.connect(SID_A.toString());
        verify(facade).logon(SID_A);
    }

    @Test
    void connect_unknownSession_isNoOp() {
        service.connect("GHOST:SESSION");
        verifyNoInteractions(facade);
    }

    @Test
    void disconnect_knownSession_delegatesToFacade() {
        sessionIDs.put(SID_A.toString(), SID_A);
        service.disconnect(SID_A.toString());
        verify(facade).logout(SID_A);
    }

    @Test
    void disconnect_unknownSession_isNoOp() {
        service.disconnect("GHOST:SESSION");
        verifyNoInteractions(facade);
    }

    // ── sequence number reads ────────────────────────────────────────────────

    @Test
    void getTxSequence_knownSession_delegatesToFacade() {
        sessionIDs.put(SID_A.toString(), SID_A);
        when(facade.getExpectedSenderNum(SID_A)).thenReturn(42);

        assertEquals(42, service.getTxSequence(SID_A.toString()));
        verify(facade).getExpectedSenderNum(SID_A);
    }

    @Test
    void getRxSequence_knownSession_delegatesToFacade() {
        sessionIDs.put(SID_A.toString(), SID_A);
        when(facade.getExpectedTargetNum(SID_A)).thenReturn(38);

        assertEquals(38, service.getRxSequence(SID_A.toString()));
        verify(facade).getExpectedTargetNum(SID_A);
    }

    @Test
    void getTxSequence_unknownSession_returnsZero() {
        assertEquals(0, service.getTxSequence("GHOST:SESSION"));
        verifyNoInteractions(facade);
    }

    @Test
    void getRxSequence_unknownSession_returnsZero() {
        assertEquals(0, service.getRxSequence("GHOST:SESSION"));
        verifyNoInteractions(facade);
    }

    // ── resetSequence ────────────────────────────────────────────────────────

    @Test
    void resetSequence_knownSession_delegatesToFacade() throws Exception {
        sessionIDs.put(SID_A.toString(), SID_A);
        service.resetSequence(SID_A.toString());
        verify(facade).reset(SID_A);
    }

    @Test
    void resetSequence_unknownSession_isNoOp() {
        assertDoesNotThrow(() -> service.resetSequence("GHOST:SESSION"));
        verifyNoInteractions(facade);
    }

    @Test
    void resetSequence_facadeThrows_wrapsInRuntimeException() throws Exception {
        sessionIDs.put(SID_A.toString(), SID_A);
        doThrow(new Exception("IO error")).when(facade).reset(SID_A);

        assertThrows(RuntimeException.class, () -> service.resetSequence(SID_A.toString()));
    }

    // ── enabledSessions tracking ──────────────────────────────────────────────

    @Nested
    class SessionActivityState {

        @Test
        void unknownSession_isIdle() {
            assertEquals(SessionActivity.IDLE, service.getSessionActivity("nope"));
        }

        @Test
        void configuredButNeverStarted_isIdle() {
            service.onSessionCreated(SID_A);
            assertEquals(SessionActivity.IDLE, service.getSessionActivity(SID_A.toString()),
                    "a session the user never started offers Connect/Listen");
        }

        @Test
        void startedButNoCounterpartyYet_isPending() {
            service.onSessionCreated(SID_A);
            service.connect(SID_A.toString());
            assertEquals(SessionActivity.PENDING, service.getSessionActivity(SID_A.toString()),
                    "dialling out, or bound and waiting — the control must offer to stop it");
        }

        @Test
        void loggedOn_isEstablished() {
            service.onSessionCreated(SID_A);
            service.connect(SID_A.toString());
            service.onLogon(SID_A);
            assertEquals(SessionActivity.ESTABLISHED, service.getSessionActivity(SID_A.toString()));
        }

        @Test
        void stoppingWhilePending_returnsToIdle() {
            service.onSessionCreated(SID_A);
            service.connect(SID_A.toString());
            service.disconnect(SID_A.toString());
            assertEquals(SessionActivity.IDLE, service.getSessionActivity(SID_A.toString()),
                    "pressing Stop must return the control to Connect/Listen");
        }

        @Test
        void droppedAfterBeingEstablished_isIdleNotPending() {
            service.onSessionCreated(SID_A);
            service.connect(SID_A.toString());
            service.onLogon(SID_A);
            service.disconnect(SID_A.toString());
            service.onLogout(SID_A);
            assertEquals(SessionActivity.IDLE, service.getSessionActivity(SID_A.toString()));
        }

        @Test
        void counterpartyDropsButSessionStillEnabled_isPendingAgain() {
            service.onSessionCreated(SID_A);
            service.connect(SID_A.toString());
            service.onLogon(SID_A);
            service.onLogout(SID_A);   // peer went away; the user never pressed Stop
            assertEquals(SessionActivity.PENDING, service.getSessionActivity(SID_A.toString()),
                    "still retrying/listening, so the control must still offer Stop");
        }

        @Test
        void activityIsPerSession() {
            service.onSessionCreated(SID_A);
            service.onSessionCreated(SID_B);
            service.connect(SID_A.toString());
            assertEquals(SessionActivity.PENDING, service.getSessionActivity(SID_A.toString()));
            assertEquals(SessionActivity.IDLE,    service.getSessionActivity(SID_B.toString()));
        }
    }

    @Nested
    class EnabledSessionsTracking {

        @Test
        void initiallyEmpty() {
            assertTrue(service.getEnabledSessionIds().isEmpty());
        }

        @Test
        void connect_addsSessionToEnabledSet() {
            sessionIDs.put(SID_A.toString(), SID_A);
            service.connect(SID_A.toString());
            assertTrue(service.getEnabledSessionIds().contains(SID_A.toString()));
        }

        @Test
        void disconnect_removesSessionFromEnabledSet() {
            sessionIDs.put(SID_A.toString(), SID_A);
            service.connect(SID_A.toString());
            service.disconnect(SID_A.toString());
            assertFalse(service.getEnabledSessionIds().contains(SID_A.toString()));
        }

        @Test
        void connect_multipleSessionsAllTracked() {
            sessionIDs.put(SID_A.toString(), SID_A);
            sessionIDs.put(SID_B.toString(), SID_B);
            service.connect(SID_A.toString());
            service.connect(SID_B.toString());
            Set<String> enabled = service.getEnabledSessionIds();
            assertTrue(enabled.contains(SID_A.toString()));
            assertTrue(enabled.contains(SID_B.toString()));
        }

        @Test
        void disconnect_removesOnlyThatSession_othersUnaffected() {
            sessionIDs.put(SID_A.toString(), SID_A);
            sessionIDs.put(SID_B.toString(), SID_B);
            service.connect(SID_A.toString());
            service.connect(SID_B.toString());

            service.disconnect(SID_A.toString());

            assertFalse(service.getEnabledSessionIds().contains(SID_A.toString()),
                    "disconnected session must be removed from enabled set");
            assertTrue(service.getEnabledSessionIds().contains(SID_B.toString()),
                    "other session must remain in enabled set");
        }

        @Test
        void getEnabledSessionIds_returnsUnmodifiableView() {
            service.connect(SID_A.toString());
            Set<String> enabled = service.getEnabledSessionIds();
            assertThrows(UnsupportedOperationException.class, () -> enabled.add("NEW"));
        }
    }

    // ── addSession ────────────────────────────────────────────────────────────

    @Nested
    class AddSession {

        @Test
        void delegatesToSessionAdder() {
            List<NewSessionRequest> captured = new ArrayList<>();
            GatewayConnectionService svc = serviceWithDelegates(captured::add, (sid, req) -> {}, sid -> {});

            NewSessionRequest req = initiatorRequest();
            svc.addSession(req);

            assertEquals(1, captured.size());
            assertSame(req, captured.get(0));
        }

        @Test
        void testConstructor_withoutAdder_throwsUnsupported() {
            // 3-arg constructor wires UnsupportedOperationException for delegation methods
            assertThrows(UnsupportedOperationException.class, () -> service.addSession(initiatorRequest()));
        }
    }

    // ── updateSession ─────────────────────────────────────────────────────────

    @Nested
    class UpdateSession {

        @Test
        void purgesOldSessionStatesBeforeDelegating() {
            List<String> updatedIds = new ArrayList<>();
            GatewayConnectionService svc = serviceWithDelegates(
                    req -> {},
                    (oldSid, req) -> updatedIds.add(oldSid),
                    sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            svc.onSessionCreated(SID_A);

            svc.updateSession(SID_A.toString(), initiatorRequest());

            assertFalse(svc.listSessionIds().contains(SID_A.toString()),
                    "old session state must be removed from states map");
            assertFalse(sessionIDs.containsKey(SID_A.toString()),
                    "old session ID must be removed from sessionIDs map");
        }

        @Test
        void removesOldSessionFromEnabledSessionsBeforeDelegating() {
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            svc.onSessionCreated(SID_A);
            svc.connect(SID_A.toString());    // adds SID_A to enabledSessions

            svc.updateSession(SID_A.toString(), initiatorRequest());

            assertFalse(svc.getEnabledSessionIds().contains(SID_A.toString()),
                    "old session must be removed from enabledSessions so it is not auto-reconnected");
        }

        @Test
        void doesNotRemoveOtherSessionsFromEnabledSessions() {
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            sessionIDs.put(SID_B.toString(), SID_B);
            svc.onSessionCreated(SID_A);
            svc.onSessionCreated(SID_B);
            svc.connect(SID_A.toString());    // A enabled
            svc.connect(SID_B.toString());    // B enabled

            svc.updateSession(SID_A.toString(), initiatorRequest());   // edit A

            assertTrue(svc.getEnabledSessionIds().contains(SID_B.toString()),
                    "session B must remain in enabledSessions — editing A must not affect B");
        }

        @Test
        void delegatesToSessionUpdater() {
            List<String> capturedOldId = new ArrayList<>();
            List<NewSessionRequest> capturedReq = new ArrayList<>();
            GatewayConnectionService svc = serviceWithDelegates(
                    req -> {},
                    (oldSid, req) -> { capturedOldId.add(oldSid); capturedReq.add(req); },
                    sid -> {});

            NewSessionRequest req = initiatorRequest();
            svc.updateSession(SID_A.toString(), req);

            assertEquals(1, capturedOldId.size());
            assertEquals(SID_A.toString(), capturedOldId.get(0));
            assertSame(req, capturedReq.get(0));
        }
    }

    // ── deleteSession ─────────────────────────────────────────────────────────

    @Nested
    class DeleteSession {

        @Test
        void whenConnected_disconnectsBeforeDelegatingToDeleter() {
            List<String> deletedIds = new ArrayList<>();
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, deletedIds::add);

            sessionIDs.put(SID_A.toString(), SID_A);
            svc.onSessionCreated(SID_A);
            svc.onLogon(SID_A);   // make CONNECTED

            svc.deleteSession(SID_A.toString());

            verify(facade).logout(SID_A);          // disconnect happened
            assertEquals(List.of(SID_A.toString()), deletedIds);  // deleter was called
        }

        @Test
        void whenNotConnected_doesNotCallLogout() {
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            svc.onSessionCreated(SID_A);   // CREATED — not CONNECTED

            svc.deleteSession(SID_A.toString());

            verify(facade, never()).logout(any());
        }

        @Test
        void purgesStateAndRemovesFromEnabledSessions() {
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            svc.onSessionCreated(SID_A);
            svc.connect(SID_A.toString());   // adds to enabledSessions

            svc.deleteSession(SID_A.toString());

            assertFalse(svc.listSessionIds().contains(SID_A.toString()),
                    "deleted session must be removed from states map");
            assertFalse(sessionIDs.containsKey(SID_A.toString()),
                    "deleted session must be removed from sessionIDs map");
            assertFalse(svc.getEnabledSessionIds().contains(SID_A.toString()),
                    "deleted session must be removed from enabledSessions");
        }

        @Test
        void doesNotPurgeOtherSessions() {
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, sid -> {});

            sessionIDs.put(SID_A.toString(), SID_A);
            sessionIDs.put(SID_B.toString(), SID_B);
            svc.onSessionCreated(SID_A);
            svc.onSessionCreated(SID_B);
            svc.connect(SID_B.toString());   // B enabled

            svc.deleteSession(SID_A.toString());   // delete A

            assertTrue(svc.listSessionIds().contains(SID_B.toString()),
                    "session B state must not be removed when A is deleted");
            assertTrue(svc.getEnabledSessionIds().contains(SID_B.toString()),
                    "session B must remain enabled when A is deleted");
        }

        @Test
        void delegatesToSessionDeleter() {
            List<String> deletedIds = new ArrayList<>();
            GatewayConnectionService svc = serviceWithDelegates(req -> {}, (sid, req) -> {}, deletedIds::add);

            svc.deleteSession(SID_A.toString());

            assertEquals(List.of(SID_A.toString()), deletedIds);
        }
    }

    // ── sendRaw SOH injection prevention ─────────────────────────────────────

    @Nested
    class SendRaw {

        @Test
        void sendRaw_normalMessage_sendsFixMessage() throws Exception {
            sessionIDs.put(SID_A.toString(), SID_A);
            // 35=D|55=AAPL|38=100 — pipe is the delimiter
            service.sendRaw(SID_A.toString(), "35=D|55=AAPL|38=100", "|");
            verify(facade).sendToTarget(any(Message.class), eq(SID_A));
        }

        @Test
        void sendRaw_valueContainingSoh_strippedBeforeWrite() throws Exception {
            sessionIDs.put(SID_A.toString(), SID_A);
            // Attempt injection: symbol value contains embedded SOH + extra tag
            // "55=AAPL\x0149=INJECTED" should write tag 55 = "AAPL" (SOH stripped),
            // not create a second tag 49 = INJECTED
            org.mockito.ArgumentCaptor<Message> captor =
                    org.mockito.ArgumentCaptor.forClass(Message.class);

            service.sendRaw(SID_A.toString(), "35=D|55=AAPL\u000149=INJECTED", "|");

            verify(facade).sendToTarget(captor.capture(), eq(SID_A));
            Message sent = captor.getValue();

            // Tag 55 must be set — its value must not contain SOH
            String symbolValue = sent.getString(55);
            assertFalse(symbolValue.contains("\u0001"),
                    "Field value must not contain SOH after sanitization");
            // Tag 49 (SenderCompID) is engine-owned and must NOT be written to body by sendRaw
            assertFalse(sent.isSetField(49),
                    "Injected engine-owned tag must not appear in message body");
        }

        @Test
        void sendRaw_missingMsgType_throwsIllegalArgument() {
            sessionIDs.put(SID_A.toString(), SID_A);
            assertThrows(IllegalArgumentException.class,
                    () -> service.sendRaw(SID_A.toString(), "55=AAPL|38=100", "|"));
        }

        @Test
        void sendRaw_unknownSession_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.sendRaw("GHOST:SESSION", "35=D|55=AAPL", "|"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a service wired with the given lambdas.  The lambdas are plain
     * implementations (not Mockito mocks) to avoid generic type-erasure issues.
     */
    private GatewayConnectionService serviceWithDelegates(
            java.util.function.Consumer<NewSessionRequest>          adder,
            java.util.function.BiConsumer<String, NewSessionRequest> updater,
            java.util.function.Consumer<String>                      deleter) {
        // The connector starter is a no-op here: these tests exercise delegation,
        // not the FIX engine.
        return new GatewayConnectionService(sessionIDs, facade, null, adder, updater, deleter,
                sessionId -> { });
    }

    private static NewSessionRequest initiatorRequest() {
        return new NewSessionRequest(
                "Initiator", "FIX.4.4", "FIX.4.4",
                "SENDER", "TARGET", "localhost", 9876, 30, true);
    }
}
