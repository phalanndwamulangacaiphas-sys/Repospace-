package com.repospace.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class FileUtil {

    private static final Set<String> IGNORED_FOLDERS = Set.of(".git", ".repospace");

    private FileUtil() {
    }

    public static List<Path> listFilesRecursive(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        collect(root, result);
        Collections.sort(result);
        return result;
    }

    private static void collect(Path folder, List<Path> result) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            for (Path child : children) {
                if (Files.isSymbolicLink(child)) {
                    continue;
                }
                if (Files.isDirectory(child)) {
                    if (!IGNORED_FOLDERS.contains(child.getFileName().toString())) {
                        collect(child, result);
                    }
                } else if (Files.isRegularFile(child)) {
                    result.add(child);
                }
            }
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    public static boolean isMissingOrEmptyFolder(Path path) throws IOException {
        if (!Files.exists(path)) {
            return true;
        }
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
            return !children.iterator().hasNext();
        }
    }
}
