package com.gitalpha.UI.Stash;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.EStashMode;
import com.gitalpha.Type.StashEntry;
import com.gitalpha.Type.StashWindowState;
import com.gitalpha.UI.GitDirProjectManager.ImmutableChangesWidget;
import com.gitalpha.UI.GitDirProjectManager.TextViewerWidget;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Stash management window: a three-column layout with a stash list on the
 * left, a read-only file-change list for the selected stash in the centre
 * (reusing {@link ImmutableChangesWidget}), and a virtualized diff viewer
 * (reusing {@link TextViewerWidget}) on the right.
 * Bottom buttons provide stash operations (rename, pop, drop, apply, save).
 * <p>
 * Opened from the Git → Stash… menu entry; one instance per repository.
 * Window bounds, maximized state, column divider positions and the Save mode
 * are persisted in the session file as a single state shared by all
 * repositories ({@link StashWindowState}). Column
 * positions are re-applied in the {@code onShown} handler once the window is
 * laid out at its final size — applying them earlier lets SplitPaneSkin's
 * size-settling redistribute the dividers (JDK-8092863).
 */
public class StashWidget extends Stage implements IObject
{
	/**
	 * The repository this stash window operates on
	 */
	private final GitDir GitDirTarget;
	/**
	 * Observable list backing the stash ListView
	 */
	private final ObservableList<StashEntry> StashEntries = FXCollections.observableArrayList();
	/**
	 * Currently selected stash entry (null if none)
	 */
	private StashEntry SelectedStash = null;

	/**
	 * Stash list (the selection source); the centre file list is the shared
	 * {@link ImmutableChangesWidget} below
	 */
	private final ListView<StashEntry> lst_StashList;
	/**
	 * Read-only file-change list following the stash selection; selection is
	 * routed to the diff viewer through the widget's selection handler
	 */
	private final ImmutableChangesWidget<StashEntry.StashFile> ImmutableChangesWidgetInstance;
	/**
	 * Virtualized diff viewer reused from the project view
	 */
	private final TextViewerWidget DiffViewer;
	/**
	 * Three-column split pane; divider positions are persisted as the column sizes
	 */
	private final SplitPane SplitPaneInstance;
	/**
	 * Retained window state, shared across all repositories: every stash window
	 * restores from and writes back to the same engine-held state, so the last
	 * window to change it wins. Bounds are only mutated while the window is
	 * windowed, so the last windowed geometry survives a maximize/un-maximize
	 * cycle and is what gets persisted (a maximized window's geometry is the
	 * whole screen and must not be stored).
	 */
	private final StashWindowState WindowState;

	/**
	 * Bottom action buttons — enabled/disabled based on selection
	 */
	private final Button btn_Rename;
	private final Button btn_Pop;
	private final Button btn_Drop;
	private final Button btn_Apply;
	private final Button btn_Save;
	/**
	 * Mode selector for the Save operation: which files {@code git stash push}
	 * captures (see {@link EStashMode}). Disabled during in-flight operations
	 * together with the action buttons.
	 */
	private final ComboBox<EStashMode> cmb_StashMode;
	/**
	 * Auto Restore checkbox: when checked, a successful Save immediately
	 * applies the new stash ({@code stash@{0}}) back, so the stashed changes
	 * stay in the working tree while a copy remains in the stash list.
	 * The checked state is persisted in the session via {@link StashWindowState}.
	 */
	private final CheckBox chk_AutoRestore;

	/**
	 * Monotonic counter invalidating in-flight stash-file-list loads when the
	 * stash selection changes: completions compare the captured value and drop
	 * stale results (rapid selection changes must not paint an older stash's
	 * file list under the current selection).
	 */
	private int StashSelectionVersion = 0;
	/**
	 * Monotonic counter invalidating in-flight diff loads when the file
	 * selection changes (independent of {@link #StashSelectionVersion} — a file
	 * selection must not invalidate a pending stash-file-list load).
	 */
	private int FileSelectionVersion = 0;
	/**
	 * Set when the window closes; background work skips UI updates afterwards
	 */
	private volatile boolean IsDisposed = false;

