package com.npsoftdev.fixsimulator.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlPersistenceServiceTest {

    @TempDir
    Path tempDir;

    private YamlPersistenceService yaml;

    @BeforeEach
    void setUp() {
        yaml = new YamlPersistenceService(tempDir);
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Test
    void exists_returnsFalseWhenFileMissing() {
        assertFalse(yaml.exists("nonexistent.yaml"));
    }

    @Test
    void exists_returnsTrueAfterSave() throws IOException {
        yaml.save("test.yaml", new SimpleDto("hello"));
        assertTrue(yaml.exists("test.yaml"));
    }

    // ── save / load round-trip ────────────────────────────────────────────────

    @Test
    void saveAndLoad_roundTripPreservesData() throws IOException {
        SimpleDto original = new SimpleDto("world");
        yaml.save("simple.yaml", original);

        SimpleDto loaded = yaml.load("simple.yaml", SimpleDto.class);
        assertEquals("world", loaded.value);
    }

    @Test
    void saveAndLoad_listField_preservedCorrectly() throws IOException {
        ListDto dto = new ListDto(List.of("alpha", "beta", "gamma"));
        yaml.save("list.yaml", dto);

        ListDto loaded = yaml.load("list.yaml", ListDto.class);
        assertEquals(List.of("alpha", "beta", "gamma"), loaded.items);
    }

    @Test
    void save_overwritesPreviousContent() throws IOException {
        yaml.save("update.yaml", new SimpleDto("first"));
        yaml.save("update.yaml", new SimpleDto("second"));

        SimpleDto loaded = yaml.load("update.yaml", SimpleDto.class);
        assertEquals("second", loaded.value);
    }

    // ── Atomic write — .tmp file is cleaned up ────────────────────────────────

    @Test
    void save_noTmpFileLeftBehind() throws IOException {
        yaml.save("atomic.yaml", new SimpleDto("data"));

        // The .tmp sibling must not remain after a successful write
        assertFalse(Files.exists(tempDir.resolve("atomic.yaml.tmp")),
                ".tmp file should be cleaned up after successful atomic write");
    }

    // ── null-value omission (NON_NULL serialization) ──────────────────────────

    @Test
    void save_nullFieldsOmittedFromYaml() throws IOException {
        NullableDto dto = new NullableDto("present", null);
        yaml.save("nullable.yaml", dto);

        String content = Files.readString(tempDir.resolve("nullable.yaml"));
        assertFalse(content.contains("missing"), "null field should not be written to YAML");
        assertTrue(content.contains("present"));
    }

    // ── Unknown-property tolerance ────────────────────────────────────────────

    @Test
    void load_unknownFieldsAreIgnoredGracefully() throws IOException {
        // Write YAML with an extra field not present in SimpleDto
        Files.writeString(tempDir.resolve("extra.yaml"),
                "value: known\nextraField: should-be-ignored\n");

        SimpleDto dto = yaml.load("extra.yaml", SimpleDto.class);
        assertEquals("known", dto.value);
    }

    // ── Subdirectory creation ─────────────────────────────────────────────────

    @Test
    void save_createsParentDirectoriesIfNeeded() throws IOException {
        YamlPersistenceService nested = new YamlPersistenceService(
                tempDir.resolve("sub/deep"));
        nested.save("file.yaml", new SimpleDto("deep"));
        assertTrue(nested.exists("file.yaml"));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    static class SimpleDto {
        public String value;
        public SimpleDto() {}
        public SimpleDto(String value) { this.value = value; }
    }

    static class ListDto {
        public List<String> items;
        public ListDto() {}
        public ListDto(List<String> items) { this.items = items; }
    }

    static class NullableDto {
        public String present;
        public String missing;
        public NullableDto() {}
        public NullableDto(String present, String missing) {
            this.present = present;
            this.missing = missing;
        }
    }
}
