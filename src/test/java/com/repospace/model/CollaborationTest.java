package com.repospace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.repospace.database.DatabaseConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

public class CollaborationTest {

    @Test
    public void onlyYourOwnRepositoriesCanBePinnedAndAtMostSix() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Repository foreign = TestSupport.simpleRepository(ben, "Bens", "public");
        assertThrows(IllegalStateException.class, () -> PinnedRepositories.pin(ann.getUserID(), foreign));

        for (int i = 1; i <= PinnedRepositories.MAX_PINS; i++) {
            PinnedRepositories.pin(ann.getUserID(), TestSupport.simpleRepository(ann, "Repo" + i, "public"));
        }
        Repository seventh = TestSupport.simpleRepository(ann, "Repo7", "public");
        assertThrows(IllegalStateException.class, () -> PinnedRepositories.pin(ann.getUserID(), seventh));
        assertEquals(6, PinnedRepositories.pinnedFor(ann.getUserID(), ann.getUserID()).size());
        assertEquals("Repo1", PinnedRepositories.pinnedFor(ann.getUserID(), ann.getUserID()).get(0).getName());

        PinnedRepositories.unpin(ann.getUserID(), PinnedRepositories.pinnedFor(ann.getUserID(), ann.getUserID()).get(0).getRepoID());
        PinnedRepositories.pin(ann.getUserID(), seventh);
        assertTrue(PinnedRepositories.isPinned(ann.getUserID(), seventh.getRepoID()));
        assertThrows(IllegalStateException.class, () -> PinnedRepositories.pin(ann.getUserID(), seventh));
    }

    @Test
    public void aPinnedPrivateRepositoryIsHiddenFromOtherPeople() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(ann, "Secret", "public");
        PinnedRepositories.pin(ann.getUserID(), repo);
        assertEquals(1, PinnedRepositories.pinnedFor(ann.getUserID(), ben.getUserID()).size());
        repo.changeVisibility("private");
        assertTrue(PinnedRepositories.pinnedFor(ann.getUserID(), ben.getUserID()).isEmpty());
        assertEquals(1, PinnedRepositories.pinnedFor(ann.getUserID(), ann.getUserID()).size());
    }

    @Test
    public void discussionsHaveRepliesCategoriesAndPermissions() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("olga", "olga@example.com", "password123");
        User guest = User.register("greg", "greg@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "Open", "public");

        Discussion idea = Discussion.start(repo, guest, "Ideas", "Dark mode?", "Could the app have a dark theme?");
        assertEquals("greg", idea.getAuthorName());
        idea.reply(repo, owner, "It already does - see Settings.");
        idea.reply(repo, guest, "Thanks!");

        List<Discussion> all = Discussion.findByRepo(repo, guest.getUserID(), null);
        assertEquals(1, all.size());
        assertEquals(2, all.get(0).getReplyCount());
        assertEquals(2, all.get(0).replies(repo, guest.getUserID()).size());
        assertEquals("olga", all.get(0).replies(repo, guest.getUserID()).get(0).getAuthorName());
        assertTrue(Discussion.findByRepo(repo, guest.getUserID(), "Q&A").isEmpty());

        assertThrows(IllegalStateException.class, () -> Discussion.start(repo, guest, "Announcements", "Hi", "text"));
        Discussion.start(repo, owner, "Announcements", "Version 2", "Released today");
        assertThrows(IllegalArgumentException.class, () -> Discussion.start(repo, owner, "Gossip", "x", "y"));
        assertThrows(IllegalArgumentException.class, () -> Discussion.start(repo, owner, "General", "  ", "y"));
        assertThrows(IllegalArgumentException.class, () -> idea.reply(repo, guest, "   "));
    }

    @Test
    public void closingADiscussionStopsRepliesAndOnlyAuthorOrOwnerMayManageIt() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("olga", "olga@example.com", "password123");
        User guest = User.register("greg", "greg@example.com", "password123");
        User stranger = User.register("sam", "sam@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "Open", "public");
        Discussion d = Discussion.start(repo, guest, "Q&A", "How do I download?", "Where is the button?");

        assertThrows(IllegalStateException.class, () -> d.setClosed(repo, stranger.getUserID(), true));
        assertThrows(IllegalStateException.class, () -> d.delete(repo, stranger.getUserID()));
        d.setClosed(repo, owner.getUserID(), true);
        assertTrue(d.isClosed());
        assertThrows(IllegalStateException.class, () -> d.reply(repo, guest, "still there?"));
        d.setClosed(repo, guest.getUserID(), false);
        d.reply(repo, guest, "found it");
        d.delete(repo, guest.getUserID());
        assertTrue(Discussion.findByRepo(repo, owner.getUserID(), null).isEmpty());
    }

    @Test
    public void privateRepositoriesHaveNoDiscussionsForOutsiders() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("olga", "olga@example.com", "password123");
        User guest = User.register("greg", "greg@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "Hidden", "private");
        assertThrows(IllegalStateException.class, () -> Discussion.start(repo, guest, "General", "Hello", "Let me in"));
        assertThrows(IllegalStateException.class, () -> Discussion.findByRepo(repo, guest.getUserID(), null));
        Discussion own = Discussion.start(repo, owner, "General", "Note", "to self");
        assertThrows(IllegalStateException.class, () -> own.replies(repo, guest.getUserID()));
    }

    @Test
    public void aBoardHasThreeColumnsAndOnlyTheOwnerChangesIt() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("pia", "pia@example.com", "password123");
        User other = User.register("quin", "quin@example.com", "password123");
        Projects project = new Projects(owner.getUserID(), "Thesis", "");
        project.createProject();
        Repository repo = TestSupport.simpleRepository(owner, "Chapters", "private");

        BoardTask a = BoardTask.add(project, owner.getUserID(), "Write intro", "500 words", null);
        BoardTask b = BoardTask.add(project, owner.getUserID(), "Collect data", "", repo.getRepoID());
        BoardTask c = BoardTask.add(project, owner.getUserID(), "Proofread", "", null);
        assertEquals("To do", a.getStatus());
        assertEquals(repo.getRepoID(), b.getRepoID().intValue());
        assertNull(a.getRepoID());

        a.move(project, owner.getUserID(), "Done");
        b.move(project, owner.getUserID(), "In progress");
        List<BoardTask> tasks = BoardTask.forProject(project, owner.getUserID());
        assertEquals(3, tasks.size());
        assertEquals("Done", tasks.get(0).getStatus());
        assertEquals("In progress", tasks.get(1).getStatus());
        assertEquals(33, BoardTask.percentDone(tasks));
        assertEquals(0, BoardTask.percentDone(List.of()));

        assertThrows(IllegalArgumentException.class, () -> c.move(project, owner.getUserID(), "Someday"));
        assertThrows(IllegalStateException.class, () -> c.move(project, other.getUserID(), "Done"));
        assertThrows(IllegalStateException.class, () -> BoardTask.add(project, other.getUserID(), "Sneaky", "", null));
        assertThrows(IllegalStateException.class, () -> c.delete(project, other.getUserID()));
        assertThrows(IllegalArgumentException.class, () -> BoardTask.add(project, owner.getUserID(), " ", "", null));

        User third = User.register("rex", "rex@example.com", "password123");
        Repository foreign = TestSupport.simpleRepository(third, "NotMine", "public");
        assertThrows(IllegalArgumentException.class, () -> BoardTask.add(project, owner.getUserID(), "Link", "", foreign.getRepoID()));

        c.delete(project, owner.getUserID());
        assertEquals(2, BoardTask.forProject(project, owner.getUserID()).size());
    }

    @Test
    public void aPrivateProjectBoardIsOnlyVisibleToItsOwner() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("pia", "pia@example.com", "password123");
        User other = User.register("quin", "quin@example.com", "password123");
        Projects project = new Projects(owner.getUserID(), "Plan", "");
        project.createProject();
        BoardTask.add(project, owner.getUserID(), "Step one", "", null);
        assertThrows(IllegalStateException.class, () -> BoardTask.forProject(project, other.getUserID()));
        project.changeVisibility("public");
        assertEquals(1, BoardTask.forProject(project, other.getUserID()).size());
        project.deleteProject();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM board_tasks")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    public void aReleaseIsFixedToTheVersionItWasMadeFrom() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("rita", "rita@example.com", "password123");
        User other = User.register("sid", "sid@example.com", "password123");
        Repository repo = TestSupport.repository(owner, "App", "public", Map.of("main.txt", "version one"));

        Release v1 = Release.publish(repo, owner.getUserID(), "v1.0", "First release", "## Notes\n* works", false);
        Path source = Path.of(repo.getSourcePath());
        Files.writeString(source.resolve("main.txt"), "version two");
        new Commit(repo.getRepoID(), "Second version", "rita").commit();
        Release v2 = Release.publish(repo, owner.getUserID(), "v2.0-beta", "", "", true);
        assertEquals("v2.0-beta", v2.getTitle());

        List<Release> list = Release.findByRepo(repo, other.getUserID());
        assertEquals(2, list.size());
        assertEquals("v2.0-beta", list.get(0).getTag());
        assertEquals("v1.0", Release.latest(repo, other.getUserID()).getTag());
        assertEquals(1, v1.getCommit().getVersionNo());

        Path zip = Files.createTempDirectory("repospace-release").resolve("v1.zip");
        assertEquals(1, v1.downloadZip(repo, other.getUserID(), zip));
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertEquals("version one", new String(z.getInputStream(z.getEntry("main.txt")).readAllBytes()));
        }
    }

    @Test
    public void releaseRulesTagsOwnerAndPrivacy() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("rita", "rita@example.com", "password123");
        User other = User.register("sid", "sid@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "App", "private");

        assertThrows(IllegalStateException.class, () -> Release.publish(repo, other.getUserID(), "v1", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> Release.publish(repo, owner.getUserID(), "bad tag!", "", "", false));
        Release r = Release.publish(repo, owner.getUserID(), "v1", "", "", false);
        assertThrows(IllegalArgumentException.class, () -> Release.publish(repo, owner.getUserID(), "V1", "", "", false));
        assertThrows(IllegalStateException.class, () -> Release.findByRepo(repo, other.getUserID()));
        assertThrows(IllegalStateException.class, () -> r.delete(repo, other.getUserID()));
        assertEquals(1, Release.countForOwner(owner.getUserID(), false));
        assertEquals(0, Release.countForOwner(owner.getUserID(), true));
        r.delete(repo, owner.getUserID());
        assertTrue(Release.findByRepo(repo, owner.getUserID()).isEmpty());

        Repository empty = TestSupport.repository(owner, "Empty", "public", Map.of());
        assertThrows(IllegalStateException.class, () -> Release.publish(empty, owner.getUserID(), "v1", "", "", false));
    }

    @Test
    public void wikiPagesKeepRevisionsAndOnlyTheOwnerWrites() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("wilma", "wilma@example.com", "password123");
        User reader = User.register("walt", "walt@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "Docs", "public");

        WikiPage home = WikiPage.save(repo, owner, "Home", "Welcome. See [[Install]].");
        assertEquals(1, home.getRevision());
        WikiPage again = WikiPage.save(repo, owner, "home", "Welcome again.");
        assertEquals(2, again.getRevision());
        WikiPage.save(repo, owner, "Install", "Run the jar.");

        List<WikiPage> pages = WikiPage.findByRepo(repo, reader.getUserID());
        assertEquals(2, pages.size());
        assertEquals("Welcome again.", WikiPage.find(repo, reader.getUserID(), "HOME").getBody());
        assertNull(WikiPage.find(repo, reader.getUserID(), "Missing"));

        assertThrows(IllegalStateException.class, () -> WikiPage.save(repo, reader, "Hack", "x"));
        assertThrows(IllegalArgumentException.class, () -> WikiPage.save(repo, owner, "", "x"));
        assertThrows(IllegalArgumentException.class, () -> WikiPage.save(repo, owner, "Bad [title]", "x"));
        assertThrows(IllegalStateException.class, () -> pages.get(0).delete(repo, reader.getUserID()));
        pages.get(0).delete(repo, owner.getUserID());
        assertEquals(1, WikiPage.findByRepo(repo, owner.getUserID()).size());

        repo.changeVisibility("private");
        assertThrows(IllegalStateException.class, () -> WikiPage.findByRepo(repo, reader.getUserID()));
    }

    @Test
    public void sponsorsPledgeToTiersAndTheOwnerSeesTheTotal() throws Exception {
        TestSupport.freshDatabase();
        User maker = User.register("maya", "maya@example.com", "password123");
        User fan1 = User.register("finn", "finn@example.com", "password123");
        User fan2 = User.register("fern", "fern@example.com", "password123");
        SponsorTier coffee = SponsorTier.create(maker.getUserID(), "Coffee", 5, "A coffee a month");
        SponsorTier lunch = SponsorTier.create(maker.getUserID(), "Lunch", 20, "");

        assertEquals(2, SponsorTier.forUser(maker.getUserID(), fan1.getUserID()).size());
        assertEquals("Coffee", SponsorTier.forUser(maker.getUserID(), fan1.getUserID()).get(0).getName());

        Sponsorship.pledge(fan1, coffee, "Love your work");
        Sponsorship.pledge(fan2, lunch, "");
        assertEquals(2, Sponsorship.countSponsors(maker.getUserID()));
        assertEquals(25, Sponsorship.monthlyTotal(maker.getUserID(), maker.getUserID()));
        assertThrows(IllegalStateException.class, () -> Sponsorship.monthlyTotal(maker.getUserID(), fan1.getUserID()));

        Sponsorship changed = Sponsorship.pledge(fan1, lunch, "More!");
        assertEquals("Lunch", changed.getTierName());
        assertEquals(2, Sponsorship.countSponsors(maker.getUserID()));
        assertEquals(40, Sponsorship.monthlyTotal(maker.getUserID(), maker.getUserID()));
        assertEquals(1, Sponsorship.madeBy(fan1.getUserID()).size());

        Sponsorship.cancel(fan1.getUserID(), maker.getUserID());
        assertNull(Sponsorship.find(fan1.getUserID(), maker.getUserID()));
        assertEquals(1, Sponsorship.sponsorsOf(maker.getUserID(), fan2.getUserID()).size());
        assertEquals("fern", Sponsorship.sponsorsOf(maker.getUserID(), fan2.getUserID()).get(0).getSponsorName());
    }

    @Test
    public void sponsorRulesNoSelfSponsoringTierLimitsAndPrivateProfiles() throws Exception {
        TestSupport.freshDatabase();
        User maker = User.register("maya", "maya@example.com", "password123");
        User fan = User.register("finn", "finn@example.com", "password123");
        SponsorTier tier = SponsorTier.create(maker.getUserID(), "Coffee", 5, "");
        assertThrows(IllegalStateException.class, () -> Sponsorship.pledge(maker, tier, ""));
        assertThrows(IllegalArgumentException.class, () -> SponsorTier.create(maker.getUserID(), "Free", 0, ""));
        assertThrows(IllegalArgumentException.class, () -> SponsorTier.create(maker.getUserID(), "", 5, ""));
        for (int i = 2; i <= SponsorTier.MAX_TIERS; i++) {
            SponsorTier.create(maker.getUserID(), "Tier" + i, 5 * i, "");
        }
        assertThrows(IllegalStateException.class, () -> SponsorTier.create(maker.getUserID(), "One too many", 99, ""));
        assertThrows(IllegalStateException.class, () -> tier.delete(fan.getUserID()));

        Sponsorship.pledge(fan, tier, "hi");
        Profile profile = Profile.forUser(maker.getUserID());
        profile.changeVisibility("private");
        profile.updateProfile();
        assertTrue(SponsorTier.forUser(maker.getUserID(), fan.getUserID()).isEmpty());
        assertTrue(Sponsorship.sponsorsOf(maker.getUserID(), fan.getUserID()).isEmpty());
        assertFalse(SponsorTier.forUser(maker.getUserID(), maker.getUserID()).isEmpty());
        User late = User.register("lola", "lola@example.com", "password123");
        assertThrows(IllegalStateException.class, () -> Sponsorship.pledge(late, tier, ""));

        tier.delete(maker.getUserID());
        assertEquals(0, Sponsorship.countSponsors(maker.getUserID()));
        assertNotNull(SponsorTier.forUser(maker.getUserID(), maker.getUserID()));
    }

    @Test
    public void deletingARepositoryRemovesEverythingAttachedToIt() throws Exception {
        TestSupport.freshDatabase();
        User owner = User.register("dora", "dora@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(owner, "Temp", "public");
        PinnedRepositories.pin(owner.getUserID(), repo);
        Discussion.start(repo, owner, "General", "Hello", "world").reply(repo, owner, "again");
        Release.publish(repo, owner.getUserID(), "v1", "", "", false);
        WikiPage.save(repo, owner, "Home", "text");
        Projects project = new Projects(owner.getUserID(), "Plan", "");
        project.createProject();
        BoardTask.add(project, owner.getUserID(), "Linked", "", repo.getRepoID());

        repo.deleteRepository();

        for (String table : new String[]{"pinned_repos", "discussions", "discussion_replies", "releases", "wiki_pages", "commits"}) {
            try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rs.next();
                assertEquals(0, rs.getInt(1), table + " should be empty");
            }
        }
        List<BoardTask> cards = BoardTask.forProject(project, owner.getUserID());
        assertEquals(1, cards.size());
        assertNull(cards.get(0).getRepoID());
    }
}
