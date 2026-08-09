package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IRefreshGitDirEvent;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.*;

import java.util.Objects;

/**
 * Project view for one open repository. The content is an outer two-column
 * grid: the left sub-tab pane (fixed 500px) holds "Changes" (branches, file
 * changes and the commit form) and "History" (the {@link TreeViewWidget}
 * commit-graph placeholder); the shared {@link TextViewerWidget} diff viewer
 * fills the right column and shows whichever of the two tabs' selections is
 * active. Subscribes to global refresh events and keeps its sub-widgets in
 * sync with the repository state.
 */
public class GitDirWidget extends StackPane
{
	/**
	 * Assembles the layout and registers a refresh-event listener that
	 * rebuilds this project when its repo (or any repo, null target) refreshes.
	 *
	 * @param _TabButton the tab hosting this project widget
	 * @param _GitDir    the repository this widget displays
	 */
	public GitDirWidget(GitDirTabButton _TabButton, GitDir _GitDir)
	{
		super();

		assert _TabButton != null;
		assert _GitDir != null;

		TabButton = _TabButton;
		GitDirTarget = _GitDir;

		// Create grid layout
		BranchWidgetInstance = new BranchWidget(GitDirTarget, this);
		ChangesWidgetInstance = new ChangesWidget(GitDirTarget, this);
		CommitWidgetInstance = new CommitWidget(GitDirTarget, this);
		TextViewerWidgetInstance = new TextViewerWidget(GitDirTarget, this); // TODO: Pass the selected FileChange when implemented
		TreeViewWidgetInstance = new TreeViewWidget(GitDirTarget, this);

		// Outer two-column grid: the left sub-tab pane keeps a fixed width and
		// the diff viewer (column 1) takes all remaining space. The viewer is
		// shared across both tabs, so switching "Changes" / "History" keeps the
		// same TextViewerWidget on screen.
		var __OuterGrid = new GridPane();
		var __LeftColumn = new ColumnConstraints();
		__LeftColumn.setMinWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setPrefWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setMaxWidth(LEFT_PANE_WIDTH);
		__OuterGrid.getColumnConstraints().add(__LeftColumn);

		// The single outer row is growable so both the tab pane and the diff
		// viewer stretch to fill the widget height.
		var __OuterRow = new RowConstraints();
		__OuterRow.setVgrow(Priority.ALWAYS);
		__OuterGrid.getRowConstraints().add(__OuterRow);

		// The "Changes" tab hosts the three working-tree widgets stacked in
		// their own grid; row sizing (the constants below) is the layout
		// authority of this widget, and CommitWidget sets no height of its own
		// — it relies on its grid row. The branch row is pinned (min == max)
		// and never grows. The commit row is pinned the same way (min == pref,
		// no vgrow): because the grid sits inside a sub-tab pane, the tab
		// header consumes vertical space that would otherwise compress a merely
		// preferred-height row — pinning min == pref keeps the commit form at
		// full size, stuck to the bottom. Only the changes row is vgrow ALWAYS,
		// so it is the sole absorber of extra vertical space on resize.
		var __ChangesLayout = new GridPane();
		var __BranchRow = new RowConstraints();
		__BranchRow.setMinHeight(BRANCH_ROW_MIN_HEIGHT);
		__BranchRow.setMaxHeight(BRANCH_ROW_MIN_HEIGHT);
		var __ChangesRow = new RowConstraints();
		__ChangesRow.setMinHeight(CHANGES_ROW_MIN_HEIGHT);
		__ChangesRow.setVgrow(Priority.ALWAYS);
		var __CommitRow = new RowConstraints();
		__CommitRow.setPrefHeight(COMMIT_ROW_PREF_HEIGHT);
		__CommitRow.setMinHeight(COMMIT_ROW_PREF_HEIGHT);
		__ChangesLayout.getRowConstraints().addAll(__BranchRow, __ChangesRow, __CommitRow);

		__ChangesLayout.add(BranchWidgetInstance, 0, 0);  // Top
		__ChangesLayout.add(ChangesWidgetInstance, 0, 1); // Middle
		__ChangesLayout.add(CommitWidgetInstance, 0, 2);  // Bottom
		__ChangesLayout.setVgap(10);

		// The two views share the diff viewer on the right: "Changes" shows the
		// working-tree diff; "History" is still a commit-graph placeholder, so
		// its diff will surface here once the graph lands (see ROADMAP.md). Both
		// tabs are non-closable — they are the fixed structure of the project
		// widget, not user-managed tabs.
		Tab __ChangesTab = new Tab("Changes", __ChangesLayout);
		Tab __HistoryTab = new Tab("History", TreeViewWidgetInstance);
		__ChangesTab.setClosable(false);
		__HistoryTab.setClosable(false);
		TabPane __SubTabPane = new TabPane(__ChangesTab, __HistoryTab);

		__OuterGrid.add(__SubTabPane, 0, 0);
		__OuterGrid.add(TextViewerWidgetInstance, 1, 0);

		// The diff viewer fills the whole right column
		GridPane.setHgrow(TextViewerWidgetInstance, Priority.ALWAYS);
		GridPane.setVgrow(TextViewerWidgetInstance, Priority.ALWAYS);

		__OuterGrid.setHgap(10);
		getChildren().add(__OuterGrid);

		RefreshGitDirEventListener = (_GitDirTarget, _Reason) ->
		{
			if (_GitDirTarget == null || Objects.equals(_GitDirTarget.GetGitDirPath(), GitDirTarget.GetGitDirPath()))
			{
				RefreshGitDirWidget();
			}
		};
		AlphaEngine.Instance.AddIRefreshGitDirEvent(RefreshGitDirEventListener);

		RefreshGitDirWidget();
	}

