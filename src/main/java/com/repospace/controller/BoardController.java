package com.repospace.controller;

import com.repospace.model.BoardTask;
import com.repospace.model.Projects;
import com.repospace.model.Repository;
import com.repospace.util.ActivityLogger;
import com.repospace.util.Session;
import com.repospace.model.User;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class BoardController {

    private static final String NO_REPO = "(no repository)";

    @FXML
    private Label titleLabel;
    @FXML
    private Label progressLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private ListView<BoardTask> todoList;
    @FXML
    private ListView<BoardTask> progressList;
    @FXML
    private ListView<BoardTask> doneList;
    @FXML
    private Label detailsLabel;
    @FXML
    private Button moveLeftButton;
    @FXML
    private Button moveRightButton;
    @FXML
    private Button deleteCardButton;
    @FXML
    private Button openRepoButton;
    @FXML
    private VBox addBox;
    @FXML
    private TextField cardTitleField;
    @FXML
    private ComboBox<String> linkedRepoBox;
    @FXML
    private TextField cardDetailsField;
    @FXML
    private Label messageLabel;

    private User viewer;
    private Projects project;
    private boolean owner;
    private BoardTask selected;
    private boolean updating;
    private final List<Repository> ownRepos = new ArrayList<>();

    @FXML
    private void initialize() {
        viewer = Session.getCurrentUser();
        try {
            project = Projects.findById(Navigator.projectID());
        } catch (SQLException e) {
            ActivityLogger.error("Could not open a board", e);
        }
        if (project == null || !project.canBeViewedBy(viewer.getUserID())) {
            titleLabel.setText("This board is not available");
            addBox.setVisible(false);
            addBox.setManaged(false);
            setButtons(false);
            return;
        }
        owner = project.getUserID() == viewer.getUserID();
        addBox.setVisible(owner);
        addBox.setManaged(owner);
        titleLabel.setText("Board: " + project.getName());

        for (ListView<BoardTask> list : List.of(todoList, progressList, doneList)) {
            list.setCellFactory(l -> new ListCell<BoardTask>() {
                @Override
                protected void updateItem(BoardTask t, boolean empty) {
                    super.updateItem(t, empty);
                    setText(empty || t == null ? null : t.getTitle() + (t.getRepoID() == null ? "" : "   [repo]"));
                }
            });
            list.getSelectionModel().selectedItemProperty().addListener((obs, o, task) -> {
                if (!updating && task != null) {
                    select(task, list);
                }
            });
        }
        if (owner) {
            linkedRepoBox.getItems().add(NO_REPO);
            try {
                ownRepos.addAll(Repository.findByOwner(viewer.getUserID()));
            } catch (SQLException e) {
                ActivityLogger.error("Could not load repositories for the board", e);
            }
            for (Repository r : ownRepos) {
                linkedRepoBox.getItems().add(r.getName());
            }
            linkedRepoBox.setValue(NO_REPO);
        }
        reload();
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

    private void setButtons(boolean hasSelection) {
        moveLeftButton.setDisable(!owner || !hasSelection || selected == null || "To do".equals(selected.getStatus()));
        moveRightButton.setDisable(!owner || !hasSelection || selected == null || "Done".equals(selected.getStatus()));
        deleteCardButton.setDisable(!owner || !hasSelection);
        openRepoButton.setDisable(!hasSelection || selected == null || selected.getRepoID() == null);
    }

    private void reload() {
        int keep = selected == null ? -1 : selected.getTaskID();
        selected = null;
        try {
            List<BoardTask> tasks = BoardTask.forProject(project, viewer.getUserID());
            updating = true;
            try {
                todoList.getItems().setAll(inColumn(tasks, "To do"));
                progressList.getItems().setAll(inColumn(tasks, "In progress"));
                doneList.getItems().setAll(inColumn(tasks, "Done"));
            } finally {
                updating = false;
            }
            int percent = BoardTask.percentDone(tasks);
            progressBar.setProgress(percent / 100.0);
            progressLabel.setText(tasks.isEmpty() ? "No cards yet." : percent + "% done  (" + tasks.size() + " cards)");
            for (BoardTask t : tasks) {
                if (t.getTaskID() == keep) {
                    ListView<BoardTask> list = "Done".equals(t.getStatus()) ? doneList : "In progress".equals(t.getStatus()) ? progressList : todoList;
                    select(t, list);
                    updating = true;
                    try {
                        list.getSelectionModel().select(t);
                    } finally {
                        updating = false;
                    }
                }
            }
        } catch (SQLException | IllegalStateException e) {
            setMessage("Could not load the board: " + messageOf(e), true);
        }
        if (selected == null) {
            detailsLabel.setText("");
            setButtons(false);
        }
    }

    private static List<BoardTask> inColumn(List<BoardTask> tasks, String status) {
        List<BoardTask> list = new ArrayList<>();
        for (BoardTask t : tasks) {
            if (status.equals(t.getStatus())) {
                list.add(t);
            }
        }
        return list;
    }

    private void select(BoardTask task, ListView<BoardTask> from) {
        selected = task;
        updating = true;
        try {
            for (ListView<BoardTask> list : List.of(todoList, progressList, doneList)) {
                if (list != from) {
                    list.getSelectionModel().clearSelection();
                }
            }
        } finally {
            updating = false;
        }
        String repoName = "";
        if (task.getRepoID() != null) {
            try {
                Repository r = Repository.findById(task.getRepoID());
                repoName = r == null || !r.canBeViewedBy(viewer.getUserID()) ? "" : "\nLinked repository: " + r.getName();
            } catch (SQLException e) {
                ActivityLogger.error("Could not read the linked repository", e);
            }
        }
        detailsLabel.setText(task.getTitle() + (task.getDetails().isEmpty() ? "" : "\n" + task.getDetails()) + repoName);
        setButtons(true);
    }

    private void move(int direction) {
        BoardTask task = selected;
        if (task == null) {
            return;
        }
        int index = BoardTask.STATUSES.indexOf(task.getStatus()) + direction;
        if (index < 0 || index >= BoardTask.STATUSES.size()) {
            return;
        }
        try {
            task.move(project, viewer.getUserID(), BoardTask.STATUSES.get(index));
            reload();
        } catch (SQLException | IllegalStateException | IllegalArgumentException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleMoveLeft() {
        move(-1);
    }

    @FXML
    private void handleMoveRight() {
        move(1);
    }

    @FXML
    private void handleDeleteCard() {
        BoardTask task = selected;
        if (task == null) {
            return;
        }
        try {
            task.delete(project, viewer.getUserID());
            selected = null;
            reload();
            setMessage("Card deleted.", false);
        } catch (SQLException | IllegalStateException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleAddCard() {
        try {
            String choice = linkedRepoBox.getValue();
            Integer repoID = null;
            for (Repository r : ownRepos) {
                if (r.getName().equals(choice)) {
                    repoID = r.getRepoID();
                }
            }
            BoardTask.add(project, viewer.getUserID(), cardTitleField.getText(), cardDetailsField.getText(), repoID);
            cardTitleField.clear();
            cardDetailsField.clear();
            linkedRepoBox.setValue(NO_REPO);
            reload();
            setMessage("Card added to To do.", false);
        } catch (SQLException | IllegalStateException | IllegalArgumentException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleOpenRepo() {
        if (selected != null && selected.getRepoID() != null) {
            Navigator.openRepository(selected.getRepoID(), "board.fxml");
        }
    }

    @FXML
    private void handleBack() {
        Navigator.back();
    }
}
