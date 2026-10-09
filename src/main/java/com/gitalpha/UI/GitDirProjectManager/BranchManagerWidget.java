package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IRefreshGitDirEvent;
import com.gitalpha.Engine.IGitOperationCallback;
import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Type.ETextVariant;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.UI.Components.AButton;
import com.gitalpha.UI.Components.AListView;
import com.gitalpha.UI.Components.AText;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Branch management window: two columns of namespace trees — locals on the
 * left, remotes on the right — fed by one shared {@link BranchFilterRow}
 * (a single filter and a single collapse set govern both columns), with the
 * action buttons at the bottom.
 * <p>
 * The column split is <b>fixed at 50/50 and not adjustable</b>: an
 * equal-split {@link HBox} (both panes grow equally) replaces the draggable
 * SplitPane, with one static 1px {@link Region} separator drawn between the
 * panes — nothing to grab. Under each list a <b>fixed-height detail pane</b>
 * shows the selected branch's name and its linked branch(es); it is pinned
 * (min == pref == max) with no grow priority, so <b>window resizes only
 * stretch the lists</b>.
 * <p>
 * Branch cells show their upstream links inline: a local renders
 * {@code main (origin/main)} when linked (bare when not), a remote renders
 * {@code main (main, experiment)} listing every local branch linked to it —
 * git allows many locals to track one remote, never the reverse.
 * <p>
 * Actions all run through {@link GitDir}'s operator queue with
 * {@code REFRESH_AND_UPDATE_UI}, so a completed fetch/link/unlink re-parses
 * upstreams and the refresh broadcast (heard via {@link IRefreshGitDirEvent})
 * rebuilds both columns; failures surface as error dialogs. While an
 * operation is in flight all three buttons are disabled.
 * <p>
 * Implements {@link IThemeChangeEvent}: a palette switch re-applies the
 * separator background and footer borders, then re-derives the detail values.
 * <p>
 * Opened via {@link #Open(GitDir)} (one window per repository, brought to
 * front if already open) from the Git menu and the branch picker's context
 * menu. Window bounds are not persisted yet.
 */
public class BranchManagerWidget extends Stage implements IObject, IThemeChangeEvent
{
	/** Minimum pane width (px) so a narrow window can't crush a column */
	private static final double COLUMN_MIN_WIDTH = 200;
	/** Vertical gap (px) between the stacked sections: filter row / columns / buttons */
	private static final double SECTION_GAP = 6;
	/** Default window size (px) */
	private static final double DEFAULT_WIDTH = 760;
	/** Default window height (px) */
	private static final double DEFAULT_HEIGHT = 500;

	/** Open windows per repository — dedups {@link #Open(GitDir)} */
	private static final Map<GitDir, BranchManagerWidget> OpenWindows = new HashMap<>();

	/**
	 * Open the branch manager for a repository: focus the existing window if
	 * one is already showing, otherwise build and show a new one. The window
	 * removes itself from the dedup map when it closes (its own
	 * {@code onHidden} handler — never replaced externally, which would
	 * clobber the internal dispose logic).
	 *
	 * @param _GitDir the repository to manage (no-op when null)
	 */
	public static void Open(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;
		BranchManagerWidget __Existing = OpenWindows.get(_GitDir);
		if (__Existing != null && __Existing.isShowing())
		{
			__Existing.toFront();
			return;
		}
		BranchManagerWidget __Widget = new BranchManagerWidget(_GitDir);
		OpenWindows.put(_GitDir, __Widget);
	}

	/** The repository this window operates on */
	private final GitDir GitDirTarget;
	/** Local branches cached from the last refresh (read only after {@link #CacheBranches}) */
	private final List<GitBranch> LocalBranches = new ArrayList<>();
	/** Remote branches cached from the last refresh */
	private final List<GitBranch> RemoteBranches = new ArrayList<>();
	/** Reverse link map: remote full path → every local full path tracking it (many-to-one allowed) */
	private final Map<String, List<String>> LocalsByUpstream = new HashMap<>();
	/** Collapse keys shared by both columns (one tree state per window) */
	private final Set<String> CollapsedNamespaces = new HashSet<>();
	/** Full path of the currently checked-out branch (active styling, local column only) */
	private String ActiveBranchPath = "";