	/**
	 * Builds and shows the stash window for the given repository.
	 *
	 * @param _GitDirTarget the repository to manage stashes for
	 */
	public StashWidget(GitDir _GitDirTarget)
	{
		GitDirTarget = _GitDirTarget;

		// --- Left pane: stash list ---
		lst_StashList = new ListView<>(StashEntries);
		lst_StashList.setCellFactory(__List -> new StashListCell());
		lst_StashList.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) ->
		{
			SelectedStash = __New;
			OnStashSelectionChanged();
		});

		VBox leftPane = new VBox(lst_StashList);
		VBox.setVgrow(lst_StashList, Priority.ALWAYS);

		// --- Centre pane: immutable file change list ---
		ImmutableChangesWidgetInstance = new ImmutableChangesWidget<StashEntry.StashFile>();
		ImmutableChangesWidgetInstance.SetSelectionHandler(__Selected -> OnFileSelectionChanged(__Selected));

		VBox centrePane = new VBox(ImmutableChangesWidgetInstance);
		VBox.setVgrow(ImmutableChangesWidgetInstance, Priority.ALWAYS);

		// --- Right pane: virtualized diff viewer ---
		DiffViewer = new TextViewerWidget(GitDirTarget);
		VBox rightPane = new VBox(DiffViewer);
		VBox.setVgrow(DiffViewer, Priority.ALWAYS);

		// --- Bottom buttons ---
		btn_Save = new Button("Save");
		btn_Save.setTooltip(new Tooltip("Create a new stash from current changes"));
		btn_Save.setOnAction(__Event -> OnSaveStash());

		btn_Rename = new Button("Rename");
		btn_Rename.setDisable(true);
		btn_Rename.setOnAction(__Event -> OnRenameStash());

		btn_Pop = new Button("Pop");
		btn_Pop.setDisable(true);
		btn_Pop.setOnAction(__Event -> OnPopStash());

		btn_Drop = new Button("Drop");
		btn_Drop.setDisable(true);
		btn_Drop.setOnAction(__Event -> OnDropStash());

		btn_Apply = new Button("Apply");
		btn_Apply.setDisable(true);
		btn_Apply.setOnAction(__Event -> OnApplyStash());

		// Mode selector for Save: populated from the enum values, with the
		// first (Default) mode preselected so the combo never starts empty.
		cmb_StashMode = new ComboBox<>(FXCollections.observableArrayList(EStashMode.values()));
		cmb_StashMode.getSelectionModel().selectFirst();
		cmb_StashMode.setTooltip(new Tooltip("Which files the Save operation stashes"));

		// Auto Restore: after a successful Save, re-apply the new stash so the
		// stashed changes remain in the working tree. The checked state is
		// persisted with the window state (see StashWindowState); its listener
		// is attached once the retained state is restored (below).
		chk_AutoRestore = new CheckBox("Auto Restore");
		chk_AutoRestore.setTooltip(new Tooltip("After Save, immediately re-apply the stash so the changes stay in the working tree"));

		Button btn_Close = new Button("Close");
		btn_Close.setOnAction(__Event -> close());

		// Bottom-bar layout: the selection-dependent operations stay left-aligned
		// while the spacer Region (index 4) absorbs the extra width, pushing the
		// mode selector, Auto Restore checkbox, Save and Close to the right edge.
		HBox bottomBar = new HBox(10, btn_Rename, btn_Pop, btn_Drop, btn_Apply, new Region(), cmb_StashMode, chk_AutoRestore, btn_Save, btn_Close);
		HBox.setHgrow(bottomBar.getChildren().get(4), Priority.ALWAYS);
		bottomBar.setAlignment(Pos.CENTER_LEFT);
		bottomBar.setPadding(new Insets(8));

		// --- Main layout ---
		SplitPaneInstance = new SplitPane(leftPane, centrePane, rightPane);
		// Initial divider positions — keep in sync with StashWindowState's
		// Column1/Column2 defaults, which stand in when no saved state exists.
		SplitPaneInstance.setDividerPositions(0.25, 0.60);

		BorderPane root = new BorderPane();
		root.setCenter(SplitPaneInstance);
		root.setBottom(bottomBar);

		// --- Window setup ---
		setTitle("Stash — " + GitDirTarget.GetGitDirPath().getParent().getFileName());
		setScene(new Scene(root, 1000, 600));
		initModality(Modality.NONE);

		// Restore the persisted window state (position, size, maximized) before
		// showing; the retained state keeps the last windowed geometry so a
		// saved-maximized window restores its windowed size on un-maximize.
		// Column positions are applied later in the onShown handler, once the
		// window has been laid out at its final size.
		StashWindowState __Saved = AlphaEngine.Instance.GetStashWindowState();
		WindowState = __Saved != null ? __Saved : new StashWindowState();
		// Clamp the restored geometry to the primary screen so a saved position
		// from a now-disconnected monitor or a stale size can't open off-screen.
		javafx.geometry.Rectangle2D __Screen = javafx.stage.Screen.getPrimary().getVisualBounds();
		if (WindowState.GetX() >= 0)
			setX(Math.min(Math.max(WindowState.GetX(), __Screen.getMinX()), __Screen.getMaxX() - 200));
		if (WindowState.GetY() >= 0)
			setY(Math.min(Math.max(WindowState.GetY(), __Screen.getMinY()), __Screen.getMaxY() - 100));
		setWidth(Math.min(WindowState.GetWidth(), __Screen.getWidth()));
		setHeight(Math.min(WindowState.GetHeight(), __Screen.getHeight()));
		setMaximized(WindowState.GetMaximized());
		// Apply the persisted Auto Restore state, then wire the checkbox to write
		// user changes back into the retained window state. The listener must be
		// attached after the restoration so a stale checked state is not saved
		// back during construction.
		chk_AutoRestore.setSelected(WindowState.GetAutoRestore());
		chk_AutoRestore.selectedProperty().addListener((__Obs, __Old, __New) ->
		{
			WindowState.SetAutoRestore(__New);
			AlphaEngine.Instance.SetStashWindowState(WindowState);
		});
		// Restore the persisted Save mode, then wire the combo to write user
		// changes back into the retained window state. The listener is attached
		// after the restore so a stale selection is not saved back during
		// construction.
		cmb_StashMode.getSelectionModel().select(WindowState.GetStashMode());
		cmb_StashMode.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) ->
		{
			if (__New != null)
			{
				WindowState.SetStashMode(__New);
				AlphaEngine.Instance.SetStashWindowState(WindowState);
			}
		});
		if (WindowState.GetColumn1() > 0 && WindowState.GetColumn2() > WindowState.GetColumn1())
			SplitPaneInstance.setDividerPositions(WindowState.GetColumn1(), WindowState.GetColumn2());

		// Persist bounds while the window is windowed only — a maximized window's
		// geometry is the whole screen, so its position/size must not overwrite
		// the windowed restore size (same rule as the main window).
		xProperty().addListener((__Obs, __Old, __New) ->
		{
			if (!isMaximized())
				PersistWindowState();
		});
		yProperty().addListener((__Obs, __Old, __New) ->
		{
			if (!isMaximized())
				PersistWindowState();
		});
		widthProperty().addListener((__Obs, __Old, __New) ->
		{
			if (!isMaximized())
				PersistWindowState();
		});
		heightProperty().addListener((__Obs, __Old, __New) ->
		{
			if (!isMaximized())
				PersistWindowState();
		});
		maximizedProperty().addListener((__Obs, __Old, __New) -> PersistWindowState());
		// Column sizes (SplitPane divider positions) are always persisted.
		SplitPaneInstance.getDividers().get(0).positionProperty().addListener((__Obs, __Old, __New) -> PersistWindowState());
		SplitPaneInstance.getDividers().get(1).positionProperty().addListener((__Obs, __Old, __New) -> PersistWindowState());
		// Flush the in-memory state into the session file on close.
		setOnHidden(__Event ->
		{
			IsDisposed = true;
			PersistWindowState();
			AlphaEngine.Instance.SaveSession();
		});

		// Apply the saved column positions once the window is shown and laid out
		// at its final size, then kick off the initial stash-list load. Applying
		// the divider positions before show() lets SplitPaneSkin's size-settling
		// redistribute the dividers (JDK-8092863), which would both render the
		// wrong columns and persist the wrong values; on shown the pane sits at
		// its final size, so the fractions map to exactly the pixels the user
		// left, and the values written back by the layout match what is saved.
		setOnShown(__Event ->
		{
			// Only apply a plausible saved state: a zero first fraction means the
			// columns were never persisted, and an out-of-order pair (column 2 at
			// or before column 1) would render invalid pane widths.
			if (WindowState.GetColumn1() > 0 && WindowState.GetColumn2() > WindowState.GetColumn1())
				SplitPaneInstance.setDividerPositions(WindowState.GetColumn1(), WindowState.GetColumn2());
			LoadStashList();
		});
		show();
	}

	@Override
	public Object GetParent()
	{
		return GitDirTarget;
	}

	/**
	 * Writes the current window geometry (windowed only), maximized flag and
	 * SplitPane divider positions into the retained state and hands it to the
	 * engine. The engine persists it during the next {@link AlphaEngine#SaveSession()}.
	 */
	private void PersistWindowState()
	{
		if (!isMaximized())
			WindowState.SetWindowBounds((int) getX(), (int) getY(), (int) getWidth(), (int) getHeight());
		WindowState.SetMaximized(isMaximized());
		WindowState.SetColumns(SplitPaneInstance.getDividers().get(0).getPosition(), SplitPaneInstance.getDividers().get(1).getPosition());
		// The Save mode is a plain preference: persist whatever is currently
		// selected so it is flushed on close even if the combo listener was not
		// the last writer.
		EStashMode __Mode = cmb_StashMode.getSelectionModel().getSelectedItem();
		if (__Mode != null)
			WindowState.SetStashMode(__Mode);
		AlphaEngine.Instance.SetStashWindowState(WindowState);
	}

	// ---------- Stash list ----------

	/**
	 * Loads the stash list via {@link GitStashOperator#ListStashes()} and
	 * populates the ListView. Runs synchronously — must not be called on the
	 * FX thread.
	 */
	private void LoadStashList()
	{
		CompletableFuture.runAsync(() ->
		{
			try
			{
				List<StashEntry> __Entries = GitDirTarget.GetStashOperator().ListStashes();
				Platform.runLater(() ->
				{
					StashEntries.setAll(__Entries);
					if (!__Entries.isEmpty())
						lst_StashList.getSelectionModel().selectFirst();
				});
			}
			catch (Exception __Ex)
			{
				Platform.runLater(() -> ShowError("Failed to load stash list", __Ex.getMessage()));
			}
		});
	}

	// ---------- Stash selection ----------

	/**
	 * When the user selects a stash entry, fetches the file list for that stash
	 * and replaces the centre list. The diff viewer is cleared as well: the
	 * shown diff belongs to a file of the previously selected stash and is no
	 * longer valid once the file list is replaced.
	 */
	private void OnStashSelectionChanged()
	{
		// Bump the version so any in-flight load for a previous selection is
		// dropped when it completes (rapid selection changes must not paint an
		// older stash's file list under the current selection).
		int __Version = ++StashSelectionVersion;
		ImmutableChangesWidgetInstance.Clear();
		DiffViewer.SetRawDiffText(null);
		UpdateButtonStates();

		StashEntry __CurrentStash = SelectedStash;
		if (__CurrentStash == null)
			return;

		CompletableFuture.runAsync(() ->
		{
			try
			{
				List<StashEntry.StashFile> __Files = GitDirTarget.GetStashOperator().GetFiles(__CurrentStash);
				Platform.runLater(() ->
				{
					if (__Version != StashSelectionVersion)
						return;   // stale — the selection moved on
					ImmutableChangesWidgetInstance.SetEntries(__Files);
				});
			}
			catch (Exception __Ex)
			{
				Platform.runLater(() ->
				{
					if (__Version != StashSelectionVersion)
						return;   // stale — don't alert over a newer selection
					ShowError("Failed to load stash files", __Ex.getMessage());
				});
			}
		});
	}

	// ---------- File selection ----------

	/**
	 * When the user selects a file in the stash file list, shows the diff of
	 * that file in the diff viewer. An empty selection (or no stash selected)
	 * clears the viewer. The git command runs off the FX thread; the result is
	 * applied back on the FX thread via {@code Platform.runLater}. Invoked from
	 * the {@link ImmutableChangesWidget} selection handler with the new entry.
	 */
	private void OnFileSelectionChanged(StashEntry.StashFile _Selected)
	{
		// Bump the version so an in-flight diff load for a previous selection is
		// dropped (rapid selection changes must not paint an older file's diff).
		// The stash version is captured too: a stash switch clears the file list
		// (selection → null), which fires another file-selection change, but the
		// guard must also reject a diff that raced across a stash switch.
		int __Version = ++FileSelectionVersion;
		int __StashVersion = StashSelectionVersion;
		StashEntry __CurrentStash = SelectedStash;
		if (_Selected == null || __CurrentStash == null)
		{
			DiffViewer.SetRawDiffText(null);
			return;
		}

		CompletableFuture.runAsync(() ->
		{
			try
			{
				String __Diff = GitDirTarget.GetStashOperator().GetFileDiff(__CurrentStash, _Selected.Path());
				Platform.runLater(() ->
				{
					if (__Version != FileSelectionVersion || __StashVersion != StashSelectionVersion)
						return;   // stale — the selection moved on
					DiffViewer.SetRawDiffText(__Diff);
				});
			}
			catch (Exception __Ex)
			{
				Platform.runLater(() ->
				{
					if (__Version != FileSelectionVersion || __StashVersion != StashSelectionVersion)
						return;   // stale — don't alert over a newer selection
					ShowError("Failed to load diff", __Ex.getMessage());
				});
			}
		});
	}

	// ---------- Stash operations ----------

	/**
	 * Creates a new stash via {@code git stash push} after prompting for an
	 * optional description. On success, refreshes the stash list and the main
	 * project UI. When Auto Restore is checked, the freshly created stash (now
	 * at {@code stash@{0}}) is immediately applied back, so the stashed
	 * changes stay in the working tree while a copy remains in the stash list.
	 */
	private void OnSaveStash()
	{
		TextInputDialog __Dialog = new TextInputDialog();
		__Dialog.setTitle("Create Stash");
		__Dialog.setHeaderText("Enter a stash description (optional)");
		__Dialog.setContentText("Description:");
		__Dialog.showAndWait().ifPresent(__Message ->
		{
			EStashMode __Mode = cmb_StashMode.getSelectionModel().getSelectedItem();
			DisableButtons(true);
			GitDirTarget.GetStashOperator().Push(__Mode, __Message, (__Ok, __Err, __Dir) ->
			{
				Platform.runLater(() ->
				{
					if (IsDisposed)
						return;
					if (!__Ok)
					{
						DisableButtons(false);
						ShowError("Stash failed", __Err);
						return;
					}
					// With auto restore the buttons stay disabled until the
					// follow-up apply completes (AutoRestoreNewStash re-enables
					// them); without it, re-enable now.
					if (chk_AutoRestore.isSelected())
						AutoRestoreNewStash();
					else
						DisableButtons(false);
					LoadStashList();
				});
			});
		});
	}

	/**
	 * Applies the stash created by the most recent Save back into the working
	 * tree. The fresh stash is always {@code stash@{0}}, so no lookup is needed;
	 * the application runs through the same operator queue (serialized after the
	 * push). Errors surface as an alert — the stash itself remains saved either
	 * way, so nothing is lost.
	 */
	private void AutoRestoreNewStash()
	{
		// Placeholder carrying only the index: the apply target is the fresh
		// stash at stash@{0}, which needs no other metadata.
		StashEntry __NewStash = new StashEntry(0, "", "", "");
		GitDirTarget.GetStashOperator().Apply(__NewStash, (__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (IsDisposed)
					return;
				DisableButtons(false);
				if (!__Ok)
					ShowError("Auto restore failed", __Err);
			});
		});
	}

	/**
	 * Applies and removes the selected stash via {@code git stash pop}.
	 */
	private void OnPopStash()
	{
		if (SelectedStash == null)
			return;
		DisableButtons(true);
		GitDirTarget.GetStashOperator().Pop(SelectedStash, (__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (IsDisposed)
					return;
				DisableButtons(false);
				if (!__Ok)
					ShowError("Pop failed", __Err);
				else
					LoadStashList();
			});
		});
	}

	/**
	 * Removes the selected stash via {@code git stash drop}, after a
	 * confirmation prompt — dropping a stash permanently discards its changes.
	 */
	private void OnDropStash()
	{
		if (SelectedStash == null)
			return;

		// A drop is irreversible — git cannot restore a dropped stash — so ask
		// for explicit confirmation and quote the description to make clear
		// exactly which stash is about to be lost.
		Alert __Confirm = new Alert(Alert.AlertType.CONFIRMATION);
		__Confirm.setTitle("Drop Stash");
		__Confirm.setHeaderText("Drop the selected stash?");
		__Confirm.setContentText(SelectedStash.GetDescription().isEmpty()
			? "This permanently deletes " + SelectedStash.GetStashRef() + " with no way to recover its changes."
			: "This permanently deletes " + SelectedStash.GetStashRef() + " (\"" + SelectedStash.GetDescription() + "\") with no way to recover its changes.");
		__Confirm.showAndWait().ifPresent(__Response ->
		{
			if (__Response != ButtonType.OK)
				return;
			DisableButtons(true);
			GitDirTarget.GetStashOperator().Drop(SelectedStash, (__Ok, __Err, __Dir) ->
			{
				Platform.runLater(() ->
				{
					if (IsDisposed)
						return;
					DisableButtons(false);
					if (!__Ok)
						ShowError("Drop failed", __Err);
					else
						LoadStashList();
				});
			});
		});
	}

	/**
	 * Applies the selected stash without removing it via {@code git stash apply}.
	 */
	private void OnApplyStash()
	{
		if (SelectedStash == null)
			return;
		DisableButtons(true);
		GitDirTarget.GetStashOperator().Apply(SelectedStash, (__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (IsDisposed)
					return;
				DisableButtons(false);
				if (!__Ok)
					ShowError("Apply failed", __Err);
			});
		});
	}

	/**
	 * Prompts for a new name and renames the selected stash.
	 * Git has no native stash rename, so the stash commit is recreated with the
	 * new message (see {@link GitStashOperator#Rename}). The five git commands
	 * run as one queued task on the GitOperator runner thread, so no other git
	 * operation can interleave and break the sequence's atomicity.
	 */
	private void OnRenameStash()
	{
		if (SelectedStash == null)
			return;

		TextInputDialog __Dialog = new TextInputDialog(SelectedStash.GetDescription());
		__Dialog.setTitle("Rename Stash");
		__Dialog.setHeaderText("Enter a new description for this stash");
		__Dialog.setContentText("Description:");
		__Dialog.showAndWait().ifPresent(__NewMessage ->
		{
			if (__NewMessage.isBlank())
				return;

			// Capture the target on the FX thread: the queued rename must not
			// read the mutable SelectedStash field (a selection change mid-flight
			// would make the final drop target the *wrong* stash).
			StashEntry __Target = SelectedStash;
			if (__Target == null)
				return;

			DisableButtons(true);
			GitDirTarget.GetStashOperator().Rename(__Target, __NewMessage, (__Ok, __Err, __Dir) ->
			{
				Platform.runLater(() ->
				{
					if (IsDisposed)
						return;
					DisableButtons(false);
					if (!__Ok)
						ShowError("Rename failed", __Err);
					else
						LoadStashList();
				});
			});
		});
	}

	// ---------- Helpers ----------

	/**
	 * Enables/disables all action buttons during an in-flight operation.
	 */
	private void DisableButtons(boolean _Disable)
	{
		btn_Rename.setDisable(_Disable);
		btn_Pop.setDisable(_Disable);
		btn_Drop.setDisable(_Disable);
		btn_Apply.setDisable(_Disable);
		btn_Save.setDisable(_Disable);
		cmb_StashMode.setDisable(_Disable);
		chk_AutoRestore.setDisable(_Disable);
	}

	/**
	 * Updates button enable states based on the current selection.
	 */
	private void UpdateButtonStates()
	{
		boolean __HasSelection = SelectedStash != null;
		btn_Rename.setDisable(!__HasSelection);
		btn_Pop.setDisable(!__HasSelection);
		btn_Drop.setDisable(!__HasSelection);
		btn_Apply.setDisable(!__HasSelection);
	}

	/**
	 * Shows an error alert on the FX thread.
	 */
	private void ShowError(String _Header, String _Content)
	{
		Alert __Alert = new Alert(Alert.AlertType.ERROR);
		__Alert.setTitle("Stash");
		__Alert.setHeaderText(_Header);
		__Alert.setContentText(_Content);
		__Alert.showAndWait();
	}

	// ---------- Inner types ----------

	/**
	 * Cell factory for the stash list.
	 * Shows {@code stash@{N}:} in grey followed by the description (or raw line)
	 * so the index is visually distinct from the content.
	 */
	private static class StashListCell extends ListCell<StashEntry>
	{
		@Override
		protected void updateItem(StashEntry _Item, boolean _Empty)
		{
			super.updateItem(_Item, _Empty);
			if (_Empty || _Item == null)
			{
				setText(null);
				setGraphic(null);
			}
			else
			{
				Text __Index = new Text("stash@{" + _Item.GetIndex() + "}: ");
				__Index.setFill(Color.GRAY);
				Text __Desc = new Text(_Item.GetDescription().isEmpty() ? _Item.GetRawLine() : _Item.GetDescription());
				HBox __Box = new HBox(2, __Index, __Desc);
				setGraphic(__Box);
				setText(null);
			}
		}
	}
}
