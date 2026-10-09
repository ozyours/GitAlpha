package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.EButtonVariant;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.UI.Components.AButton;
import com.gitalpha.UI.Components.AText;
import com.gitalpha.Type.ETextVariant;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch selector widget: a compact {@link AButton} showing the active
 * branch (with a dot) that opens a {@link BranchPickerPopover} on click.
 * The popover lists all local + remote branches with a filter field —
 * designed for repos with many branches where a flat dropdown is painful.
 * Activating a branch in the popover (primary click or Enter) triggers
 * checkout through the operator queue; selection alone never does, so a
 * right-click can open the context menu without checking out first. The
 * menu is shared by the button and the popover: on the popover it acts on
 * the right-clicked row, on the button on the active branch. Checkout and
 * the Branch Manager window are implemented — create, delete, push and pull
 * are placeholders.
 */
class BranchWidget extends BaseWidget
{
	/** Unicode dot shown next to the active branch name */
	private static final String ACTIVE_BRANCH_DOT = "\u25CF"; // "●"
	/** Inline CSS for the active branch label; fixed green, not palette-derived (matches the popover cell's active marker) */
	private static final String ACTIVE_BRANCH_STYLE = "-fx-font-weight: bold; -fx-text-fill: #2e7d32;";
	/** Vertical gap between the label and the button */
	private static final double V_GAP = 4;

	/** Button that shows the active branch and opens the popover on click */
	private final AButton BranchButton;
	/** Label inside the button showing the active branch name */
	private final Label ActiveBranchLabel;
	/** Non-modal popover listing all branches with a filter field */
	private final BranchPickerPopover BranchPopover;

	/**
	 * Build the branch widget: wire the active-branch button, the picker
	 * popover (checkout on activation) and the context menu, then populate
	 * the branch list from the repository.
	 *
	 * @param _GitDirTarget       the repository this widget operates on
	 * @param _GitDirWidgetTarget the hosting project widget
	 */
	public BranchWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// ── Active-branch label (inside the button graphic) ──
		ActiveBranchLabel = new Label(ACTIVE_BRANCH_DOT + " ...");
		ActiveBranchLabel.setStyle(ACTIVE_BRANCH_STYLE);

		// ── Trigger button ──
		BranchButton = new AButton("", EButtonVariant.NORMAL);
		BranchButton.setGraphic(ActiveBranchLabel);
		BranchButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
		BranchButton.setOnAction(__E -> TogglePopover());
		// Right-click opens the context menu instead of the popover.
		BranchButton.setOnMouseClicked(__E ->
		{
			if (__E.getButton() == javafx.scene.input.MouseButton.SECONDARY)
				ShowContextMenu(__E.getScreenX(), __E.getScreenY());
		});

		// ── Popover ──
		BranchPopover = new BranchPickerPopover();
		BranchPopover.SetOnBranchSelected(__Path -> CheckoutBranchByName(__Path));

		// Populate and wire up
		UpdateBranchList();
		SetupContextMenu();

