package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.ERefreshPolicy;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.EFileChangeScope;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * One row in the ChangesWidget list: a file entry (checkbox + status + path)
 * or a persistent section header ("Staged"/"Unstaged"). Header rows carry no
 * FileChange and are never selectable; file entries route their stage toggle
 * to the owning ChangesWidget.
 */
class ChangeEntryWidget extends HBox implements IObject
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ChangesWidget ChangesWidget;
	private final FileChange FileChangeTarget;
	private final CheckBox CommitCheckBox;
	final boolean IsHeader;

	/**
	 * Creates a file entry for the given change. The checkbox is pre-checked for
	 * staged files; toggling it stages/unstages through the owning widget.
	 *
	 * @param _ChangesWidget    the owning list widget (routes the stage toggle)
	 * @param _FileChangeTarget the file change this row represents
	 */
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
	}

	/**
	 * Creates a persistent section header row ("Staged"/"Unstaged"). Headers
	 * have no FileChange and keep an invisible checkbox so the row layout stays
	 * aligned with file entries.
	 *
	 * @param _HeaderText the section title rendered in bold
	 */
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

	/** @return true when the commit checkbox is ticked; header rows are never selected */
	public boolean IsSelected()
	{
		return !IsHeader && CommitCheckBox.isSelected();
	}

	/** @return the file change backing this row, or null for a header row */
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

/**
 * Staged/unstaged file-changes list for one repository. Entries are grouped
 * under persistent header widgets, re-synced from GitDir on refresh, and the
 * list selection drives the diff viewer in the owning GitDirWidget.
 */
