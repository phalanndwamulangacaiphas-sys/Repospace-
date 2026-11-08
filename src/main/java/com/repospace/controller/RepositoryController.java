package com.repospace.controller;

import com.repospace.model.Commit;
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
import java.util.List;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class RepositoryController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField descriptionField;
    @FXML
    private ComboBox<String> visibilityBox;
    @FXML
    private TextField sourceField;
    @FXML
    private ListView<Repository> repoList;
    @FXML
    private Label selectedLabel;
    @FXML
    private TextField commitMessageField;
    @FXML
    private Button commitButton;
    @FXML
    private Button checkButton;
    @FXML
    private Button openPageButton;
    @FXML
    private Button filesButton;
    @FXML
    private Button restoreButton;
    @FXML
    private Button cloneButton;
    @FXML
    private Button downloadButton;
    @FXML
    private Button visibilityButton;
    @FXML
    private Button deleteButton;
    @FXML
    private ListView<Commit> commitList;
    @FXML
    private TextArea detailsArea;
    @FXML
    private Label statusLabel;

    private User user;

    @FXML
    private void initialize() {
        user = Session.getCurrentUser();
        visibilityBox.getItems().setAll(Repository.VISIBILITIES);
        visibilityBox.setValue("private");

        repoList.setCellFactory(list -> new ListCell<Repository>() {
            @Override
            protected void updateItem(Repository repo, boolean empty) {
                super.updateItem(repo, empty);
                if (empty || repo == null) {
                    setText(null);
                } else {
                    setText(repo.getName() + "  [" + repo.getVisibility() + "]"
                            + (repo.isOwnedBy(user.getUserID()) ? "" : "  - by " + ownerName(repo)));
                }
            }
        });
        commitList.setCellFactory(list -> new ListCell<Commit>() {
            @Override
            protected void updateItem(Commit commit, boolean empty) {
                super.updateItem(commit, empty);
                setText(empty || commit == null ? null
                        : "v" + commit.getVersionNo() + "   " + DateUtil.format(commit.getDate()) + "   " + commit.getMessage());
            }
        });
        repoList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> onRepositorySelected(newValue));
        commitList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showCommitDetails(newValue));

        refreshRepositories();
        updateButtons();
    }

    private String ownerName(Repository repo) {
        try {
            User owner = User.findById(repo.getOwnerID());
            return owner == null ? "unknown" : owner.getUsername();
        } catch (SQLException e) {
            return "unknown";
        }
    }

    private Repository selectedRepo() {
        return repoList.getSelectionModel().getSelectedItem();
    }

    private Commit selectedCommit() {
        return commitList.getSelectionModel().getSelectedItem();
    }

    private void setStatus(String text, boolean isError) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().remove("message");
        if (isError) {
            statusLabel.getStyleClass().add("message");
        }
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private void refreshRepositories() {
        try {
            Repository previous = selectedRepo();
            List<Repository> repos = Repository.findVisibleTo(user.getUserID());
            repoList.getItems().setAll(repos);
            if (previous != null) {
                repos.stream().filter(r -> r.getRepoID() == previous.getRepoID()).findFirst()
                        .ifPresent(r -> repoList.getSelectionModel().select(r));
            }
        } catch (SQLException e) {
            setStatus("Could not load repositories: " + e.getMessage(), true);
        }
    }

    private void onRepositorySelected(Repository repo) {
        commitList.getItems().clear();
        detailsArea.clear();
        if (repo != null) {
            selectedLabel.setText(repo.isOwnedBy(user.getUserID())
                    ? repo.getName() + "  -  tracking " + repo.getSourcePath()
                    : repo.getName() + "  -  public repository by " + ownerName(repo));
            try {
                commitList.getItems().setAll(Commit.findByRepo(repo.getRepoID()));
            } catch (SQLException e) {
                setStatus("Could not load history: " + e.getMessage(), true);
            }
        } else {
            selectedLabel.setText("Select a repository");
        }
        updateButtons();
    }

    private void showCommitDetails(Commit commit) {
        if (commit == null) {
            return;
        }
        try {
            detailsArea.setText(commit.viewDetails());
        } catch (SQLException e) {
            setStatus("Could not load details: " + e.getMessage(), true);
        }
        updateButtons();
    }

    private void updateButtons() {
        Repository repo = selectedRepo();
        boolean owned = repo != null && repo.isOwnedBy(user.getUserID());
        commitButton.setDisable(!owned);
        commitMessageField.setDisable(!owned);
        checkButton.setDisable(!owned);
        deleteButton.setDisable(!owned);
        cloneButton.setDisable(repo == null);
        downloadButton.setDisable(repo == null);
        visibilityButton.setDisable(!owned);
        visibilityButton.setText(repo != null && repo.isPublic() ? "Make private" : "Make public");
        openPageButton.setDisable(repo == null);
        filesButton.setDisable(repo == null);
        restoreButton.setDisable(repo == null || selectedCommit() == null);
    }

    private File chooseFolder(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        return chooser.showDialog(repoList.getScene().getWindow());
    }

    private boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Please confirm");
        return alert.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    @FXML
    private void handleBrowse() {
        File folder = chooseFolder("Choose the folder to track");
        if (folder != null) {
            sourceField.setText(folder.getAbsolutePath());
        }
    }

    @FXML
    private void handleCreateRepo() {
        try {
            Repository repo = new Repository(user.getUserID(), nameField.getText(), descriptionField.getText(),
                    visibilityBox.getValue(), sourceField.getText());
            repo.createRepository();
            nameField.clear();
            descriptionField.clear();
            sourceField.clear();
            setStatus("Repository created. Type a message and press Commit to save the first version.", false);
            refreshRepositories();
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), true);
        } catch (SQLException | IOException e) {
            setStatus("Could not create the repository: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleCommit() {
        Repository repo = selectedRepo();
        if (repo == null) {
            return;
        }
        String message = commitMessageField.getText();
        String author = user.getUsername();
        commitButton.setDisable(true);
        setStatus("Saving a snapshot...", false);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                new Commit(repo.getRepoID(), message, author).commit();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            commitMessageField.clear();
            setStatus("Commit saved.", false);
            onRepositorySelected(repo);
        });
        task.setOnFailed(e -> {
            setStatus(messageOf(task.getException()), true);
            ActivityLogger.error("Commit failed", task.getException());
            updateButtons();
        });
        Thread worker = new Thread(task, "repospace-commit");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleCheckChanges() {
        Repository repo = selectedRepo();
        if (repo == null) {
            return;
        }
        try {
            List<String> changes = repo.pendingChanges();
            detailsArea.setText(changes.isEmpty() ? "No changes since the last commit." : "Changes since the last commit:\n" + String.join("\n", changes));
        } catch (SQLException | IOException e) {
            setStatus("Could not check changes: " + messageOf(e), true);
        }
    }

    @FXML
    private void handleOpenPage() {
        Repository repo = selectedRepo();
        if (repo != null) {
            Navigator.openRepository(repo.getRepoID(), "repositories.fxml");
        }
    }

    @FXML
    private void handleViewFiles() {
        Repository repo = selectedRepo();
        if (repo == null) {
            return;
        }
        try {
            List<String> files = repo.viewFiles();
            detailsArea.setText(files.isEmpty() ? "No committed files yet." : "Files in the latest version:\n" + String.join("\n", files));
        } catch (SQLException e) {
            setStatus("Could not load files: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleRestore() {
        Commit commit = selectedCommit();
        if (commit == null) {
            return;
        }
        File folder = chooseFolder("Choose the folder to restore version v" + commit.getVersionNo() + " into");
        if (folder == null) {
            return;
        }
        try {
            Path target = folder.toPath();
            if (!FileUtil.isMissingOrEmptyFolder(target)
                    && !confirm("The folder is not empty. Files with the same name will be overwritten. Continue?")) {
                return;
            }
            int count = commit.restore(target);
            setStatus(count + " files of version v" + commit.getVersionNo() + " restored.", false);
        } catch (SQLException | IOException | IllegalStateException e) {
            setStatus("Restore failed: " + messageOf(e), true);
        }
    }

    @FXML
    private void handleClone() {
        Repository repo = selectedRepo();
        if (repo == null) {
            return;
        }
        File folder = chooseFolder("Choose an empty folder for the clone");
        if (folder == null) {
            return;
        }
        try {
            repo.cloneRepository(folder.toPath());
            setStatus("Repository cloned to " + folder.getAbsolutePath(), false);
        } catch (SQLException | IOException | IllegalStateException e) {
            setStatus("Clone failed: " + messageOf(e), true);
        }
    }

    @FXML
    private void handleDownloadZip() {
        Repository repo = selectedRepo();
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
        try {
            int count = repo.download(user.getUserID(), target);
            setStatus(count + " files saved in " + target, false);
        } catch (SQLException | IOException | IllegalStateException e) {
            setStatus("Download failed: " + messageOf(e), true);
        }
    }

    @FXML
    private void handleToggleVisibility() {
        Repository repo = selectedRepo();
        if (repo == null) {
            return;
        }
        String next = repo.isPublic() ? "private" : "public";
        if ("public".equals(next)
                && !confirm("Everybody who uses Repospace will be able to see and download the latest version of '" + repo.getName() + "'. Make it public?")) {
            return;
        }
        try {
            repo.changeVisibility(next);
            setStatus("'" + repo.getName() + "' is now " + next + ".", false);
            refreshRepositories();
        } catch (SQLException | IllegalStateException e) {
            setStatus("Could not change it: " + messageOf(e), true);
        }
    }

    @FXML
    private void handleDeleteRepo() {
        Repository repo = selectedRepo();
        if (repo == null || !confirm("Delete '" + repo.getName() + "' and all its saved versions? Your original folder is not touched.")) {
            return;
        }
        try {
            repo.deleteRepository();
            setStatus("Repository deleted.", false);
            refreshRepositories();
            onRepositorySelected(null);
        } catch (SQLException | IOException e) {
            setStatus("Could not delete: " + messageOf(e), true);
        }
    }
}