		// Layout: label + button stacked vertically
		AText __Label = new AText("Branch", ETextVariant.TITLE);
		VBox __VBox = new VBox(V_GAP, __Label, BranchButton);
		VBox.setVgrow(BranchButton, Priority.ALWAYS);
		getChildren().add(__VBox);
	}

	// ------------------------------------------------------------------
	//  Popover toggle
	// ------------------------------------------------------------------

	/** Show the branch-picker popover below the button, or hide it if already visible. */
	private void TogglePopover()
	{
		if (BranchPopover.IsShowing())
			BranchPopover.Hide();
		else
			BranchPopover.Show(BranchButton);
	}

	// ------------------------------------------------------------------
	//  Context menu
	// ------------------------------------------------------------------

	/** The shared context menu for branch operations (shown on right-click). */
	private ContextMenu ContextMenuInstance = null;

	/**
	 * Build the context menu with branch operations: Checkout, Create New
	 * Branch, Delete, Push, Pull, plus the Branch Manager window (the one
	 * place to link/unlink/fetch — the create/delete/push/pull entries listed
	 * above stay placeholders). Stored in a field so both the button and the
	 * popover can show it.
	 */
	private void SetupContextMenu()
	{
		ContextMenuInstance = new ContextMenu();

		MenuItem __CheckoutItem = new MenuItem("Checkout");
		__CheckoutItem.setOnAction(__E -> CheckoutSelectedBranch());

		MenuItem __CreateItem = new MenuItem("Create New Branch...");
		__CreateItem.setOnAction(__E -> CreateNewBranch());

		MenuItem __DeleteItem = new MenuItem("Delete Branch");
		__DeleteItem.setOnAction(__E -> DeleteBranch());

		MenuItem __PushItem = new MenuItem("Push Branch");
		__PushItem.setOnAction(__E -> PushBranch());

		MenuItem __PullItem = new MenuItem("Pull Branch");
		__PullItem.setOnAction(__E -> PullBranch());

		MenuItem __ManagerItem = new MenuItem("Branch Manager...");
		__ManagerItem.setOnAction(__E -> BranchManagerWidget.Open(GetGitDirTarget()));

		ContextMenuInstance.getItems().addAll(
				__CheckoutItem,
				new SeparatorMenuItem(),
				__CreateItem,
				__DeleteItem,
				new SeparatorMenuItem(),
				__PushItem,
				__PullItem,
				new SeparatorMenuItem(),
				__ManagerItem);

		// Also attach to the popover for right-click on individual branches.
		BranchPopover.SetContextMenu(ContextMenuInstance);
	}

	/**
	 * Show the context menu at the given screen coordinates. Clears the
	 * popover's recorded right-click target first — the button carries no
	 * row context — so menu actions fall back to the active branch instead
	 * of a stale row from an earlier popover right-click.
	 */
	private void ShowContextMenu(double _ScreenX, double _ScreenY)
	{
		BranchPopover.ClearContextMenuTarget();
		if (ContextMenuInstance != null)
			ContextMenuInstance.show(BranchButton, _ScreenX, _ScreenY);
	}

	// ------------------------------------------------------------------
	//  Context-menu actions
	// ------------------------------------------------------------------

	/**
	 * Check out the branch the menu was opened on: the popover's
	 * right-clicked row when present, otherwise the active branch shown on
	 * the button (the menu is shared by both). No-op when there is no
	 * target (detached HEAD / no active branch).
	 */
	private void CheckoutSelectedBranch()
	{
		String __Target = BranchPopover.GetContextMenuTargetPath();
		if (__Target == null || __Target.isBlank())
			__Target = GetGitDirTarget().GetActiveBranch();
		if (__Target != null && !__Target.isBlank())
			CheckoutBranchByName(__Target);
	}

	/** Prompt the user for a new branch name and create it. Not yet implemented. */
	private void CreateNewBranch()
	{
		// TODO: Show dialog to create new branch
	}

	/** Delete the selected branch. Not yet implemented. */
	private void DeleteBranch()
	{
		// TODO: Implement delete branch
	}

	/** Push the selected branch to its remote. Not yet implemented. */
	private void PushBranch()
	{
		// TODO: Implement push branch
	}

	/** Pull the latest changes for the selected branch from its remote. Not yet implemented. */
	private void PullBranch()
	{
		// TODO: Implement pull branch
	}

	// ------------------------------------------------------------------
	//  Branch list refresh
	// ------------------------------------------------------------------

	/**
	 * Rebuild the popover's branch list from the current GitDir state and
	 * update the button label to show the active branch. May be called from
	 * a refresh callback, so the actual UI update is deferred to the JavaFX
	 * thread.
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
			BranchPopover.SetBranches(__Branches, __Active);

			// Update the button label to show the active branch.
			if (__Active != null && !__Active.isBlank())
			{
				ActiveBranchLabel.setText(ACTIVE_BRANCH_DOT + " " + __Active);
				ActiveBranchLabel.setStyle(ACTIVE_BRANCH_STYLE);
			}
			else
			{
				ActiveBranchLabel.setText(ACTIVE_BRANCH_DOT + " (no branch)");
				ActiveBranchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #808080;");
			}
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
