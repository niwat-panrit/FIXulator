package com.npsoftdev.fixsimulator.plugins.connection;

import com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest;
import com.npsoftdev.fixsimulator.plugins.connection.api.SessionStartException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import quickfix.ConfigError;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.core.plugin.NavSection;

/**
 * Unit tests for {@link DefaultFixGatewayPlugin} focusing on the two pieces
 * that can be exercised without a running QuickFIX/J engine:
 *
 * <ol>
 *   <li><strong>Per-session settings builder</strong> — verifies that
 *       {@code buildPerSessionSettings(NewSessionRequest)} produces a correctly
 *       structured {@link SessionSettings} for initiator, acceptor, FIX 4.x,
 *       and FIXT 1.1 (FIX 5.x) session configurations.</li>
 *   <li><strong>Config file persistence</strong> — verifies that
 *       {@code persistSettings()} serialises the master {@link SessionSettings}
 *       to the expected {@code .cfg} format so sessions survive a restart.</li>
 * </ol>
 */
class DefaultFixGatewayPluginTest {

    // ── buildPerSessionSettings ───────────────────────────────────────────────

    @Nested
    class BuildPerSessionSettings {

        @Test
        void initiator_containsConnectHostAndPort() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "SENDER", "TARGET", "fix-server.example.com", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("initiator",            s.getString(sid, "ConnectionType"));
            assertEquals("fix-server.example.com", s.getString(sid, "SocketConnectHost"));
            assertEquals("9876",                 s.getString(sid, "SocketConnectPort"));
        }