public class ChangesWidget extends BaseWidget
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;
	/** Fallback uniform row height (px) used if the sample measurement fails */
	private static final double DEFAULT_ROW_HEIGHT = 32.0;
	/**
	 * Extra height (px) added to the measured entry height to cover the default
	 * {@code .list-cell} vertical padding (0.25em top + bottom in Modena).
	 */
	private static final double LIST_CELL_VERTICAL_PADDING = 8.0;

	private final ListView<ChangeEntryWidget> ChangesListView;

	/** Persistent header widgets — reused across refreshes so a highlighted header keeps its highlight */
	private final ChangeEntryWidget StagedHeader = new ChangeEntryWidget("Staged");
	private final ChangeEntryWidget UnstagedHeader = new ChangeEntryWidget("Unstaged");

	public ChangesWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// Create and configure ListView. The row height is pinned via
		// setFixedCellSize (see ComputeFixedCellSize) as a workaround for the
		// JavaFX VirtualFlow size-estimation regression that breaks scrolling
		// for long lists (JDK-8296871 / JDK-8301375 / JDK-8328167).
		ChangesListView = new ListView<>();
		ChangesListView.setFixedCellSize(ComputeFixedCellSize());

		// Add ListView to the StackPane
		getChildren().add(ChangesListView);

		// The diff viewer follows the ListView selection (highlight), not clicks.
		// A file entry shows its diff; a header (or no selection) has no FileChange,
		// so the diff viewer becomes empty.
		ChangesListView.getSelectionModel().selectedItemProperty().addListener((__Obs, __OldItem, __NewItem) ->
		{
			if (__NewItem != null && !__NewItem.IsHeader)
			{
				Debug.Log(Debug.ChangesCategory, "[Changes] Selection -> ReadFileChange(%s)\n", __NewItem.GetFileChange().GetFilePath());
				GetGitDirWidgetTarget().ReadFileChange(__NewItem.GetFileChange());
			}
			else
			{
				Debug.Log(Debug.ChangesCategory, "[Changes] Selection (header/none) -> ReadFileChange(null)\n");
				GetGitDirWidgetTarget().ReadFileChange(null);
			}
		});

		// Clicking empty space deselects nothing — selection is the sole driver of the diff viewer.
	}

	/**
	 * Computes the uniform ListView row height by measuring a sample entry, then
	 * returns it (plus the cell's own padding) for {@code setFixedCellSize}.
	 * <p>
	 * Pinning a fixed cell size makes VirtualFlow's scroll-range math exact. This
	 * works around a JavaFX regression where, for long lists, the scrollbar maximum
	 * is under-computed from estimated cell sizes, leaving the last entries
	 * unreachable by scrolling (keyboard selection still reaches them, but the view
	 * cannot render them). Falls back to a constant if the measurement fails.
	 */
	private double ComputeFixedCellSize()
	{
		try
		{
			var __Sample = new ChangeEntryWidget(this, new FileChange(
				GetGitDirTarget().GetRepoRootPath().resolve("__row_height_probe__"),
				EFileChangeStatus.Modified, EFileChangeScope.UNSTAGED, GetGitDirTarget()));
			__Sample.applyCss();
			double __Height = Math.ceil(__Sample.prefHeight(-1) + LIST_CELL_VERTICAL_PADDING);
			// Sanity floor: a sub-20px measurement means the CSS probe failed to
			// lay out (missing stylesheet/fonts), so fall back to the constant.
			return __Height >= 20 ? __Height : DEFAULT_ROW_HEIGHT;
		}
		catch (Exception __Ex)
		{
			return DEFAULT_ROW_HEIGHT;
		}
	}

	/**
	 * Rebuild the staged/unstaged entries from the current GitDir state.
	 *
	 * Surviving FileChanges keep their existing ChangeEntryWidget instances (so the
	 * current selection and the diff viewer survive a refresh), each section is
	 * re-sorted by path normalized to '/' to mirror git's ordering, and the new order
	 * is applied in place by relocating entries rather than duplicating them.
	 */
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

		// Keep each section sorted by path as git reports them: paths are normalized
		// to '/' separators so the sort key matches git's byte-wise ordering closely.
		__OldStaged.sort(Comparator.comparing(__Entry -> __Entry.GetFileChange().GetFilePath().toString().replace('\\', '/')));
		__OldUnstaged.sort(Comparator.comparing(__Entry -> __Entry.GetFileChange().GetFilePath().toString().replace('\\', '/')));

		// Build the desired item order: preserved entries keep their widgets (they may
		// be relocated by the sort), new entries land in their sorted position. Header
		// widgets are persistent instances, kept and reused.
		List<ChangeEntryWidget> __Desired = new ArrayList<>();
		__Desired.add(StagedHeader);
		__Desired.addAll(__OldStaged);
		__Desired.add(UnstagedHeader);
		__Desired.addAll(__OldUnstaged);

		// Apply the minimal change to the items list instead of clearing/repopulating:
		// removeIf drops entries that are gone, and the loop below is move-capable, so a
		// survivor that changed sort position is relocated instead of duplicated. The
		// selected item reference is left untouched, so the diff viewer does not flicker.
		var __Items = ChangesListView.getItems();
		var __DesiredSet = new HashSet<>(__Desired);
		__Items.removeIf(__Entry -> !__DesiredSet.contains(__Entry));

		int __InsertIdx = 0;
		for (var __DesiredEntry : __Desired)
		{
			if (__InsertIdx >= __Items.size() || __Items.get(__InsertIdx) != __DesiredEntry)
			{
				int __FoundAt = __Items.indexOf(__DesiredEntry);
				if (__FoundAt > __InsertIdx)
					__Items.remove(__FoundAt); // relocate an existing survivor into its sorted slot
				__Items.add(__InsertIdx, __DesiredEntry);
			}
			__InsertIdx++;
		}

		// Ensure the virtual flow re-lays out after the in-place mutation so the
		// scrollbar range (exact thanks to setFixedCellSize) covers the last entry.
		ChangesListView.requestLayout();
	}

	/**
	 * Mark-and-sweep merge for one section (staged or unstaged):
	 * existing entries that match a new FileChange are kept;
	 * unmatched old entries are removed;
	 * unmatched new FileChanges get new ChangeEntryWidgets created.
	 * Both input lists are mutated in place — _NewChanges is consumed by the match.
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

	/**
	 * Stage (git add) or unstage (git reset HEAD --) a file through the
	 * GitOperator queue with REFRESH_AND_UPDATE_UI, so the post-operation
	 * refresh/UI broadcast is handled by the operator. The source checkbox is
	 * disabled while the command runs; on failure it is re-enabled, its selection
	 * reverted, and an error dialog shown.
	 *
	 * @param _Change          the file change to move between scopes
	 * @param _ShouldBeStaged  true to stage, false to unstage
	 * @param _SourceCheckBox  the checkbox the user toggled (disabled during the op)
	 */
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
