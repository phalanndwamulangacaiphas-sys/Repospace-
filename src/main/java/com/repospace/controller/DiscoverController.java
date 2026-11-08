package com.repospace.controller;

import com.repospace.model.Profile;
import com.repospace.model.Projects;
import com.repospace.model.Repository;
import com.repospace.model.User;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.FileUtil;
import com.repospace.util.Session;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class DiscoverController {

    @FXML
    private ComboBox<String> modeBox;
    @FXML
    private TextField searchField;
    @FXML
    private ListView<User> resultList;
    @FXML
    private ImageView pictureView;
    @FXML
    private Label nameLabel;
    @FXML
    private Label joinedLabel;
    @FXML
    private Label bioLabel;
    @FXML
    private ListView<Projects> projectList;
    @FXML
    private ListView<Repository> repoList;
    @FXML
    private Button profileButton;
    @FXML
    private Button openRepoButton;
    @FXML
    private Button zipButton;
    @FXML
    private Button folderButton;
    @FXML
    private Label messageLabel;

    private static final String PEOPLE = "People";
    private static final String REPOSITORIES = "Repositories";

    private User viewer;
    private final Map<Integer, String> ownerNames = new HashMap<>();

    @FXML
    private void initialize() {
        viewer = Session.getCurrentUser();
        modeBox.getItems().setAll(PEOPLE, REPOSITORIES);
        modeBox.setValue(PEOPLE);
        resultList.setCellFactory(list -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getUsername());
            }
        });
        projectList.setCellFactory(list -> new ListCell<Projects>() {
            @Override
            protected void updateItem(Projects project, boolean empty) {
                super.updateItem(project, empty);
                setText(empty || project == null ? null
                        : project.getName() + "  [" + project.getStatus() + "]"
                        + (project.getDescription().isEmpty() ? "" : "  - " + project.getDescription()));
            }
        });
        repoList.setCellFactory(list -> new ListCell<Repository>() {
            @Override
            protected void updateItem(Repository repo, boolean empty) {
                super.updateItem(repo, empty);
                String owner = ownerNames.get(repo == null ? 0 : repo.getOwnerID());
                setText(empty || repo == null ? null
                        : (owner == null ? "" : owner + " / ") + repo.getName()
                        + (repo.getTopics().isEmpty() ? "" : "  [" + repo.getTopics().replace(",", ", ") + "]")
                        + (repo.getDescription().isEmpty() ? "" : "  - " + repo.getDescription()));
            }
        });
        resultList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> showUser(selected));
        repoList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> updateButtons());
        updateButtons();
    }

    private void updateButtons() {
        boolean none = repoList.getSelectionModel().getSelectedItem() == null;
        zipButton.setDisable(none);
        folderButton.setDisable(none);
        openRepoButton.setDisable(none);
        profileButton.setDisable(resultList.getSelectionModel().getSelectedItem() == null);
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

    @FXML
    private void handleModeChanged() {
        boolean people = PEOPLE.equals(modeBox.getValue());
        searchField.setPromptText(people ? "Search for a username" : "Search public repositories by name, description or topic");
        searchField.clear();
    }

    private void searchRepositories() {
        try {
            List<Repository> found = Repository.searchPublic(searchField.getText());
            resultList.getItems().clear();
            showUser(null);
            for (Repository r : found) {
                User owner = User.findById(r.getOwnerID());
                ownerNames.put(r.getOwnerID(), owner == null ? "unknown" : owner.getUsername());
            }
            repoList.getItems().setAll(found);
            nameLabel.setText("Repository search");
            setMessage(found.isEmpty() ? "No public repositories found." : found.size() + " public repositories found. Select one to open or download it.", false);
        } catch (SQLException e) {
            setMessage("Search failed: " + e.getMessage(), true);
        }
        updateButtons();
    }

    @FXML
    private void handleOpenProfile() {
        User selected = resultList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Navigator.openUserPage(selected.getUserID(), "discover.fxml");
        }
    }

    @FXML
    private void handleOpenRepo() {
        Repository repo = repoList.getSelectionModel().getSelectedItem();
        if (repo != null) {
            Navigator.openRepository(repo.getRepoID(), "discover.fxml");
        }
    }

    @FXML
    private void handleSearch() {
        if (REPOSITORIES.equals(modeBox.getValue())) {
            searchRepositories();
            return;
        }
        try {
            List<User> found = User.searchUsers(searchField.getText());
            resultList.getItems().setAll(found);
            showUser(null);
            setMessage(found.isEmpty() ? "No users found." : found.size() + " user(s) found. Select one to see their profile.", false);
        } catch (SQLException e) {
            setMessage("Search failed: " + e.getMessage(), true);
        }
    }

    private void showUser(User other) {
        projectList.getItems().clear();
        repoList.getItems().clear();
        ownerNames.clear();
        pictureView.setImage(null);
        joinedLabel.setText("");
        bioLabel.setText("");
        if (other == null) {
            nameLabel.setText("Select a user");
            updateButtons();
            return;
        }
        nameLabel.setText(other.getUsername());
        try {
            Profile profile = Profile.forViewer(viewer.getUserID(), other.getUserID());
            if (profile.isHidden()) {
                joinedLabel.setText("This profile is private.");
            } else {
                joinedLabel.setText("Member since " + DateUtil.format(profile.getJoinDate()));
                bioLabel.setText(profile.getBio().isEmpty() ? "(no bio)" : profile.getBio());
                String picture = profile.getProfilePicture();
                if (!picture.isBlank() && new File(picture).isFile()) {
                    pictureView.setImage(new Image(new File(picture).toURI().toString(), 90, 90, true, true));
                }
            }
            projectList.getItems().setAll(Projects.findPublicByUser(other.getUserID()));
            repoList.getItems().setAll(Repository.findPublicByOwner(other.getUserID()));
        } catch (SQLException e) {
            ActivityLogger.error("Could not load a profile", e);
            setMessage("Could not load this profile: " + e.getMessage(), true);
        }
        updateButtons();
    }

    @FXML
    private void handleDownloadZip() {
        Repository repo = repoList.getSelectionModel().getSelectedItem();
        if (repo == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save '" + repo.getName() + "' as a ZIP file");
        chooser.setInitialFileName(repo.getName() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP files", "*.zip"));
        File file = chooser.showSaveDialog(repoList.getScene().getWindow());
        if (file == null) {
            return;
        }
        Path target = file.getName().toLowerCase().endsWith(".zip") ? file.toPath() : file.toPath().resolveSibling(file.getName() + ".zip");
        download(repo, target);
    }

    @FXML
    private void handleDownloadFolder() {
        Repository repo = repoList.getSelectionModel().getSelectedItem();
        if (repo == null) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose an empty folder for '" + repo.getName() + "'");
        File folder = chooser.showDialog(repoList.getScene().getWindow());
        if (folder == null) {
            return;
        }
        try {
            if (!FileUtil.isMissingOrEmptyFolder(folder.toPath())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please choose an empty folder (or make a new one).", ButtonType.OK);
                alert.setHeaderText("The folder is not empty");
                alert.showAndWait();
                return;
            }
        } catch (IOException e) {
            setMessage("Could not check the folder: " + messageOf(e), true);
            return;
        }
        download(repo, folder.toPath());
    }

    private void download(Repository repo, Path target) {
        try {
            int count = repo.download(viewer.getUserID(), target);
            setMessage(count + " files of '" + repo.getName() + "' saved in " + target, false);
        } catch (SQLException | IOException | IllegalStateException e) {
            setMessage("Download failed: " + messageOf(e), true);
        }
    }
}
