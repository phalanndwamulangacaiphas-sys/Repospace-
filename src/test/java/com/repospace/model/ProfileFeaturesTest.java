package com.repospace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.repospace.util.PagesServer;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProfileFeaturesTest {

    @Test
    public void profileReadmeAndStatusAreSavedAndHiddenWhenPrivate() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Profile p = Profile.forUser(ann.getUserID());
        p.editReadme("# Hi, I am Ann\nI have {{commits}} commits.", "Building Repospace");
        p.updateProfile();

        Profile seen = Profile.forViewer(ben.getUserID(), ann.getUserID());
        assertEquals("Building Repospace", seen.getStatus());
        assertTrue(seen.getReadme().startsWith("# Hi"));

        p.changeVisibility("private");
        p.updateProfile();
        Profile hidden = Profile.forViewer(ben.getUserID(), ann.getUserID());
        assertEquals("", hidden.getReadme());
        assertEquals("", hidden.getStatus());
        assertThrows(IllegalArgumentException.class, () -> p.editReadme("x".repeat(Profile.MAX_README + 1), ""));
        assertThrows(IllegalArgumentException.class, () -> p.editReadme("", "y".repeat(Profile.MAX_STATUS + 1)));
    }

    @Test
    public void aboutBoxStoresDescriptionWebsiteAndCleanTopics() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(ann, "Notes", "public");

        repo.updateAbout(ann.getUserID(), "My study notes", "https://example.com/notes", "Java, School Project, java");
        Repository reloaded = Repository.findById(repo.getRepoID());
        assertEquals("My study notes", reloaded.getDescription());
        assertEquals("https://example.com/notes", reloaded.getWebsite());
        assertEquals(java.util.List.of("java", "school-project"), reloaded.getTopicList());

        assertThrows(IllegalStateException.class, () -> repo.updateAbout(ben.getUserID(), "hijack", "", ""));
        assertThrows(IllegalArgumentException.class, () -> repo.updateAbout(ann.getUserID(), "x", "javascript:alert(1)", ""));
        assertThrows(IllegalArgumentException.class, () -> repo.updateAbout(ann.getUserID(), "x", "not a url", ""));
        assertThrows(IllegalArgumentException.class, () -> repo.updateAbout(ann.getUserID(), "x", "", "bad_topic!"));
        assertThrows(IllegalArgumentException.class, () -> repo.updateAbout(ann.getUserID(), "x", "", "a,b,c,d,e,f,g,h,i,j,k"));
        assertThrows(IllegalArgumentException.class, () -> repo.updateAbout(ann.getUserID(), "d".repeat(Repository.MAX_DESCRIPTION + 1), "", ""));
        assertEquals("My study notes", Repository.findById(repo.getRepoID()).getDescription());
    }

    @Test
    public void readmeComesFromTheProjectFileOtherwiseFromRepospace() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Repository repo = TestSupport.simpleRepository(ann, "Notes", "public");

        assertEquals("", repo.readmeFor(ben.getUserID()));
        repo.saveReadme(ann.getUserID(), "# Written in Repospace");
        assertEquals("# Written in Repospace", repo.readmeFor(ben.getUserID()));
        assertFalse(repo.readmeComesFromFiles());
        assertThrows(IllegalStateException.class, () -> repo.saveReadme(ben.getUserID(), "vandalism"));

        Files.writeString(Path.of(repo.getSourcePath()).resolve("readme.MD"), "# From the file", StandardCharsets.UTF_8);
        new Commit(repo.getRepoID(), "Add readme", "ann").commit();
        assertTrue(repo.readmeComesFromFiles());
        assertEquals("# From the file", repo.readmeFor(ben.getUserID()));

        repo.changeVisibility("private");
        assertThrows(IllegalStateException.class, () -> repo.readmeFor(ben.getUserID()));
        assertEquals("# From the file", repo.readmeFor(ann.getUserID()));
    }

    @Test
    public void searchFindsPublicRepositoriesByNameDescriptionOrTopic() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        Repository a = TestSupport.simpleRepository(ann, "Weather-App", "public");
        Repository b = TestSupport.simpleRepository(ann, "Secret-Weather", "private");
        a.updateAbout(ann.getUserID(), "Shows forecasts", "", "api, javafx");
        b.updateAbout(ann.getUserID(), "Shows forecasts", "", "api");

        assertEquals(1, Repository.searchPublic("weather").size());
        assertEquals(1, Repository.searchPublic("FORECAST").size());
        assertEquals(1, Repository.searchPublic("javafx").size());
        assertEquals(0, Repository.searchPublic("%").size());
        assertEquals(0, Repository.searchPublic("   ").size());
    }

    @Test
    public void statisticsCountCommitsLanguagesAndStreaks() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        User ben = User.register("ben", "ben@example.com", "password123");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/Main.java", "class Main { /* long file */ ".repeat(40) + "}");
        files.put("tools/run.py", "print('hi')");
        files.put("README.md", "# readme");
        Repository open = TestSupport.repository(ann, "Open", "public", files);
        Repository hidden = TestSupport.repository(ann, "Hidden", "private", Map.of("secret.c", "int main(){}"));
        Release.publish(open, ann.getUserID(), "v1", "", "", false);

        ProfileStats own = ProfileStats.of(ann.getUserID(), ann.getUserID());
        assertEquals(2, own.getRepositories());
        assertEquals(2, own.getCommits());
        assertEquals("Java", own.topLanguage());
        assertTrue(own.getCurrentStreak() >= 1);
        assertTrue(own.getLongestStreak() >= 1);
        assertEquals(1, own.getReleases());
        assertTrue(own.languagePercentages().containsKey("C"));

        ProfileStats others = ProfileStats.of(ben.getUserID(), ann.getUserID());
        assertEquals(1, others.getRepositories());
        assertEquals(1, others.getCommits());
        assertFalse(others.languagePercentages().containsKey("C"));
        assertEquals(1, others.recentActivity(10).stream().filter(t -> t.contains("committed")).count());
        assertTrue(others.recentActivity(10).stream().anyMatch(t -> t.contains("released v1")));
        assertFalse(others.recentActivity(10).stream().anyMatch(t -> t.contains("Hidden")));
        assertEquals(1, ProfileStats.commitsPerDay(ben.getUserID(), ann.getUserID(), 7).values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(hidden.getRepoID() > 0, true);
    }

    @Test
    public void placeholdersAndBadgesShowTheLiveNumbers() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        TestSupport.simpleRepository(ann, "One", "public");
        ProfileStats stats = ProfileStats.of(ann.getUserID(), ann.getUserID());

        assertEquals("ann has 1 repos and 1 commits {{unknown}}", stats.expand("{{username}} has {{repos}} repos and {{commits}} commits {{unknown}}"));
        assertEquals("", stats.expand(null));

        Badge commits = stats.badge("commits");
        assertNotNull(commits);
        assertEquals("1", commits.getValue());
        String svg = commits.toSvg();
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("commits"));
        assertNull(stats.badge("nonsense"));
        assertEquals(7, stats.badges().size());
        Badge evil = new Badge("x", "<b>", "\"&", "#000000");
        assertFalse(evil.toSvg().contains("<b>"));
    }

    private static Repository siteRepository(User owner, String visibility) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.html", "<h1>Hello site</h1><link rel=stylesheet href=style.css>");
        files.put("style.css", "h1{color:#106ffd}");
        files.put("docs/index.html", "<p>docs home</p>");
        files.put("docs/page.html", "<p>docs page</p>");
        return TestSupport.repository(owner, "mysite", visibility, files);
    }

    @Test
    public void aPagesSiteServesTheLatestVersionOfAPublicRepository() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        Repository repo = siteRepository(ann, "public");

        assertEquals(404, PagesServer.handle("GET", "/ann/mysite/").getStatus());
        repo.setPagesEnabled(ann.getUserID(), true);

        PagesServer.Response index = PagesServer.handle("GET", "/ann/mysite/");
        assertEquals(200, index.getStatus());
        assertTrue(index.text().contains("Hello site"));
        assertTrue(index.getContentType().startsWith("text/html"));
        assertEquals("text/css; charset=utf-8", PagesServer.handle("GET", "/ann/mysite/style.css").getContentType());
        assertTrue(PagesServer.handle("GET", "/ann/mysite/docs/").text().contains("docs home"));
        assertTrue(PagesServer.handle("GET", "/ann/mysite/docs").text().contains("docs home"));
        assertEquals(404, PagesServer.handle("GET", "/ann/mysite/missing.html").getStatus());
        assertEquals(404, PagesServer.handle("GET", "/nobody/mysite/").getStatus());
        assertEquals(404, PagesServer.handle("GET", "/ann/unknown/").getStatus());

        Files.writeString(Path.of(repo.getSourcePath()).resolve("index.html"), "<h1>Version two</h1>");
        new Commit(repo.getRepoID(), "Update", "ann").commit();
        assertTrue(PagesServer.handle("GET", "/ann/mysite/").text().contains("Version two"));

        assertTrue(PagesServer.handle("GET", "/").text().contains("ann/mysite"));
    }

    @Test
    public void pagesNeverServesPrivateRepositoriesAndRefusesBadRequests() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        Repository repo = siteRepository(ann, "private");
        assertThrows(IllegalStateException.class, () -> repo.setPagesEnabled(ann.getUserID(), true));

        repo.changeVisibility("public");
        repo.setPagesEnabled(ann.getUserID(), true);
        assertEquals(200, PagesServer.handle("GET", "/ann/mysite/").getStatus());
        repo.changeVisibility("private");
        assertFalse(repo.isPagesEnabled());
        assertEquals(404, PagesServer.handle("GET", "/ann/mysite/").getStatus());

        repo.changeVisibility("public");
        repo.setPagesEnabled(ann.getUserID(), true);
        assertEquals(400, PagesServer.handle("GET", "/ann/mysite/../../etc/passwd").getStatus());
        assertEquals(400, PagesServer.handle("GET", "/ann/mysite/..\\x").getStatus());
        assertEquals(405, PagesServer.handle("POST", "/ann/mysite/").getStatus());
        assertEquals(400, PagesServer.handle("GET", "no-slash").getStatus());
        assertThrows(IllegalStateException.class, () -> repo.setPagesEnabled(User.register("bob", "bob@example.com", "password123").getUserID(), false));
    }

    @Test
    public void aSiteWithoutIndexShowsItsReadmeAndBadgesComeFromThePublicProfile() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        Repository repo = TestSupport.repository(ann, "docsonly", "public", Map.of("notes.txt", "x"));
        repo.saveReadme(ann.getUserID(), "# My docs\n\n<script>bad()</script>");
        repo.setPagesEnabled(ann.getUserID(), true);
        PagesServer.Response r = PagesServer.handle("GET", "/ann/docsonly/");
        assertEquals(200, r.getStatus());
        assertTrue(r.text().contains("<h1>My docs</h1>"));
        assertFalse(r.text().contains("<script>bad()"));

        PagesServer.Response badge = PagesServer.handle("GET", "/badges/ann/commits.svg");
        assertEquals(200, badge.getStatus());
        assertEquals("image/svg+xml; charset=utf-8", badge.getContentType());
        assertTrue(badge.text().contains("<svg"));
        assertEquals(404, PagesServer.handle("GET", "/badges/ann/unknown.svg").getStatus());
        assertEquals(404, PagesServer.handle("GET", "/badges/ghost/commits.svg").getStatus());

        Profile p = Profile.forUser(ann.getUserID());
        p.changeVisibility("private");
        p.updateProfile();
        assertEquals(404, PagesServer.handle("GET", "/badges/ann/commits.svg").getStatus());
    }

    @Test
    public void theRealServerAnswersOverHttpOnLocalhostOnly() throws Exception {
        TestSupport.freshDatabase();
        User ann = User.register("ann", "ann@example.com", "password123");
        Repository repo = siteRepository(ann, "public");
        repo.setPagesEnabled(ann.getUserID(), true);
        int port = PagesServer.start(0);
        try {
            assertTrue(PagesServer.isRunning());
            assertEquals(port, PagesServer.getPort());
            assertEquals("http://localhost:" + port + "/ann/mysite/", PagesServer.siteUrl("ann", "mysite"));

            HttpURLConnection ok = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/ann/mysite/").openConnection();
            assertEquals(200, ok.getResponseCode());
            try (InputStream in = ok.getInputStream()) {
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("Hello site"));
            }
            assertEquals("nosniff", ok.getHeaderField("X-Content-Type-Options"));

            HttpURLConnection missing = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/ann/mysite/nothing.html").openConnection();
            assertEquals(404, missing.getResponseCode());
            HttpURLConnection post = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/ann/mysite/").openConnection();
            post.setRequestMethod("POST");
            post.setDoOutput(true);
            post.getOutputStream().write(1);
            assertEquals(405, post.getResponseCode());
        } finally {
            PagesServer.stop();
        }
        assertFalse(PagesServer.isRunning());
        assertNull(PagesServer.siteUrl("ann", "mysite"));
    }
}
