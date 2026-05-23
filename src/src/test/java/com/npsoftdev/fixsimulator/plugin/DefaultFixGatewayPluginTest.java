package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest;
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

import static org.junit.jupiter.api.Assertions.*;

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
