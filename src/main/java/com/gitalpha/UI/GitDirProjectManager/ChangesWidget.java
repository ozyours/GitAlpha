package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.EFileChangeScope;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

class ChangeEntryWidget extends HBox implements IObject
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ChangesWidget ChangesWidget;
	private final FileChange FileChangeTarget;
	private final CheckBox commitCheckBox;
	final boolean IsHeader;

	public ChangeEntryWidget(ChangesWidget _ChangesWidget, FileChange _FileChangeTarget)
	{
		ChangesWidget = _ChangesWidget;
		FileChangeTarget = _FileChangeTarget;
		IsHeader = false;

		// Configure the HBox
		setSpacing(SPACING);
		setPadding(new Insets(PADDING));

		// Create checkbox for selecting files to commit
		commitCheckBox = new CheckBox();
		commitCheckBox.setSelected(FileChangeTarget._Scope() == EFileChangeScope.STAGED);
		commitCheckBox.setOnAction(event ->
		{
			ChangesWidget.ToggleStagedState(FileChangeTarget, commitCheckBox.isSelected(), commitCheckBox);
		});

		// Create text showing file status and path
		Text statusText = createStatusText(FileChangeTarget._Status());
		Text pathText = new Text(FileChangeTarget._FilePath().getFileName().toString());

		// Add components to the entry
		getChildren().addAll(commitCheckBox, statusText, pathText);

		setOnMouseClicked(mouseEvent ->
		{
			ChangesWidget.GetGitDirProjectManagerTarget().ReadFileChange(FileChangeTarget);
		});
	}

	public ChangeEntryWidget(String _HeaderText)
	{
		ChangesWidget = null;
		FileChangeTarget = null;
		IsHeader = true;

		setSpacing(SPACING);
		setPadding(new Insets(PADDING));
		Text headerText = new Text(_HeaderText);
		headerText.setStyle("-fx-font-weight: bold;");
		getChildren().add(headerText);

		commitCheckBox = new CheckBox();
		commitCheckBox.setVisible(false);
		commitCheckBox.setManaged(false);
	}

	public boolean isSelected()
	{
		return !IsHeader && commitCheckBox.isSelected();
	}

	public FileChange getFileChange()
	{
		return FileChangeTarget;
	}

	private Text createStatusText(EFileChangeStatus status)
	{
		Text statusText = new Text();
		statusText.setText(switch (status)
		{
			case Added -> "[Added] ";
			case Modified -> "[Modified] ";
			case Removed -> "[Removed] ";
		});

		statusText.setFill(switch (status)
		{
			case Added -> Color.GREEN;
			case Modified -> Color.ORANGE;
			case Removed -> Color.RED;
		});

		return statusText;
	}

	@Override
	public Object GetParent()
	{
		return ChangesWidget;
	}
}

public class ChangesWidget extends BaseWidget
{
	private static final double MIN_WIDTH = 400;
	private static final double MIN_HEIGHT = 300;
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ListView<ChangeEntryWidget> changesListView;

	public ChangesWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		// Create and configure ListView
		changesListView = new ListView<>();
		changesListView.setMinSize(MIN_WIDTH, MIN_HEIGHT);

		// Add ListView to the StackPane
		getChildren().add(changesListView);
		// Clear the diff viewer when clicking on headers or empty space
		changesListView.addEventHandler(MouseEvent.MOUSE_CLICKED, event ->
		{
			// Walk up from the clicked node to see if it is inside a ChangesEntry
			Node clickTarget = (Node) event.getTarget();
			while (clickTarget != null && !(clickTarget instanceof ChangeEntryWidget))
				clickTarget = clickTarget.getParent();

			if (clickTarget instanceof ChangeEntryWidget entry)
			{
				// Click on a "Staged" or "Unstaged" header -> clear the diff view and selection
				if (entry.IsHeader)
				{
					changesListView.getSelectionModel().clearSelection();
					GetGitDirProjectManagerTarget().ReadFileChange(null);
				}
			}
			else
			{
				// Click on empty space in the ListView -> clear the diff view and selection
				changesListView.getSelectionModel().clearSelection();
				GetGitDirProjectManagerTarget().ReadFileChange(null);
			}
		});
	}

	public void updateChanges()
	{
		changesListView.getItems().clear();

		List<FileChange> staged = new ArrayList<>();
		List<FileChange> unstaged = new ArrayList<>();
		for (FileChange change : GetGitDirTarget().GetChangedFiles())
		{
			if (change._Scope() == EFileChangeScope.STAGED)
				staged.add(change);
			else
				unstaged.add(change);
		}

		changesListView.getItems().add(new ChangeEntryWidget("Staged"));
		for (FileChange change : staged)
		{
			changesListView.getItems().add(new ChangeEntryWidget(this, change));
		}

		changesListView.getItems().add(new ChangeEntryWidget("Unstaged"));
		for (FileChange change : unstaged)
		{
			changesListView.getItems().add(new ChangeEntryWidget(this, change));
		}
	}

	/**
	 * Get all selected file changes
	 *
	 * @return List of FileChange that are selected for commit
	 */
	public List<FileChange> getSelectedChanges()
	{
		List<FileChange> selectedChanges = new ArrayList<>();
		for (ChangeEntryWidget entry : changesListView.getItems())
		{
			if (entry.isSelected())
			{
				selectedChanges.add(entry.getFileChange());
			}
		}
		return selectedChanges;
	}

	void ToggleStagedState(FileChange _Change, boolean _ShouldBeStaged, CheckBox _SourceCheckBox)
	{
		if (_Change == null || _Change._FilePath() == null)
			return;

		Path __RelativePath = GetGitDirTarget().GetRepoRootPath().relativize(_Change._FilePath());
		List<String> __Cmd = new ArrayList<>();
		if (_ShouldBeStaged)
		{
			__Cmd.add("add");
			__Cmd.add("--");
			__Cmd.add(__RelativePath.toString());
		}
		else
		{
			__Cmd.add("reset");
			__Cmd.add("HEAD");
			__Cmd.add("--");
			__Cmd.add(__RelativePath.toString());
		}

		_SourceCheckBox.setDisable(true);
		GetGitDirTarget().RunCMDAsync(__Cmd).thenAccept(result ->
		{
			if (result.getKey() != 0)
				throw new RuntimeException(result.getValue());

			Platform.runLater(() ->
			{
				AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("git-operation-completed", GetGitDirTarget());
			});
		}).exceptionally(ex ->
		{
			Platform.runLater(() ->
			{
				_SourceCheckBox.setDisable(false);
				_SourceCheckBox.setSelected(!_ShouldBeStaged);

				Alert __Alert = new Alert(Alert.AlertType.ERROR);
				__Alert.setTitle("Git Operation Failed");
				__Alert.setHeaderText(_ShouldBeStaged ? "Failed to stage file" : "Failed to unstage file");
				__Alert.setContentText(ex.getMessage());
				__Alert.showAndWait();
			});
			return null;
		});
	}
}