        @Test
        void acceptor_containsAcceptPortAndNoConnectHost() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Acceptor", "FIX.4.4", "FIX.4.4",
                    "SENDER", "TARGET", "0.0.0.0", 7001, 30, false);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("acceptor", s.getString(sid, "ConnectionType"));
            assertEquals("7001",     s.getString(sid, "SocketAcceptPort"));
            // SocketConnectHost must not be written for acceptor sessions
            assertThrows(ConfigError.class, () -> s.getString(sid, "SocketConnectHost"));
        }

        @Test
        void fix44_omitsDefaultApplVerID() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "S", "T", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertThrows(ConfigError.class, () -> s.getString(sid, "DefaultApplVerID"),
                    "FIX 4.x sessions must not have DefaultApplVerID");
        }

        @Test
        void fixt11_includesDefaultApplVerID_forFix50sp2() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.5.0SP2", "FIXT.1.1",
                    "S", "T", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("FIXT.1.1", sid.getBeginString());
            // 9 = FIX.5.0SP2 ApplVerID as defined in toApplVerID()
            assertEquals("9", s.getString(sid, "DefaultApplVerID"));
        }

        @Test
        void fixt11_includesDefaultApplVerID_forFix50() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.5.0", "FIXT.1.1",
                    "S", "T", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("7", s.getString(sid, "DefaultApplVerID"));
        }

        @Test
        void heartbeatInterval_isWrittenCorrectly() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "S", "T", "localhost", 9876, 45, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("45", s.getString(sid, "HeartBtInt"));
        }

        @Test
        void resetOnLogon_trueIsEncodedAsY() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "S", "T", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            assertEquals("Y", s.getString(sessionId(s), "ResetOnLogon"));
        }

        @Test
        void resetOnLogon_falseIsEncodedAsN() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "S", "T", "localhost", 9876, 30, false);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            assertEquals("N", s.getString(sessionId(s), "ResetOnLogon"));
        }

        @Test
        void compIds_matchRequest() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "MYSENDER", "MYTARGET", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            SessionID sid = sessionId(s);

            assertEquals("MYSENDER", sid.getSenderCompID());
            assertEquals("MYTARGET", sid.getTargetCompID());
            assertEquals("FIX.4.4",  sid.getBeginString());
        }

        @Test
        void defaultSection_containsReconnectInterval() throws ConfigError {
            NewSessionRequest req = new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4",
                    "S", "T", "localhost", 9876, 30, true);

            SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(req);
            // ReconnectInterval is in the DEFAULT section (no session ID needed)
            assertEquals("5", s.getString("ReconnectInterval"));
        }
    }

    // ── persistSettings ───────────────────────────────────────────────────────

    @Nested
    class PersistSettings {

        @TempDir
        Path tempDir;

        @Test
        void writesSessionSectionsToFile() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath,
                    "[SESSION]\nBeginString=FIX.4.4\nSenderCompID=SIM\nTargetCompID=EX\n"
                  + "ConnectionType=initiator\nSocketConnectHost=localhost\nSocketConnectPort=9876\n"
                  + "HeartBtInt=30\nResetOnLogon=Y\n");

            plugin.persistSettings();

            String written = Files.readString(cfgPath);
            assertTrue(written.contains("[SESSION]"),       "file must contain a [SESSION] block");
            assertTrue(written.contains("BeginString=FIX.4.4"), "session BeginString must be written");
            assertTrue(written.contains("SenderCompID=SIM"),    "SenderCompID must be written");
            assertTrue(written.contains("TargetCompID=EX"),     "TargetCompID must be written");
        }

        @Test
        void writesMultipleSessionSections() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath,
                    "[SESSION]\nBeginString=FIX.4.4\nSenderCompID=SIM\nTargetCompID=EX1\n"
                  + "ConnectionType=initiator\nSocketConnectPort=9876\n\n"
                  + "[SESSION]\nBeginString=FIX.4.4\nSenderCompID=SIM\nTargetCompID=EX2\n"
                  + "ConnectionType=initiator\nSocketConnectPort=9877\n");

            plugin.persistSettings();

            String written = Files.readString(cfgPath);
            assertTrue(written.contains("TargetCompID=EX1"), "first session must be persisted");
            assertTrue(written.contains("TargetCompID=EX2"), "second session must be persisted");
        }

        @Test
        void nullConfigFilePath_doesNotThrowAndWritesNothing() throws Exception {
            // Plugin constructed without a file path — persistSettings is a no-op
            DefaultFixGatewayPlugin plugin = pluginWithSessions(null,
                    "[SESSION]\nBeginString=FIX.4.4\nSenderCompID=SIM\nTargetCompID=EX\n"
                  + "ConnectionType=initiator\nSocketConnectPort=9876\n");

            assertDoesNotThrow(plugin::persistSettings);
        }

        @Test
        void fixtSession_persistsDefaultApplVerID() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath,
                    "[SESSION]\nBeginString=FIXT.1.1\nSenderCompID=SIM\nTargetCompID=EX\n"
                  + "ConnectionType=initiator\nSocketConnectPort=9876\nDefaultApplVerID=9\n");

            plugin.persistSettings();

            String written = Files.readString(cfgPath);
            assertTrue(written.contains("DefaultApplVerID=9"),
                    "FIXT session's DefaultApplVerID must be persisted");
        }

        @Test
        void writtenFileParsesBackToEquivalentSettings() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath,
                    "[SESSION]\nBeginString=FIX.4.4\nSenderCompID=SIM\nTargetCompID=EX\n"
                  + "ConnectionType=initiator\nSocketConnectHost=myhost\n"
                  + "SocketConnectPort=9876\nHeartBtInt=30\nResetOnLogon=Y\n");

            plugin.persistSettings();

            // The written file must be parseable by QFJ
            SessionSettings reloaded = new SessionSettings(Files.newInputStream(cfgPath));
            SessionID sid = sessionId(reloaded);
            assertEquals("SIM", sid.getSenderCompID());
            assertEquals("EX",  sid.getTargetCompID());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the first (and typically only) session ID from a {@link SessionSettings}.
     */
    /**
     * An acceptor session must be driven by a {@link quickfix.SocketAcceptor}, not a
     * {@link quickfix.SocketInitiator}.
     *
     * <p>These pin down the bug where every session got a SocketInitiator regardless
     * of its ConnectionType. That failed <em>silently</em>: the initiator constructed
     * without complaint, created no session, never fired {@code onCreate}, and so the
     * acceptor vanished from the connection list while its config sat correctly in
     * {@code fix-gateway.cfg}.</p>
     */
    @Nested
    class ConnectorSelection {

        private SessionSettings acceptorSettings() throws ConfigError {
            return DefaultFixGatewayPlugin.buildPerSessionSettings(new NewSessionRequest(
                    "Acceptor", "FIX.4.4", "FIX.4.4", "SIMULATOR", "CLIENT",
                    "0.0.0.0", freePort(), 30, false));
        }

        /** A port the OS is not currently using, so the acceptor can actually bind. */
        private int freePort() {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new IllegalStateException("could not reserve a free port", e);
            }
        }

        private SessionSettings initiatorSettings() throws ConfigError {
            return DefaultFixGatewayPlugin.buildPerSessionSettings(new NewSessionRequest(
                    "Initiator", "FIX.4.4", "FIX.4.4", "SIMULATOR", "EXCHANGE",
                    "localhost", 9876, 30, false));
        }

        @Test
        void acceptorSession_isDetectedAsAcceptor() throws ConfigError {
            SessionSettings s = acceptorSettings();
            assertTrue(DefaultFixGatewayPlugin.isAcceptor(s, sessionId(s)));
        }

        @Test
        void initiatorSession_isNotDetectedAsAcceptor() throws ConfigError {
            SessionSettings s = initiatorSettings();
            assertFalse(DefaultFixGatewayPlugin.isAcceptor(s, sessionId(s)));
        }

        @Test
        void missingConnectionType_defaultsToInitiator() throws Exception {
            SessionSettings s = new SessionSettings(new ByteArrayInputStream((
                    "[DEFAULT]\nStartTime=00:00:00\nEndTime=00:00:00\n\n"
                  + "[SESSION]\nBeginString=FIX.4.4\n"
                  + "SenderCompID=A\nTargetCompID=B\n")
                    .getBytes(StandardCharsets.UTF_8)));
            assertFalse(DefaultFixGatewayPlugin.isAcceptor(s, sessionId(s)),
                    "an unreadable ConnectionType must not silently produce an acceptor");
        }

        @Test
        void socketInitiator_silentlyCreatesNoSessionForAnAcceptor() throws Exception {
            SessionSettings s = acceptorSettings();
            RecordingApplication app = new RecordingApplication();

            quickfix.Initiator wrong = new quickfix.SocketInitiator(
                    app, new quickfix.MemoryStoreFactory(), s, new quickfix.DefaultMessageFactory());
            try {
                wrong.start();   // even started, it adopts nothing
                assertTrue(wrong.getSessions().isEmpty(),
                        "this is the trap: the wrong connector type does not throw, it just does nothing");
                assertTrue(app.created.isEmpty(),
                        "onCreate never fires, which is why the session vanished from the UI list");
            } finally {
                wrong.stop(true);
            }
        }

        @Test
        void socketAcceptor_createsTheSessionAndFiresOnCreate() throws Exception {
            SessionSettings s = acceptorSettings();
            SessionID expected = sessionId(s);
            RecordingApplication app = new RecordingApplication();

            quickfix.Acceptor acceptor = new quickfix.SocketAcceptor(
                    app, new quickfix.MemoryStoreFactory(), s, new quickfix.DefaultMessageFactory());
            try {
                // An acceptor creates its sessions when it starts and binds, not in
                // the constructor — which is why startSessionConnector() must start
                // the connector before looking the session up.
                acceptor.start();
                assertEquals(List.of(expected), acceptor.getSessions());
                assertTrue(app.created.contains(expected),
                        "onCreate must fire so the session reaches the connection list");
            } finally {
                acceptor.stop(true);
            }
        }
    }

    /**
     * A busy acceptor port must be a reportable configuration problem, not an
     * internal error, and must not cost the user the session they just typed in.
     */
    @Nested
    class PortInUse {

        @TempDir
        Path tempDir;

        @Test
        void bindFailureIsRecognisedAsAPortConflict() throws Exception {
            try (java.net.ServerSocket hog = new java.net.ServerSocket(0)) {
                SessionSettings s = DefaultFixGatewayPlugin.buildPerSessionSettings(
                        new NewSessionRequest("Acceptor", "FIX.4.4", "FIX.4.4",
                                "SIM", "CLI", "0.0.0.0", hog.getLocalPort(), 30, false));
                quickfix.Acceptor acceptor = new quickfix.SocketAcceptor(
                        new RecordingApplication(), new quickfix.MemoryStoreFactory(), s,
                        new quickfix.DefaultMessageFactory());
                Exception thrown = assertThrows(Exception.class, acceptor::start,
                        "binding a port already held must fail");
                assertTrue(SessionStartException.isAddressInUse(thrown),
                        "the BindException is wrapped several layers deep and must still be found");
                try { acceptor.stop(true); } catch (Exception ignored) { }
            }
        }

        @Test
        void unrelatedFailureIsNotReportedAsAPortConflict() {
            assertFalse(SessionStartException.isAddressInUse(
                    new quickfix.ConfigError("Missing SenderCompID")),
                    "only bind failures may be described to the user as a port conflict");
        }

        @Test
        void selfReferencingCauseDoesNotHang() {
            RuntimeException loop = new RuntimeException("boom") {
                @Override public synchronized Throwable getCause() { return this; }
            };
            assertFalse(SessionStartException.isAddressInUse(loop));
        }

        @Test
        void configurationSurvivesAFailedStart() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            try (java.net.ServerSocket hog = new java.net.ServerSocket(0)) {
                int busyPort = hog.getLocalPort();
                DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath, "");

                SessionStartException e = assertThrows(SessionStartException.class, () ->
                        plugin.getConnectionService().addSession(new NewSessionRequest(
                                "Acceptor", "FIX.4.4", "FIX.4.4", "SIM", "CLI",
                                "0.0.0.0", busyPort, 30, false)));

                assertTrue(e.isPortInUse(), "expected a port-conflict diagnosis, got: " + e.getMessage());
                assertTrue(e.getMessage().contains(String.valueOf(busyPort)),
                        "the message must name the port so the user knows what to free");

                // The whole point: the session is still configured, so the user can
                // free the port and press Listen instead of retyping it.
                assertTrue(Files.exists(cfgPath), "config must be written before the start is attempted");
                String cfg = Files.readString(cfgPath);
                assertTrue(cfg.contains("SocketAcceptPort=" + busyPort),
                        "the failed session must still be persisted:\n" + cfg);
            }
        }

        @Test
        void startSucceedsOnceThePortIsFree() throws Exception {
            Path cfgPath = tempDir.resolve("fix-gateway.cfg");
            int port;
            DefaultFixGatewayPlugin plugin = pluginWithSessions(cfgPath, "");
            try (java.net.ServerSocket hog = new java.net.ServerSocket(0)) {
                port = hog.getLocalPort();
                assertThrows(SessionStartException.class, () ->
                        plugin.getConnectionService().addSession(new NewSessionRequest(
                                "Acceptor", "FIX.4.4", "FIX.4.4", "SIM", "CLI",
                                "0.0.0.0", port, 30, false)));
            }   // port released here — as if the user shut down whatever held it

            // Retry, exactly as the Listen button does.
            plugin.getConnectionService().connect("FIX.4.4:SIM->CLI");
            assertTrue(plugin.getConnectionService().listSessionIds().contains("FIX.4.4:SIM->CLI"),
                    "after a successful retry the session must be live");
            plugin.stopAllConnectors();
        }
    }

    /** Records onCreate callbacks; every other callback is a no-op. */
    private static class RecordingApplication extends quickfix.ApplicationAdapter {
        final List<SessionID> created = new java.util.ArrayList<>();
        @Override public void onCreate(SessionID sessionId) { created.add(sessionId); }
    }

    private static SessionID sessionId(SessionSettings s) {
        return s.sectionIterator().next();
    }

    /**
     * Builds a {@link DefaultFixGatewayPlugin} whose master {@link SessionSettings}
     * is loaded from the given INI-style session string.  The plugin is NOT
     * initialised (no QFJ engine started) — only the settings and file path are set.
     */
    private static DefaultFixGatewayPlugin pluginWithSessions(Path cfgPath, String sessionCfg)
            throws ConfigError, IOException {
        String full = "[DEFAULT]\nConnectionType=initiator\nReconnectInterval=5\n"
                    + "StartTime=00:00:00\nEndTime=00:00:00\nHeartBtInt=30\n"
                    + "UseDataDictionary=N\nResetOnLogon=Y\nCheckLatency=N\n\n"
                    + sessionCfg;

        SessionSettings settings = new SessionSettings(
                new ByteArrayInputStream(full.getBytes(StandardCharsets.UTF_8)));

        return new DefaultFixGatewayPlugin(
                "test", "Test", "bi-test",
                NavSection.ADMIN, null,
                settings, cfgPath);
    }
}
