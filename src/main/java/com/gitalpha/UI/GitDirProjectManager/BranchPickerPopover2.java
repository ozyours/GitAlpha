package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ThemeColor;
import com.gitalpha.UI.GitDirProjectManager.BranchListWidget.BranchListEntry_Branch;
import com.gitalpha.UI.GitDirProjectManager.BranchListWidget.BranchListWidget;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.stage.Popup;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Branch picker popover built on {@link BranchListWidget}: a non-modal
 * {@link Popup} anchored below a trigger node, containing the widget's
 * filter field + list (both sides, section labels when both have content).
 * Only selectable entries fire {@code _OnSelect} — branch rows on primary
 * click or Enter — after which the popover hides itself.
 * <p>
 * Right-clicking a branch row records a context-menu target (read back via
 * {@link #GetContextMenuTargetPath()}) before the shared menu shows, and
 * every hide path clears the list selection so a reopen starts clean.
 * <p>
 * The popup renders in its own scene, so this class owns the container skin
 * (palette-derived background/border inlined on the widget root — node-level
 * styles can't resolve the scene's {@code -gitalpha-*} variables, the same
 * convention as the old popover) and re-bakes it on palette switches via
 * {@link IThemeChangeEvent}.
 */
public class BranchPickerPopover2 implements IThemeChangeEvent
{
	/** Popup width (px) — wide enough for branch paths */
	public static final double WIDTH = 360;
	/** Maximum popup height (px); the list scrolls beyond this */
	public static final double HEIGHT = 340;

	/** The popup window that hosts the picker content */
	private final Popup PopupInstance;
	/** The list widget hosted inside the popup (also the skinned container) */
	private final BranchListWidget ListWidget;
	/** Context menu shown on right-click (may be null) */
	private ContextMenu ContextMenuInstance;
	/** Branch path the context menu was invoked on from the list (null = none) */
	private String ContextMenuTargetPath;

	/**
	 * Create the picker; call {@link #ShowAt(Node)} to display it under an
	 * anchor node. Registers this instance with {@link ThemeManager} (weak
	 * reference) so the container skin re-bakes when the palette changes.
	 *
	 * @param _OnSelect fired with the clicked branch (the widget only emits
	 *                  selectable entries; the popover hides itself after one)
	 */
	public BranchPickerPopover2(Consumer<GitBranch> _OnSelect)
	{
		// The picker shows both sides; section labels appear when both have content.
		ListWidget = new BranchListWidget(true, true, __Entry -> {
			if (_OnSelect != null && __Entry instanceof BranchListEntry_Branch __BranchRow) {
				_OnSelect.accept(__BranchRow.Branch());
				Hide();
			}
		});
		ListWidget.setPrefWidth(WIDTH);
		ListWidget.setPrefHeight(HEIGHT);
		ApplyContainerSkin(ThemeManager.Instance.GetPalette());
		ThemeManager.Instance.AddIThemeChangeEvent(this);

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

		PopupInstance = new Popup();
		// Dismiss on outside click (autoHide) or Escape; autoFix keeps it on-screen.
		PopupInstance.setAutoHide(true);
		PopupInstance.setAutoFix(true);
		PopupInstance.setHideOnEscape(true);
		PopupInstance.getContent().add(ListWidget);
		// Any hide path (toggle, branch picked, outside click, Escape) must
		// drop the selection — hide() alone misses the auto-hide routes.
		PopupInstance.setOnHidden(__E -> ListWidget.ClearSelection());
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
	 * Show the popover anchored below the given trigger node and focus its
	 * filter field so the user can type immediately. No-op when the anchor
	 * is not in a showing window yet ({@code localToScreen} returns null).
	 *
	 * @param _Anchor the node to anchor below (typically the branch button)
	 */
	public void ShowAt(Node _Anchor)
	{
		Point2D __TopLeft = _Anchor.localToScreen(0, 0);
		if (__TopLeft == null)
			return;
		// Position below the anchor, left-aligned with it. localToScreen
		// yields a Point2D (no public RectBounds in JavaFX geometry).
		PopupInstance.show(_Anchor.getScene().getWindow(),
				__TopLeft.getX(),
				__TopLeft.getY() + _Anchor.getBoundsInLocal().getHeight() + 2);
		ListWidget.RequestFocusFilter();
	}

	/** Hide the popover (no-op when already hidden). */
	public void Hide()
	{
		PopupInstance.hide();
	}

	/** @return true when the popover is currently visible */
	public boolean IsShowing()
	{
		return PopupInstance.isShowing();
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

	// ------------------------------------------------------------------
	//  Theme
	// ------------------------------------------------------------------

	/**
	 * Theme-change push: re-bake the container's inline skin with the new
	 * palette's colors. The widget's own controls re-bake their skins
	 * independently.
	 *
	 * @param _Palette the palette to read colors from
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplyContainerSkin(_Palette);
	}

	/**
	 * Apply the palette-derived background and border to the popup's
	 * container (the widget root). Inlined hex values because the popup
	 * runs in its own scene where the scene-level {@code -gitalpha-*}
	 * variables do not resolve.
	 *
	 * @param _Palette the palette to read colors from
	 */
	private void ApplyContainerSkin(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		String __Border = _Palette.GetBorderColor().GetHex(__Lookup);
		ListWidget.setStyle(
				"-fx-background-color: " + __Bg + ";"
				+ "-fx-background-insets: 0;"
				+ "-fx-background-radius: 4;"
				+ "-fx-border-color: " + __Border + ";"
				+ "-fx-border-width: 1;"
				+ "-fx-border-radius: 4;");
	}
}
