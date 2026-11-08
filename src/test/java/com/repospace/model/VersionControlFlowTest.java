package com.repospace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.repospace.database.DatabaseConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

public class VersionControlFlowTest {

    private static void useFreshDatabase() throws IOException, SQLException {
        Path dir = Files.createTempDirectory("repospace-data");
        System.setProperty("repospace.data", dir.toString());
        DatabaseConnection.initialise();
    }

    @Test
    public void registerAndLogin() throws Exception {
        useFreshDatabase();
        User created = User.register("alice", "alice@example.com", "password123");
        assertTrue(created.getUserID() > 0);
        assertFalse(created.getPassword().contains("password123"));

        User viaUsername = new User();
        assertTrue(viaUsername.login("alice", "password123"));
        User viaEmail = new User();
        assertTrue(viaEmail.login("ALICE@example.com", "password123"));
        assertFalse(new User().login("alice", "wrong-password"));
        assertFalse(new User().login("nobody", "password123"));
    }

    @Test
    public void duplicateAndInvalidRegistrationsAreRejected() throws Exception {
        useFreshDatabase();
        User.register("bob", "bob@example.com", "password123");
        assertThrows(IllegalArgumentException.class, () -> User.register("bob", "other@example.com", "password123"));
        assertThrows(IllegalArgumentException.class, () -> User.register("carol", "bob@example.com", "password123"));
        assertThrows(IllegalArgumentException.class, () -> User.register("dave", "not-an-email", "password123"));
        assertThrows(IllegalArgumentException.class, () -> User.register("eve", "eve@example.com", "short"));
    }

    @Test
    public void projectsCanBeCreatedUpdatedAndDeleted() throws Exception {
        useFreshDatabase();
        User user = User.register("frank", "frank@example.com", "password123");
        Projects project = new Projects(user.getUserID(), "Thesis", "Final-year report");
        project.createProject();
        assertEquals(1, Projects.findByUser(user.getUserID()).size());

        project.updateStatus("Completed");
        assertEquals("Completed", Projects.findByUser(user.getUserID()).get(0).getStatus());

        project.deleteProject();
        assertTrue(Projects.findByUser(user.getUserID()).isEmpty());
    }

    @Test
    public void settingsAndProfilePersist() throws Exception {
        useFreshDatabase();
        User user = User.register("grace", "grace@example.com", "password123");

        Settings settings = Settings.forUser(user.getUserID());
        assertEquals("Light", settings.getTheme());
        settings.changeTheme("Dark");
        settings.toggleNotifications();
        Settings reloaded = Settings.forUser(user.getUserID());
        assertEquals("Dark", reloaded.getTheme());
        assertFalse(reloaded.isNotifications());

        Profile profile = Profile.forUser(user.getUserID());
        profile.editProfile("Hello there", "");
        profile.updateProfile();
        assertEquals("Hello there", Profile.forUser(user.getUserID()).getBio());
    }

