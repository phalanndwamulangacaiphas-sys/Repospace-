package com.repospace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class FileUtilTest {

    @Test
    public void sha256MatchesKnownValue() throws IOException {
        Path file = Files.createTempFile("repospace", ".txt");
        Files.writeString(file, "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileUtil.sha256(file));
    }

    @Test
    public void differentContentGivesDifferentHash() throws IOException {
        Path a = Files.createTempFile("repospace", ".txt");
        Path b = Files.createTempFile("repospace", ".txt");
        Files.writeString(a, "one");
        Files.writeString(b, "two");
        assertNotEquals(FileUtil.sha256(a), FileUtil.sha256(b));
    }

    @Test
    public void listFilesRecursiveFindsNestedFilesOnly() throws IOException {
        Path root = Files.createTempDirectory("repospace");
        Files.writeString(root.resolve("top.txt"), "1");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/deep.txt"), "2");
        Files.createDirectories(root.resolve("empty"));

        assertEquals(2, FileUtil.listFilesRecursive(root).size());
    }

    @Test
    public void deleteRecursivelyRemovesTheWholeTree() throws IOException {
        Path root = Files.createTempDirectory("repospace");
        Files.createDirectories(root.resolve("x/y"));
        Files.writeString(root.resolve("x/y/f.txt"), "data");

        FileUtil.deleteRecursively(root);

        assertFalse(Files.exists(root));
    }

    @Test
    public void emptyFolderCheck() throws IOException {
        Path root = Files.createTempDirectory("repospace");
        assertTrue(FileUtil.isMissingOrEmptyFolder(root));
        assertTrue(FileUtil.isMissingOrEmptyFolder(root.resolve("does-not-exist")));
        Files.writeString(root.resolve("f.txt"), "x");
        assertFalse(FileUtil.isMissingOrEmptyFolder(root));
    }
}
