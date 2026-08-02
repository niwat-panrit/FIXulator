package com.npsoftdev.fixsimulator.core.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultLogFileServiceTest {

    /** Mirrors DefaultLogFileService.MAX_READ_BYTES. */
    private static final long MAX_READ_BYTES = 5 * 1024 * 1024;

    @TempDir
    Path tempDir;

    private Path logFile;
    private DefaultLogFileService service;

    @BeforeEach
    void setUp() {
        logFile = tempDir.resolve("app.log");
        service = new DefaultLogFileService(logFile);
    }

    /** Writes the given lines, each terminated by \n, replacing any existing file. */
    private void writeLines(String... lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append('\n');
        Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8);
    }

    // ── getActiveLogFile ──────────────────────────────────────────────────────

    @Test
    void getActiveLogFile_returnsConfiguredPathEvenWhenMissing() {
        assertEquals(logFile, service.getActiveLogFile());
        assertFalse(Files.exists(logFile), "precondition: file does not exist yet");
    }

    // ── fileSizeBytes ─────────────────────────────────────────────────────────

    @Test
    void fileSizeBytes_returnsZeroWhenFileMissing() {
        assertEquals(0L, service.fileSizeBytes());
    }

    @Test
    void fileSizeBytes_returnsActualSize() throws IOException {
        writeLines("abc");                       // "abc\n" = 4 bytes
        assertEquals(4L, service.fileSizeBytes());
    }

    // ── readTail ──────────────────────────────────────────────────────────────

    @Test
    void readTail_returnsEmptyWhenFileMissing() {
        assertEquals(List.of(), service.readTail(10));
    }

    @Test
    void readTail_returnsAllLinesWhenFileShorterThanMax() throws IOException {
        writeLines("one", "two");
        assertEquals(List.of("one", "two"), service.readTail(10));
    }

    @Test
    void readTail_returnsOnlyLastNLines() throws IOException {
        writeLines("one", "two", "three", "four");
        assertEquals(List.of("three", "four"), service.readTail(2));
    }

    @Test
    void readTail_zeroLinesReturnsEmpty() throws IOException {
        writeLines("one", "two");
        assertEquals(List.of(), service.readTail(0));
    }

    @Test
    void readTail_readsFinalLineWithoutTrailingNewline() throws IOException {
        Files.writeString(logFile, "one\ntwo", StandardCharsets.UTF_8);
        assertEquals(List.of("one", "two"), service.readTail(10));
    }

    // ── readFrom ──────────────────────────────────────────────────────────────

    @Test
    void readFrom_returnsEmptyWhenFileMissing() {
        assertEquals(List.of(), service.readFrom(0));
    }

    @Test
    void readFrom_zeroOffsetReturnsWholeFile() throws IOException {
        writeLines("one", "two");
        assertEquals(List.of("one", "two"), service.readFrom(0));
    }

    @Test
    void readFrom_midFileOffsetReturnsOnlyNewLines() throws IOException {
        writeLines("one", "two");                // "one\n" = 4 bytes
        assertEquals(List.of("two"), service.readFrom(4));
    }

    @Test
    void readFrom_offsetEqualToSizeReturnsEmpty() throws IOException {
        writeLines("one");
        assertEquals(List.of(), service.readFrom(service.fileSizeBytes()),
                "offset at EOF means nothing new has been appended");
    }

    @Test
    void readFrom_offsetPastEndRereadsFromStartAfterRotation() throws IOException {
        // Caller's offset is from before rotation; the file is now smaller.
        writeLines("fresh line");
        assertEquals(List.of("fresh line"), service.readFrom(9_999),
                "a shrunk file means rotation — restart from the beginning");
    }

    @Test
    void readFrom_negativeOffsetIsClampedToStart() throws IOException {
        writeLines("one", "two");
        assertEquals(List.of("one", "two"), service.readFrom(-50));
    }

    @Test
    void readFrom_emptyFileReturnsEmpty() throws IOException {
        Files.writeString(logFile, "", StandardCharsets.UTF_8);
        assertEquals(List.of(), service.readFrom(0));
    }

    @Test
    void readFrom_midLineOffsetReturnsPartialFirstLine() throws IOException {
        writeLines("one", "two");
        assertEquals(List.of("ne", "two"), service.readFrom(1),
                "offset is a byte position, not a line boundary");
    }

    // ── readFrom — 5 MB cap ───────────────────────────────────────────────────

    @Test
    void readFrom_capsReadAtMaxReadBytes() throws IOException {
        // 1023 chars + '\n' = exactly 1024 bytes per line; 6144 lines = 6 MB.
        String line = "x".repeat(1023);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 6144; i++) lines.add(line);
        Files.writeString(logFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);

        assertEquals(6L * 1024 * 1024, service.fileSizeBytes(), "precondition: 6 MB file");

        List<String> read = service.readFrom(0);
        // 5 MB / 1024 bytes per line = 5120 whole lines, no partial tail.
        assertEquals(5120, read.size(),
                "read must stop at the 5 MB cap rather than returning the whole file");
        assertTrue(read.size() * 1024L <= MAX_READ_BYTES);
    }

    // ── readFrom — UTF-8 boundary safety ──────────────────────────────────────

    @Test
    void readFrom_capSplittingMultiByteCharDoesNotEmitReplacementChar() throws IOException {
        // Lay out the file so the 5 MB cut lands *inside* a 2-byte character:
        // MAX-1 ASCII bytes, then 'é' (0xC3 0xA9) straddling the boundary.
        String filler = "a".repeat((int) MAX_READ_BYTES - 1);
        Files.writeString(logFile, filler + "é\nrest of the log\n", StandardCharsets.UTF_8);

        List<String> read = service.readFrom(0);

        assertFalse(String.join("", read).contains("�"),
                "a character split by the cap must be left for the next read, not mangled");
        assertEquals(1, read.size());
        assertEquals(filler, read.get(0), "the truncated 'é' is dropped, the ASCII run is intact");
    }

    @Test
    void readFrom_multiByteCharsWithinWindowSurviveIntact() throws IOException {
        writeLines("ราคา 100", "naïve café", "日本語ログ");
        assertEquals(List.of("ราคา 100", "naïve café", "日本語ログ"), service.readFrom(0));
    }

    @Test
    void readFrom_partialTrailingCharIsReturnedOnceCompleted() throws IOException {
        // Simulate a writer caught mid-character: only the first byte of 'é' is on disk.
        Files.write(logFile, new byte[] { 'o', 'k', '\n', (byte) 0xC3 });
        assertEquals(List.of("ok"), service.readFrom(0),
                "an incomplete trailing char is withheld rather than mangled");

        // The writer finishes the character and the line.
        Files.write(logFile, new byte[] { (byte) 0xA9, '\n' },
                java.nio.file.StandardOpenOption.APPEND);
        assertEquals(List.of("é"), service.readFrom(3),
                "the withheld byte is picked up on the next read");
    }

    @Test
    void readFrom_capAppliesRelativeToOffsetNotFileStart() throws IOException {
        String line = "y".repeat(1023);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 6144; i++) lines.add(line);
        Files.writeString(logFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);

        // Start 2 MB in: 4 MB remains, which is under the cap, so all of it is returned.
        long offset = 2L * 1024 * 1024;
        assertEquals(4096, service.readFrom(offset).size());
    }
}
