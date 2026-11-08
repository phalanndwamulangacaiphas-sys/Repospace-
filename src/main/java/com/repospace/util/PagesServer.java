package com.repospace.util;

import com.repospace.model.Badge;
import com.repospace.model.Commit;
import com.repospace.model.Profile;
import com.repospace.model.ProfileStats;
import com.repospace.model.Repository;
import com.repospace.model.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PagesServer {

    public static final int DEFAULT_PORT = 8765;
    private static final long MAX_FILE = 20L * 1024 * 1024;

    private static HttpServer server;
    private static ExecutorService executor;

    private PagesServer() {
    }

    public static final class Response {
        private final int status;
        private final String contentType;
        private final byte[] body;

        Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBody() {
            return body;
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public static synchronized int start(int preferredPort) throws IOException {
        if (server != null) {
            return server.getAddress().getPort();
        }
        HttpServer created;
        try {
            created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), preferredPort), 0);
        } catch (IOException busy) {
            created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        }
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread t = new Thread(runnable, "repospace-pages");
            t.setDaemon(true);
            return t;
        });
        created.setExecutor(executor);
        created.createContext("/", PagesServer::serve);
        created.start();
        server = created;
        ActivityLogger.info("Pages server started on port " + created.getAddress().getPort());
        return created.getAddress().getPort();
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            server = null;
            executor = null;
            ActivityLogger.info("Pages server stopped");
        }
    }

    public static synchronized boolean isRunning() {
        return server != null;
    }

    public static synchronized int getPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public static synchronized String siteUrl(String username, String repoName) {
        if (server == null) {
            return null;
        }
        return "http://localhost:" + getPort() + encodePath("/" + username + "/" + repoName + "/");
    }

    static String encodePath(String path) {
        try {
            return new java.net.URI(null, null, path, null).getRawPath();
        } catch (java.net.URISyntaxException e) {
            return path.replace(" ", "%20");
        }
    }

    private static void serve(HttpExchange exchange) throws IOException {
        try {
            Response r = handle(exchange.getRequestMethod(), exchange.getRequestURI().getPath());
            exchange.getResponseHeaders().set("Content-Type", r.getContentType());
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
            exchange.sendResponseHeaders(r.getStatus(), head ? -1 : r.getBody().length == 0 ? -1 : r.getBody().length);
            if (!head && r.getBody().length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(r.getBody());
                }
            }
        } finally {
            exchange.close();
        }
    }

    public static Response handle(String method, String path) {
        try {
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                return page(405, "Method not allowed", "<p>Pages only answers GET requests.</p>");
            }
            if (path == null || !path.startsWith("/") || path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
                return page(400, "Bad request", "<p>That address is not valid.</p>");
            }
            String[] parts = path.substring(1).split("/", -1);
            for (String part : parts) {
                if (part.equals("..") || part.equals(".")) {
                    return page(400, "Bad request", "<p>That address is not valid.</p>");
                }
            }
            if (path.equals("/")) {
                return siteList();
            }
            if (parts[0].equals("badges")) {
                return badge(parts);
            }
            if (parts.length < 2) {
                return notFound();
            }
            return site(parts);
        } catch (SQLException | IOException | RuntimeException e) {
            ActivityLogger.error("Pages request failed: " + path, e);
            return page(500, "Something went wrong", "<p>This page could not be shown.</p>");
        }
    }

    private static Response siteList() throws SQLException {
        StringBuilder sb = new StringBuilder("<p>Public repositories with a Pages site:</p><ul>");
        int count = 0;
        for (Repository repo : Repository.findPagesSites()) {
            User owner = User.findById(repo.getOwnerID());
            if (owner != null) {
                String url = "/" + owner.getUsername() + "/" + repo.getName() + "/";
                sb.append("<li><a href=\"").append(Markdown.escape(encodePath(url))).append("\">")
                        .append(Markdown.escape(owner.getUsername() + "/" + repo.getName())).append("</a></li>");
                count++;
            }
        }
        sb.append("</ul>");
        if (count == 0) {
            sb = new StringBuilder("<p>No Pages sites yet. Open a public repository in Repospace and turn on Pages.</p>");
        }
        return page(200, "Repospace Pages", sb.toString());
    }

    private static Response badge(String[] parts) throws SQLException {
        if (parts.length != 3 || !parts[2].endsWith(".svg")) {
            return notFound();
        }
        User user = User.findByUsername(parts[1]);
        if (user == null || Profile.forViewer(0, user.getUserID()).isHidden()) {
            return notFound();
        }
        Badge badge = ProfileStats.of(0, user.getUserID()).badge(parts[2].substring(0, parts[2].length() - 4));
        if (badge == null) {
            return notFound();
        }
        return new Response(200, "image/svg+xml; charset=utf-8", badge.toSvg().getBytes(StandardCharsets.UTF_8));
    }

    private static Response site(String[] parts) throws SQLException, IOException {
        User owner = User.findByUsername(parts[0]);
        if (owner == null) {
            return notFound();
        }
        Repository repo = Repository.findByOwnerAndName(owner.getUserID(), parts[1]);
        if (repo == null || !repo.isPagesEnabled() || !repo.isPublic()) {
            return notFound();
        }
        Commit latest = Commit.latestFor(repo.getRepoID());
        if (latest == null) {
            return page(200, repo.getName(), "<p>This site has no saved version yet.</p>");
        }
        StringBuilder wanted = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) {
                wanted.append('/');
            }
            wanted.append(parts[i]);
        }
        String file = wanted.toString();
        if (file.isEmpty() || file.endsWith("/")) {
            file = file + "index.html";
        }
        Map<String, String> manifest = latest.loadManifest();
        if (!manifest.containsKey(file) && !file.contains(".")) {
            file = file + "/index.html";
        }
        if (manifest.containsKey(file)) {
            byte[] bytes = latest.readFile(manifest, file, MAX_FILE);
            return new Response(200, contentType(file), bytes);
        }
        if (file.equals("index.html")) {
            String readme = repo.readmeFor(owner.getUserID());
            if (readme != null && !readme.isBlank()) {
                return page(200, repo.getName(), Markdown.toHtml(readme));
            }
        }
        return notFound();
    }

    private static Response notFound() {
        return page(404, "Not found", "<p>There is nothing at this address.</p>");
    }

    private static Response page(int status, String title, String bodyHtml) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + Markdown.escape(title) + "</title>"
                + "<style>body{font-family:Segoe UI,Arial,sans-serif;max-width:760px;margin:40px auto;padding:0 16px;color:#050b1b}"
                + "a{color:#106ffd}code,pre{background:#eef3ff;border-radius:4px}pre{padding:10px;overflow:auto}"
                + "blockquote{border-left:4px solid #149dfd;margin-left:0;padding-left:12px;color:#334}</style></head><body>"
                + "<h1>" + Markdown.escape(title) + "</h1>" + bodyHtml + "</body></html>";
        return new Response(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    static String contentType(String file) {
        String name = file.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "html", "htm" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "txt", "md" -> "text/plain; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "ico" -> "image/x-icon";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    public static List<String> describeSites() throws SQLException {
        List<String> names = new java.util.ArrayList<>();
        for (Repository repo : Repository.findPagesSites()) {
            User owner = User.findById(repo.getOwnerID());
            if (owner != null) {
                names.add(owner.getUsername() + "/" + repo.getName());
            }
        }
        return names;
    }
}
