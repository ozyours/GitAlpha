package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

import com.atlasui.type.EButtonVariant;
import com.gitalpha.Type.GitBranch;
import com.atlasui.ui.components.AButton;
import com.atlasui.ui.components.AListView;
import com.atlasui.ui.components.ATextField;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A filterable, selectable list of branch entries: section labels
 * (Local/Remote), hierarchical namespace headers with expand/collapse, and
 * branch rows. Pure view — never calls git; the caller feeds it branches via
 * {@link #SetBranches(List)} and the list rebuilds through {@link #Update()}.
 * <p>
 * Arguments are virtual behavior: the constructor takes
 * {@code ShowLocalBranches} / {@code ShowRemoteBranches} flags, and rows
 * gate on {@link BranchListEntry#IsSelectable()} (base default
 * {@code false}; branch entries override to {@code true}) so only
 * branches fire {@link #SetOnSelect(Consumer)} — on primary click or Enter.
 * <p>
 * Filter keystrokes are debounced (300 ms) before calling {@link #Update()};
 * the filter row also carries {@code +} / {@code −} glyph buttons that
 * expand or collapse every namespace in one click. Namespace headers toggle
 * their collapse state on primary click; the collapse set lives here,
 * survives filter changes, and is overridden (not cleared) while a filter
 * is active.
 * <p>
 * Row display: {@code <branch name> (<unpushed commits>) (<linked branch>)}
 * e.g. {@code master (2) (origin/master)} — the unpushed segment renders
 * only when the branch is ahead of its upstream (the count rides on
 * {@link GitBranch#UnpushedCommits()}, filled by the refresh's single
 * for-each-ref call). The active
 * branch renders with a dot + bold green, remotes muted grey (the same fixed
 * hex markers the old popover used — literals, not palette slots).
 */
public class BranchListWidget extends VBox
{
	/** Left indent (px) applied per tree depth level for namespace/branch rows */
	private static final double TREE_INDENT = 14;
	/** Debounce (ms) between a filter keystroke and the list rebuild */
	private static final long FILTER_DEBOUNCE_MS = 300;
	/** Width (px) of the expand/collapse-all buttons beside the filter field */
	private static final double TREE_BUTTON_WIDTH = 28;
	/** Horizontal gap (px) between the filter row's children: field ↔ expand ↔ collapse */
	private static final double FILTER_ROW_GAP = 4;
	/** Unicode dot shown next to the active branch name */
	private static final String ACTIVE_BRANCH_DOT = "\u25CF"; // "●"
	/** Inline CSS for the active branch row; fixed green, not palette-derived */
	private static final String ACTIVE_BRANCH_STYLE = "-fx-font-weight: bold; -fx-text-fill: #2e7d32;";
	/** Inline CSS for remote branch rows; fixed grey, not palette-derived */
	private static final String REMOTE_BRANCH_STYLE = "-fx-text-fill: #808080;";

	// ---------- Virtual-behavior arguments ----------
	/** Whether local branches are listed */
	private final boolean ShowLocalBranches;
	/** Whether remote branches are listed */
	private final boolean ShowRemoteBranches;

	// ---------- UI ----------
	private final ATextField FilterField;
	private final ListView<BranchListRow> ListViewWidget;
	private final ObservableList<BranchListRow> Rows;

	// ---------- Callbacks ----------
	private Consumer<BranchListEntry> OnSelect;
	private BiConsumer<BranchListEntry, MouseEvent> OnContextMenu;
	private Consumer<BranchListEntry> OnSelectionChanged;
	private BranchListEntry SelectedEntry;
	/** Full path of the remembered selection — re-resolved after every rebuild */
	private String SelectedBranchPath;
	/** Full path of the checked-out branch (active-row marker), set by the host */
	private String ActiveBranchPath = "";

	// ---------- Debounce state ----------
	private final PauseTransition DebounceTimer;
	private String PendingFilterText = "";

	// ---------- Data & tree state ----------
	private final List<GitBranch> AllBranches = new ArrayList<>();
	/** Collapse keys: full namespace paths the user folded away */
	private final Set<String> Collapsed = new HashSet<>();

	/**
	 * Build the widget.
	 *
	 * @param _ShowLocalBranches  list local branches (section labels appear
	 *                            only when both sides are shown and have
	 *                            visible rows)
	 * @param _ShowRemoteBranches list remote branches
	 * @param _OnSelect           called when a user clicks/Enters a selectable
	 *                            entry (branch rows); may be null
	 */
	public BranchListWidget(boolean _ShowLocalBranches, boolean _ShowRemoteBranches,
			Consumer<BranchListEntry> _OnSelect)
	{
		ShowLocalBranches = _ShowLocalBranches;
		ShowRemoteBranches = _ShowRemoteBranches;
		OnSelect = _OnSelect;

		// --- Layout ---
		setSpacing(4);
		setPadding(new javafx.geometry.Insets(6));

		FilterField = new ATextField();
		FilterField.setPromptText("Filter branches...");
		// Shrink below pref so the fixed-width glyph buttons keep their size
		// when the row narrows.
		FilterField.setMinWidth(0);
		FilterField.setMaxWidth(Double.MAX_VALUE);

		// --- Expand/collapse-all buttons beside the filter field ---
		AButton __BtnExpandAll = new AButton("+", EButtonVariant.NORMAL);
		__BtnExpandAll.setTooltip(new Tooltip("Expand all namespaces"));
		__BtnExpandAll.setOnAction(__E -> ExpandAllNamespaces());
		AButton __BtnCollapseAll = new AButton("\u2212", EButtonVariant.NORMAL); // "−"
		__BtnCollapseAll.setTooltip(new Tooltip("Collapse all namespaces"));
		__BtnCollapseAll.setOnAction(__E -> CollapseAllNamespaces());
		// Pin min/pref/max to one width so both glyph buttons render at an
		// identical size regardless of each glyph's advance width (they would
		// otherwise be content-sized by their "+"/"−" text). The skin's 4/12
		// horizontal padding would leave only 4px inside a 28px button and
		// overrun-clip the glyph to nothing, so override the padding inline
		// to 4/6 (inline styles beat the skin stylesheet and survive
		// ApplySkin's stylesheet re-bake) — 16px of content room, enough for
		// "+" and "−" at the skin's 12px font.
		for (AButton __TreeButton : new AButton[] { __BtnExpandAll, __BtnCollapseAll })
		{
			__TreeButton.setMinWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setPrefWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setMaxWidth(TREE_BUTTON_WIDTH);
			__TreeButton.setStyle("-fx-padding: 4 6 4 6;");
		}
		HBox __FilterRow = new HBox(FILTER_ROW_GAP, FilterField, __BtnExpandAll, __BtnCollapseAll);
		// Field absorbs the row's leftover width; the glyph buttons stay pinned.
		HBox.setHgrow(FilterField, Priority.ALWAYS);

		Rows = FXCollections.observableArrayList();
		ListViewWidget = new AListView<>(Rows);
		ListViewWidget.setCellFactory(__List -> CreateRowCell());
		// Selection is a view concern hosts care about (detail panes, action
		// gating): fire on every selection move — clicks, arrow keys, the
		// right-click select JavaFX does on secondary press — and on clears.
		// Activation (OnSelect) is a separate, explicit gesture.
		// The listener also keeps the rebuild restore key in step with the
		// highlight, not only with activations: a passive selection (arrow
		// keys / clicks with no activation handler) must survive Update() the
		// same way an activated one does. Update() snapshots the path before
		// setAll, so this listener's intermediate null-fire during the clear
		// cannot clobber the restore.
		ListViewWidget.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) -> {
			SelectedBranchPath = (__New != null && __New.Entry() instanceof BranchListEntry_Branch __Branch)
					? __Branch.Branch().FullPath() : null;
			if (OnSelectionChanged != null)
				OnSelectionChanged.accept(__New != null ? __New.Entry() : null);
		});

		// --- Debounce: keystrokes rebuild the list via Update() ---
		DebounceTimer = new PauseTransition(Duration.millis(FILTER_DEBOUNCE_MS));
		DebounceTimer.setOnFinished(__E -> Update());
		FilterField.textProperty().addListener((__Obs, __Old, __New) -> {
			PendingFilterText = __New == null ? "" : __New;
			DebounceTimer.playFromStart();
		});

		getChildren().addAll(__FilterRow, ListViewWidget);
		VBox.setVgrow(ListViewWidget, Priority.ALWAYS);

		SetupClickHandlers();
		SetupKeyHandlers();
	}

	// ------------------------------------------------------------------
	//  Click / key emission
	// ------------------------------------------------------------------

	/**
	 * Primary click: namespace rows toggle their collapse state (rebuilt
	 * immediately, single-click only so a double-click can't flicker two
	 * toggles), branch rows fire {@code OnSelect} only when the entry
	 * overrides {@code IsSelectable()} to true. Right-click: hit-tests the
	 * row under the cursor — JavaFX moves the selection on press for
	 * secondary buttons too ({@code CellBehaviorBase}), so the cursor
	 * position, not the selection, is what a context menu must act on.
	 */
	private void SetupClickHandlers()
	{
		ListViewWidget.setOnMouseClicked(__Event ->
		{
			if (__Event.getClickCount() != 1)
				return; // a double-click is two clicks — toggle only once
			BranchListRow __Row = RowAtEvent(__Event);
			if (__Event.getButton() == MouseButton.SECONDARY)
			{
				if (__Row != null && __Row.Entry() != null && OnContextMenu != null)
					OnContextMenu.accept(__Row.Entry(), __Event);
				return;
			}
			if (__Event.getButton() != MouseButton.PRIMARY || __Row == null || __Row.Entry() == null)
				return;

			if (__Row.Entry() instanceof BranchListEntry_Namespace __Namespace)
			{
				ToggleNamespace(__Namespace.GetFullPath());
				return;
			}
			if (!__Row.Entry().IsSelectable())
				return;
			ActivateEntry(__Row.Entry());
		});
	}

	/**
	 * Enter activates the keyboard-highlighted row: unlike a right-click
	 * (whose selection is ambiguous — {@code CellBehaviorBase} also selects
	 * on secondary press), an Enter implies the user pointed the list
	 * cursor at the row, so the ListView selection is the right target.
	 */
	private void SetupKeyHandlers()
	{
		ListViewWidget.setOnKeyPressed(__Event ->
		{
			if (__Event.getCode() != KeyCode.ENTER)
				return;
			BranchListRow __Row = ListViewWidget.getSelectionModel().getSelectedItem();
			if (__Row == null || __Row.Entry() == null || !__Row.Entry().IsSelectable())
				return;
			ActivateEntry(__Row.Entry());
			__Event.consume();
		});
	}

	/** Remember the entry as the selection and fire {@code OnSelect}. */
	private void ActivateEntry(BranchListEntry _Entry)
	{
		SelectedEntry = _Entry;
		SelectedBranchPath = (_Entry instanceof BranchListEntry_Branch __Branch)
				? __Branch.Branch().FullPath() : null;
		if (OnSelect != null)
			OnSelect.accept(_Entry);
	}

	/**
	 * Hit-test the row under a mouse event by walking up from the event
	 * target to the enclosing {@link ListCell}, then indexing {@link #Rows}.
	 *
	 * @param _Event the mouse event to resolve
	 * @return the row under the cursor, or null when not over a cell
	 */
	private BranchListRow RowAtEvent(MouseEvent _Event)
	{
		if (!(_Event.getTarget() instanceof Node __Node))
			return null;
		while (__Node != null && __Node != ListViewWidget && !(__Node instanceof ListCell))
			__Node = __Node.getParent();
		if (!(__Node instanceof ListCell<?> __Cell))
			return null;
		int __Index = __Cell.getIndex();
		if (__Index < 0 || __Index >= Rows.size())
			return null;
		return Rows.get(__Index);
	}

	// ------------------------------------------------------------------
	//  Public API
	// ------------------------------------------------------------------

	/**
	 * Feed the widget its branch data (a snapshot copy is kept — the caller
	 * may reuse its list). Rebuilds the rows immediately. This is the only
	 * data-in path: no git call happens anywhere inside this class.
	 *
	 * @param _Branches all branches (local + remote) to display
	 */
	public void SetBranches(List<GitBranch> _Branches)
	{
		AllBranches.clear();
		AllBranches.addAll(_Branches);
		Update();
	}

	/**
	 * Re-populate the list rows from the cached branches, the current filter
	 * text and the collapse set. Pure view rebuild — no git, no live-list
	 * read; safe on the FX thread. The filter field's debounce, namespace
	 * toggles and data refreshes all land here. The remembered selection is
	 * re-resolved by branch path against the fresh entries (rows are rebuilt
	 * as new objects each time), and cleared when the branch disappeared.
	 * The path is snapshotted before {@code setAll} so the selection
	 * listener's intermediate null-fire during the clear cannot clobber
	 * the restore.
	 */
	public void Update()
	{
		// Selection is path-based: rows are recreated on every rebuild, so
		// the old entry objects are gone — re-resolve after setAll.
		String __SelectedPath = SelectedBranchPath;

		String __Filter = PendingFilterText.trim().toLowerCase();
		List<BranchListRow> __LocalRows = new ArrayList<>();
		List<BranchListRow> __RemoteRows = new ArrayList<>();
		if (ShowLocalBranches)
			EmitSide(__LocalRows, false, __Filter);
		if (ShowRemoteBranches)
			EmitSide(__RemoteRows, true, __Filter);

		// Section labels only when both sides have visible content — a
		// single section needs no divider (matches the old popover).
		List<BranchListRow> __Rows = new ArrayList<>();
		boolean __BothVisible = !__LocalRows.isEmpty() && !__RemoteRows.isEmpty();
		if (__BothVisible)
			__Rows.add(SectionRow("Local"));
		__Rows.addAll(__LocalRows);
		if (__BothVisible)
		{
			__Rows.add(SectionRow("Remote"));
			__Rows.addAll(__RemoteRows);
		}
		else
		{
			__Rows.addAll(__RemoteRows);
		}
		Rows.setAll(__Rows);

		ResolveSelection(__SelectedPath);
	}

	/**
	 * Return the remembered selection: the entry last activated (primary
	 * click / Enter), re-resolved by branch path across rebuilds. Passive
	 * moves (arrow keys) never touch it directly — they only move the
	 * restore path the selection listener tracks, so the next
	 * {@link #Update()} re-resolves the highlighted branch into it (or
	 * drops it when that branch is gone).
	 *
	 * @return the activated or restored entry, or {@code null} when none
	 */
	public BranchListEntry GetSelectedEntry()
	{
		return SelectedEntry;
	}

	/**
	 * Set the on-select handler (overrides any previous).
	 *
	 * @param _Handler callback for selectable-entry clicks (nullable)
	 */
	public void SetOnSelect(Consumer<BranchListEntry> _Handler)
	{
		OnSelect = _Handler;
	}

	/**
	 * Set the right-click handler; receives the entry hit-tested under the
	 * cursor plus the mouse event (for screen coordinates). Never fires for
	 * empty space or non-cell rows.
	 *
	 * @param _Handler callback for secondary clicks (nullable to disable)
	 */
	public void SetOnContextMenu(BiConsumer<BranchListEntry, MouseEvent> _Handler)
	{
		OnContextMenu = _Handler;
	}

	/**
	 * Set the selection-change handler: fires on every ListView selection
	 * move (click, arrow keys, JavaFX's right-press select) and on clears,
	 * with the selected entry or null. A rebuild can re-fire this — an
	 * intermediate clear while rows are replaced, then the restored
	 * re-select — so handlers must tolerate null → entry churn. Distinct
	 * from {@link #SetOnSelect(Consumer)} — selection is passive (drives
	 * detail views / button gating), activation is the explicit click/Enter
	 * gesture.
	 *
	 * @param _Handler callback for selection changes (nullable)
	 */
	public void SetOnSelectionChanged(Consumer<BranchListEntry> _Handler)
	{
		OnSelectionChanged = _Handler;
	}

	/**
	 * Remember the checked-out branch path so its row renders with the
	 * active marker (dot + bold green). Store-only — the next
	 * {@link #Update()} repaints with it.
	 *
	 * @param _ActiveBranchPath full path of the active branch ("" = none)
	 */
	public void SetActiveBranch(String _ActiveBranchPath)
	{
		ActiveBranchPath = _ActiveBranchPath == null ? "" : _ActiveBranchPath;
	}

	/**
	 * Forget the remembered selection (and clear the ListView's own
	 * highlight); called when the hosting popup hides.
	 */
	public void ClearSelection()
	{
		SelectedEntry = null;
		SelectedBranchPath = null;
		ListViewWidget.getSelectionModel().clearSelection();
	}

	/** Move keyboard focus into the filter field (e.g. when the view opens). */
	public void RequestFocusFilter()
	{
		FilterField.requestFocus();
	}

	// ------------------------------------------------------------------
	//  Row building
	// ------------------------------------------------------------------

	/**
	 * Emit one side (local or remote) into {@code _Rows}: the root-level
	 * branches plus the namespace trie. Section labels are decided by the
	 * caller (both sides must be non-empty).
	 *
	 * @param _Rows   destination row list for this side
	 * @param _Remote true to emit the remote side
	 * @param _Filter lower-cased filter text ("" = no filter)
	 */
	private void EmitSide(List<BranchListRow> _Rows, boolean _Remote, String _Filter)
	{
		// Partition this side's branches into the trie: the root pseudo-node
		// collects namespace-less branches; every path segment a node.
		NamespaceNode __Root = new NamespaceNode("", "");
		for (GitBranch __Branch : AllBranches)
		{
			if (__Branch.Remote() != _Remote)
				continue;
			if (__Branch.Namespace().isEmpty())
			{
				__Root.Leaves.add(__Branch);
				continue;
			}
			NamespaceNode __Cursor = __Root;
			StringBuilder __Path = new StringBuilder();
			for (String __Segment : __Branch.Namespace())
			{
				if (__Path.length() > 0)
					__Path.append('/');
				__Path.append(__Segment);
				final String __Seg = __Segment;
				final String __FullPath = __Path.toString();
				__Cursor = __Cursor.Children.computeIfAbsent(__Seg,
						__K -> new NamespaceNode(__Seg, __FullPath));
			}
			__Cursor.Leaves.add(__Branch);
		}

		boolean __HasFilter = !_Filter.isEmpty();
		EmitNode(_Rows, __Root, 0, false, __HasFilter, _Filter);
	}

	/**
	 * Depth-first emission of one trie node's children: child namespaces
	 * first (alphabetical via {@link TreeMap} — namespaces stay pinned above
	 * the branches sharing their parent), then the node's own leaves. A
	 * header emits at this call's {@code _Depth} and its children recurse at
	 * {@code _Depth + 1}, so indentation grows one level per path segment.
	 * Filtering overrides collapse (matches are never hidden); a header's
	 * own path matching the filter force-shows its whole subtree.
	 *
	 * @param _Rows      destination row list
	 * @param _Node      trie node whose children are emitted
	 * @param _Depth     tree level of this call's rows (0 = side top)
	 * @param _ForceShow true when an ancestor matched the filter
	 * @param _HasFilter true when a non-empty filter is active
	 * @param _Filter    lower-cased filter text ("" when no filter)
	 */
	private void EmitNode(List<BranchListRow> _Rows, NamespaceNode _Node, int _Depth,
			boolean _ForceShow, boolean _HasFilter, String _Filter)
	{
		for (NamespaceNode __Child : _Node.Children.values())
		{
			boolean __ChildForce = _ForceShow
					|| (_HasFilter && __Child.FullPath.toLowerCase().contains(_Filter));
			if (_HasFilter && !__ChildForce && !SubtreeHasMatch(__Child, _Filter))
				continue;

			boolean __Collapsed = !_HasFilter && Collapsed.contains(__Child.FullPath);
			_Rows.add(NamespaceRow(__Child.Segment, __Child.FullPath, _Depth, !__Collapsed));
			if (__Collapsed)
				continue;
			EmitNode(_Rows, __Child, _Depth + 1, __ChildForce, _HasFilter, _Filter);
		}

		for (GitBranch __Leaf : _Node.Leaves)
		{
			if (!_HasFilter || _ForceShow || __Leaf.Matches(_Filter))
				_Rows.add(BranchRow(__Leaf, _Depth));
		}
	}

	/**
	 * @param _Node   the trie node to test
	 * @param _Filter lower-cased filter text
	 * @return true when any branch leaf anywhere in the node's subtree
	 *         matches the filter
	 */
	private static boolean SubtreeHasMatch(NamespaceNode _Node, String _Filter)
	{
		for (GitBranch __Leaf : _Node.Leaves)
		{
			if (__Leaf.Matches(_Filter))
				return true;
		}
		for (NamespaceNode __Child : _Node.Children.values())
		{
			if (SubtreeHasMatch(__Child, _Filter))
				return true;
		}
		return false;
	}

	/** Fold/unfold one namespace (keyed by full path) and rebuild. */
	private void ToggleNamespace(String _FullPath)
	{
		if (!Collapsed.remove(_FullPath))
			Collapsed.add(_FullPath);
		Update();
	}

	/**
	 * Expand every namespace recursively (the {@code +} button): clears the
	 * collapse set and rebuilds, so the whole tree shows at once with every
	 * header at its expanded arrow. A filter suppresses collapse, so the
	 * visible rows only change once the filter is cleared.
	 */
	private void ExpandAllNamespaces()
	{
		Collapsed.clear();
		Update();
	}

	/**
	 * Collapse every namespace recursively (the {@code −} button): marks
	 * every namespace prefix present in the cached branches as collapsed,
	 * then rebuilds. Only the headers remain visible — each with its
	 * collapsed arrow — and root-level branches (no namespace) stay listed,
	 * since they have no header to hide behind. Stale keys from earlier
	 * branch sets are kept; they are simply never looked up again by
	 * {@link #EmitNode}. A filter suppresses collapse, so the recorded
	 * keys only take visible effect once the filter is cleared.
	 */
	private void CollapseAllNamespaces()
	{
		for (GitBranch __Branch : AllBranches)
		{
			if (__Branch.Namespace().isEmpty())
				continue;
			// Mark every prefix: "a/b/c" collapses "a", "a/b" and "a/b/c".
			String __PathSoFar = "";
			for (String __Segment : __Branch.Namespace())
			{
				__PathSoFar = __PathSoFar.isEmpty() ? __Segment : __PathSoFar + "/" + __Segment;
				Collapsed.add(__PathSoFar);
			}
		}
		Update();
	}

	/**
	 * Re-resolve the remembered selection by branch path against the
	 * freshly rebuilt rows: re-select it in the ListView (setAll dropped
	 * the old highlight) or clear it when the branch is gone.
	 *
	 * @param _SelectedPath the previously selected branch full path (null = none)
	 */
	private void ResolveSelection(String _SelectedPath)
	{
		SelectedEntry = null;
		SelectedBranchPath = _SelectedPath;
		if (_SelectedPath == null)
		{
			ListViewWidget.getSelectionModel().clearSelection();
			return;
		}
		for (int __Idx = 0; __Idx < Rows.size(); ++__Idx)
		{
			BranchListRow __Row = Rows.get(__Idx);
			if (__Row.Entry() instanceof BranchListEntry_Branch __Branch
					&& __Branch.Branch().FullPath().equals(_SelectedPath))
			{
				SelectedEntry = __Row.Entry();
				ListViewWidget.getSelectionModel().select(__Idx);
				return;
			}
		}
		// The branch vanished (deleted / filtered away) — selection is gone.
		SelectedBranchPath = null;
		ListViewWidget.getSelectionModel().clearSelection();
	}

	// ------------------------------------------------------------------
	//  Row factories
	// ------------------------------------------------------------------

	private static BranchListRow SectionRow(String _Title)
	{
		BranchListEntry __Entry = new BranchListEntry_Section(_Title);
		return new BranchListRow(BranchListRow.RowKind.SECTION,
				__Entry.GetDisplayText(), __Entry, null, 0);
	}

	private static BranchListRow NamespaceRow(String _Segment, String _FullPath, int _Depth, boolean _Expanded)
	{
		BranchListEntry __Entry = new BranchListEntry_Namespace(_Segment, _FullPath, _Depth, _Expanded);
		return new BranchListRow(BranchListRow.RowKind.NAMESPACE,
				__Entry.GetDisplayText(), __Entry, null, _Depth);
	}

	private static BranchListRow BranchRow(GitBranch _Branch, int _Depth)
	{
		// The count and link both ride on the branch: the refresh's single
		// for-each-ref call captures git's %(upstream:track) ahead count
		// alongside the upstream link, so no per-branch query is ever needed.
		BranchListEntry __Entry = new BranchListEntry_Branch(_Branch, true, true);
		return new BranchListRow(BranchListRow.RowKind.BRANCH,
				__Entry.GetDisplayText(), __Entry, null, _Depth);
	}

	// ------------------------------------------------------------------
	//  Cell factory
	// ------------------------------------------------------------------

	/**
	 * Recycled list cell: section labels bold+muted, namespace headers bold
	 * with their toggle arrow (text built at emission time), branch rows
	 * indented by depth — active branch dot + bold green, remotes muted
	 * grey (fixed hex literals, per the old popover's convention). Cells are
	 * reused, so every row must reset text, graphic, tooltip, style and the
	 * selectable cue.
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
				getStyleClass().remove("selectable-item");

				if (_Empty || _Row == null)
					return;

				setText(_Row.Text());
				int __LeftPad = (int) (6 + _Row.Depth() * TREE_INDENT);
				if (_Row.Entry() instanceof BranchListEntry_Section)
				{
					setStyle("-fx-font-weight: bold; -fx-text-fill: #808080; -fx-padding: 10 10 4 6;");
				}
				else if (_Row.Entry() instanceof BranchListEntry_Namespace)
				{
					setStyle("-fx-font-weight: bold; -fx-padding: 6 10 6 " + __LeftPad + ";");
				}
				else if (_Row.Entry() instanceof BranchListEntry_Branch __Branch)
				{
					setStyle(BranchStyle(__Branch, __LeftPad));
					// Entry tooltip first (full path), then the active marker
					// overwrites it — "checked out" is the more useful hint.
					if (_Row.Entry().GetTooltip() != null)
						setTooltip(new Tooltip(_Row.Entry().GetTooltip()));
					if (__Branch.Branch().FullPath().equals(ActiveBranchPath))
					{
						setGraphic(new Label(ACTIVE_BRANCH_DOT));
						setTooltip(new Tooltip("Currently checked out"));
					}
					if (__Branch.IsSelectable())
						getStyleClass().add("selectable-item");
				}
			}
		};
	}

	/**
	 * Style one branch row: active (checked out) wins with bold green,
	 * remotes render muted grey, locals plain — all with the row's depth
	 * indent. Literals, not palette slots, so they never change themes.
	 *
	 * @param _Branch  the branch entry being rendered
	 * @param _LeftPad computed left padding for the depth indent
	 * @return the inline CSS string
	 */
	private String BranchStyle(BranchListEntry_Branch _Branch, int _LeftPad)
	{
		String __Padding = " -fx-padding: 4 10 4 " + _LeftPad + ";";
		if (_Branch.Branch().FullPath().equals(ActiveBranchPath))
			return ACTIVE_BRANCH_STYLE + __Padding;
		if (_Branch.Branch().Remote())
			return REMOTE_BRANCH_STYLE + __Padding;
		return __Padding;
	}

	// ------------------------------------------------------------------
	//  Trie node (local to row building)
	// ------------------------------------------------------------------

	/**
	 * One node of the namespace trie built per side: a path segment, its
	 * full path from the side root (the collapse key), child nodes sorted
	 * alphabetically via {@link TreeMap}, and the leaves terminating here.
	 */
	private static final class NamespaceNode
	{
		/** This level's path segment ("" for the root pseudo-node) */
		final String Segment;
		/** Full path from the root ("" for the root pseudo-node) — the collapse key */
		final String FullPath;
		/** Child nodes keyed by segment; TreeMap iteration = alphabetical */
		final Map<String, NamespaceNode> Children = new TreeMap<>();
		/** Branches whose namespace path ends exactly at this node */
		final List<GitBranch> Leaves = new ArrayList<>();

		NamespaceNode(String _Segment, String _FullPath)
		{
			Segment = _Segment;
			FullPath = _FullPath;
		}
	}
}
