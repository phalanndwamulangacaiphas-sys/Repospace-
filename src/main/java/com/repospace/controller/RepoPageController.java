package com.repospace.controller;

import com.repospace.Repospace;
import com.repospace.model.Discussion;
import com.repospace.model.PinnedRepositories;
import com.repospace.model.Release;
import com.repospace.model.Repository;
import com.repospace.model.User;
import com.repospace.model.WikiPage;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.PagesServer;
import com.repospace.util.Session;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class RepoPageController {

    private static final String ALL = "All";

    @FXML
    private Label titleLabel;
    @FXML
    private Button pinButton;
    @FXML
    private Label metaLabel;
    @FXML
    private TabPane tabPane;

    @FXML
    private Label aboutLabel;
    @FXML
    private FlowPane topicsBox;
    @FXML
    private Hyperlink websiteLink;
    @FXML
    private VBox ownerAboutBox;
    @FXML
    private TextField descriptionField;
    @FXML
    private TextField websiteField;
    @FXML
    private TextField topicsField;
    @FXML
    private Label readmeSourceLabel;
    @FXML
    private VBox readmeBox;
    @FXML
    private VBox ownerReadmeBox;
    @FXML
    private TextArea readmeArea;

    @FXML
    private ListView<Release> releaseList;
    @FXML
    private Label releaseTitleLabel;
    @FXML
    private Label releaseMetaLabel;
    @FXML
    private VBox releaseNotesBox;
    @FXML
    private Button downloadReleaseButton;
    @FXML
    private Button deleteReleaseButton;
    @FXML
    private VBox ownerReleaseBox;
    @FXML
    private TextField tagField;
    @FXML
    private TextField releaseTitleField;
    @FXML
    private TextArea releaseNotesArea;
    @FXML
    private CheckBox prereleaseCheck;

    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private ListView<Discussion> discussionList;
    @FXML
    private ComboBox<String> newCategoryBox;
    @FXML
    private TextField newTitleField;
    @FXML
    private TextArea newBodyArea;
    @FXML
    private Label threadTitleLabel;
    @FXML
    private Label threadMetaLabel;
    @FXML
    private VBox threadBox;
    @FXML
    private TextArea replyArea;
    @FXML
    private Button replyButton;
    @FXML
    private Button closeThreadButton;
    @FXML
    private Button deleteThreadButton;

    @FXML
    private Tab wikiTab;
    @FXML
    private ListView<WikiPage> wikiList;
    @FXML
    private Label wikiTitleLabel;
    @FXML
    private Label wikiMetaLabel;
    @FXML
    private VBox wikiViewBox;
    @FXML
    private VBox ownerWikiBox;
    @FXML
    private TextField wikiTitleField;
    @FXML
    private TextArea wikiBodyArea;
    @FXML
    private Button deleteWikiButton;

    @FXML
    private Label pagesStatusLabel;
    @FXML
    private Button pagesToggleButton;
    @FXML
    private Button openSiteButton;
    @FXML
    private Label pagesBadgesLabel;

    @FXML
    private Label messageLabel;

    private User viewer;
    private Repository repo;
    private String ownerName = "";
    private boolean owner;
    private Release selectedRelease;
    private Discussion selectedDiscussion;
    private WikiPage selectedWiki;
    private int keepDiscussionID = -1;
    private boolean loading;

    @FXML
    private void initialize() {
        viewer = Session.getCurrentUser();
        try {
            repo = Repository.findById(Navigator.repositoryID());
            if (repo != null) {
                User o = User.findById(repo.getOwnerID());
                ownerName = o == null ? "unknown" : o.getUsername();
            }
        } catch (SQLException e) {
            ActivityLogger.error("Could not open a repository page", e);
        }
        if (repo == null || !repo.canBeViewedBy(viewer.getUserID())) {
            titleLabel.setText("This repository is not available");
            metaLabel.setText("It does not exist, or it is private.");
            pinButton.setDisable(true);
            tabPane.setDisable(true);
            return;
        }
        owner = repo.isOwnedBy(viewer.getUserID());

        setVisibleAndManaged(ownerAboutBox, owner);
        setVisibleAndManaged(ownerReadmeBox, owner);
        setVisibleAndManaged(ownerReleaseBox, owner);
        setVisibleAndManaged(ownerWikiBox, owner);
        setVisibleAndManaged(pinButton, owner);
        setVisibleAndManaged(pagesToggleButton, owner);

        releaseList.setCellFactory(list -> new ListCell<Release>() {
            @Override
            protected void updateItem(Release r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.getTag() + (r.isPrerelease() ? " (pre-release)" : "") + "  -  " + r.getTitle());
            }
        });
        discussionList.setCellFactory(list -> new ListCell<Discussion>() {
            @Override
            protected void updateItem(Discussion d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : "[" + d.getCategory() + "] " + d.getTitle() + (d.isClosed() ? "  (closed)" : "")
                        + "  -  " + d.getReplyCount() + " replies");
            }
        });
        wikiList.setCellFactory(list -> new ListCell<WikiPage>() {
            @Override
            protected void updateItem(WikiPage p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getTitle());
            }
        });
        categoryFilter.getItems().add(ALL);
        categoryFilter.getItems().addAll(Discussion.CATEGORIES);
        categoryFilter.setValue(ALL);
        newCategoryBox.getItems().setAll(Discussion.CATEGORIES);
        newCategoryBox.setValue("General");

        releaseList.getSelectionModel().selectedItemProperty().addListener((obs, o, r) -> {
            if (!loading) {
                showRelease(r);
            }
        });
        discussionList.getSelectionModel().selectedItemProperty().addListener((obs, o, d) -> {
            if (!loading) {
                showDiscussion(d);
            }
        });
        wikiList.getSelectionModel().selectedItemProperty().addListener((obs, o, p) -> {
            if (!loading) {
                showWiki(p);
            }
        });

        loadAbout();
        loadReleases();
        loadDiscussions();
        loadWiki();
        loadPages();
        showRelease(null);
        showDiscussion(null);
        showWiki(null);
    }

    private static void setVisibleAndManaged(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().remove("message");
        if (isError) {
            messageLabel.getStyleClass().add("message");
        }
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private void attempt(String failurePrefix, ThrowingAction action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            setMessage(messageOf(e), true);
        } catch (SQLException | IOException e) {
            ActivityLogger.error(failurePrefix, e);
            setMessage(failurePrefix + ": " + messageOf(e), true);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws SQLException, IOException;
    }

    private int uid() {
        return viewer.getUserID();
    }

    private void handleLink(String url) {
        if (url.startsWith("wiki:")) {
            openWikiPage(url.substring(5));
        } else {
            Repospace.openInBrowser(url);
        }
    }

    @FXML
    private void handleBack() {
        Navigator.back();
    }

    private void loadAbout() {
        titleLabel.setText(ownerName + " / " + repo.getName());
        metaLabel.setText((repo.isPublic() ? "Public" : "Private") + " repository  -  created " + DateUtil.format(repo.getCreatedDate())
                + (owner ? "  -  tracking " + repo.getSourcePath() : ""));
        aboutLabel.setText(repo.getDescription().isEmpty() ? "No description." : repo.getDescription());
        topicsBox.getChildren().clear();
        for (String topic : repo.getTopicList()) {
            Label pill = new Label(topic);
            pill.getStyleClass().add("pill");
            topicsBox.getChildren().add(pill);
        }
        websiteLink.setText(repo.getWebsite());
        setVisibleAndManaged(websiteLink, !repo.getWebsite().isEmpty());
        if (owner) {
            descriptionField.setText(repo.getDescription());
            websiteField.setText(repo.getWebsite());
            topicsField.setText(String.join(", ", repo.getTopicList()));
            readmeArea.setText(repo.getReadme());
            updatePinButton();
        }
        attempt("Could not load the README", () -> {
            String text = repo.readmeFor(uid());
            boolean fromFile = repo.readmeComesFromFiles();
            readmeSourceLabel.setText(fromFile ? "Shown from README.md in the latest saved version."
                    : "Written in Repospace (add a README.md to the project folder and commit it to replace this).");
            readmeBox.getChildren().setAll(MarkdownView.render(text.isBlank() ? "*This repository has no README yet.*" : text, this::handleLink));
        });
    }

    private void updatePinButton() {
        try {
            pinButton.setText(PinnedRepositories.isPinned(uid(), repo.getRepoID()) ? "Unpin from my page" : "Pin to my page");
        } catch (SQLException e) {
            pinButton.setText("Pin to my page");
        }
    }

    @FXML
    private void handleTogglePin() {
        attempt("Could not change the pin", () -> {
            if (PinnedRepositories.isPinned(uid(), repo.getRepoID())) {
                PinnedRepositories.unpin(uid(), repo.getRepoID());
                setMessage("Unpinned from your page.", false);
            } else {
                PinnedRepositories.pin(uid(), repo);
                setMessage("Pinned to your page (at most " + PinnedRepositories.MAX_PINS + ").", false);
            }
            updatePinButton();
        });
    }

    @FXML
    private void handleOpenWebsite() {
        Repospace.openInBrowser(repo.getWebsite());
    }

    @FXML
    private void handleSaveAbout() {
        attempt("Could not save About", () -> {
            repo.updateAbout(uid(), descriptionField.getText(), websiteField.getText(), topicsField.getText());
            loadAbout();
            setMessage("About saved.", false);
        });
    }

    @FXML
    private void handlePreviewReadme() {
        readmeSourceLabel.setText("Preview of what you typed (not saved yet).");
        readmeBox.getChildren().setAll(MarkdownView.render(readmeArea.getText(), this::handleLink));
    }

    @FXML
    private void handleSaveReadme() {
        attempt("Could not save the README", () -> {
            repo.saveReadme(uid(), readmeArea.getText());
            loadAbout();
            setMessage("README saved.", false);
        });
    }

    private void loadReleases() {
        attempt("Could not load releases", () -> {
            loading = true;
            try {
                releaseList.getItems().setAll(Release.findByRepo(repo, uid()));
            } finally {
                loading = false;
            }
        });
    }

    private void showRelease(Release r) {
        selectedRelease = r;
        releaseNotesBox.getChildren().clear();
        if (r == null) {
            releaseTitleLabel.setText("Select a release");
            releaseMetaLabel.setText("");
        } else {
            releaseTitleLabel.setText(r.getTag() + "  -  " + r.getTitle());
            String version = "";
            try {
                version = r.getCommit() == null ? "" : "  -  saved version v" + r.getCommit().getVersionNo();
            } catch (SQLException e) {
                ActivityLogger.error("Could not read the release's version", e);
            }
            releaseMetaLabel.setText("Published " + DateUtil.format(r.getCreatedDate()) + version + (r.isPrerelease() ? "  -  pre-release" : ""));
            releaseNotesBox.getChildren().setAll(MarkdownView.render(r.getNotes().isBlank() ? "*No release notes.*" : r.getNotes(), this::handleLink));
        }
        downloadReleaseButton.setDisable(r == null);
        deleteReleaseButton.setDisable(r == null || !owner);
        setVisibleAndManaged(deleteReleaseButton, owner);
    }

    @FXML
    private void handlePublishRelease() {
        attempt("Could not publish the release", () -> {
            Release made = Release.publish(repo, uid(), tagField.getText(), releaseTitleField.getText(), releaseNotesArea.getText(), prereleaseCheck.isSelected());
            tagField.clear();
            releaseTitleField.clear();
            releaseNotesArea.clear();
            prereleaseCheck.setSelected(false);
            loadReleases();
            setMessage("Release " + made.getTag() + " published.", false);
        });
    }

    @FXML
    private void handleDownloadRelease() {
        Release r = selectedRelease;
        if (r == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save release " + r.getTag() + " as a ZIP file");
        chooser.setInitialFileName(repo.getName() + "-" + r.getTag() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File file = chooser.showSaveDialog(releaseList.getScene().getWindow());
        if (file == null) {
            return;
        }
        Path target = file.getName().toLowerCase().endsWith(".zip") ? file.toPath() : file.toPath().resolveSibling(file.getName() + ".zip");
        attempt("Download failed", () -> {
            int count = r.downloadZip(repo, uid(), target);
            setMessage(count + " files of release " + r.getTag() + " saved in " + target, false);
        });
    }

    @FXML
    private void handleDeleteRelease() {
        Release r = selectedRelease;
        if (r == null) {
            return;
        }
        attempt("Could not delete the release", () -> {
            r.delete(repo, uid());
            loadReleases();
            showRelease(null);
            setMessage("Release deleted.", false);
        });
    }

    private void loadDiscussions() {
        attempt("Could not load discussions", () -> {
            String filter = categoryFilter.getValue();
            List<Discussion> list = Discussion.findByRepo(repo, uid(), filter == null || ALL.equals(filter) ? null : filter);
            int keep = keepDiscussionID;
            loading = true;
            try {
                discussionList.getItems().setAll(list);
                for (Discussion d : list) {
                    if (d.getDiscussionID() == keep) {
                        discussionList.getSelectionModel().select(d);
                    }
                }
            } finally {
                loading = false;
            }
        });
    }

    @FXML
    private void handleFilterDiscussions() {
        if (repo != null) {
            keepDiscussionID = -1;
            loadDiscussions();
            showDiscussion(null);
        }
    }

    private void showDiscussion(Discussion d) {
        selectedDiscussion = d;
        keepDiscussionID = d == null ? -1 : d.getDiscussionID();
        threadBox.getChildren().clear();
        if (d == null) {
            threadTitleLabel.setText("Select a discussion");
            threadMetaLabel.setText("");
        } else {
            threadTitleLabel.setText(d.getTitle());
            threadMetaLabel.setText(d.getCategory() + "  -  started by " + d.getAuthorName() + " on " + DateUtil.format(d.getCreatedDate())
                    + (d.isClosed() ? "  -  CLOSED" : ""));
            threadBox.getChildren().add(post(d.getAuthorName(), d.getCreatedDate(), d.getBody()));
            attempt("Could not load the replies", () -> {
                for (Discussion.Reply reply : d.replies(repo, uid())) {
                    threadBox.getChildren().add(post(reply.getAuthorName(), reply.getCreatedDate(), reply.getBody()));
                }
            });
        }
        boolean mayManage = d != null && (owner || d.getAuthorID() == uid());
        replyButton.setDisable(d == null || d.isClosed());
        replyArea.setDisable(d == null || d.isClosed());
        closeThreadButton.setDisable(!mayManage);
        closeThreadButton.setText(d != null && d.isClosed() ? "Reopen discussion" : "Close discussion");
        deleteThreadButton.setDisable(!mayManage);
    }

    private VBox post(String author, java.time.LocalDateTime date, String body) {
        VBox box = new VBox(4);
        box.getStyleClass().add("card");
        box.getChildren().add(new Label(author + "  -  " + DateUtil.format(date)));
        box.getChildren().add(MarkdownView.render(body, this::handleLink));
        return box;
    }

    @FXML
    private void handleStartDiscussion() {
        attempt("Could not start the discussion", () -> {
            Discussion d = Discussion.start(repo, viewer, newCategoryBox.getValue(), newTitleField.getText(), newBodyArea.getText());
            newTitleField.clear();
            newBodyArea.clear();
            keepDiscussionID = d.getDiscussionID();
            loadDiscussions();
            showDiscussion(discussionList.getSelectionModel().getSelectedItem());
            setMessage("Discussion started.", false);
        });
    }

    @FXML
    private void handleReply() {
        Discussion d = selectedDiscussion;
        if (d == null) {
            return;
        }
        attempt("Could not send the reply", () -> {
            d.reply(repo, viewer, replyArea.getText());
            replyArea.clear();
            loadDiscussions();
            showDiscussion(discussionList.getSelectionModel().getSelectedItem());
            setMessage("Reply sent.", false);
        });
    }

    @FXML
    private void handleToggleClosed() {
        Discussion d = selectedDiscussion;
        if (d == null) {
            return;
        }
        attempt("Could not change the discussion", () -> {
            d.setClosed(repo, uid(), !d.isClosed());
            loadDiscussions();
            showDiscussion(discussionList.getSelectionModel().getSelectedItem());
        });
    }

    @FXML
    private void handleDeleteDiscussion() {
        Discussion d = selectedDiscussion;
        if (d == null) {
            return;
        }
        attempt("Could not delete the discussion", () -> {
            d.delete(repo, uid());
            keepDiscussionID = -1;
            loadDiscussions();
            showDiscussion(null);
            setMessage("Discussion deleted.", false);
        });
    }

    private void loadWiki() {
        attempt("Could not load the wiki", () -> {
            int keep = selectedWiki == null ? -1 : selectedWiki.getPageID();
            List<WikiPage> pages = WikiPage.findByRepo(repo, uid());
            loading = true;
            try {
                wikiList.getItems().setAll(pages);
                for (WikiPage p : pages) {
                    if (p.getPageID() == keep) {
                        wikiList.getSelectionModel().select(p);
                    }
                }
            } finally {
                loading = false;
            }
        });
    }

    private void showWiki(WikiPage p) {
        selectedWiki = p;
        wikiViewBox.getChildren().clear();
        if (p == null) {
            wikiTitleLabel.setText("Select a wiki page");
            wikiMetaLabel.setText(wikiList.getItems().isEmpty() ? "This wiki has no pages yet." : "");
        } else {
            wikiTitleLabel.setText(p.getTitle());
            wikiMetaLabel.setText("Revision " + p.getRevision() + "  -  edited by " + p.getUpdatedBy() + " on " + DateUtil.format(p.getUpdatedDate()));
            wikiViewBox.getChildren().setAll(MarkdownView.render(p.getBody().isBlank() ? "*This page is empty.*" : p.getBody(), this::handleLink));
            wikiTitleField.setText(p.getTitle());
            wikiBodyArea.setText(p.getBody());
        }
        deleteWikiButton.setDisable(p == null);
    }

    private void openWikiPage(String title) {
        tabPane.getSelectionModel().select(wikiTab);
        for (WikiPage p : wikiList.getItems()) {
            if (p.getTitle().equalsIgnoreCase(title)) {
                wikiList.getSelectionModel().select(p);
                showWiki(p);
                return;
            }
        }
        if (owner) {
            wikiList.getSelectionModel().clearSelection();
            showWiki(null);
            wikiTitleField.setText(title);
            wikiBodyArea.clear();
            setMessage("The page '" + title + "' does not exist yet - write it below and save it.", false);
        } else {
            setMessage("The page '" + title + "' does not exist.", true);
        }
    }

    @FXML
    private void handleNewWikiPage() {
        wikiList.getSelectionModel().clearSelection();
        showWiki(null);
        wikiTitleField.clear();
        wikiBodyArea.clear();
    }

    @FXML
    private void handleSaveWiki() {
        attempt("Could not save the wiki page", () -> {
            WikiPage saved = WikiPage.save(repo, viewer, wikiTitleField.getText(), wikiBodyArea.getText());
            selectedWiki = saved;
            loadWiki();
            showWiki(saved);
            setMessage("Wiki page saved.", false);
        });
    }

    @FXML
    private void handleDeleteWiki() {
        WikiPage p = selectedWiki;
        if (p == null) {
            return;
        }
        attempt("Could not delete the wiki page", () -> {
            p.delete(repo, uid());
            selectedWiki = null;
            loadWiki();
            showWiki(null);
            wikiTitleField.clear();
            wikiBodyArea.clear();
            setMessage("Wiki page deleted.", false);
        });
    }

    private void loadPages() {
        if (repo.isPagesEnabled() && !PagesServer.isRunning()) {
            try {
                PagesServer.start(PagesServer.DEFAULT_PORT);
            } catch (IOException e) {
                ActivityLogger.error("Could not start the Pages server", e);
            }
        }
        String url = PagesServer.siteUrl(ownerName, repo.getName());
        if (!repo.isPublic()) {
            pagesStatusLabel.setText("Pages is off. A site is public, so the repository must be public first.");
        } else if (repo.isPagesEnabled()) {
            pagesStatusLabel.setText(url == null ? "Pages is on, but the local web server could not be started." : "Pages is ON. Your site: " + url);
        } else {
            pagesStatusLabel.setText("Pages is off for this repository.");
        }
        pagesToggleButton.setText(repo.isPagesEnabled() ? "Turn off Pages" : "Turn on Pages");
        pagesToggleButton.setDisable(!repo.isPublic() && !repo.isPagesEnabled());
        openSiteButton.setDisable(!repo.isPagesEnabled() || url == null);
        if (owner && PagesServer.isRunning()) {
            String base = "http://localhost:" + PagesServer.getPort() + "/badges/" + ownerName + "/";
            pagesBadgesLabel.setText("Live badges of your profile (they update by themselves), for example in a site's HTML:\n"
                    + base + "commits.svg\n" + base + "repositories.svg\n" + base + "streak.svg\n" + base + "top-language.svg");
        } else {
            pagesBadgesLabel.setText("");
        }
    }

    @FXML
    private void handleTogglePages() {
        attempt("Could not change Pages", () -> {
            boolean turnOn = !repo.isPagesEnabled();
            repo.setPagesEnabled(uid(), turnOn);
            if (turnOn) {
                PagesServer.start(PagesServer.DEFAULT_PORT);
            }
            loadPages();
            setMessage(turnOn ? "Pages is on." : "Pages is off.", false);
        });
    }

    @FXML
    private void handleOpenSite() {
        String url = PagesServer.siteUrl(ownerName, repo.getName());
        if (url == null) {
            setMessage("The local web server is not running.", true);
            return;
        }
        Repospace.openInBrowser(url);
    }
}