    @Test
    public void commitHistoryRestoreAndDelete() throws Exception {
        useFreshDatabase();
        User user = User.register("henry", "henry@example.com", "password123");
        Path source = Files.createTempDirectory("repospace-source");
        Files.writeString(source.resolve("notes.txt"), "first draft");

        Repository repo = new Repository(user.getUserID(), "Notes", "my notes", "private", source.toString());
        repo.createRepository();
        assertTrue(repo.getRepoID() > 0);
        assertTrue(repo.viewFiles().isEmpty());

        assertThrows(IllegalStateException.class, () -> repo.cloneRepository(Files.createTempDirectory("repospace-clone")));

        new Commit(repo.getRepoID(), "First version", "henry").commit();
        assertEquals(List.of("notes.txt"), repo.viewFiles());

        assertThrows(IllegalStateException.class, () -> new Commit(repo.getRepoID(), "No change", "henry").commit());

        Files.writeString(source.resolve("notes.txt"), "second draft");
        Files.writeString(source.resolve("extra.txt"), "new file");
        assertEquals(2, repo.pendingChanges().size());
        new Commit(repo.getRepoID(), "Second version", "henry").commit();

        List<Commit> commits = Commit.findByRepo(repo.getRepoID());
        assertEquals(2, commits.size());
        assertEquals(2, commits.get(0).getVersionNo());
        assertEquals(1, commits.get(1).getVersionNo());
        assertEquals(2, History.findByRepo(repo.getRepoID()).size());
        assertEquals("v2", History.findByRepo(repo.getRepoID()).get(0).getVersion());
        assertNotNull(commits.get(0).viewDetails());

        Path restored = Files.createTempDirectory("repospace-restored");
        commits.get(1).restore(restored);
        assertEquals("first draft", Files.readString(restored.resolve("notes.txt")));
        assertFalse(Files.exists(restored.resolve("extra.txt")));

        Path cloned = Files.createTempDirectory("repospace-clone2");
        repo.cloneRepository(cloned);
        assertEquals("second draft", Files.readString(cloned.resolve("notes.txt")));
        assertEquals("new file", Files.readString(cloned.resolve("extra.txt")));

        assertThrows(IllegalArgumentException.class, () -> new Commit(repo.getRepoID(), "  ", "henry").commit());

        repo.deleteRepository();
        assertTrue(Repository.findByOwner(user.getUserID()).isEmpty());
        assertTrue(Commit.findByRepo(repo.getRepoID()).isEmpty());
        assertFalse(Files.exists(repo.getRepoDir()));
    }

    @Test
    public void publicRepositoriesAreVisibleToOthersButPrivateOnesAreNot() throws Exception {
        useFreshDatabase();
        User owner = User.register("ivy", "ivy@example.com", "password123");
        User other = User.register("jack", "jack@example.com", "password123");
        Path folder = Files.createTempDirectory("repospace-vis");

        new Repository(owner.getUserID(), "Shared", "", "public", folder.toString()).createRepository();
        new Repository(owner.getUserID(), "Secret", "", "private", folder.toString()).createRepository();

        List<Repository> seenByOther = Repository.findVisibleTo(other.getUserID());
        assertEquals(1, seenByOther.size());
        assertEquals("Shared", seenByOther.get(0).getName());
        assertEquals(2, Repository.findVisibleTo(owner.getUserID()).size());
    }

    @Test
    public void edgeCasesAreRejectedPolitely() throws Exception {
        useFreshDatabase();
        User user = User.register("kim", "kim@example.com", "password123");
        final int uid = user.getUserID();
        Path emptyFolder = Files.createTempDirectory("repospace-empty");

        Repository repo = new Repository(uid, "Empty", "", "private", emptyFolder.toString());
        repo.createRepository();

        assertThrows(IllegalStateException.class, () -> new Commit(repo.getRepoID(), "msg", "kim").commit());
        assertThrows(IllegalArgumentException.class, () -> new Repository(uid, "Bad", "", "private", "/no/such/folder").createRepository());
        assertThrows(IllegalArgumentException.class, () -> new Repository(uid, "Weird", "", "secret", emptyFolder.toString()).createRepository());
        assertThrows(IllegalArgumentException.class, () -> new Repository(uid, "empty", "", "private", emptyFolder.toString()).createRepository());
        assertThrows(IllegalArgumentException.class, () -> new Projects(uid, "   ", "").createProject());
    }

    @Test
    public void persistableInterfaceWorksForEveryImplementor() throws Exception {
        useFreshDatabase();
        User user = User.register("lena", "lena@example.com", "password123");
        int uid = user.getUserID();

        Projects project = new Projects(uid, "Demo", "first");
        Profile profile = Profile.forUser(uid);
        Settings settings = Settings.forUser(uid);
        profile.editProfile("polymorphism works", "");
        settings.setNotifications(false);

        for (Persistable item : List.of(project, profile, settings)) {
            item.save();
            assertTrue(item.getId() > 0);
        }

        assertEquals(1, Projects.findByUser(uid).size());
        assertEquals("polymorphism works", Profile.forUser(uid).getBio());
        assertFalse(Settings.forUser(uid).isNotifications());
    }
}
