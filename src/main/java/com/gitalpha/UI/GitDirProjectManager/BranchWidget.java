package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.IGitOperationCallback;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.EButtonVariant;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.UI.Components.AButton;
import com.gitalpha.UI.Components.AContextMenu;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Branch selector widget: a compact row of controls that sits above the
 * sub-tab strip and is shared by both sub-tabs. The row stacks an {@link HBox}
 * of three controls above a reserved track-counts line:
 * <ul>
 *   <li>the active-branch {@link AButton} — the widest control. Its graphic is
 *       a two-line stack: the "Current Branch:" caption (small, muted) over the
 *       branch name (normal skin, no marker); clicking it opens a
 *       {@link BranchPickerPopover} listing all local + remote branches with a
 *       filter field, and activating a branch (primary click or Enter)
 *       triggers checkout through the operator queue;</li>
 *   <li>the context-sensitive Quick Action button — narrower than the branch
 *       button: {@code Pull} when the active local branch is behind its
 *       upstream, {@code Push} when it is ahead, {@code Fetch} otherwise
 *       (behind wins over ahead when diverged; no active branch / detached
 *       HEAD yields {@code Fetch});</li>
 *   <li>a fixed-size {@code ...} overflow button whose themed context menu
 *       offers {@code Fetch} and {@code Branch Manager...}.</li>
 * </ul>
 * The button row is followed by the always-present counts line, which reports
 * the active branch's unpushed / unpulled commit counts (empty when in sync or
 * with no active branch) and reserves its line height even when empty.
 * The branch and quick action buttons grow with the row while the overflow
 * button is pinned; the quick action is capped at
 * {@link #QUICK_ACTION_MAX_FRACTION} of the row width so the branch button is
 * always the larger of the two.
 */
class BranchWidget extends BaseWidget
{
	/** Caption shown above the active branch name inside the button graphic */
	private static final String CAPTION_TEXT = "Current Branch:";
	/** Placeholder name shown when the repository has no checked-out branch */
	private static final String NO_BRANCH_TEXT = "(no branch)";
	/**
	 * Muted grey inline style (fixed literal, not palette-derived) for the
	 * secondary labels: the placeholder branch name and the counts line
	 */
	private static final String MUTED_LABEL_STYLE = "-fx-text-fill: #808080;";
	/**
	 * Inline CSS for the button's caption line: muted grey, smaller size
	 */
	private static final String CAPTION_LABEL_STYLE = "-fx-text-fill: #808080; -fx-font-size: 10px;";
	/**
	 * Vertical gap between the button row and the counts line
	 */
	private static final double V_GAP = 4;
	/**
	 * Horizontal gap between the buttons of the branch row
	 */
	private static final double H_GAP = 6;
	/**
	 * Pinned minimum height (px) of the reserved track-counts line — keeps the
	 * line alive (and the row height stable) even when its text is empty
	 */
	private static final double COUNTS_LINE_MIN_HEIGHT = 18;
	/**
	 * Fraction of the branch row width the quick action button may occupy at
	 * most; the branch button keeps the remainder, so it is always wider
	 */
	private static final double QUICK_ACTION_MAX_FRACTION = 0.35;
	/**
	 * Fixed side (px) of the square {@code ...} overflow button — min == pref == max
	 * on both width and height, so it never flexes into a wide rectangle; ≈ the
	 * two-line branch/quick button height, so it aligns visually with them
	 */
	private static final double OVERFLOW_BUTTON_SIZE = 40;
	/** Label of the quick action / overflow Fetch entry */
	private static final String QUICK_FETCH_LABEL = "Fetch";
	/** Label of the quick action Pull entry (active branch behind its upstream) */
	private static final String QUICK_PULL_LABEL = "Pull";
	/** Label of the quick action Push entry (active branch ahead of its upstream) */
	private static final String QUICK_PUSH_LABEL = "Push";
	/** Label of the overflow button */
	private static final String OVERFLOW_LABEL = "...";

	/**
	 * Button that shows the active branch (two-line graphic) and opens the popover on click
	 */
	private final AButton BranchButton;
	/**
	 * Context-sensitive quick action button (Pull / Push / Fetch)
	 */
	private final AButton QuickActionButton;
	/**
	 * Fixed-size overflow button opening the secondary-action menu
	 */
	private final AButton OverflowButton;
	/**
	 * Single reusable overflow menu (items + handlers wired once); the
	 * {@code ...} button toggles it instead of building a menu per press, so
	 * repeated presses cannot stack overlapping popups
	 */
	private final AContextMenu OverflowMenu;
	/**
	 * Second line of the branch button graphic: the active branch name only
	 * (no marker, no count), or {@link #NO_BRANCH_TEXT} when detached
	 */
	private final Label ActiveBranchNameLabel;
	/**
	 * Reserved line below the button row showing the active branch's unpushed /
	 * unpulled counts (empty when in sync or with no active branch)
	 */
	private final Label TrackCountsLabel;
	/**
	 * Non-modal picker popover listing all branches, built on BranchListWidget
	 */
	private final BranchPickerPopover BranchPicker;

	/**
	 * Build the branch widget: wire the active-branch button (its popover
	 * performs the checkout on activation), the quick action button and the
	 * reusable overflow menu, then populate the branch list from the
	 * repository.
	 *
	 * @param _GitDirTarget       the repository this widget operates on
	 * @param _GitDirWidgetTarget the hosting project widget
	 */
	public BranchWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// ── Branch button graphic: caption over the active branch name ──
		// The caption is small/muted; the name keeps the normal skin (no
		// inline style) so it reads like ordinary button text. The name label
		// shows the placeholder (muted) until the first refresh lands.
		Label __Caption = new Label(CAPTION_TEXT);
		__Caption.setStyle(CAPTION_LABEL_STYLE);
		ActiveBranchNameLabel = new Label(NO_BRANCH_TEXT);
		ActiveBranchNameLabel.setStyle(MUTED_LABEL_STYLE);
		VBox __BranchGraphic = new VBox(__Caption, ActiveBranchNameLabel);

		// ── Trigger button ──
		BranchButton = new AButton("", EButtonVariant.NORMAL);
		BranchButton.setGraphic(__BranchGraphic);
		BranchButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
		// Buttons cap their max width at their computed pref; without this the
		// button would not absorb the HBox slack and the flex would never happen.
		BranchButton.setMaxWidth(Double.MAX_VALUE);
		// Uncapped max height like the quick action, so when the HBox's
		// fillHeight stretches the row both buttons land on the exact same
		// height (otherwise the branch button would stay at its two-line pref).
		BranchButton.setMaxHeight(Double.MAX_VALUE);
		BranchButton.setOnAction(__E -> TogglePopover());

		// ── Quick action + overflow buttons ──
		// The quick action's label/action are set by UpdateBranchList once
		// the active branch's track state is known; Fetch is the safe default
		// before the first refresh lands.
		QuickActionButton = new AButton(QUICK_FETCH_LABEL, EButtonVariant.NORMAL);
		// Let the label shrink below its text width on a narrow pane; it still
		// grows up to its 35% cap below.
		QuickActionButton.setMinWidth(0);
		// The HBox stretches to the row height set by the taller two-line
		// branch button; without an uncapped max height the quick action would
		// stay at its own (single-line) pref height.
		QuickActionButton.setMaxHeight(Double.MAX_VALUE);
		OverflowButton = new AButton(OVERFLOW_LABEL, EButtonVariant.NORMAL);
		OverflowButton.setMinWidth(OVERFLOW_BUTTON_SIZE);
		OverflowButton.setPrefWidth(OVERFLOW_BUTTON_SIZE);
		OverflowButton.setMaxWidth(OVERFLOW_BUTTON_SIZE);
		// Fixed square: pin the height the same way so the overflow button is
		// 40x40 and never stretches with the row into a wide rectangle.
		OverflowButton.setMinHeight(OVERFLOW_BUTTON_SIZE);
		OverflowButton.setPrefHeight(OVERFLOW_BUTTON_SIZE);
		OverflowButton.setMaxHeight(OVERFLOW_BUTTON_SIZE);
		// The themed button skin's ~12px horizontal padding leaves too little
		// content room for the glyph at this fixed size — same override the
		// branch-list +/- glyph buttons use.
		OverflowButton.setStyle("-fx-padding: 4 6 4 6;");
		// One reusable overflow menu, built once with its handlers wired here:
		// the button toggles this instance rather than constructing a menu per
		// press, so repeated presses cannot leave multiple menus on screen.
		OverflowMenu = new AContextMenu();
		MenuItem __FetchItem = new MenuItem(QUICK_FETCH_LABEL);
		__FetchItem.setOnAction(__E -> RunQuickAction("Fetch Failed", "Failed to fetch from remote(s)",
				__Cb -> GetGitDirTarget().FetchAll(__Cb)));
		MenuItem __BranchManagerItem = new MenuItem("Branch Manager\u2026");
		__BranchManagerItem.setOnAction(__E -> BranchManagerWidget.Open(GetGitDirTarget()));
		OverflowMenu.getItems().addAll(__FetchItem, __BranchManagerItem);
		OverflowButton.setOnAction(__E -> ShowOverflowMenu());

		// ── Popover ──
		BranchPicker = new BranchPickerPopover(branch ->
		{
			CheckoutBranchByName(branch.FullPath());
		});

		// Populate and wire up
		UpdateBranchList();

		// Layout: [branch (grows, widest)] [quick action (grows, capped)]
		// [overflow (fixed)] above a reserved counts line. Both growing
		// children stretch with the row, but the quick action is capped at a
		// fraction of the width so the branch button always wins the space.
		HBox __ButtonRow = new HBox(H_GAP, BranchButton, QuickActionButton, OverflowButton);
		HBox.setHgrow(BranchButton, Priority.ALWAYS);
		HBox.setHgrow(QuickActionButton, Priority.ALWAYS);
		QuickActionButton.maxWidthProperty().bind(__ButtonRow.widthProperty().multiply(QUICK_ACTION_MAX_FRACTION));
		TrackCountsLabel = new Label("");
		TrackCountsLabel.setStyle(MUTED_LABEL_STYLE);
		TrackCountsLabel.setMinHeight(COUNTS_LINE_MIN_HEIGHT);
		VBox __VBox = new VBox(V_GAP, __ButtonRow, TrackCountsLabel);
		VBox.setVgrow(__ButtonRow, Priority.ALWAYS);
		getChildren().add(__VBox);
	}

	// ------------------------------------------------------------------
	//  Popover toggle
	// ------------------------------------------------------------------

	/**
	 * Show the branch-picker popover below the button, or hide it if already visible.
	 */
	private void TogglePopover()
	{
		if (BranchPicker.IsShowing())
			BranchPicker.Hide();
		else
			BranchPicker.ShowAt(BranchButton);
	}

	// ------------------------------------------------------------------
	//  Overflow menu
	// ------------------------------------------------------------------

	/**
	 * Toggle the single reusable {@code ...} overflow {@link #OverflowMenu}
	 * below the button: a press while it is showing hides it, otherwise it
	 * shows. Fetch runs {@code git fetch --all}, and Branch Manager opens the
	 * per-repository management window via {@link BranchManagerWidget#Open}.
	 * Operating on one shared instance (rather than building a menu per press)
	 * is what stops repeated presses from stacking overlapping popups.
	 */
	private void ShowOverflowMenu()
	{
		if (OverflowMenu.isShowing())
		{
			OverflowMenu.hide();
			return;
		}
		OverflowMenu.show(OverflowButton, Side.BOTTOM, 0, 0);
	}

	// ------------------------------------------------------------------
	//  Branch list refresh
	// ------------------------------------------------------------------

	/**
	 * Rebuild the popover's branch list from the current GitDir state, update
	 * the button's name line to the active branch, retarget the quick action
	 * from the active branch's ahead/behind state, and refresh the counts
	 * line. May be called from a refresh callback, so the actual UI update is
	 * deferred to the JavaFX thread.
	 */
	public void UpdateBranchList()
	{
		Debug.Log(Debug.BranchesCategory, "Updating branch list");
		Platform.runLater(() ->
		{
			String __Active = GetGitDirTarget().GetActiveBranch();
			// Copy the live list — the operator runner mutates it; the popover must not alias it.
			List<GitBranch> __Branches = new ArrayList<>(GetGitDirTarget().GetBranches());

			// Update the popover's backing list.
			BranchPicker.SetBranches(__Branches, __Active);

			// Locate the active local branch in the snapshot so the quick
			// action and the counts line can read its upstream track state
			// (null when detached / no active branch → Fetch, empty counts).
			GitBranch __ActiveBranch = null;
			if (__Active != null && !__Active.isBlank())
			{
				for (GitBranch __Branch : __Branches)
				{
					if (!__Branch.Remote() && __Branch.FullPath().equals(__Active))
					{
						__ActiveBranch = __Branch;
						break;
					}
				}
			}
			UpdateQuickAction(__ActiveBranch);

			// Update the button's name line (branch name only) and the counts line.
			if (__Active != null && !__Active.isBlank())
			{
				ActiveBranchNameLabel.setText(__Active);
				ActiveBranchNameLabel.setStyle("");
			}
			else
			{
				ActiveBranchNameLabel.setText(NO_BRANCH_TEXT);
				ActiveBranchNameLabel.setStyle(MUTED_LABEL_STYLE);
			}
			TrackCountsLabel.setText(FormatTrackCounts(__ActiveBranch));
		});
	}

	/**
	 * Render the tracked-commit counts for the active branch, showing only the
	 * non-zero parts: {@code "N unpushed, M unpulled"} when diverged,
	 * {@code "N unpushed"} ahead-only, {@code "M unpulled"} behind-only, and
	 * empty when in sync or with no active branch. The counts ride on the
	 * refresh's single for-each-ref track query (see
	 * {@link GitBranch#UnpushedCommits()} / {@link GitBranch#UnpulledCommits()}),
	 * so no extra git call is made here.
	 *
	 * @param _ActiveBranch the active local branch from the refresh snapshot
	 *                      (null when there is no active local branch)
	 * @return the counts text, or empty string when nothing is out of sync
	 */
	private static String FormatTrackCounts(GitBranch _ActiveBranch)
	{
		if (_ActiveBranch == null)
			return "";
		int __Unpushed = _ActiveBranch.UnpushedCommits();
		int __Unpulled = _ActiveBranch.UnpulledCommits();
		if (__Unpushed > 0 && __Unpulled > 0)
			return __Unpushed + " unpushed, " + __Unpulled + " unpulled";
		if (__Unpushed > 0)
			return __Unpushed + " unpushed";
		if (__Unpulled > 0)
			return __Unpulled + " unpulled";
		return "";
	}

	/**
	 * Retarget the quick action button from the active branch's upstream
	 * track state, GitHub-Desktop style: behind → {@code Pull}, else ahead →
	 * {@code Push}, else {@code Fetch} (behind wins over ahead when diverged;
	 * a null active branch — detached HEAD — yields {@code Fetch}).
	 *
	 * @param _ActiveBranch the active local branch from the refresh snapshot
	 *                      (null when there is no active local branch)
	 */
	private void UpdateQuickAction(GitBranch _ActiveBranch)
	{
		if (_ActiveBranch != null && _ActiveBranch.UnpulledCommits() > 0)
		{
			QuickActionButton.setText(QUICK_PULL_LABEL);
			QuickActionButton.setOnAction(__E -> RunQuickAction("Pull Failed", "Failed to pull from upstream",
					__Cb -> GetGitDirTarget().PullBranch(__Cb)));
		}
		else if (_ActiveBranch != null && _ActiveBranch.UnpushedCommits() > 0)
		{
			QuickActionButton.setText(QUICK_PUSH_LABEL);
			QuickActionButton.setOnAction(__E -> RunQuickAction("Push Failed", "Failed to push to upstream",
					__Cb -> GetGitDirTarget().PushBranch(__Cb)));
		}
		else
		{
			QuickActionButton.setText(QUICK_FETCH_LABEL);
			QuickActionButton.setOnAction(__E -> RunQuickAction("Fetch Failed", "Failed to fetch from remote(s)",
					__Cb -> GetGitDirTarget().FetchAll(__Cb)));
		}
	}

	/**
	 * Run a quick-action git operation through the operator queue and show an
	 * error dialog on failure. Both action buttons are disabled while the
	 * operation is in flight so repeated clicks cannot stack git operations,
	 * and are re-enabled on the JavaFX thread when it completes (success or
	 * failure). The callback runs on the operator runner thread, so the
	 * re-enable and alert are posted with {@link Platform#runLater} (same
	 * pattern as {@link #CheckoutBranchByName(String)}); the operator's
	 * refresh broadcast routes back through {@link GitDirWidget} to
	 * {@link #UpdateBranchList()}, which re-reads the track state.
	 *
	 * @param _Title  error dialog title
	 * @param _Header error dialog header line
	 * @param _Op     starts the operation (wires the provided callback)
	 */
	private void RunQuickAction(String _Title, String _Header, Consumer<IGitOperationCallback> _Op)
	{
		QuickActionButton.setDisable(true);
		OverflowButton.setDisable(true);
		_Op.accept((__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				QuickActionButton.setDisable(false);
				OverflowButton.setDisable(false);
				if (!__Ok)
				{
					Alert __Alert = new Alert(Alert.AlertType.ERROR);
					ThemeManager.Instance.ApplyThemeToDialog(__Alert);
					__Alert.setTitle(_Title);
					__Alert.setHeaderText(_Header);
					__Alert.setContentText(__Err);
					__Alert.showAndWait();
				}
			});
		});
	}

	// ------------------------------------------------------------------
	//  Checkout
	// ------------------------------------------------------------------

	/**
	 * Check out a branch by its full name through the GitOperator queue
	 * (REFRESH_AND_UPDATE_UI) and show an error dialog on failure. The
	 * operator broadcasts the refresh, which routes back through
	 * {@link GitDirWidget} to {@link #UpdateBranchList()} so the button and
	 * popover pick up the new active branch. The callback runs on the
	 * operator runner thread, so the alert is posted with
	 * {@link Platform#runLater}.
	 */
	private void CheckoutBranchByName(String _FullName)
	{
		GetGitDirTarget().ChangeBranch(_FullName, (__Ok, __Err, __Dir) ->
		{
			if (!__Ok)
			{
				Platform.runLater(() ->
				{
					Alert __Alert = new Alert(Alert.AlertType.ERROR);
					ThemeManager.Instance.ApplyThemeToDialog(__Alert);
					__Alert.setTitle("Checkout Failed");
					__Alert.setHeaderText("Failed to checkout branch");
					__Alert.setContentText(__Err);
					__Alert.showAndWait();
				});
			}
		});
	}
}
