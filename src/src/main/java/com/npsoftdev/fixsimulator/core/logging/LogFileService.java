package com.npsoftdev.fixsimulator.core.logging;

import java.nio.file.Path;
import java.util.List;

/**
 * Provides read access to the application's active log file.
 */
public interface LogFileService {

    /** Path to the currently active (writable) log file. May not exist yet on first run. */
    Path getActiveLogFile();

    /** Current size of the active log file in bytes (0 if it does not exist). */
    long fileSizeBytes();

    /**
     * Returns the last {@code maxLines} lines from the log file.
     * Returns all available lines if the file has fewer than {@code maxLines}.
     */
    List<String> readTail(int maxLines);

    /**
     * Returns all lines that start at or after {@code byteOffset} in the log file.
     * If the file is smaller than {@code byteOffset} (e.g. after a rotation), returns all lines.
     * Capped at 5 MB per call to prevent OOM on very fast log bursts.
     */
    List<String> readFrom(long byteOffset);
}
