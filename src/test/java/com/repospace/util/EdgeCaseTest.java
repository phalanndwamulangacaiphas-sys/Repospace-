package com.repospace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class EdgeCaseTest {

    private static Path temp() throws IOException {
        return Files.createTempDirectory("repospace-edge");
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    @Test
    public void emptyFolderGivesAnEmptyManifest() throws IOException {
        assertTrue(SnapshotStore.snapshot(temp(), temp()).isEmpty());
    }

    @Test
    public void emptyFileIsTrackedWithTheKnownEmptyHash() throws IOException {
        Path source = temp();
        write(source.resolve("empty.txt"), new byte[0]);

        Map<String, String> manifest = SnapshotStore.snapshot(source, temp());

        assertEquals(1, manifest.size());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.get("empty.txt"));
    }

    @Test
    public void binaryFileSurvivesARoundTripByteForByte() throws IOException {
        Path source = temp();
        Path objects = temp();
        Path target = temp();
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);
        write(source.resolve("data.bin"), data);

        SnapshotStore.restore(SnapshotStore.snapshot(source, objects), objects, target);

        assertEquals(-1L, Files.mismatch(source.resolve("data.bin"), target.resolve("data.bin")));
    }

    @Test
    public void unicodeAndSpacesInFileNamesAreKept() throws IOException {
        Path source = temp();
        Path objects = temp();
        Path target = temp();
        write(source.resolve("café notes.txt"), "espresso".getBytes(StandardCharsets.UTF_8));
        write(source.resolve("my folder/second file.txt"), "two".getBytes(StandardCharsets.UTF_8));

        Map<String, String> manifest = SnapshotStore.snapshot(source, objects);
        SnapshotStore.restore(manifest, objects, target);

        assertTrue(manifest.containsKey("café notes.txt"));
        assertEquals("espresso", Files.readString(target.resolve("café notes.txt")));
        assertEquals("two", Files.readString(target.resolve("my folder/second file.txt")));
    }

    @Test
    public void veryDeepFolderStructureIsHandledByTheRecursion() throws IOException {
        Path source = temp();
        Path deep = source;
        for (int i = 0; i < 40; i++) {
            deep = deep.resolve("level" + i);
        }
        write(deep.resolve("bottom.txt"), "deep".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, FileUtil.listFilesRecursive(source).size());
    }

    @Test
    public void fiveHundredFilesAreAllTrackedAndRestored() throws IOException {
        Path source = temp();
        Path objects = temp();
        Path target = temp();
        for (int i = 0; i < 500; i++) {
            write(source.resolve("folder" + (i % 10) + "/file" + i + ".txt"), ("content " + i).getBytes(StandardCharsets.UTF_8));
        }

        Map<String, String> manifest = SnapshotStore.snapshot(source, objects);

        assertEquals(500, manifest.size());
        assertEquals(500, SnapshotStore.restore(manifest, objects, target));
    }

    @Test
    public void restoringIntoAFolderThatDoesNotExistCreatesIt() throws IOException {
        Path source = temp();
        Path objects = temp();
        write(source.resolve("a.txt"), "x".getBytes(StandardCharsets.UTF_8));
        Path target = temp().resolve("new/nested/folder");

        SnapshotStore.restore(SnapshotStore.snapshot(source, objects), objects, target);

        assertTrue(Files.exists(target.resolve("a.txt")));
    }

    @Test
    public void aMissingStoredCopyIsReportedNotIgnored() throws IOException {
        Map<String, String> manifest = new TreeMap<>();
        manifest.put("lost.txt", "b".repeat(64));
        assertThrows(IOException.class, () -> SnapshotStore.restore(manifest, temp(), temp()));
    }

    @Test
    public void sandboxEdgeCasesFolderRoundTripsExactly() throws IOException {
        Path sandbox = Paths.get("sandbox", "edge-cases");
        assertTrue(Files.isDirectory(sandbox), "run the tests from the project folder");
        Path objects = temp();
        Path target = temp();

        Map<String, String> manifest = SnapshotStore.snapshot(sandbox, objects);
        SnapshotStore.restore(manifest, objects, target);

        assertTrue(manifest.containsKey("empty-file.txt"));
        assertTrue(manifest.containsKey("binary.bin"));
        assertTrue(manifest.containsKey("spaces in name.txt"));
        assertTrue(manifest.containsKey("deep/a/b/c/d/file.txt"));
        assertFalse(manifest.keySet().stream().anyMatch(p -> p.contains("empty-folder")));
        for (String relative : manifest.keySet()) {
            assertEquals(-1L, Files.mismatch(sandbox.resolve(relative), target.resolve(relative)), relative);
        }
    }
}
