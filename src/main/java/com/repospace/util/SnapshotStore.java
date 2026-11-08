package com.repospace.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SnapshotStore {

    private SnapshotStore() {
    }

    public static Map<String, String> snapshot(Path source, Path objectsDir) throws IOException {
        Files.createDirectories(objectsDir);
        Map<String, String> manifest = new TreeMap<>();
        for (Path file : FileUtil.listFilesRecursive(source)) {
            String relative = source.relativize(file).toString().replace('\\', '/');
            String hash = FileUtil.sha256(file);
            Path blob = objectsDir.resolve(hash);
            if (!Files.exists(blob)) {
                Files.copy(file, blob);
            }
            manifest.put(relative, hash);
        }
        return manifest;
    }

    public static List<String> diff(Map<String, String> oldManifest, Map<String, String> newManifest) {
        List<String> changes = new ArrayList<>();
        Map<String, String> sortedNew = new TreeMap<>(newManifest);
        for (Map.Entry<String, String> entry : sortedNew.entrySet()) {
            String before = oldManifest.get(entry.getKey());
            if (before == null) {
                changes.add("ADDED    " + entry.getKey());
            } else if (!before.equals(entry.getValue())) {
                changes.add("MODIFIED " + entry.getKey());
            }
        }
        for (String path : new TreeMap<>(oldManifest).keySet()) {
            if (!newManifest.containsKey(path)) {
                changes.add("DELETED  " + path);
            }
        }
        return changes;
    }

    public static int restore(Map<String, String> manifest, Path objectsDir, Path target) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        Files.createDirectories(base);
        int count = 0;
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            String hash = entry.getValue();
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IOException("Corrupt snapshot entry for " + entry.getKey());
            }
            Path destination = base.resolve(entry.getKey()).normalize();
            if (!destination.startsWith(base)) {
                throw new IOException("Unsafe path in snapshot: " + entry.getKey());
            }
            Path blob = objectsDir.resolve(hash);
            if (!Files.exists(blob)) {
                throw new IOException("Stored copy of " + entry.getKey() + " is missing");
            }
            Files.createDirectories(destination.getParent());
            Files.copy(blob, destination, StandardCopyOption.REPLACE_EXISTING);
            count++;
        }
        return count;
    }

    public static int exportZip(Map<String, String> manifest, Path objectsDir, Path zipFile) throws IOException {
        Path target = zipFile.toAbsolutePath().normalize();
        if (Files.isDirectory(target)) {
            throw new IOException("Please give the ZIP a file name (for example project.zip), not a folder.");
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "repospace-", ".part");
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            for (Map.Entry<String, String> entry : manifest.entrySet()) {
                String name = entry.getKey();
                String hash = entry.getValue();
                if (!hash.matches("[0-9a-f]{64}")) {
                    throw new IOException("Corrupt snapshot entry for " + name);
                }
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || List.of(name.split("/")).contains("..")) {
                    throw new IOException("Unsafe path in snapshot: " + name);
                }
                Path blob = objectsDir.resolve(hash);
                if (!Files.exists(blob)) {
                    throw new IOException("Stored copy of " + name + " is missing");
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(blob, zip);
                zip.closeEntry();
                count++;
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return count;
    }
}