	/** Fixed width (px) of the left pane; the diff viewer takes the remaining width */
	private static final double LEFT_PANE_WIDTH = 500;
	/** Pinned height (px) of the branch tree row (min == max, never grows or shrinks) */
	private static final double BRANCH_ROW_MIN_HEIGHT = 140;
	/**
	 * Minimum height (px) of the changes list row. The only growable row
	 * (vgrow ALWAYS, no max height) — it absorbs all extra vertical space.
	 */
	private static final double CHANGES_ROW_MIN_HEIGHT = 240;
	/**
	 * Height (px) of the commit form row; the min height is pinned to this same
	 * value and there is no vgrow, so the sub-tab header above the grid cannot
	 * compress the form and it always renders at full size, stuck to the bottom.
	 */
	private static final double COMMIT_ROW_PREF_HEIGHT = 240;

	/** The tab that hosts this project widget */
	public final GitDirTabButton TabButton;
	/** The repository this widget displays */
	public final GitDir GitDirTarget;

	private final BranchWidget BranchWidgetInstance;
	private final ChangesWidget ChangesWidgetInstance;
	private final CommitWidget CommitWidgetInstance;
	/** The single shared diff viewer (right column of the outer grid) that
	 * serves both sub-tabs: "Changes" shows the working-tree diff, "History"
	 * will show the selected commit's diff. Selection-driven — no explicit
	 * refresh of its own. */
	private final TextViewerWidget TextViewerWidgetInstance;
	/** The "History" sub-tab content: the commit-graph placeholder (the diff
	 * for a selected commit is shown by {@link #TextViewerWidgetInstance}). */
	private final TreeViewWidget TreeViewWidgetInstance;
	/** Refresh listener that rebuilds this widget when its repo refreshes (null target = any repo) */
	private final IRefreshGitDirEvent RefreshGitDirEventListener;
	private boolean Disposed = false;

	/** Point the diff viewer at the given file change (selection-driven). */
	public void ReadFileChange(FileChange _FileChange)
	{
		TextViewerWidgetInstance.SetFileChange(_FileChange);
	}

	/**
	 * Refreshes the repository state, then on the FX thread rebuilds the
	 * changes and branch lists. No-op once disposed. The diff viewer needs
	 * no explicit refresh — it follows the changes-list selection.
	 */
	public void RefreshGitDirWidget()
	{
		if (Disposed)
			return;

		GitDirTarget.Refresh((__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (Disposed)
					return;

				ChangesWidgetInstance.UpdateChanges();
				BranchWidgetInstance.UpdateBranchList();
			});
		});
	}

	/** Unregisters the refresh listener and marks this widget disposed. */
	public void Dispose()
	{
		if (Disposed)
			return;

		Disposed = true;
		AlphaEngine.Instance.RemoveIRefreshGitDirEvent(RefreshGitDirEventListener);
	}
}
