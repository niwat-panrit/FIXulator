package com.npsoftdev.fixsimulator.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.SessionID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        service    = new GatewayConnectionService(sessionIDs, facade);
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
}
