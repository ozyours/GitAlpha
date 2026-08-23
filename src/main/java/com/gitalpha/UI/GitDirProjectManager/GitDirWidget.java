package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IRefreshGitDirEvent;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.Components.ASplitPane;
import com.gitalpha.UI.Components.ATabButton;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

import java.util.Objects;

/**
 * Project view for one open repository. The content is an outer split pane:
 * the left sub-tab pane (user-resizable, persisted globally) is a header row
 * of two {@link ATabButton}s above a {@link StackPane} content swap — no
 * TabPane machinery — holding "Changes" (branches, file changes and the
 * commit form) and "History" (the {@link TreeViewWidget} commit-graph
 * placeholder). The shared {@link TextViewerWidget} diff viewer fills the
 * right pane and follows the "Changes" file-list selection; the "History"
 * graph will drive it once implemented. Subscribes to global refresh events
 * and keeps its sub-widgets in sync with the repository state.
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

		// Create the sub-widgets; the "Changes" grid is assembled below.
		BranchWidgetInstance = new BranchWidget(GitDirTarget, this);
		ChangesWidgetInstance = new ChangesWidget(GitDirTarget, this);
		CommitWidgetInstance = new CommitWidget(GitDirTarget, this);
		TextViewerWidgetInstance = new TextViewerWidget(GitDirTarget, this); // TODO: Pass the selected FileChange when implemented
		TreeViewWidgetInstance = new TreeViewWidget(GitDirTarget, this);

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
		// The single column stretches to fill the grid's full width so the
		// branch/changes/commit widgets extend across the whole left pane of
		// the outer split pane: Hgrow ALWAYS absorbs the extra space the grid
		// is given, and PercentWidth 100 pins the column to the entire grid
		// width. Without these flags the widgets would sit at their preferred
		// widths and leave the rest of the left pane empty.
		var __ContentColumn = new ColumnConstraints();
		__ContentColumn.setHgrow(Priority.ALWAYS);
		__ContentColumn.setPercentWidth(100);
		__ChangesLayout.getColumnConstraints().add(__ContentColumn);

		__ChangesLayout.add(BranchWidgetInstance, 0, 0);  // Top
		__ChangesLayout.add(ChangesWidgetInstance, 0, 1); // Middle
		__ChangesLayout.add(CommitWidgetInstance, 0, 2);  // Bottom
		__ChangesLayout.setVgap(10);

		// The two views share the diff viewer on the right: "Changes" shows the
		// working-tree diff; "History" is still a commit-graph placeholder, so
		// its diff will surface here once the graph lands (see ROADMAP.md). The
		// sub-tab header is an HBox ({@code a-tab-header}, spacing 0) of real
		// buttons (ATabButton) above a StackPane ({@code a-tab-content} with
		// hairline) content swap — no TabPane machinery, so the header height
		// is predictable and the selected button is styled by its own skin.
		btn_ChangesTab = new ATabButton("Changes");
		btn_HistoryTab = new ATabButton("History");
		HBox __TabHeader = new HBox(btn_ChangesTab, btn_HistoryTab);
		__TabHeader.getStyleClass().add("a-tab-header");
		__TabHeader.setSpacing(0);
		stk_SubTabContent = new StackPane();
		stk_SubTabContent.getStyleClass().add("a-tab-content");
		// The header strip (Background 2) and the content hairline are part of
		// the same tab skin, so the strip and its two buttons re-bake together
		// on palette switches. The sheets are attached to the header/content
		// nodes themselves so the descendant selectors match.
		__TabHeader.getStylesheets().addAll(com.gitalpha.Theme.ThemeManager.Instance.GetSubTabButtonStylesheets());
		stk_SubTabContent.getStylesheets().addAll(com.gitalpha.Theme.ThemeManager.Instance.GetSubTabButtonStylesheets());
		// Keep a strong reference so the weak-registered listener is not pruned
		// while this widget lives; it re-bakes the header/content strip on theme
		// changes (the buttons re-bake via their own ATabButton listeners).
		TabSkinListener = _Palette ->
		{
			__TabHeader.getStylesheets().clear();
			__TabHeader.getStylesheets().addAll(com.gitalpha.Theme.ThemeManager.Instance.GetSubTabButtonStylesheets());
			stk_SubTabContent.getStylesheets().clear();
			stk_SubTabContent.getStylesheets().addAll(com.gitalpha.Theme.ThemeManager.Instance.GetSubTabButtonStylesheets());
		};
		com.gitalpha.Theme.ThemeManager.Instance.AddIThemeChangeEvent(TabSkinListener);
		btn_ChangesTab.setOnAction(__Event -> SelectSubTab(btn_ChangesTab, __ChangesLayout));
		btn_HistoryTab.setOnAction(__Event -> SelectSubTab(btn_HistoryTab, TreeViewWidgetInstance));
		VBox __SubPanel = new VBox(__TabHeader, stk_SubTabContent);
		VBox.setVgrow(stk_SubTabContent, Priority.ALWAYS);
		SelectSubTab(btn_ChangesTab, __ChangesLayout); // initial: Changes tab active

		// Outer split pane: the left sub-tab pane and the shared diff viewer
		// are separated by a draggable divider — the vertical border between
		// the two panels. The left-pane width is user-resizable and persisted
		// globally (one width for every project, stored directly in the session
		// file by AlphaEngine), and the divider itself is themed by the
		// split-pane skin.
		ASplitPane __SplitPane = new ASplitPane(__SubPanel, TextViewerWidgetInstance);
		// Neither side may collapse completely: the sub-tab panel keeps a floor
		// width so the branch/changes/commit widgets stay usable, and the diff
		// viewer keeps a floor too so a wide window can't be monopolized by the
		// left pane. The SplitPane divider drag respects both floors.
		__SubPanel.setMinWidth(LEFT_PANE_MIN_WIDTH);
		TextViewerWidgetInstance.setMinWidth(RIGHT_PANE_MIN_WIDTH);

		// Restore the persisted column width and keep it stable across window
		// resizes. The divider position is a fraction, so the saved pixel width
		// is converted to a fraction only once the pane has a real width —
		// applying it before the pane is sized would let SplitPaneSkin's
		// size-settling redistribute the divider (JDK-8092863). Every later
		// resize (window maximize/restore) re-applies the saved width the same
		// way, so the left pane keeps its user-chosen pixel width instead of
		// being scaled with the window.
		__SplitPane.widthProperty().addListener((__Obs, __Old, __New) ->
		{
			double __Total = __SplitPane.getWidth();
			if (__Total <= 0)
				return;
			// Convert the saved pixel width to a divider fraction and clamp so
			// neither pane violates its floor on a narrow window (a stale huge
			// width must not collapse the viewer).
			double __MinFraction = LEFT_PANE_MIN_WIDTH / __Total;
			double __MaxFraction = 1.0 - RIGHT_PANE_MIN_WIDTH / __Total;
			double __Fraction = AlphaEngine.Instance.GetSharedLeftPaneWidth() / __Total;
			__SplitPane.setDividerPositions(Math.max(__MinFraction, Math.min(__MaxFraction, __Fraction)));
		});

		// Persist the left-pane width (in pixels) back into the shared engine
		// state only while the user is dragging the divider — globally, not per
		// project; the next session save writes it to the file. Window
		// maximize/restore changes the pane width and moves the divider too,
		// but that is a transient resize redistribution, not user intent, and
		// must not overwrite the persisted width. The mouse flags gate the
		// position listener so only genuine drags write the shared scalar.
		final boolean[] __Dragging = { false };
		__SplitPane.addEventFilter(MouseEvent.MOUSE_PRESSED, __Event -> __Dragging[0] = true);
		__SplitPane.addEventFilter(MouseEvent.MOUSE_RELEASED, __Event -> __Dragging[0] = false);
		__SplitPane.getDividers().get(0).positionProperty().addListener((__Obs, __Old, __New) ->
		{
			if (!__Dragging[0] || __New == null || __SplitPane.getWidth() <= 0)
				return;
			AlphaEngine.Instance.SetSharedLeftPaneWidth(__New.doubleValue() * __SplitPane.getWidth());
		});

		getChildren().add(__SplitPane);

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

	/**
	 * Minimum width (px) of the left sub-tab pane. The SplitPane divider
	 * cannot shrink it below this, so the branch/changes/commit widgets always
	 * stay usable. The pane's preferred/actual width is user-resizable and
	 * persisted globally via {@link AlphaEngine#GetSharedLeftPaneWidth()}.
	 */
	private static final double LEFT_PANE_MIN_WIDTH = 250;
	/**
	 * Minimum width (px) of the diff viewer (right pane). The SplitPane
	 * divider cannot shrink it below this, so a wide window can't be
	 * monopolized by the left pane.
	 */
	private static final double RIGHT_PANE_MIN_WIDTH = 200;
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

	/** The "Changes" sub-tab header button (selected by default) */
	private final ATabButton btn_ChangesTab;
	/** The "History" sub-tab header button */
	private final ATabButton btn_HistoryTab;
	/** The sub-tab content stack ({@code a-tab-content} hairline) — shows the active tab's view */
	private final StackPane stk_SubTabContent;
	/**
	 * Strong reference to the theme listener that re-bakes the header
	 * ({@code a-tab-header}, Background2 strip) and content hairline
	 * stylesheets on palette switches; held strongly so the weak registry in
	 * {@code ThemeManager} does not prune it while this widget lives.
	 */
	private final com.gitalpha.Theme.IThemeChangeEvent TabSkinListener;

	private final BranchWidget BranchWidgetInstance;
	/** The staged/unstaged file list of the "Changes" sub-tab */
	private final ChangesWidget ChangesWidgetInstance;
	/** The commit summary/description form at the bottom of the "Changes" sub-tab */
	private final CommitWidget CommitWidgetInstance;
	/** The single shared diff viewer (right pane of the outer split pane) that
	 * serves both sub-tabs: "Changes" shows the working-tree diff, "History"
	 * will show the selected commit's diff. Selection-driven — no explicit
	 * refresh of its own. */
	private final TextViewerWidget TextViewerWidgetInstance;
	/** The "History" sub-tab content: the commit-graph placeholder (the diff
	 * for a selected commit is shown by {@link #TextViewerWidgetInstance}). */
	private final TreeViewWidget TreeViewWidgetInstance;
	/** Refresh listener that rebuilds this widget when its repo refreshes (null target = any repo) */
	private final IRefreshGitDirEvent RefreshGitDirEventListener;
	/** True once the widget is disposed; guards async refresh callbacks and the dispose itself */
	private boolean Disposed = false;

	/** Point the diff viewer at the given file change (selection-driven). */
	public void ReadFileChange(FileChange _FileChange)
	{
		TextViewerWidgetInstance.SetFileChange(_FileChange);
	}

	/**
	 * Switch the active sub-tab: restyle the header buttons (selected state)
	 * and swap the content stack to the selected view. The diff viewer is
	 * selection-driven ({@link #ReadFileChange}), so no diff refresh is needed
	 * here.
	 *
	 * @param _Selected the button to mark as the active tab
	 * @param _Content  the view to show in the content stack
	 */
	private void SelectSubTab(ATabButton _Selected, Node _Content)
	{
		btn_ChangesTab.SetSelected(_Selected == btn_ChangesTab);
		btn_HistoryTab.SetSelected(_Selected == btn_HistoryTab);
		stk_SubTabContent.getChildren().setAll(_Content);
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

	/**
	 * Unregisters the refresh and tab-skin theme listeners and marks this
	 * widget disposed. The theme listener ({@link #TabSkinListener}) must be
	 * removed here because {@code ThemeManager} holds it weakly — the strong
	 * reference in this widget would otherwise keep re-baking header/content
	 * stylesheets after disposal.
	 */
	public void Dispose()
	{
		if (Disposed)
			return;

		Disposed = true;
		AlphaEngine.Instance.RemoveIRefreshGitDirEvent(RefreshGitDirEventListener);
		com.gitalpha.Theme.ThemeManager.Instance.RemoveIThemeChangeEvent(TabSkinListener);
	}
}