	/** Shared filter row: one field + expand/collapse-all buttons for both columns */
	private final BranchFilterRow FilterRow;
	/** Rebuilt row lists backing the two columns */
	private final ObservableList<BranchListRow> LocalRows = FXCollections.observableArrayList();
	private final ObservableList<BranchListRow> RemoteRows = FXCollections.observableArrayList();
	/** The two tree columns */
	private final AListView<BranchListRow> LocalList;
	private final AListView<BranchListRow> RemoteList;
	/** Static 1px column separator (palette border color) — replaces the draggable SplitPane divider */
	private final Region reg_ColumnSeparator;
	/** Fixed-height detail footers under the two lists (pinned min == pref == max) */
	private final GridPane grid_LocalDetail;
	private final GridPane grid_RemoteDetail;
	/** Detail-footer values: branch name + linked branch(es) per column, re-styled on palette change */
	private final Label lbl_LocalName;
	private final Label lbl_LocalLinked;
	private final Label lbl_RemoteName;
	private final Label lbl_RemoteLinked;
	/** Bottom action buttons — Fetch always available, Link/Unlink selection-gated */
	private final AButton btn_Fetch;
	private final AButton btn_Link;
	private final AButton btn_Unlink;
	/** Refresh listener that reloads this window when its repo refreshes (null target = any repo) */
	private final IRefreshGitDirEvent RefreshListener;
	/** True while a git operation is in flight — gates all three buttons */
	private boolean Busy = false;
	/** True once the window closed; guards async callbacks and the rebuild itself */
	private boolean IsDisposed = false;

