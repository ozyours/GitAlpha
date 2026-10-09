package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ThemeColor;
import com.gitalpha.UI.Components.AListView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Branch-picker popover: a non-modal {@link Popup} anchored below a trigger
 * node, containing a {@link BranchFilterRow} at the top (filter field plus
 * expand-all / collapse-all buttons acting on the whole namespace tree) and
 * a virtualized {@link AListView} of {@link BranchListRow} below.
 * Branches are split into
 * two top-level sections — <b>Local</b> and <b>Remote</b> — each introduced
 * by a bold muted section label. Within each section, branches are grouped
 * into a <em>hierarchical</em> namespace tree: every path segment gets its
 * own collapsible header row (indented one level per segment), child
 * namespaces are pinned above the branches sharing their parent, and the
 * branches at that node are listed beneath them. {@code origin/feature/login}
 * therefore renders as an {@code origin} header, a nested {@code feature}
 * header, and the {@code login} leaf — not one flat {@code origin/feature}
 * header. Root-level branches (no namespace) appear after the namespace
 * groups of their section, without a header.
 * <p>
 * Section labels are shown only when both sections have visible content —
 * a single section needs no divider. The filter narrows branches
 * per-namespace: a header matches when its full path contains the filter
 * <em>or</em> any descendant branch matches. When a namespace itself
 * matches, its whole subtree is shown; otherwise only the matching
 * descendants appear. Namespaces with no matches are hidden entirely.
 * During filtering, collapse state is ignored so matches are always
 * visible.
 * <p>
 * The popup renders in its own scene, so the container VBox carries an inline
 * palette-derived background/border (same convention as {@code DialogSkin});
 * the {@link ATextField} and {@link AListView} inside carry their own
 * data-URI skins and re-bake on palette switches via
 * {@link IThemeChangeEvent}.
 * <p>
 * Activation fires {@link Consumer}{@code <String>} with the full branch
 * path — but only on an <em>explicit</em> gesture: a primary-button click
 * on a branch row, or Enter on the focused list. Selection alone is inert:
 * JavaFX updates the selection on {@code MOUSE_PRESSED} for right-clicks
 * too (see {@code CellBehaviorBase}: {@code SECONDARY && !selected} selects
 * the row), so wiring checkout to {@code selectedItemProperty} would check
 * out the branch <em>before</em> the context menu could open. The owner is
 * responsible for triggering the checkout. A right-click context menu can
 * be attached; its target row is hit-tested from the cursor position
 * rather than read from the selection.
 */
class BranchPickerPopover implements IThemeChangeEvent
{
	/** Popup width (px) — wide enough for namespaced branch paths */
	private static final double POPUP_WIDTH = 300;
	/** Maximum popup height (px); the list scrolls beyond this */
	private static final double POPUP_MAX_HEIGHT = 400;
	/** Gap (px) between popup rows */
	private static final double V_GAP = 4;
	/** Padding inside the popup container */
	private static final double CONTAINER_PADDING = 6;
	/** Section label text for local branches */
	private static final String SECTION_LOCAL = "Local";
	/** Section label text for remote branches */
	private static final String SECTION_REMOTE = "Remote";

	/** The popup window that hosts the picker content */
	private final Popup PopupInstance;
	/** Container VBox carrying the inline palette-derived skin */
	private final VBox Container;
	/** Shared filter row: field + expand-all / collapse-all buttons */
	private final BranchFilterRow FilterRow;
	/** Virtualized list of section labels + namespace headers + branch rows */
	private final AListView<BranchListRow> BranchList;
	/** The rebuilt flat row list (sections + headers + visible branches) */
	private final ObservableList<BranchListRow> VisibleRows;
	/** All branches (unfiltered backing store) */
	private final List<GitBranch> AllEntries;
	/** Full namespace paths (collapse keys) the user has manually collapsed */
	private final Set<String> CollapsedNamespaces;
	/** Context menu shown on right-click (may be null) */
	private ContextMenu ContextMenuInstance = null;
	/** Callback fired with the full branch path when the user activates a branch */
	private Consumer<String> BranchSelectedHandler = null;
	/** Full path of the currently checked-out branch (for active-branch styling) */
	private String ActiveBranchPath = "";
	/** Full path of the branch under the cursor when the context menu last opened (null = none) */
	private String ContextMenuTargetPath = null;

