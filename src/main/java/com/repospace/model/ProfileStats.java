package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProfileStats {

    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        String[][] pairs = {
            {"java", "Java"}, {"kt", "Kotlin"}, {"py", "Python"}, {"js", "JavaScript"}, {"ts", "TypeScript"},
            {"html", "HTML"}, {"htm", "HTML"}, {"css", "CSS"}, {"fxml", "FXML"}, {"xml", "XML"}, {"sql", "SQL"},
            {"c", "C"}, {"h", "C"}, {"cpp", "C++"}, {"hpp", "C++"}, {"cs", "C#"}, {"go", "Go"}, {"rs", "Rust"},
            {"php", "PHP"}, {"rb", "Ruby"}, {"swift", "Swift"}, {"sh", "Shell"}, {"bat", "Batch"}, {"md", "Markdown"}
        };
        for (String[] p : pairs) {
            LANGUAGES.put(p[0], p[1]);
        }
    }

    private final int userID;
    private final String username;
    private final LocalDateTime memberSince;
    private int repositories;
    private int commits;
    private int files;
    private int releases;
    private int sponsors;
    private int pinned;
    private int currentStreak;
    private int longestStreak;
    private final Map<String, Long> languageBytes = new TreeMap<>();
    private final List<String> activity = new ArrayList<>();

    private ProfileStats(int userID, String username, LocalDateTime memberSince) {
        this.userID = userID;
        this.username = username;
        this.memberSince = memberSince;
    }

    public static ProfileStats of(int viewerID, int userID) throws SQLException {
        User user = User.findById(userID);
        if (user == null) {
            throw new IllegalArgumentException("No such user.");
        }
        Profile profile = Profile.forUser(userID);
        ProfileStats s = new ProfileStats(userID, user.getUsername(), profile.getJoinDate());
        boolean own = viewerID == userID;
        List<Repository> repos = own ? Repository.findByOwner(userID) : Repository.findPublicByOwner(userID);
        s.repositories = repos.size();
        TreeSet<LocalDate> days = new TreeSet<>();
        List<String[]> feed = new ArrayList<>();
        for (Repository repo : repos) {
            List<Commit> list = Commit.findByRepo(repo.getRepoID());
            s.commits += list.size();
            for (Commit c : list) {
                days.add(c.getDate().toLocalDate());
                feed.add(new String[]{DateUtil.toDb(c.getDate()), DateUtil.format(c.getDate()) + "  committed v" + c.getVersionNo()
                    + " to " + repo.getName() + ": " + c.getMessage()});
            }
            if (!list.isEmpty()) {
                Commit latest = list.get(0);
                Map<String, Long> sizes = latest.loadFileSizes();
                s.files += sizes.size();
                for (Map.Entry<String, Long> e : sizes.entrySet()) {
                    String language = languageOf(e.getKey());
                    if (language != null) {
                        s.languageBytes.merge(language, Math.max(1L, e.getValue()), Long::sum);
                    }
                }
            }
            for (Release r : Release.findByRepo(repo, userID)) {
                s.releases++;
                feed.add(new String[]{DateUtil.toDb(r.getCreatedDate()), DateUtil.format(r.getCreatedDate()) + "  released " + r.getTag()
                    + " of " + repo.getName()});
            }
        }
        s.pinned = PinnedRepositories.pinnedFor(userID, viewerID).size();
        s.sponsors = Sponsorship.countSponsors(userID);
        s.computeStreaks(days);
        feed.sort((a, b) -> b[0].compareTo(a[0]));
        for (String[] f : feed) {
            s.activity.add(f[1]);
        }
        return s;
    }

    static String languageOf(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot < 0 || dot < slash) {
            return null;
        }
        return LANGUAGES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private void computeStreaks(TreeSet<LocalDate> days) {
        int run = 0;
        LocalDate previous = null;
        for (LocalDate d : days) {
            run = previous != null && previous.plusDays(1).equals(d) ? run + 1 : 1;
            longestStreak = Math.max(longestStreak, run);
            previous = d;
        }
        LocalDate day = LocalDate.now();
        if (!days.contains(day)) {
            day = day.minusDays(1);
        }
        int streak = 0;
        while (days.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        currentStreak = streak;
    }

    public Map<String, Integer> languagePercentages() {
        long total = languageBytes.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Integer> result = new LinkedHashMap<>();
        if (total == 0) {
            return result;
        }
        languageBytes.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> result.put(e.getKey(), (int) Math.round(100.0 * e.getValue() / total)));
        return result;
    }

    public String topLanguage() {
        Map<String, Integer> p = languagePercentages();
        return p.isEmpty() ? "none yet" : p.keySet().iterator().next();
    }

    public List<Badge> badges() {
        List<Badge> list = new ArrayList<>();
        list.add(new Badge("repositories", "repositories", String.valueOf(repositories), "#106ffd"));
        list.add(new Badge("commits", "commits", String.valueOf(commits), "#149dfd"));
        list.add(new Badge("streak", "streak", currentStreak + (currentStreak == 1 ? " day" : " days"), currentStreak > 0 ? "#2e9e5b" : "#6b7a99"));
        list.add(new Badge("top-language", "top language", topLanguage(), "#463afc"));
        list.add(new Badge("releases", "releases", String.valueOf(releases), "#c77d12"));
        list.add(new Badge("sponsors", "sponsors", String.valueOf(sponsors), "#b83280"));
        list.add(new Badge("member-since", "member since", memberSince == null ? "-" : memberSince.toLocalDate().toString(), "#34c3fd"));
        return list;
    }

    public Badge badge(String key) {
        for (Badge b : badges()) {
            if (b.getKey().equals(key)) {
                return b;
            }
        }
        return null;
    }

    public String expand(String text) {
        if (text == null) {
            return "";
        }
        String out = text;
        out = out.replace("{{username}}", username);
        out = out.replace("{{repos}}", String.valueOf(repositories));
        out = out.replace("{{commits}}", String.valueOf(commits));
        out = out.replace("{{files}}", String.valueOf(files));
        out = out.replace("{{streak}}", String.valueOf(currentStreak));
        out = out.replace("{{longest-streak}}", String.valueOf(longestStreak));
        out = out.replace("{{top-language}}", topLanguage());
        out = out.replace("{{releases}}", String.valueOf(releases));
        out = out.replace("{{sponsors}}", String.valueOf(sponsors));
        out = out.replace("{{pinned}}", String.valueOf(pinned));
        out = out.replace("{{member-since}}", memberSince == null ? "" : memberSince.toLocalDate().toString());
        return out;
    }

    public List<String> recentActivity(int limit) {
        return new ArrayList<>(activity.subList(0, Math.min(limit, activity.size())));
    }

    public static Map<LocalDate, Integer> commitsPerDay(int viewerID, int userID, int days) throws SQLException {
        Map<LocalDate, Integer> map = new TreeMap<>();
        String since = DateUtil.toDb(DateUtil.now().minusDays(days).toLocalDate().atStartOfDay());
        String sql = "SELECT c.commit_date FROM commits c JOIN repositories r ON r.id = c.repo_id WHERE r.owner_id = ? AND c.commit_date >= ?"
                + (viewerID == userID ? "" : " AND r.visibility = 'public'");
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userID);
            ps.setString(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.merge(DateUtil.fromDb(rs.getString(1)).toLocalDate(), 1, Integer::sum);
                }
            }
        }
        return map;
    }

    public int getUserID() {
        return userID;
    }

    public String getUsername() {
        return username;
    }

    public int getRepositories() {
        return repositories;
    }

    public int getCommits() {
        return commits;
    }

    public int getFiles() {
        return files;
    }

    public int getReleases() {
        return releases;
    }

    public int getSponsors() {
        return sponsors;
    }

    public int getPinned() {
        return pinned;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }
}
