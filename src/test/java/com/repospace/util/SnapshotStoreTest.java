package com.repospace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class SnapshotStoreTest {

    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("repospace-test");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    public void snapshotRecordsEveryFileIncludingSubFolders() throws IOException {
        Path source = tempDir();
        Path objects = tempDir();
        write(source.resolve("a.txt"), "alpha");
        write(source.resolve("sub/b.txt"), "beta");
        write(source.resolve("sub/deeper/c.txt"), "gamma");

        Map<String, String> manifest = SnapshotStore.snapshot(source, objects);

        assertEquals(3, manifest.size());
        assertTrue(manifest.containsKey("a.txt"));
        assertTrue(manifest.containsKey("sub/b.txt"));
        assertTrue(manifest.containsKey("sub/deeper/c.txt"));
    }

    @Test
    public void identicalFilesAreStoredOnlyOnce() throws IOException {
        Path source = tempDir();
        Path objects = tempDir();
        write(source.resolve("one.txt"), "same content");
        write(source.resolve("two.txt"), "same content");

        SnapshotStore.snapshot(source, objects);

        try (var stored = Files.list(objects)) {
            assertEquals(1, stored.count());
        }
    }

    @Test
    public void ignoredFoldersAreSkipped() throws IOException {
        Path source = tempDir();
        write(source.resolve("keep.txt"), "x");
        write(source.resolve(".git/config"), "ignored");

        Map<String, String> manifest = SnapshotStore.snapshot(source, tempDir());

        assertEquals(1, manifest.size());
        assertFalse(manifest.containsKey(".git/config"));
    }

    @Test
    public void diffFindsAddedModifiedAndDeletedFiles() {
        Map<String, String> before = new TreeMap<>();
        before.put("keep.txt", "h1");
        before.put("edit.txt", "h2");
        before.put("gone.txt", "h3");
        Map<String, String> after = new TreeMap<>();
        after.put("keep.txt", "h1");
        after.put("edit.txt", "h2-changed");
        after.put("new.txt", "h4");

        List<String> changes = SnapshotStore.diff(before, after);

        assertEquals(3, changes.size());
        assertTrue(changes.contains("MODIFIED edit.txt"));
        assertTrue(changes.contains("ADDED    new.txt"));
        assertTrue(changes.contains("DELETED  gone.txt"));
    }

    @Test
    public void diffOfIdenticalManifestsIsEmpty() {
        Map<String, String> manifest = new TreeMap<>();
        manifest.put("a.txt", "h1");
        assertTrue(SnapshotStore.diff(manifest, new TreeMap<>(manifest)).isEmpty());
    }

    @Test
    public void restoreRebuildsTheOriginalFiles() throws IOException {
        Path source = tempDir();
        Path objects = tempDir();
        Path target = tempDir();
        write(source.resolve("a.txt"), "alpha");
        write(source.resolve("sub/b.txt"), "beta");
        Map<String, String> manifest = SnapshotStore.snapshot(source, objects);

        int count = SnapshotStore.restore(manifest, objects, target);

        assertEquals(2, count);
        assertEquals("alpha", Files.readString(target.resolve("a.txt")));
        assertEquals("beta", Files.readString(target.resolve("sub/b.txt")));
    }

    @Test
    public void restoreGivesBackTheOldVersionAfterTheFileChanged() throws IOException {
        Path source = tempDir();
        Path objects = tempDir();
        Path target = tempDir();
        write(source.resolve("notes.txt"), "version one");
        Map<String, String> v1 = SnapshotStore.snapshot(source, objects);
        write(source.resolve("notes.txt"), "version two");
        Map<String, String> v2 = SnapshotStore.snapshot(source, objects);

        SnapshotStore.restore(v1, objects, target);

        assertEquals("version one", Files.readString(target.resolve("notes.txt")));
        assertEquals(1, SnapshotStore.diff(v1, v2).size());
    }

    @Test
    public void restoreRefusesPathsThatEscapeTheTargetFolder() throws IOException {
        Path objects = tempDir();
        Path target = tempDir();
        String hash = "a".repeat(64);
        Files.writeString(objects.resolve(hash), "x");
        Map<String, String> evil = new TreeMap<>();
        evil.put("../outside.txt", hash);

        assertThrows(IOException.class, () -> SnapshotStore.restore(evil, objects, target));
    }

    @Test
    public void restoreRefusesCorruptHashes() throws IOException {
        Map<String, String> bad = new TreeMap<>();
        bad.put("a.txt", "../../etc/passwd");
        Path objects = tempDir();
        Path target = tempDir();

        assertThrows(IOException.class, () -> SnapshotStore.restore(bad, objects, target));
    }
}