	/**
	 * Create the picker popover with its filter row — the filter field plus
	 * expand-all / collapse-all buttons — and the branch list. The popup is
	 * not shown until {@link #Show(Node)} is called. Registers this instance
	 * with {@link ThemeManager} (weak reference) so the container skin
	 * re-bakes when the palette changes.
	 */
	public BranchPickerPopover()
	{
		AllEntries = new ArrayList<>();
		VisibleRows = FXCollections.observableArrayList();
		CollapsedNamespaces = new HashSet<>();

		// ── Filter row (field + expand/collapse-all buttons) ──
		FilterRow = new BranchFilterRow(__Text -> RebuildRows(),
				this::ExpandAllNamespaces, this::CollapseAllNamespaces);

		// ── Branch list ──
		BranchList = new AListView<>(VisibleRows);
		BranchList.setCellFactory(__Ignored -> CreateRowCell());
		// Reserve ~40px inside POPUP_MAX_HEIGHT for the filter row (field +
		// expand/collapse buttons) + container padding.
		BranchList.setPrefHeight(POPUP_MAX_HEIGHT - 40);
		BranchList.setMaxHeight(POPUP_MAX_HEIGHT - 40);

		// ── Container ──
		Container = new VBox(V_GAP, FilterRow, BranchList);
		Container.setPadding(new Insets(CONTAINER_PADDING));
		Container.setPrefWidth(POPUP_WIDTH);
		Container.setMaxWidth(POPUP_WIDTH);
		Container.setMaxHeight(POPUP_MAX_HEIGHT);
		VBox.setVgrow(BranchList, Priority.ALWAYS);
		ApplyContainerSkin(ThemeManager.Instance.GetPalette());
		ThemeManager.Instance.AddIThemeChangeEvent(this);

		// ── Popup ──
		PopupInstance = new Popup();
		PopupInstance.getContent().add(Container);
		// Dismiss on outside click (autoHide) or Escape; autoFix keeps it on-screen.
		PopupInstance.setAutoHide(true);
		PopupInstance.setAutoFix(true);
		PopupInstance.setHideOnEscape(true);
		PopupInstance.setWidth(POPUP_WIDTH);

		SetupActivationHandlers();
	}

	// ------------------------------------------------------------------
	//  Public API
	// ------------------------------------------------------------------

	/**
	 * Replace the branch list shown in the popover. Takes its own copy of
	 * {@code _Branches}, so the caller may reuse its list afterwards;
	 * preserves the filter text and collapse state — collapse keys are
	 * namespace paths, so keys left over from namespaces that no longer
	 * exist are simply never looked up — and rebuilds the visible rows
	 * immediately.
	 *
	 * @param _Branches     all branches (local + remote) to display
	 * @param _ActiveBranch full path of the currently checked-out branch
	 *                      (may be null; no branch is then marked active)
	 */
	public void SetBranches(List<GitBranch> _Branches, String _ActiveBranch)
	{
		ActiveBranchPath = _ActiveBranch == null ? "" : _ActiveBranch;
		AllEntries.clear();
		AllEntries.addAll(_Branches);
		RebuildRows();
	}