	/**
	 * Build and show the branch manager window for the given repository.
	 * Prefer {@link #Open(GitDir)} — it dedups per repository.
	 *
	 * @param _GitDirTarget the repository to manage
	 */
	public BranchManagerWidget(GitDir _GitDirTarget)
	{
		GitDirTarget = _GitDirTarget;

		// ── Filter row: one field + expand/collapse governing both columns ──
		// Filter keystrokes only rebuild rows from the cached branch lists
		// (no git call, no live-list read), so typing stays on the FX thread.
		FilterRow = new BranchFilterRow(__Text -> RebuildRows(),
				this::ExpandAllNamespaces, this::CollapseAllNamespaces);

		// ── Columns ──
		LocalList = new AListView<>(LocalRows);
		LocalList.setCellFactory(__Ignored -> CreateRowCell(true));
		LocalList.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) -> OnSelectionChanged());
		RemoteList = new AListView<>(RemoteRows);
		RemoteList.setCellFactory(__Ignored -> CreateRowCell(false));
		RemoteList.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) -> OnSelectionChanged());

		// ── Detail footers ──
		// Built before the skins pass and pinned after it, so the measured
		// height already includes the footer's top border (Region min/max
		// heights are outer sizes, and the border width never changes with
		// the palette).
		lbl_LocalName = new Label();
		lbl_LocalLinked = new Label();
		grid_LocalDetail = CreateDetailPane(lbl_LocalName, lbl_LocalLinked);
		lbl_RemoteName = new Label();
		lbl_RemoteLinked = new Label();
		grid_RemoteDetail = CreateDetailPane(lbl_RemoteName, lbl_RemoteLinked);
		reg_ColumnSeparator = new Region();
		// An empty Region's pref width is 0 — pin it to the 1px line it draws,
		// otherwise the HBox would collapse it to nothing.
		reg_ColumnSeparator.setMinWidth(1);
		reg_ColumnSeparator.setPrefWidth(1);
		reg_ColumnSeparator.setMaxWidth(1);
		ApplyPaneSkins(ThemeManager.Instance.GetPalette());
		ThemeManager.Instance.AddIThemeChangeEvent(this);
		// Pin the footers (min == pref == max): no grow priority + fixed
		// height means window resizes only stretch the lists above them.
		double __LocalDetailHeight = grid_LocalDetail.prefHeight(-1);
		grid_LocalDetail.setMinHeight(__LocalDetailHeight);
		grid_LocalDetail.setMaxHeight(__LocalDetailHeight);
		double __RemoteDetailHeight = grid_RemoteDetail.prefHeight(-1);
		grid_RemoteDetail.setMinHeight(__RemoteDetailHeight);
		grid_RemoteDetail.setMaxHeight(__RemoteDetailHeight);

		VBox __LocalPane = new VBox(4, ColumnHeader("Local"), LocalList, grid_LocalDetail);
		VBox.setVgrow(LocalList, Priority.ALWAYS);
		__LocalPane.setMinWidth(COLUMN_MIN_WIDTH);
		VBox __RemotePane = new VBox(4, ColumnHeader("Remote"), RemoteList, grid_RemoteDetail);
		VBox.setVgrow(RemoteList, Priority.ALWAYS);
		__RemotePane.setMinWidth(COLUMN_MIN_WIDTH);

		// Fixed 50/50 split, not adjustable: prefWidth(0) makes HBox allocate
		// each pane only its minimum first and then hand the remaining width
		// out equally to the ALWAYS-grow children — exact halves at any window
		// width, with no divider node to drag. The 1px Region between them
		// draws the static separator line (palette border color).
		__LocalPane.setPrefWidth(0);
		__LocalPane.setMaxWidth(Double.MAX_VALUE);
		__RemotePane.setPrefWidth(0);
		__RemotePane.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(__LocalPane, Priority.ALWAYS);
		HBox.setHgrow(__RemotePane, Priority.ALWAYS);
		HBox __Split = new HBox(6, __LocalPane, reg_ColumnSeparator, __RemotePane);

		// ── Bottom buttons ──
		btn_Fetch = new AButton("Fetch");
		btn_Fetch.setTooltip(new Tooltip("Fetch all remotes (updates remote-tracking branches only)"));
		btn_Fetch.setOnAction(__E -> RunOperation(
				(__Cb) -> GitDirTarget.FetchAll(__Cb), "Fetch Failed", "Failed to fetch from remote(s)"));
		btn_Link = new AButton("Link");
		btn_Link.setTooltip(new Tooltip("Link the selected local branch to the selected remote branch (set upstream)"));
		btn_Link.setOnAction(__E -> RunOperation(
				(__Cb) -> GitDirTarget.SetUpstream(SelectedPath(LocalList), SelectedPath(RemoteList), __Cb),
				"Link Failed", "Failed to set the upstream"));
		btn_Unlink = new AButton("Unlink");
		btn_Unlink.setTooltip(new Tooltip("Remove the upstream link of the selected local branch"));
		btn_Unlink.setOnAction(__E -> RunOperation(
				(__Cb) -> GitDirTarget.UnsetUpstream(SelectedPath(LocalList), __Cb),
				"Unlink Failed", "Failed to unset the upstream"));
		HBox __BottomBar = new HBox(10, btn_Fetch, btn_Link, btn_Unlink);
		__BottomBar.setAlignment(Pos.CENTER_LEFT);

		// ── Root layout ──
		BorderPane __Root = new BorderPane();
		__Root.setPadding(new Insets(8));
		__Root.setTop(FilterRow);
		__Root.setCenter(__Split);
		__Root.setBottom(__BottomBar);
		// BorderPane stacks its regions flush against each other — carve the
		// section gaps out as child margins (below the filter row, above the
		// button bar) so the three bands read as separate strips.
		BorderPane.setMargin(FilterRow, new Insets(0, 0, SECTION_GAP, 0));
		BorderPane.setMargin(__BottomBar, new Insets(SECTION_GAP, 0, 0, 0));

		// ── Window setup (mirrors StashWidget) ──
		setTitle("Branches — " + GitDirTarget.GetGitDirPath().getParent().getFileName());
		Scene __Scene = new Scene(__Root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		setScene(__Scene);
		// Register so the scene-level base theme (the .root focus-ring kill +
		// palette CSS variables) applies to this window too.
		ThemeManager.Instance.RegisterScene(__Scene);
		initModality(Modality.NONE);

		// Broadcasts arrive for this repo (or globally with a null target);
		// each triggers a data refresh whose callback rebuilds on the FX
		// thread — the same ordered-after-writes pattern as GitDirWidget.
		RefreshListener = (_GitDir, _Reason) ->
		{
			if (_GitDir == null || Objects.equals(_GitDir.GetGitDirPath(), GitDirTarget.GetGitDirPath()))
				Reload();
		};
		AlphaEngine.Instance.AddIRefreshGitDirEvent(RefreshListener);

		// Dispose on close: stop listening and drop the dedup entry. The
		// handler is installed exactly once (here), so Open() must never
		// set its own onHidden — that would replace this one. The two-arg
		// remove only drops the map entry while it still points at THIS
		// window, so a stale close can never evict a newer window's entry.
		setOnHidden(__Event ->
		{
			IsDisposed = true;
			AlphaEngine.Instance.RemoveIRefreshGitDirEvent(RefreshListener);
			OpenWindows.remove(GitDirTarget, this);
		});

		// Gate Link/Unlink from the start (no selection yet) and paint both
		// detail footers with their placeholder state — the async refresh
		// callback is the first thing that could produce a real selection.
		OnSelectionChanged();
		show();
		// Initial load: refresh first so the rebuild reads data ordered after
		// the runner's writes (the live branch list must never be iterated
		// while the runner thread mutates it).
		Reload();
	}

	/**
	 * @return the repository this window operates on (the owning
	 *         {@link GitDir}, as required by {@link IObject})
	 */
	@Override
	public Object GetParent()
	{
		return GitDirTarget;
	}

	// ------------------------------------------------------------------
	//  Data flow
	// ------------------------------------------------------------------

	/**
	 * Queue a data refresh of the repository; its callback copies the fresh
	 * branch state into this window's caches and rebuilds both columns on
	 * the FX thread. No-op once disposed. A failed refresh still rebuilds —
	 * showing the last known state beats showing nothing (same policy as
	 * {@code GitDirWidget}).
	 */
	private void Reload()
	{
		if (IsDisposed)
			return;
		GitDirTarget.Refresh((__Ok, __Err, __Dir) -> Platform.runLater(() ->
		{
			if (IsDisposed)
				return;
			CacheBranches();
			RebuildRows();
		}));
	}

	/**
	 * Copy the live branch list into this window's own caches: the local and
	 * remote column stores, the reverse link map (remote → linked locals)
	 * and the active-branch path. Called only from refresh callbacks — the
	 * runner thread writes the live list, so it must be read strictly after
	 * a refresh completes, never from filter keystrokes or button handlers.
	 */
	private void CacheBranches()
	{
		LocalBranches.clear();
		RemoteBranches.clear();
		LocalsByUpstream.clear();
		for (GitBranch __Branch : GitDirTarget.GetBranches())
		{
			if (__Branch.Remote())
			{
				RemoteBranches.add(__Branch);
			}
			else
			{
				LocalBranches.add(__Branch);
				// Reverse index: many locals may track one remote.
				if (__Branch.HasUpstream())
					LocalsByUpstream.computeIfAbsent(__Branch.Upstream(), __K -> new ArrayList<>())
							.add(__Branch.FullPath());
			}
		}
		String __Active = GitDirTarget.GetActiveBranch();
		ActiveBranchPath = __Active == null ? "" : __Active;
	}

	/**
	 * Rebuild both columns' visible rows from the cached branches, the
	 * shared filter and the shared collapse set. Selection is remembered
	 * across the rebuild ({@code setAll} clears it) so operations that
	 * refresh mid-interaction don't lose the user's picks; then the action
	 * buttons are re-gated. Safe to call from any FX-thread path — it only
	 * touches this window's caches, never the live list.
	 */
	private void RebuildRows()
	{
		String __SelLocal = SelectedPath(LocalList);
		String __SelRemote = SelectedPath(RemoteList);

		String __Filter = FilterRow.GetFilterText();
		LocalRows.setAll(BranchTree.BuildRows(LocalBranches, __Filter, CollapsedNamespaces));
		RemoteRows.setAll(BranchTree.BuildRows(RemoteBranches, __Filter, CollapsedNamespaces));

		RestoreSelection(LocalList, __SelLocal);
		RestoreSelection(RemoteList, __SelRemote);
		UpdateActionButtons();
	}

	/**
	 * Run one git operation through the operator with all three buttons
	 * gated until it completes. The callback always fires (even after a
	 * failure or skipped refresh); UI work is wrapped for the FX thread and
	 * failures surface as an error dialog. The synchronous path is guarded:
	 * a validation exception (the {@code GitDir} methods reject null/blank
	 * arguments by throwing) would otherwise leave {@link #Busy} latched —
	 * and with it all three buttons — because the callback never fires.
	 *
	 * @param _Op     starts the operation (wires the provided callback)
	 * @param _Title  error dialog title
	 * @param _Header error dialog header
	 */
	private void RunOperation(Consumer<IGitOperationCallback> _Op, String _Title, String _Header)
	{
		Busy = true;
		UpdateActionButtons();
		try
		{
			_Op.accept((__Ok, __Err, __Dir) -> Platform.runLater(() ->
			{
				if (IsDisposed)
					return;
				Busy = false;
				UpdateActionButtons();
				if (!__Ok)
					ShowError(_Title, _Header, __Err);
			}));
		}
		catch (RuntimeException __Ex)
		{
			// The callback will never fire — release the latch here.
			Busy = false;
			UpdateActionButtons();
			ShowError(_Title, _Header, __Ex.getMessage());
		}
	}

	/**
	 * Re-gate the action buttons from the current selections: Fetch needs
	 * none but is blocked while any operation is in flight; Link needs a
	 * branch row selected on both sides; Unlink needs a <em>linked</em> local
	 * branch (git errors on an unlinked one).
	 */
	private void UpdateActionButtons()
	{
		BranchListRow __Local = LocalList.getSelectionModel().getSelectedItem();
		BranchListRow __Remote = RemoteList.getSelectionModel().getSelectedItem();
		boolean __LocalBranch = __Local != null && __Local.IsBranch();
		boolean __RemoteBranch = __Remote != null && __Remote.IsBranch();
		btn_Fetch.setDisable(Busy);
		btn_Link.setDisable(Busy || !(__LocalBranch && __RemoteBranch));
		btn_Unlink.setDisable(Busy || !(__LocalBranch && __Local.Branch().HasUpstream()));
	}

	/**
	 * Single entry point for selection changes in either column: re-gate the
	 * action buttons and repaint both detail footers. Wired to both lists'
	 * {@code selectedItemProperty} and called once before the window shows.
	 */
	private void OnSelectionChanged()
	{
		UpdateActionButtons();
		UpdateDetailPanes();
	}

	/**
	 * Repaint both detail footers from the current selections: name = the
	 * branch's full path, linked = its upstream (local column) or every
	 * local tracking it (remote column). A header row or an empty selection
	 * shows em-dash placeholders, all muted. Reads only this window's caches
	 * ({@link #LocalsByUpstream} etc.), so it is safe on any FX-thread path.
	 */
	private void UpdateDetailPanes()
	{
		ApplyDetail(lbl_LocalName, lbl_LocalLinked,
				LocalList.getSelectionModel().getSelectedItem(), true);
		ApplyDetail(lbl_RemoteName, lbl_RemoteLinked,
				RemoteList.getSelectionModel().getSelectedItem(), false);
	}

	/**
	 * Fill one detail footer's value labels for the selected row and re-style
	 * them from the active palette (styles are derived here, so a palette
	 * change only needs to call this again).
	 *
	 * @param _Name        the branch-name value label
	 * @param _Linked      the linked-branch value label
	 * @param _Row         the selected row (null / header = placeholder state)
	 * @param _LocalColumn true for the local footer (link = the branch's own
	 *                     upstream), false for the remote footer (link =
	 *                     every local tracking this remote)
	 */
	private void ApplyDetail(Label _Name, Label _Linked, BranchListRow _Row, boolean _LocalColumn)
	{
		if (_Row == null || !_Row.IsBranch())
		{
			_Name.setText("\u2014");
			_Linked.setText("\u2014");
			ApplyValueStyle(_Name, ETextVariant.MUTED);
			ApplyValueStyle(_Linked, ETextVariant.MUTED);
			return;
		}

		_Name.setText(_Row.Branch().FullPath());
		ApplyValueStyle(_Name, ETextVariant.BOLD);

		String __Linked;
		if (_LocalColumn)
		{
			__Linked = _Row.Branch().HasUpstream() ? _Row.Branch().Upstream() : "Not linked";
		}
		else
		{
			List<String> __Locals = LocalsByUpstream.get(_Row.Branch().FullPath());
			__Linked = (__Locals == null || __Locals.isEmpty())
					? "Not linked" : String.join(", ", __Locals);
		}
		_Linked.setText(__Linked);
		ApplyValueStyle(_Linked, "Not linked".equals(__Linked) ? ETextVariant.MUTED : ETextVariant.BODY);
	}

	/**
	 * Apply an {@link ETextVariant} to a {@link Label}. The variant resolves
	 * Text CSS ({@code -fx-fill}); Labels paint text via
	 * {@code -fx-text-fill}, so the color property name is swapped — the
	 * remaining declarations (font weight/size/family) are valid for Labels
	 * as written, keeping {@code ETextVariant} the single source of truth.
	 *
	 * @param _Label   the label to style
	 * @param _Variant the variant whose palette-resolved CSS to apply
	 */
	private static void ApplyValueStyle(Label _Label, ETextVariant _Variant)
	{
		_Label.setStyle(_Variant.ResolveCss(ThemeManager.Instance.GetPalette())
				.replace("-fx-fill:", "-fx-text-fill:"));
	}

	/**
	 * Build one fixed-height detail footer: a 2-column grid (caption | value)
	 * whose value labels ellipsize instead of overflowing. Captions are
	 * auto-theming {@link AText}s; value labels carry palette-derived inline
	 * styles re-applied by {@link #ApplyDetail}. The caller pins the result's
	 * height after {@link #ApplyPaneSkins} has added the top border.
	 *
	 * @param _Name   value label for the branch name row
	 * @param _Linked value label for the linked-branch row
	 * @return the footer grid; padding is applied here, the top border later
	 *         by the skins pass
	 */
	private static GridPane CreateDetailPane(Label _Name, Label _Linked)
	{
		for (Label __Value : new Label[] { _Name, _Linked })
		{
			// Let the value shrink below its text width inside the growing
			// column so long branch paths ellipsize instead of overflowing
			// into the neighbouring column.
			__Value.setMinWidth(0);
			__Value.setMaxWidth(Double.MAX_VALUE);
			__Value.setTextOverrun(OverrunStyle.ELLIPSIS);
		}

		ColumnConstraints __Caption = new ColumnConstraints(); // caption column: natural width
		ColumnConstraints __Value = new ColumnConstraints();
		__Value.setHgrow(Priority.ALWAYS); // values take the extra column width

		GridPane __Pane = new GridPane();
		__Pane.setHgap(8);
		__Pane.setVgap(4);
		__Pane.setPadding(new Insets(6));
		__Pane.getColumnConstraints().addAll(__Caption, __Value);
		__Pane.add(new AText("Name", ETextVariant.MUTED), 0, 0);
		__Pane.add(_Name, 1, 0);
		__Pane.add(new AText("Linked", ETextVariant.MUTED), 0, 1);
		__Pane.add(_Linked, 1, 1);
		return __Pane;
	}

	// ------------------------------------------------------------------
	//  Theme
	// ------------------------------------------------------------------

	/**
	 * Theme-change push: re-bake the palette-colored chrome (column
	 * separator background, detail-footer top borders) and re-style the
	 * detail value labels. The captions, lists and buttons carry their own
	 * skins or auto-theming {@link AText} styles. Runs on the FX thread.
	 *
	 * @param _Palette the new palette to derive colors from
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplyPaneSkins(_Palette);
		UpdateDetailPanes();
	}

	/**
	 * Apply the palette-derived colors to the window's inline-styled chrome:
	 * the static column separator's background and the two detail footers'
	 * top borders. Inlined hex (the convention for node-level styles — scene
	 * {@code -gitalpha-*} variables do not resolve there); called from the
	 * constructor and from {@link #Event(ColorPalette)}.
	 *
	 * @param _Palette the palette to read colors from
	 */
	private void ApplyPaneSkins(ColorPalette _Palette)
	{
		String __Border = _Palette.GetBorderColor().GetHex(_Palette.GetColorLookup());
		reg_ColumnSeparator.setStyle("-fx-background-color: " + __Border + ";");
		String __FooterStyle = "-fx-border-color: " + __Border + ";"
				+ "-fx-border-width: 1 0 0 0;";
		grid_LocalDetail.setStyle(__FooterStyle);
		grid_RemoteDetail.setStyle(__FooterStyle);
	}

	/**
	 * Show a modal error dialog themed with the active palette (the popup
	 * and dialog surfaces carry their own skins; the scene-level registration
	 * above covers the rest).
	 *
	 * @param _Title  dialog title
	 * @param _Header dialog header line
	 * @param _Error  the git error text (falls back to a generic message)
	 */
	private void ShowError(String _Title, String _Header, String _Error)
	{
		Alert __Alert = new Alert(Alert.AlertType.ERROR);
		ThemeManager.Instance.ApplyThemeToDialog(__Alert);
		__Alert.setTitle(_Title);
		__Alert.setHeaderText(_Header);
		__Alert.setContentText(_Error == null || _Error.isBlank() ? "Unknown error" : _Error);
		__Alert.showAndWait();
	}

	// ------------------------------------------------------------------
	//  Tree helpers
	// ------------------------------------------------------------------

	/** Expand every namespace in both columns: clear the shared collapse set. */
	private void ExpandAllNamespaces()
	{
		CollapsedNamespaces.clear();
		RebuildRows();
	}

	/** Collapse every namespace in both columns: mark every prefix, then rebuild. */
	private void CollapseAllNamespaces()
	{
		List<GitBranch> __All = new ArrayList<>(LocalBranches);
		__All.addAll(RemoteBranches);
		BranchTree.CollapseAll(__All, CollapsedNamespaces);
		RebuildRows();
	}

	/**
	 * Toggle one namespace header (shared collapse set — both columns
	 * rebuild so the tree state stays consistent).
	 *
	 * @param _Namespace the full namespace path acting as the collapse key
	 */
	private void ToggleNamespace(String _Namespace)
	{
		BranchTree.ToggleNamespace(CollapsedNamespaces, _Namespace);
		RebuildRows();
	}

	/**
	 * @param _List the column list to read
	 * @return the selected row's branch full path, or null when nothing (or
	 *         a header row) is selected
	 */
	private static String SelectedPath(AListView<BranchListRow> _List)
	{
		BranchListRow __Row = _List.getSelectionModel().getSelectedItem();
		return (__Row != null && __Row.IsBranch()) ? __Row.Branch().FullPath() : null;
	}

	/**
	 * Re-select the row carrying {@code _Path} after a rebuild (selection is
	 * cleared by {@code setAll}); a path that the filter now hides simply
	 * stays unselected.
	 *
	 * @param _List the column list
	 * @param _Path the previously selected branch full path (null = none)
	 */
	private static void RestoreSelection(AListView<BranchListRow> _List, String _Path)
	{
		if (_Path == null)
			return;
		List<BranchListRow> __Rows = _List.getItems();
		for (int __Idx = 0; __Idx < __Rows.size(); ++__Idx)
		{
			BranchListRow __Row = __Rows.get(__Idx);
			if (__Row.IsBranch() && __Row.Branch().FullPath().equals(_Path))
			{
				_List.getSelectionModel().select(__Idx);
				return;
			}
		}
	}

	/**
	 * Build a bold column header label ("Local" / "Remote"). The gap between
	 * the header and its list comes from the column VBox's spacing — AText
	 * extends Text, which carries no padding API.
	 *
	 * @param _Text the header text
	 * @return the styled header node
	 */
	private static AText ColumnHeader(String _Text)
	{
		return new AText(_Text, ETextVariant.TITLE);
	}

	// ------------------------------------------------------------------
	//  Cell factory
	// ------------------------------------------------------------------

	/**
	 * Recycled list cell for one column. Namespace headers show a toggle
	 * arrow + this level's segment, indent one {@link BranchTree#TREE_INDENT}
	 * step per depth, and expand/collapse their group on left-click (the
	 * click is consumed so the ListView never selects header rows). Branch
	 * rows show the leaf name plus an inline upstream suffix: the local
	 * column renders {@code (origin/main)} from the branch's own link, the
	 * remote column renders {@code (main, experiment)} from the reverse map.
	 * The active branch (local column only — a remote's path must never
	 * match it) gets a dot graphic + bold green text. Cells are reused, so
	 * every row must reset text, graphic, tooltip, style and mouse handler.
	 *
	 * @param _LocalColumn true for the local column (link suffix + active
	 *                     styling come from different sources per side)
	 * @return the cell factory product
	 */
	private ListCell<BranchListRow> CreateRowCell(boolean _LocalColumn)
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
					case BRANCH -> RenderBranch(_Row.Branch(), _Row.Depth(), _LocalColumn);
				}
			}

			/**
			 * Paint a section label (defensive — the manager's columns
			 * normally carry none): bold muted text, click consumed so the
			 * row is never selected.
			 */
			private void RenderSection(String _Label)
			{
				setText(_Label);
				setStyle("-fx-font-weight: bold; -fx-text-fill: #808080;"
						+ " -fx-padding: 10 10 4 6;");
				setOnMouseClicked(__Event -> __Event.consume());
			}

			/**
			 * Paint a namespace-tree header: arrow + segment at its depth
			 * indent; the full path (row's {@code Path}) is the collapse
			 * key, not the display text.
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
			 * Paint a branch row: leaf name, upstream suffix, depth indent
			 * and (local column only) the active-branch styling. Active
			 * green is a fixed hex value, not a palette slot, so it never
			 * changes with the theme.
			 *
			 * @param _Entry       the branch to render
			 * @param _Depth       tree depth for the left indent (0 = root)
			 * @param _LocalColumn true when rendering the local column
			 */
			private void RenderBranch(GitBranch _Entry, int _Depth, boolean _LocalColumn)
			{
				String __Suffix = _LocalColumn ? LocalSuffix(_Entry) : RemoteSuffix(_Entry);
				setText(_Entry.Name() + __Suffix);

				// Indent one level per namespace segment above the leaf.
				String __Padding = " -fx-padding: 4 10 4 "
						+ (int) (6 + _Depth * BranchTree.TREE_INDENT) + ";";

				boolean __IsActive = _LocalColumn && _Entry.FullPath().equals(ActiveBranchPath);
				if (__IsActive)
				{
					setGraphic(new Label("\u25CF"));
					setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;" + __Padding);
					setTooltip(new Tooltip("Currently checked out"));
				}
				else
				{
					setGraphic(null);
					setStyle(__Padding);
					setTooltip(null);
				}
			}

			/**
			 * @param _Entry a local branch
			 * @return {@code " (origin/main)"} when linked, "" when not
			 */
			private String LocalSuffix(GitBranch _Entry)
			{
				return _Entry.HasUpstream() ? " (" + _Entry.Upstream() + ")" : "";
			}

			/**
			 * @param _Entry a remote branch
			 * @return {@code " (main, experiment)"} listing every local
			 *         branch tracking it, "" when none do
			 */
			private String RemoteSuffix(GitBranch _Entry)
			{
				List<String> __Locals = LocalsByUpstream.get(_Entry.FullPath());
				return (__Locals == null || __Locals.isEmpty()) ? "" : " (" + String.join(", ", __Locals) + ")";
			}
		};
	}
}
