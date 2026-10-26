package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Type.GitBranch;
import com.atlasui.UI.Components.APopup;
import com.gitalpha.UI.GitDirProjectManager.BranchListWidget.BranchListEntry_Branch;
import com.gitalpha.UI.GitDirProjectManager.BranchListWidget.BranchListWidget;
import javafx.scene.control.ContextMenu;

import java.util.List;
import java.util.function.Consumer;

/**
 * Branch picker popover: an {@link APopup} hosting a {@link BranchListWidget}
 * (filter field + list of both sides, section labels when both have content).
 * Show it below a trigger node with the inherited
 * {@link APopup#ShowAt(javafx.scene.Node)}; only selectable entries fire
 * {@code _OnSelect} — branch rows on primary click or Enter — after which the
 * popover hides itself.
 * <p>
 * Right-clicking a branch row records a context-menu target (read back via
 * {@link #GetContextMenuTargetPath()}) before the shared menu shows, and every
 * hide path clears the list selection so a reopen starts a clean list. Showing
 * moves focus into the filter field so the user can type immediately.
 * <p>
 * The popup chrome (palette background/border) is owned by {@link APopup} and
 * re-bakes on palette switches; the hosted widget's controls re-bake their own
 * skins independently.
 */
public class BranchPickerPopover extends APopup
{
	/** Popup width (px) — wide enough for branch paths */
	public static final double WIDTH = 360;
	/** Maximum popup height (px); the list scrolls beyond this */
	public static final double HEIGHT = 340;

	/** The list widget hosted inside the popup */
	private final BranchListWidget ListWidget;
	/** Context menu shown on right-click (may be null) */
	private ContextMenu ContextMenuInstance;
	/** Branch path the context menu was invoked on from the list (null = none) */
	private String ContextMenuTargetPath;

	/**
	 * Create the picker; call {@link APopup#ShowAt(javafx.scene.Node)} to
	 * display it under an anchor node.
	 *
	 * @param _OnSelect fired with the clicked branch (the widget only emits
	 *                  selectable entries; the popover hides itself after one)
	 */
	public BranchPickerPopover(Consumer<GitBranch> _OnSelect)
	{
		// Dismissable on an outside click / Escape and kept on screen — APopup's
		// default behaviour.
		super(true, true);

		// The picker shows both sides; section labels appear when both have content.
		ListWidget = new BranchListWidget(true, true, __Entry -> {
			if (_OnSelect != null && __Entry instanceof BranchListEntry_Branch __BranchRow) {
				_OnSelect.accept(__BranchRow.Branch());
				Hide();
			}
		});
		ListWidget.setPrefWidth(WIDTH);
		ListWidget.setPrefHeight(HEIGHT);
		SetContent(ListWidget);

		// Right-click on a branch row: record the target (the menu acts on
		// the cursor's row, never the selection) and show the shared menu.
		ListWidget.SetOnContextMenu((__Entry, __Event) -> {
			ContextMenuTargetPath = null;
			if (!(__Entry instanceof BranchListEntry_Branch __BranchRow))
				return; // namespace/section rows carry no branch to act on
			ContextMenuTargetPath = __BranchRow.Branch().FullPath();
			if (ContextMenuInstance != null)
				ContextMenuInstance.show(ListWidget, __Event.getScreenX(), __Event.getScreenY());
		});

		// Showing focuses the filter field so the user can type immediately.
		SetOnShown(ListWidget::RequestFocusFilter);
		// Any hide path (toggle, branch picked, outside click, Escape) must
		// drop the selection so a reopen starts clean.
		SetOnHidden(__Reason -> ListWidget.ClearSelection());
	}

	// ------------------------------------------------------------------
	//  Public API
	// ------------------------------------------------------------------

	/**
	 * Replace the branch list shown in the popover. The widget caches the
	 * list itself; its filter operates on the cache.
	 *
	 * @param _Branches     all branches (local + remote) to display
	 * @param _ActiveBranch full path of the currently checked-out branch
	 *                      (renders with the active marker; may be null)
	 */
	public void SetBranches(List<GitBranch> _Branches, String _ActiveBranch)
	{
		ListWidget.SetActiveBranch(_ActiveBranch);
		ListWidget.SetBranches(_Branches);
	}

	/**
	 * Set the context menu shown when the user right-clicks a branch row.
	 *
	 * @param _Menu the context menu (may be null to disable)
	 */
	public void SetContextMenu(ContextMenu _Menu)
	{
		ContextMenuInstance = _Menu;
	}

	/**
	 * @return the full branch path under the cursor when the context menu
	 *         last opened from this popover, or null when the menu was
	 *         opened from elsewhere (e.g. the branch button)
	 */
	public String GetContextMenuTargetPath()
	{
		return ContextMenuTargetPath;
	}

	/** Forget the recorded context-menu target (e.g. before showing the menu from the branch button). */
	public void ClearContextMenuTarget()
	{
		ContextMenuTargetPath = null;
	}
}
