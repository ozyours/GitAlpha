package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IRefreshGitDirEvent;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.application.Platform;
import javafx.scene.layout.*;

import java.util.Objects;

/**
 * Project view for one open repository: a two-column grid — branches, file
 * changes and the commit form in a fixed 500px left column, the diff viewer
 * on the right. Subscribes to global refresh events and keeps its sub-widgets
 * in sync with the repository state.
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

		var __GridLayout = new GridPane();

		// Left pane (branches + changes + commit) keeps a fixed width;
		// the diff viewer (column 1) takes all remaining space.
		var __LeftColumn = new ColumnConstraints();
		__LeftColumn.setMinWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setPrefWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setMaxWidth(LEFT_PANE_WIDTH);
		__GridLayout.getColumnConstraints().add(__LeftColumn);

		// Row constraints are assembled here too (values come from the constants
		// declared below), so all left-pane sizing (the 500px column and the
		// three row heights) is configured in one place: this widget is the
		// layout authority, and CommitWidget sets no height of its own — it
		// relies on its grid row. The branch row is pinned (min == max) and
		// never grows. The commit row keeps a fixed preferred height (no vgrow)
		// and therefore sticks to the bottom. Only the changes row is vgrow
		// ALWAYS, so it is the sole absorber of extra vertical space when the
		// window is resized.
		var __BranchRow = new RowConstraints();
		__BranchRow.setMinHeight(BRANCH_ROW_MIN_HEIGHT);
		__BranchRow.setMaxHeight(BRANCH_ROW_MIN_HEIGHT);
		var __ChangesRow = new RowConstraints();
		__ChangesRow.setMinHeight(CHANGES_ROW_MIN_HEIGHT);
		__ChangesRow.setVgrow(Priority.ALWAYS);
		var __CommitRow = new RowConstraints();
		__CommitRow.setPrefHeight(COMMIT_ROW_PREF_HEIGHT);
		__GridLayout.getRowConstraints().addAll(__BranchRow, __ChangesRow, __CommitRow);

		__GridLayout.add(BranchWidgetInstance, 0, 0);    // Top left
		__GridLayout.add(ChangesWidgetInstance, 0, 1);   // Middle left
		__GridLayout.add(CommitWidgetInstance, 0, 2);    // Bottom left
		__GridLayout.add(TextViewerWidgetInstance, 1, 0, 1, 3); // Right side, spanning 3 rows

		// The diff viewer fills the whole right column
		GridPane.setHgrow(TextViewerWidgetInstance, Priority.ALWAYS);
		GridPane.setVgrow(TextViewerWidgetInstance, Priority.ALWAYS);

		// Set spacing
		__GridLayout.setHgap(10);
		__GridLayout.setVgap(10);

		getChildren().add(__GridLayout);

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
	/** Preferred height (px) of the commit form row; fixed (no vgrow), so the form sticks to the bottom */
	private static final double COMMIT_ROW_PREF_HEIGHT = 240;

	/** The tab that hosts this project widget */
	public final GitDirTabButton TabButton;
	/** The repository this widget displays */
	public final GitDir GitDirTarget;

	private final BranchWidget BranchWidgetInstance;
	private final ChangesWidget ChangesWidgetInstance;
	private final CommitWidget CommitWidgetInstance;
	private final TextViewerWidget TextViewerWidgetInstance;
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
