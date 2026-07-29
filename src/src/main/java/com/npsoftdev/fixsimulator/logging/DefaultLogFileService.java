package com.npsoftdev.fixsimulator.logging;

import com.npsoftdev.fixsimulator.service.LogFileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
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

            String content = new String(buf, 0, read, StandardCharsets.UTF_8);
            return content.lines().collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to read log file from offset {}: {}", byteOffset, e.getMessage());
            return List.of();
        }
    }
}
