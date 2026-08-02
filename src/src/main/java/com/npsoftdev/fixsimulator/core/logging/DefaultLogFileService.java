package com.npsoftdev.fixsimulator.core.logging;

import com.npsoftdev.fixsimulator.core.logging.LogFileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads log lines directly from the Logback-managed rolling log file.
 *
 * <p>The log file path must match what is configured in {@code logback.xml}
 * (resolved identically from the {@code app.log.dir} system property).</p>
 */
public class DefaultLogFileService implements LogFileService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLogFileService.class);

    /** Maximum bytes read per {@link #readFrom} call — prevents OOM on log bursts. */
    private static final long MAX_READ_BYTES = 5 * 1024 * 1024; // 5 MB

    private final Path logFile;

    public DefaultLogFileService(Path logFile) {
        this.logFile = logFile;
    }

    @Override
    public Path getActiveLogFile() {
        return logFile;
    }

    @Override
    public long fileSizeBytes() {
        try {
            return Files.exists(logFile) ? Files.size(logFile) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public List<String> readTail(int maxLines) {
        if (!Files.exists(logFile)) return List.of();
        try {
            List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            if (all.size() <= maxLines) return all;
            return new ArrayList<>(all.subList(all.size() - maxLines, all.size()));
        } catch (IOException e) {
            log.warn("Failed to read log file tail from {}: {}", logFile, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> readFrom(long byteOffset) {
        if (!Files.exists(logFile)) return List.of();

        long size = fileSizeBytes();
        // If the file shrank (rotation), start from the beginning
        long start = (byteOffset <= size) ? Math.max(0, byteOffset) : 0L;
        if (start >= size) return List.of(); // nothing new

        long length = Math.min(size - start, MAX_READ_BYTES);

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(start);
            byte[] buf = new byte[(int) length];
            int read = raf.read(buf);
            if (read <= 0) return List.of();

            return decodeIgnoringPartialTail(buf, read).lines().collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to read log file from offset {}: {}", byteOffset, e.getMessage());
            return List.of();
        }
    }

    /**
     * Decodes {@code length} bytes of {@code buf} as UTF-8, discarding an incomplete
     * multi-byte sequence at the end.
     *
     * <p>The read window is cut at an arbitrary byte position — by {@link #MAX_READ_BYTES},
     * or by the writer being mid-line at EOF — so its last character may be truncated.
     * Decoding those bytes as if they were complete would emit U+FFFD. Passing
     * {@code endOfInput = false} makes the decoder stop before a partial trailing
     * sequence and leave it unconsumed instead; the bytes are picked up on the next
     * call, once the rest of the character has been written.</p>
     *
     * <p>Genuinely malformed bytes in the middle of the window are still replaced,
     * so a corrupt log never costs us the readable lines around it.</p>
     */
    private static String decodeIgnoringPartialTail(byte[] buf, int length) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        ByteBuffer in = ByteBuffer.wrap(buf, 0, length);
        // UTF-8 never yields more chars than bytes, so this is always large enough.
        CharBuffer out = CharBuffer.allocate(length);

        decoder.decode(in, out, false);
        out.flip();
        return out.toString();
    }
}
