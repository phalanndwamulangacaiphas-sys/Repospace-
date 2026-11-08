package com.repospace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.SnapshotStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

public class SharingTest {

    private static Path freshDatabase() throws IOException, SQLException {
        Path dir = Files.createTempDirectory("repospace-sharing");
        System.setProperty("repospace.data", dir.toString());
        DatabaseConnection.initialise();
        return dir;
    }

    private static Repository repositoryWithOneCommit(User owner, String name, String visibility) throws Exception {
        Path folder = Files.createTempDirectory("repospace-source");
        Files.writeString(folder.resolve("a.txt"), "alpha", StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("sub"));
        Files.writeString(folder.resolve("sub/b.txt"), "beta", StandardCharsets.UTF_8);
        Repository repo = new Repository(owner.getUserID(), name, "demo", visibility, folder.toString());
        repo.createRepository();
        new Commit(repo.getRepoID(), "First version", owner.getUsername()).commit();
        return repo;
    }

    @Test
    public void projectsAreMadePrivateByDefaultAndTheOwnerCanChangeThat() throws Exception {
        freshDatabase();
        User owner = User.register("ann", "ann@example.com", "password123");
        Projects project = new Projects(owner.getUserID(), "Thesis", "Final report");
        project.createProject();

        assertEquals("private", project.getVisibility());
        assertTrue(Projects.findPublicByUser(owner.getUserID()).isEmpty());

        project.changeVisibility("public");
        assertTrue(project.isPublic());
        assertEquals(1, Projects.findPublicByUser(owner.getUserID()).size());
        assertEquals("public", Projects.findByUser(owner.getUserID()).get(0).getVisibility());

        project.changeVisibility("PRIVATE");
        assertTrue(Projects.findPublicByUser(owner.getUserID()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> project.changeVisibility("friends-only"));
    }

    @Test
    public void aProjectCanBeCreatedAsPublicStraightAway() throws Exception {
        freshDatabase();
        User owner = User.register("ben", "ben@example.com", "password123");
        new Projects(owner.getUserID(), "Open source", "", "public").createProject();
        assertEquals(1, Projects.findPublicByUser(owner.getUserID()).size());
        assertThrows(IllegalArgumentException.class, () -> new Projects(owner.getUserID(), "X", "", "secret"));
    }

    @Test
    public void repositoryVisibilityCanBeSwitchedAndOthersOnlySeePublicOnes() throws Exception {
        freshDatabase();
        User owner = User.register("cleo", "cleo@example.com", "password123");
        User other = User.register("dan", "dan@example.com", "password123");
        Repository repo = repositoryWithOneCommit(owner, "Notes", "private");

        assertFalse(repo.canBeViewedBy(other.getUserID()));
        assertTrue(repo.canBeViewedBy(owner.getUserID()));
        assertTrue(Repository.findVisibleTo(other.getUserID()).isEmpty());

        repo.changeVisibility("public");
        assertEquals(1, Repository.findVisibleTo(other.getUserID()).size());
        assertEquals(1, Repository.findPublicByOwner(owner.getUserID()).size());
        assertEquals(1, Repository.findPublicNotOwnedBy(other.getUserID()).size());
        assertTrue(Repository.findPublicNotOwnedBy(owner.getUserID()).isEmpty());

        repo.changeVisibility("private");
        assertTrue(Repository.findVisibleTo(other.getUserID()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repo.changeVisibility("everyone"));
    }

    @Test
    public void profileIsPublicByDefaultAndPrivateHidesTheDetailsFromOthers() throws Exception {
        freshDatabase();
        User owner = User.register("eli", "eli@example.com", "password123");
        User viewer = User.register("fay", "fay@example.com", "password123");
        Profile mine = Profile.forUser(owner.getUserID());
        assertTrue(mine.isPublic());
        mine.editProfile("I like version control", "");
        mine.updateProfile();

        Profile seen = Profile.forViewer(viewer.getUserID(), owner.getUserID());
        assertFalse(seen.isHidden());
        assertEquals("I like version control", seen.getBio());

        mine.changeVisibility("private");
        mine.updateProfile();

        Profile hidden = Profile.forViewer(viewer.getUserID(), owner.getUserID());
        assertTrue(hidden.isHidden());
        assertEquals("", hidden.getBio());
        assertNull(hidden.getJoinDate());
        Profile own = Profile.forViewer(owner.getUserID(), owner.getUserID());
        assertFalse(own.isHidden());
        assertEquals("I like version control", own.getBio());
        assertThrows(IllegalStateException.class, hidden::updateProfile);
        assertEquals("I like version control", Profile.forUser(owner.getUserID()).getBio());
    }

    @Test
    public void searchFindsPartOfAUsernameAndNeverReturnsTheEmail() throws Exception {
        freshDatabase();
        User.register("alice.smith", "alice@example.com", "password123");
        User.register("alina", "alina@example.com", "password123");
        User.register("bob", "bob@example.com", "password123");

        List<User> found = User.searchUsers("ali");
        assertEquals(2, found.size());
        assertEquals(2, User.searchUsers("ALI").size());
        assertEquals(1, User.searchUsers("bo").size());
        assertEquals("bob", User.searchUsers("bo").get(0).getUsername());
        for (User user : found) {
            assertEquals("", user.getEmail());
            assertNull(user.getPassword());
        }
        assertTrue(User.searchUsers("zzz").isEmpty());
        assertTrue(User.searchUsers("").isEmpty());
        assertTrue(User.searchUsers("   ").isEmpty());
        assertTrue(User.searchUsers(null).isEmpty());
    }

    @Test
    public void searchWildcardsAndQuotesAreTreatedAsPlainText() throws Exception {
        freshDatabase();
        User.register("normal.user", "n@example.com", "password123");
        User.register("under_score", "u@example.com", "password123");
        assertTrue(User.searchUsers("%").isEmpty());
        assertEquals(1, User.searchUsers("_").size());
        assertTrue(User.searchUsers("' OR '1'='1").isEmpty());
        assertTrue(User.searchUsers("\\").isEmpty());
        assertEquals(2, User.searchUsers("r").size());
    }

    @Test
    public void anotherUserCanDownloadAPublicRepositoryAsZipOrFolder() throws Exception {
        freshDatabase();
        User owner = User.register("gus", "gus@example.com", "password123");
        User other = User.register("hana", "hana@example.com", "password123");
        Repository repo = repositoryWithOneCommit(owner, "Shared", "public");
        Repository seenByOther = Repository.findPublicNotOwnedBy(other.getUserID()).get(0);

        Path zip = Files.createTempDirectory("repospace-dl").resolve("shared.zip");
        assertEquals(2, seenByOther.download(other.getUserID(), zip));
        TreeSet<String> names = new TreeSet<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            zipFile.stream().map(ZipEntry::getName).forEach(names::add);
            assertEquals("alpha", new String(zipFile.getInputStream(zipFile.getEntry("a.txt")).readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(new TreeSet<>(List.of("a.txt", "sub/b.txt")), names);

        Path folder = Files.createTempDirectory("repospace-dl").resolve("copy");
        assertEquals(2, seenByOther.download(other.getUserID(), folder));
        assertEquals("beta", Files.readString(folder.resolve("sub/b.txt")));

        assertThrows(IllegalStateException.class, () -> seenByOther.download(other.getUserID(), folder));
        assertEquals("alpha", Files.readString(Path.of(repo.getSourcePath()).resolve("a.txt")));
    }

    @Test
    public void aPrivateRepositoryCanOnlyBeDownloadedByItsOwner() throws Exception {
        freshDatabase();
        User owner = User.register("ian", "ian@example.com", "password123");
        User other = User.register("joan", "joan@example.com", "password123");
        Repository repo = repositoryWithOneCommit(owner, "Secret", "private");
        Path zip = Files.createTempDirectory("repospace-dl").resolve("secret.zip");

        assertThrows(IllegalStateException.class, () -> repo.download(other.getUserID(), zip));
        assertFalse(Files.exists(zip));
        assertEquals(2, repo.download(owner.getUserID(), zip));
        assertTrue(Files.exists(zip));

        repo.changeVisibility("public");
        Path second = Files.createTempDirectory("repospace-dl").resolve("now-public.zip");
        assertEquals(2, repo.download(other.getUserID(), second));
    }

    @Test
    public void aRepositoryWithoutCommitsHasNothingToDownload() throws Exception {
        freshDatabase();
        User owner = User.register("kim", "kim@example.com", "password123");
        Path folder = Files.createTempDirectory("repospace-empty-repo");
        Repository repo = new Repository(owner.getUserID(), "Empty", "", "public", folder.toString());
        repo.createRepository();
        assertThrows(IllegalStateException.class, () -> repo.download(owner.getUserID(), Files.createTempDirectory("x").resolve("e.zip")));
    }

    @Test
    public void downloadGivesTheLatestVersionNotAnOldOne() throws Exception {
        freshDatabase();
        User owner = User.register("lee", "lee@example.com", "password123");
        Repository repo = repositoryWithOneCommit(owner, "Versions", "public");
        Files.writeString(Path.of(repo.getSourcePath()).resolve("a.txt"), "alpha v2", StandardCharsets.UTF_8);
        new Commit(repo.getRepoID(), "Second version", owner.getUsername()).commit();

        Path folder = Files.createTempDirectory("repospace-dl").resolve("latest");
        repo.download(owner.getUserID(), folder);
        assertEquals("alpha v2", Files.readString(folder.resolve("a.txt")));
    }

    @Test
    public void zipExportRefusesUnsafePathsAndLeavesNoHalfWrittenFile() throws Exception {
        Path objects = Files.createTempDirectory("repospace-objects");
        Path source = Files.createTempDirectory("repospace-src");
        Files.writeString(source.resolve("ok.txt"), "fine", StandardCharsets.UTF_8);
        Map<String, String> manifest = new TreeMap<>(SnapshotStore.snapshot(source, objects));
        manifest.put("../escaped.txt", manifest.get("ok.txt"));

        Path outDir = Files.createTempDirectory("repospace-zipout");
        Path zip = outDir.resolve("bad.zip");
        assertThrows(IOException.class, () -> SnapshotStore.exportZip(manifest, objects, zip));
        assertFalse(Files.exists(zip));
        try (var left = Files.list(outDir)) {
            assertEquals(0L, left.count());
        }
        assertThrows(IOException.class, () -> SnapshotStore.exportZip(new TreeMap<>(Map.of("/abs.txt", manifest.get("ok.txt"))), objects, zip));
        assertThrows(IOException.class, () -> SnapshotStore.exportZip(new TreeMap<>(Map.of("C:/win.txt", manifest.get("ok.txt"))), objects, zip));
        assertThrows(IOException.class, () -> SnapshotStore.exportZip(manifest, objects, outDir));
    }

    @Test
    public void databasesCreatedByTheEarlierVersionAreUpgradedWithoutLosingData() throws Exception {
        Path dir = Files.createTempDirectory("repospace-old");
        System.setProperty("repospace.data", dir.toString());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("users.db")); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE, bio TEXT NOT NULL DEFAULT '', profile_picture TEXT NOT NULL DEFAULT '', join_date TEXT NOT NULL)");
            st.execute("INSERT INTO profiles (user_id, bio, join_date) VALUES (1, 'old bio', '2026-01-01T10:00:00')");
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("software_programme.db")); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', created_date TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Planning')");
            st.execute("INSERT INTO projects (user_id, name, created_date) VALUES (1, 'Old project', '2026-01-01T10:00:00')");
        }

        DatabaseConnection.initialise();
        DatabaseConnection.initialise();

        List<Projects> projects = Projects.findByUser(1);
        assertEquals(1, projects.size());
        assertEquals("Old project", projects.get(0).getName());
        assertEquals("private", projects.get(0).getVisibility());
        Profile profile = Profile.forUser(1);
        assertEquals("old bio", profile.getBio());
        assertEquals("public", profile.getVisibility());
    }
}
