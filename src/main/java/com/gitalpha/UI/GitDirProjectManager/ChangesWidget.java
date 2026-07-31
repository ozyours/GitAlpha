package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.ERefreshPolicy;
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
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

class ChangeEntryWidget extends HBox implements IObject
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ChangesWidget ChangesWidget;
	private final FileChange FileChangeTarget;
	private final CheckBox CommitCheckBox;
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
		CommitCheckBox = new CheckBox();
		CommitCheckBox.setSelected(FileChangeTarget.GetScope() == EFileChangeScope.STAGED);
		CommitCheckBox.setOnAction(event ->
		{
			ChangesWidget.ToggleStagedState(FileChangeTarget, CommitCheckBox.isSelected(), CommitCheckBox);
		});

		// Create text showing file status and path
		Text statusText = CreateStatusText(FileChangeTarget.GetStatus());

		// Show full relative path: directory portion in gray, filename in normal color
		Path __RelativePath = ChangesWidget.GetGitDirTarget().GetRepoRootPath().relativize(FileChangeTarget.GetFilePath());
		Path __ParentDir = __RelativePath.getParent();
		Text dirText = new Text(__ParentDir != null ? __ParentDir.toString() + "\\" : "");
		dirText.setFill(Color.GRAY);
		Text fileText = new Text(__RelativePath.getFileName().toString());
		TextFlow pathFlow = new TextFlow(dirText, fileText);

		// Add components to the entry
		getChildren().addAll(CommitCheckBox, statusText, pathFlow);

		setOnMouseClicked(mouseEvent ->
		{
			ChangesWidget.GetGitDirProjectManagerWidgetTarget().ReadFileChange(FileChangeTarget);
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

		CommitCheckBox = new CheckBox();
		CommitCheckBox.setVisible(false);
		CommitCheckBox.setManaged(false);
	}

	public boolean IsSelected()
	{
		return !IsHeader && CommitCheckBox.isSelected();
	}

	public FileChange GetFileChange()
	{
		return FileChangeTarget;
	}

	private Text CreateStatusText(EFileChangeStatus status)
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

	private final ListView<ChangeEntryWidget> ChangesListView;

	public ChangesWidget(GitDir _GitDirTarget, GitDirProjectManagerWidget _GitDirProjectManagerWidgetTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerWidgetTarget);

		// Create and configure ListView
		ChangesListView = new ListView<>();
		ChangesListView.setMinSize(MIN_WIDTH, MIN_HEIGHT);

		// Add ListView to the StackPane
		getChildren().add(ChangesListView);
		// Clear the diff viewer when clicking on headers or empty space
		ChangesListView.addEventHandler(MouseEvent.MOUSE_CLICKED, event ->
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
					ChangesListView.getSelectionModel().clearSelection();
					GetGitDirProjectManagerWidgetTarget().ReadFileChange(null);
				}
			}
			else
			{
				// Click on empty space in the ListView -> clear the diff view and selection
				ChangesListView.getSelectionModel().clearSelection();
				GetGitDirProjectManagerWidgetTarget().ReadFileChange(null);
			}
		});
	}

	public void UpdateChanges()
	{
		// Partition new file changes by scope.
		List<FileChange> __NewStaged = new ArrayList<>();
		List<FileChange> __NewUnstaged = new ArrayList<>();
		for (FileChange __Change : GetGitDirTarget().GetChangedFiles())
		{
			if (__Change.GetScope() == EFileChangeScope.STAGED)
				__NewStaged.add(__Change);
			else
				__NewUnstaged.add(__Change);
		}

		// Collect existing file entry widgets from the current list, grouped by scope.
		List<ChangeEntryWidget> __OldStaged = new ArrayList<>();
		List<ChangeEntryWidget> __OldUnstaged = new ArrayList<>();
		for (ChangeEntryWidget __Entry : ChangesListView.getItems())
		{
			if (!__Entry.IsHeader && __Entry.GetFileChange() != null)
			{
				if (__Entry.GetFileChange().GetScope() == EFileChangeScope.STAGED)
					__OldStaged.add(__Entry);
				else
					__OldUnstaged.add(__Entry);
			}
		}

		// Diff-merge each section: match existing entries by (path, scope, status).
		DiffMergeSection(__OldStaged, __NewStaged);
		DiffMergeSection(__OldUnstaged, __NewUnstaged);

		// Rebuild the ListView — headers are stateless, file entries are preserved.
		var __Items = ChangesListView.getItems();
		__Items.clear();
		__Items.add(new ChangeEntryWidget("Staged"));
		__Items.addAll(__OldStaged);
		__Items.add(new ChangeEntryWidget("Unstaged"));
		__Items.addAll(__OldUnstaged);
	}

	/**
	 * Mark-and-sweep merge for one section (staged or unstaged):
	 * existing entries that match a new FileChange are kept;
	 * unmatched old entries are removed;
	 * unmatched new FileChanges get new ChangeEntryWidgets created.
	 */
	private void DiffMergeSection(List<ChangeEntryWidget> _OldEntries, List<FileChange> _NewChanges)
	{
		var __OldIter = _OldEntries.iterator();
		while (__OldIter.hasNext())
		{
			var __Old = __OldIter.next();
			FileChange __OldFC = __Old.GetFileChange();
			boolean __Found = false;

			var __NewIter = _NewChanges.iterator();
			while (__NewIter.hasNext())
			{
				FileChange __NewFC = __NewIter.next();
				if (__OldFC.GetFilePath().equals(__NewFC.GetFilePath())
					&& __OldFC.GetScope() == __NewFC.GetScope()
					&& __OldFC.GetStatus() == __NewFC.GetStatus())
				{
					__NewIter.remove(); // matched; do not create new entry
					__Found = true;
					break;
				}
			}

			if (!__Found)
				__OldIter.remove(); // no longer present
		}

		// Leftovers in _NewChanges are genuinely new entries.
		for (FileChange __NewFC : _NewChanges)
			_OldEntries.add(new ChangeEntryWidget(this, __NewFC));
	}

	/**
	 * Get all selected file changes
	 *
	 * @return List of FileChange that are selected for commit
	 */
	public List<FileChange> GetSelectedChanges()
	{
		List<FileChange> selectedChanges = new ArrayList<>();
		for (ChangeEntryWidget entry : ChangesListView.getItems())
		{
			if (entry.IsSelected())
			{
				selectedChanges.add(entry.GetFileChange());
			}
		}
		return selectedChanges;
	}

	void ToggleStagedState(FileChange _Change, boolean _ShouldBeStaged, CheckBox _SourceCheckBox)
	{
		if (_Change == null || _Change.GetFilePath() == null)
			return;

		Path __RelativePath = GetGitDirTarget().GetRepoRootPath().relativize(_Change.GetFilePath());
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
		GetGitDirTarget().GetOperator().RunGitOp(__Cmd, ERefreshPolicy.REFRESH_AND_UPDATE_UI, (__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (!__Ok)
				{
					_SourceCheckBox.setDisable(false);
					_SourceCheckBox.setSelected(!_ShouldBeStaged);

					Alert __Alert = new Alert(Alert.AlertType.ERROR);
					__Alert.setTitle("Git Operation Failed");
					__Alert.setHeaderText(_ShouldBeStaged ? "Failed to stage file" : "Failed to unstage file");
					__Alert.setContentText(__Err);
					__Alert.showAndWait();
				}
			});
		});
	}
}
