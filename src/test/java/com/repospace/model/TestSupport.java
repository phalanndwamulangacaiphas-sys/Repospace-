package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

final class TestSupport {

    private TestSupport() {
    }

    static Path freshDatabase() throws IOException, SQLException {
        Path dir = Files.createTempDirectory("repospace-test");
        System.setProperty("repospace.data", dir.toString());
        DatabaseConnection.initialise();
        return dir;
    }

    static Repository repository(User owner, String name, String visibility, Map<String, String> files) throws Exception {
        Path folder = Files.createTempDirectory("repospace-source");
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path file = folder.resolve(f.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, f.getValue(), StandardCharsets.UTF_8);
        }
        Repository repo = new Repository(owner.getUserID(), name, "demo", visibility, folder.toString());
        repo.createRepository();
        if (!files.isEmpty()) {
            new Commit(repo.getRepoID(), "First version", owner.getUsername()).commit();
        }
        return repo;
    }

    static Repository simpleRepository(User owner, String name, String visibility) throws Exception {
        return repository(owner, name, visibility, Map.of("a.txt", "alpha"));
    }
}