	/**
	 * Show the popover anchored below the given trigger node.
	 *
	 * @param _Anchor the node to anchor below (typically the branch button)
	 */
	public void Show(Node _Anchor)
	{
		Bounds __Bounds = _Anchor.localToScreen(_Anchor.getBoundsInLocal());
		// Anchor not in a showing scene — no screen position to anchor to.
		if (__Bounds == null)
			return;

		// Position below the anchor, left-aligned with it.
		PopupInstance.show(_Anchor.getScene().getWindow(), __Bounds.getMinX(), __Bounds.getMaxY());

		// Focus the filter field so the user can start typing immediately.
		FilterRow.RequestFocus();
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
	 * Set the callback fired when the user activates a branch (primary
	 * click on a branch row, or Enter). The callback receives the full
	 * branch path and runs on the FX thread. Selection changes alone never
	 * fire it.
	 *
	 * @param _Handler the activation callback (may be null to clear)
	 */
	public void SetOnBranchSelected(Consumer<String> _Handler)
	{
		BranchSelectedHandler = _Handler;
	}

	/**
	 * Set the context menu shown when the user right-clicks a branch cell.
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
	 *         opened from elsewhere (e.g. the branch button) or the cursor
	 *         was not on a branch row
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
	 * palette's colors. The inner {@code ATextField} and {@code AListView}
	 * re-bake their own skins independently via their own listeners.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplyContainerSkin(_Palette);
	}

	/**
	 * Apply the palette-derived background and border to the container VBox.
	 * Inlined hex values because the popup runs in its own scene where the
	 * scene-level {@code -gitalpha-*} variables do not resolve.
	 *
	 * @param _Palette the palette to read colors from
	 */
	private void ApplyContainerSkin(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		String __Border = _Palette.GetBorderColor().GetHex(__Lookup);
		Container.setStyle(
				"-fx-background-color: " + __Bg + ";"
				+ "-fx-background-insets: 0;"
				+ "-fx-background-radius: 4;"
				+ "-fx-border-color: " + __Border + ";"
				+ "-fx-border-width: 1;"
				+ "-fx-border-radius: 4;");
	}

	// ------------------------------------------------------------------
	//  Row building
	// ------------------------------------------------------------------

	/**
	 * Rebuild {@link #VisibleRows} from {@link #AllEntries}, the current
	 * filter text and {@link #CollapsedNamespaces}. The list is split into
	 * a <b>Local</b> section and a <b>Remote</b> section, each introduced
	 * by a section label row when both sections have visible content. The
	 * grouping rules themselves — the hierarchical namespace trie, pinned
	 * namespaces, and the filter/collapse interaction — live in
	 * {@link BranchTree#BuildRows} and are shared with the branch manager.
	 */
	private void RebuildRows()
	{
		VisibleRows.clear();

		// Split into local and remote backing stores.
		List<GitBranch> __LocalEntries = new ArrayList<>();
		List<GitBranch> __RemoteEntries = new ArrayList<>();
		for (GitBranch __Entry : AllEntries)
		{
			if (__Entry.Remote())
				__RemoteEntries.add(__Entry);
			else
				__LocalEntries.add(__Entry);
		}

		// Build each section's rows via the shared trie (no section label yet).
		String __Filter = FilterRow.GetFilterText();
		List<BranchListRow> __LocalRows = BranchTree.BuildRows(__LocalEntries, __Filter, CollapsedNamespaces);
		List<BranchListRow> __RemoteRows = BranchTree.BuildRows(__RemoteEntries, __Filter, CollapsedNamespaces);

		// Show section labels only when both sections have content — a
		// single section needs no divider.
		boolean __BothVisible = !__LocalRows.isEmpty() && !__RemoteRows.isEmpty();
		if (__BothVisible)
			VisibleRows.add(BranchListRow.Section(SECTION_LOCAL));
		VisibleRows.addAll(__LocalRows);
		if (__BothVisible)
		{
			VisibleRows.add(BranchListRow.Section(SECTION_REMOTE));
			VisibleRows.addAll(__RemoteRows);
		}
		else if (!__RemoteRows.isEmpty())
		{
			VisibleRows.addAll(__RemoteRows);
		}
	}

	/**
	 * Toggle the collapse state of a namespace header and rebuild the
	 * visible rows. Key by the header's <em>full</em> path (the row's
	 * {@code Path}), not its displayed segment, so identical segment names
	 * under different parents keep separate collapse state — the mutation
	 * itself lives in {@link BranchTree#ToggleNamespace}.
	 *
	 * @param _Namespace the full namespace path acting as the collapse key
	 */
	private void ToggleNamespace(String _Namespace)
	{
		BranchTree.ToggleNamespace(CollapsedNamespaces, _Namespace);
		RebuildRows();
	}

	/**
	 * Expand every namespace recursively: clears the collapse set and
	 * rebuilds, so the whole tree shows at once (each header renders its
	 * expanded arrow).
	 */
	private void ExpandAllNamespaces()
	{
		CollapsedNamespaces.clear();
		RebuildRows();
	}

	/**
	 * Collapse every namespace recursively: marks every namespace path
	 * present in the current branches as collapsed (see
	 * {@link BranchTree#CollapseAll}), then rebuilds. Only the headers
	 * remain visible — each with its collapsed arrow — and root-level
	 * branches (no namespace) stay listed, since they have no header to
	 * hide behind. Stale keys from earlier branch sets are kept; they are
	 * simply never looked up again (see {@link #SetBranches}).
	 */
	private void CollapseAllNamespaces()
	{
		BranchTree.CollapseAll(AllEntries, CollapsedNamespaces);
		RebuildRows();
	}

	// ------------------------------------------------------------------
	//  Activation & context menu
	// ------------------------------------------------------------------

	/**
	 * Wire explicit activation and the context menu. Activation fires only
	 * on a primary-button single click ({@code clickCount == 1}) over a
	 * branch row, or Enter on the focused list — never on a bare selection
	 * change, because JavaFX moves the selection on {@code MOUSE_PRESSED}
	 * for right-clicks too ({@code CellBehaviorBase}: {@code SECONDARY &&
	 * !selected} selects the row), which would check out the branch before
	 * the context menu could open. A right-click records the hit-tested row
	 * as the menu target and opens the menu, but only when that row is a
	 * branch and a context menu is attached.
	 */
	private void SetupActivationHandlers()
	{
		BranchList.setOnMouseClicked(__Event ->
		{
			if (__Event.getButton() == MouseButton.SECONDARY)
			{
				// The menu acts on the row under the cursor — record it and
				// only open over a branch row (sections/namespaces/empty
				// space have nothing to act on).
				RecordContextMenuTarget(__Event);
				if (ContextMenuTargetPath != null && ContextMenuInstance != null)
					ContextMenuInstance.show(BranchList, __Event.getScreenX(), __Event.getScreenY());
				return;
			}
			if (__Event.getButton() != MouseButton.PRIMARY || __Event.getClickCount() != 1)
				return;

			// Primary click on a branch row activates it; clicks on
			// section/namespace rows are inert (their cells consume
			// primary clicks, so this handler never sees them — the
			// guard is defense in depth).
			BranchListRow __Row = RowAtEvent(__Event);
			if (__Row == null || !__Row.IsBranch())
				return;
			ActivateBranch(__Row.Branch().FullPath());
		});

		// Enter activates the focused/selected branch for keyboard users.
		BranchList.setOnKeyPressed(__Event ->
		{
			if (__Event.getCode() != KeyCode.ENTER)
				return;
			BranchListRow __Row = SelectedBranchRow();
			if (__Row == null)
				return;
			ActivateBranch(__Row.Branch().FullPath());
			__Event.consume();
		});
	}

	/**
	 * Fire the activation callback with the given branch path and hide the
	 * popover. A null callback (not yet wired) is ignored.
	 *
	 * @param _FullPath the full branch path to check out
	 */
	private void ActivateBranch(String _FullPath)
	{
		if (BranchSelectedHandler == null)
			return;
		BranchSelectedHandler.accept(_FullPath);
		Hide();
	}

	/**
	 * Hit-test the row under the cursor of a mouse event by walking up
	 * from the event target to the enclosing {@link ListCell} (stopping at
	 * the list itself, so clicks on the background resolve to null), then
	 * indexing {@link #VisibleRows}. Independent of the selection model —
	 * both primary-click activation and the context menu act on the cursor
	 * position, not the selection.
	 *
	 * @param _Event the mouse event to resolve
	 * @return the row under the cursor, or null when not over a cell
	 */
	private BranchListRow RowAtEvent(MouseEvent _Event)
	{
		if (!(_Event.getTarget() instanceof Node __Node))
			return null;
		while (__Node != null && __Node != BranchList && !(__Node instanceof ListCell))
			__Node = __Node.getParent();
		if (!(__Node instanceof ListCell<?> __Cell))
			return null;
		int __Index = __Cell.getIndex();
		if (__Index < 0 || __Index >= VisibleRows.size())
			return null;
		return VisibleRows.get(__Index);
	}

	/**
	 * Resolve the Enter-key activation target — mouse activation hit-tests
	 * the cursor with {@link #RowAtEvent} instead.
	 *
	 * @return the selected row when it is a branch, else null (section and
	 *         namespace rows are never activation targets)
	 */
	private BranchListRow SelectedBranchRow()
	{
		BranchListRow __Row = BranchList.getSelectionModel().getSelectedItem();
		return (__Row != null && __Row.IsBranch()) ? __Row : null;
	}

	/**
	 * Record the branch under the cursor as the context-menu target before
	 * the menu opens. Right-clicks not on a branch row (sections,
	 * namespaces, empty space) clear the target so stale paths are never
	 * reused.
	 *
	 * @param _Event the right-click that opened the menu
	 */
	private void RecordContextMenuTarget(MouseEvent _Event)
	{
		BranchListRow __Row = RowAtEvent(_Event);
		ContextMenuTargetPath = (__Row != null && __Row.IsBranch()) ? __Row.Branch().FullPath() : null;
	}

	// ------------------------------------------------------------------
	//  Cell factory
	// ------------------------------------------------------------------

	/**
	 * Recycled list cell rendering one of three row kinds. Section labels
	 * are bold muted text with no interaction. Namespace headers show a
	 * toggle arrow and expand/collapse their group on left-click (the event
	 * is consumed so the ListView never sees it and no selection fires).
	 * Headers and branch rows indent one {@code TREE_INDENT} step per tree
	 * depth; branch rows show just the leaf name, the active branch gets a
	 * dot graphic + bold green text, remote branches are muted. Cells are
	 * reused, so every branch must reset text, graphic, tooltip, style and
	 * mouse handler.
	 */
	private ListCell<BranchListRow> CreateRowCell()
	{
		return new ListCell<>()
		{
			@Override
			protected void updateItem(BranchListRow _Row, boolean _Empty)
			{
				super.updateItem(_Row, _Empty);

				// Reset every property on reuse (cells are recycled).
				setText(null);
				setGraphic(null);
				setTooltip(null);
				setStyle("");
				setOnMouseClicked(null);

				if (_Empty || _Row == null)
					return;

				switch (_Row.Kind())
				{
					case SECTION -> RenderSection(_Row.Text());
					case NAMESPACE -> RenderNamespace(_Row);
					case BRANCH -> RenderBranch(_Row.Branch(), _Row.Depth());
				}
			}

			/**
			 * Paint a section label ("Local" / "Remote"): bold muted text
			 * with extra top padding for visual separation. Clicks are
			 * consumed so the row is never selected.
			 */
			private void RenderSection(String _Label)
			{
				setText(_Label);
				setStyle("-fx-font-weight: bold; -fx-text-fill: #808080;"
						+ " -fx-padding: 10 10 4 6;");
				setOnMouseClicked(__Event -> __Event.consume());
			}

			/**
			 * Paint a namespace-tree header: toggle arrow + this level's
			 * single path segment, indented by tree depth. The full path
			 * (row's {@code Path}) is the collapse key, not the display
			 * text — nested segments stay readable instead of repeating
			 * their parents.
			 */
			private void RenderNamespace(BranchListRow _Row)
			{
				String __FullPath = _Row.Path();
				boolean __Collapsed = CollapsedNamespaces.contains(__FullPath);
				String __Arrow = __Collapsed ? BranchTree.ARROW_COLLAPSED : BranchTree.ARROW_EXPANDED;
				setText(__Arrow + " " + _Row.Text());
				setStyle("-fx-font-weight: bold; -fx-padding: 6 10 6 "
						+ (int) (6 + _Row.Depth() * BranchTree.TREE_INDENT) + ";");

				// Consume the click so the ListView never selects header rows.
				setOnMouseClicked(__Event ->
				{
					if (__Event.getButton() == MouseButton.PRIMARY)
					{
						ToggleNamespace(__FullPath);
						__Event.consume();
					}
				});
			}

			/**
			 * Paint a branch row: leaf name, depth indent, active/remote
			 * styling. Active green and remote grey are fixed hex values,
			 * not palette slots, so they never change with the theme.
			 *
			 * @param _Entry the branch to render
			 * @param _Depth tree depth for the left indent (0 = root-level)
			 */
			private void RenderBranch(GitBranch _Entry, int _Depth)
			{
				String __Leaf = _Entry.Name();
				setText(__Leaf);

				// Indent one level per namespace segment above the leaf.
				String __Padding = " -fx-padding: 4 10 4 "
						+ (int) (6 + _Depth * BranchTree.TREE_INDENT) + ";";

				boolean __IsActive = _Entry.FullPath().equals(ActiveBranchPath);
				if (__IsActive)
				{
					setGraphic(new Label("\u25CF"));
					setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;" + __Padding);
					setTooltip(new Tooltip("Currently checked out"));
				}
				else if (_Entry.Remote())
				{
					setGraphic(null);
					setStyle("-fx-text-fill: #808080;" + __Padding);
					setTooltip(null);
				}
				else
				{
					setGraphic(null);
					setStyle(__Padding);
					setTooltip(null);
				}
			}
		};
	}
}
