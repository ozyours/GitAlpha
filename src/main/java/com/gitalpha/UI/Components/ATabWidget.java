package com.gitalpha.UI.Components;

import com.gitalpha.Engine.AlphaSettings;
import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.Type.ETabButtonVariant;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully controllable tab widget: a row of flat tab faces above a
 * {@link StackPane} content swap, optionally user-modifiable — the
 * constructor flag turns on the {@code "+"} affix (new tab), a {@code ×}
 * close button on every tab, and drag-to-reorder of the header faces. When
 * not modifiable, tabs are managed purely programmatically.
 * <p>
 * The header is horizontally scrollable when tab faces overflow: the face
 * strip sits inside a {@link ScrollPane}, while the {@code "+"} affix (in
 * modifiable mode) is pinned to the right edge outside the scroll area.
 * The scroll bar itself never shows — the pane's hbar policy is set to
 * {@link ScrollPane.ScrollBarPolicy#NEVER} so the skin never mounts it —
 * and wheel / trackpad input scrolls the strip horizontally via
 * {@link #InstallWheelScroll()}. Tab face widths are configurable
 * via {@link #SetTabMinWidth}, {@link #SetTabMaxWidth} and
 * {@link #SetTabPrefWidth}; all three apply uniformly to every face
 * including the affix.
 * <p>
 * Each tab face is a {@link TabButton} — a private {@link HBox} component
 * holding the title {@link Label} and the close {@link ATabButton} — styled
 * entirely by the cascading tab skin through the {@code a-tab-button} /
 * {@code a-tab-close} style classes; the selected and armed states are custom
 * {@code :selected} / {@code :pressed} pseudo-class toggles, and each face is
 * focus-traversable (primary click or Enter/Space activates it). The
 * {@code "+"} affix is the same component without a close button, kept out of
 * the tab list so it can never be selected or reordered. Reorder is live:
 * once a drag passes a small threshold the dragged face swaps position while
 * the pointer moves (browser-tab style), and the parallel content list plus
 * the selected index follow the permutation.
 * <p>
 * Tab action handlers resolve their index via {@code TabButtons.indexOf} at
 * invocation time rather than capturing the creation index, so closures stay
 * valid across reorders and closes.
 * <p>
 * One stylesheet (baked by {@code TabButtonSkin} at this widget's
 * {@link ETabButtonVariant}) cascades from this root
 * to all descendants ({@code .a-tab-header}, {@code .a-tab-button},
 * {@code .a-tab-close}, {@code .a-tab-content}), so header strip, tab faces,
 * close buttons and hairline re-bake together on palette switches. Listeners
 * (selection / new-tab request / tab closed) are held as weak references and
 * pruned on broadcast; the widget's own theme registration stays alive as
 * long as the widget is in the scene graph.
 */
public class ATabWidget extends VBox implements IThemeChangeEvent
{
	/** Horizontal pixels the pointer must travel before a press becomes a drag-reorder */
	private static final double DRAG_THRESHOLD = 6;

	/**
	 * Maximum face height (pixels) for the {@link ETabButtonVariant#SMALL}
	 * variant; matches the CSS-computed content height of a SMALL tab button
	 * (label + compact padding) so the header strip is visibly shorter than
	 * the NORMAL main-tab strip.
	 */
	private static final double TAB_FACE_SMALL_MAX_HEIGHT = 26;

	/**
	 * The outer header bar: carries the {@code .a-tab-header} background
	 * strip and holds the scroll pane (tab faces) plus the pinned
	 * {@code "+"} affix.
	 */
	private final HBox HeaderBar;
	/**
	 * The scroll pane wrapping the inner header. Scrollable horizontally
	 * (its hbar policy is set to {@link ScrollPane.ScrollBarPolicy#NEVER}
	 * so the skin never mounts a bar node), and wheel input is translated
	 * to horizontal scrolling by {@link #InstallWheelScroll()}. Vertical
	 * scroll is always disabled.
	 */
	private final ScrollPane HeaderScrollPane;
	/** The inner header row holding only the tab faces (no affix) */
	private final HBox Header;
	/** The content stack: whichever child was last set via setAll is visible */
	private final StackPane Content;
	/** All tab faces, in current display order */
	private final List<TabButton> TabButtons;
	/** All content nodes, parallel to TabButtons */
	private final List<Node> TabContents;
	/**
	 * Whether the user may add, close and reorder tabs (programmatic
	 * {@link #AddTab} is always available)
	 */
	private final boolean Modifiable;
	/**
	 * The tab-button skin size variant baked into this strip's cascading
	 * stylesheet ({@code NORMAL} = full-size main tabs, {@code SMALL} =
	 * compact sub-tabs); fixed at construction
	 */
	private final ETabButtonVariant TabVariant;
	/**
	 * The {@code "+"} affix shown when modifiable; null otherwise. Pinned
	 * to the right edge of the header bar, outside the scroll area.
	 * Deliberately kept out of {@link #TabButtons}, so selection,
	 * reordering and close logic never see it — its click only fires
	 * {@link INewTabRequestEvent}s.
	 */
	private final TabButton btn_AddTab;
	/** Configured minimum width for every tab face (default {@link Control#USE_COMPUTED_SIZE}) */
	private double TabMinWidth = Control.USE_COMPUTED_SIZE;
	/** Configured maximum width for every tab face (default {@link Control#USE_COMPUTED_SIZE}) */
	private double TabMaxWidth = Control.USE_COMPUTED_SIZE;
	/** Configured preferred width for every tab face (default {@link Control#USE_COMPUTED_SIZE}) */
	private double TabPrefWidth = Control.USE_COMPUTED_SIZE;
	/** Scene X where the active drag started */
	private double DragStartX;
	/** True once the active drag passed {@link #DRAG_THRESHOLD} */
	private boolean DragActive;
	/** The currently selected tab index, or -1 if nothing is selected */
	private int SelectedIndex = -1;
	/** Selection listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<ISubTabSelectionEvent>> SelectionListeners;
	/** New-tab-request listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<INewTabRequestEvent>> NewTabRequestListeners;
	/** Tab-closed listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<ITabCloseEvent>> TabCloseListeners;

	/**
	 * Create a modifiable or fixed tab widget with the {@code NORMAL} skin
	 * variant.
	 *
	 * @param _Modifiable true to let the user add ({@code "+"}), close
	 *                    ({@code ×}) and drag-reorder tabs
	 */
	public ATabWidget(boolean _Modifiable)
	{
		this(_Modifiable, ETabButtonVariant.NORMAL);
	}

	/**
	 * Create a modifiable or fixed tab widget with an explicit tab-button
	 * skin size variant.
	 *
	 * @param _Modifiable true to let the user add ({@code "+"}), close
	 *                    ({@code ×}) and drag-reorder tabs
	 * @param _Variant    the {@link TabButtonSkin} size variant to bake into
	 *                    this strip's stylesheet ({@code NORMAL} for main
	 *                    tabs, {@code SMALL} for compact secondary strips)
	 */
	public ATabWidget(boolean _Modifiable, ETabButtonVariant _Variant)
	{
		super();

		Modifiable = _Modifiable;
		TabVariant = _Variant;
		TabButtons = new ArrayList<>();
		TabContents = new ArrayList<>();
		SelectionListeners = new ArrayList<>();
		NewTabRequestListeners = new ArrayList<>();
		TabCloseListeners = new ArrayList<>();

		// Inner header: holds only the tab faces (no affix). Sits inside the
		// scroll pane, which clips and scrolls it when faces overflow.
		Header = new HBox();

		// Scroll pane: horizontal scroll on demand, no vertical bar; chrome
		// stripped so the outer header bar's background shows through cleanly.
		// Setting the hbar policy to NEVER prevents the skin from mounting a bar
		// node at all, avoiding a post-attach hide hack while preserving
		// programmatic and InstallWheelScroll() scrolling.
		HeaderScrollPane = new ScrollPane(Header);
		HeaderScrollPane.setFitToWidth(false);
		HeaderScrollPane.setFitToHeight(true);
		HeaderScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		HeaderScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		HeaderScrollPane.setBackground(Background.EMPTY);
		HeaderScrollPane.setBorder(Border.EMPTY);
		// Keep the scroll viewport itself at the compact face height: fit-to-height
		// otherwise lets the ScrollPane expand and leaves excess vertical space in
		// the SMALL header even though the tab faces are height-capped.
		if (TabVariant == ETabButtonVariant.SMALL)
		{
			HeaderScrollPane.setMinHeight(TAB_FACE_SMALL_MAX_HEIGHT);
			HeaderScrollPane.setPrefHeight(TAB_FACE_SMALL_MAX_HEIGHT);
			HeaderScrollPane.setMaxHeight(TAB_FACE_SMALL_MAX_HEIGHT);
		}
		InstallWheelScroll();

		// Outer header bar: the full-width background strip. The scroll pane
		// grows to absorb extra space; the "+" affix (if any) stays pinned at
		// its natural width on the right.
		HeaderBar = new HBox();
		HeaderBar.getStyleClass().add("a-tab-header");
		HBox.setHgrow(HeaderScrollPane, Priority.ALWAYS);
		HeaderBar.getChildren().add(HeaderScrollPane);

		if (_Modifiable)
		{
			btn_AddTab = new TabButton("+", false);
			btn_AddTab.SetOnAction(this::FireNewTabRequest);
			HeaderBar.getChildren().add(btn_AddTab);
		}
		else
		{
			btn_AddTab = null;
		}

		Content = new StackPane();
		Content.getStyleClass().add("a-tab-content");

		getChildren().addAll(HeaderBar, Content);
		VBox.setVgrow(HeaderBar, Priority.NEVER);
		// Content must grow to fill the VBox; the header bar stays at its natural height
		VBox.setVgrow(Content, Priority.ALWAYS);

		ApplySkin();

		// Apply the user-configured maximum tab face width from settings.
		int __TabMaxWidth = AlphaSettings.Get().GetSettingEntry(AlphaSettings.TabMaxSize).GetValue_AsInteger();
		SetTabMaxWidth(__TabMaxWidth);

		// Weak-reference registration: cleanup is automatic when the widget leaves the scene graph
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Create a non-modifiable tab widget (no add/close/reorder chrome).
	 */
	public ATabWidget()
	{
		this(false);
	}

	/**
	 * Map wheel input onto the horizontal scroll position: the tab strip is
	 * a single row, so a vertical wheel would otherwise drive only the
	 * disabled vertical axis and the strip would be unscrollable with the
	 * bar hidden. Wheel forward scrolls toward the strip start, wheel back
	 * toward its end (same sign convention as a vertical scrollbar);
	 * tilt-wheel/trackpad deltaX is honoured when deltaY is zero. Consumed
	 * so the pane's own handler never double-scrolls.
	 */
	private void InstallWheelScroll()
	{
		HeaderScrollPane.addEventFilter(ScrollEvent.SCROLL, __Event ->
		{
			double __Delta = __Event.getDeltaY() != 0 ? __Event.getDeltaY() : __Event.getDeltaX();
			if (__Delta == 0)
				return;
			double __Min = HeaderScrollPane.getHmin();
			double __Max = HeaderScrollPane.getHmax();
			double __Value = Math.max(__Min, Math.min(__Max, HeaderScrollPane.getHvalue() - __Delta));
			HeaderScrollPane.setHvalue(__Value);
			__Event.consume();
		});
	}

	/**
	 * Add a tab to the widget. The content node is kept alive for the
	 * widget's lifetime (the StackPane shows whichever child was last swapped
	 * in). The first tab added is selected automatically; later tabs require
	 * {@link #SelectTab}. In modifiable mode the new face is appended to the
	 * scrollable strip (the {@code "+"} affix stays pinned to the right).
	 *
	 * @param _Title   the tab label
	 * @param _Content the view to show when this tab is selected
	 * @return the zero-based index of the newly added tab
	 */
	public int AddTab(String _Title, Node _Content)
	{
		int __Index = TabButtons.size();
		TabButton __Button = CreateTabButton(_Title);
		TabButtons.add(__Button);
		TabContents.add(_Content);
		Header.getChildren().add(__Button);

		// Select the first tab automatically so the content is never empty
		// after the first AddTab; later tabs require an explicit SelectTab.
		if (__Index == 0)
			SelectTab(0);

		return __Index;
	}

	/**
	 * Build a tab face for {@link #AddTab}: selection wired to its live
	 * position; in modifiable mode also the close button and drag handlers.
	 * The face's width is configured from the current min/max/pref settings.
	 *
	 * @param _Title the tab label
	 * @return the wired face (not yet inserted anywhere)
	 */
	private TabButton CreateTabButton(String _Title)
	{
		TabButton __Button = new TabButton(_Title, Modifiable);
		__Button.SetOnAction(() -> SelectTab(TabButtons.indexOf(__Button)));
		ApplyTabSizes(__Button);

		// SMALL variant: cap each tab face's height so the compact strip is
		// visibly shorter than the NORMAL main-tab strip.
		if (TabVariant == ETabButtonVariant.SMALL)
			__Button.SetMaxFaceHeight(TAB_FACE_SMALL_MAX_HEIGHT);

		if (!Modifiable)
			return __Button;

		__Button.SetOnClose(() ->
		{
			int __I = TabButtons.indexOf(__Button);
			if (__I >= 0)
				CloseTab(__I);
		});

		InstallDragHandlers(__Button);
		return __Button;
	}

	/**
	 * Attach press/drag/release handlers that implement live reorder: after
	 * {@link #DRAG_THRESHOLD} horizontal pixels, every move recomputes the
	 * insertion slot under the pointer and permutes immediately. Drag events
	 * are consumed to prevent the enclosing {@link ScrollPane} from scrolling
	 * while a reorder gesture is in progress.
	 *
	 * @param _Button the tab face to make draggable
	 */
	private void InstallDragHandlers(TabButton _Button)
	{
		_Button.setOnMousePressed(__Event ->
		{
			DragStartX = __Event.getSceneX();
			DragActive = false;
		});
		_Button.setOnMouseDragged(__Event ->
		{
			// Always consume: dragging a face is for reordering, not scrolling.
			__Event.consume();
			if (TabButtons.size() < 2)
				return;
			if (!DragActive && Math.abs(__Event.getSceneX() - DragStartX) > DRAG_THRESHOLD)
				DragActive = true;
			if (!DragActive)
				return;

			int __From = TabButtons.indexOf(_Button);
			int __To = HitTestInsertIndex(__Event.getSceneX(), __From);
			if (__To != __From)
				MoveTab(__From, __To);
		});
		_Button.setOnMouseReleased(__Event -> DragActive = false);
	}

	/**
	 * Resolve the insertion slot for the dragged tab: the number of other
	 * faces whose midpoint sits left of the pointer, clamped to the valid
	 * range. Skipping the dragged face itself keeps the count stable while
	 * it follows the pointer.
	 *
	 * @param _SceneX pointer X in scene coordinates
	 * @param _From   current index of the dragged tab
	 * @return the target index for the dragged tab
	 */
	private int HitTestInsertIndex(double _SceneX, int _From)
	{
		int __Insert = 0;
		for (int __I = 0; __I < TabButtons.size(); __I++)
		{
			if (__I == _From)
				continue;
			TabButton __B = TabButtons.get(__I);
			double __Mid = __B.localToScene(__B.getBoundsInLocal()).getMinX() + __B.getWidth() / 2.0;
			if (_SceneX > __Mid)
				__Insert++;
		}
		return Math.min(__Insert, TabButtons.size() - 1);
	}

	/**
	 * Permute a tab from one index to another: the face moves inside the
	 * inner header, the content list follows, and {@code SelectedIndex} is
	 * remapped with the standard single-element-move shift.
	 *
	 * @param _From source index
	 * @param _To   target index
	 */
	private void MoveTab(int _From, int _To)
	{
		TabButton __Button = TabButtons.remove(_From);
		Node __MovedContent = TabContents.remove(_From);
		TabButtons.add(_To, __Button);
		TabContents.add(_To, __MovedContent);

		Header.getChildren().remove(__Button);
		Header.getChildren().add(_To, __Button);

		// Remap the selection to follow the moved element
		if (SelectedIndex == _From)
			SelectedIndex = _To;
		else if (_From < SelectedIndex && _To >= SelectedIndex)
			SelectedIndex--;
		else if (_From > SelectedIndex && _To <= SelectedIndex)
			SelectedIndex++;
	}

	/**
	 * Close the tab at the given index (user action): remove face, content
	 * and header entry; if it was selected, select the nearest surviving
	 * neighbour (same slot clamped, previous when it was last). Fires
	 * {@link ITabCloseEvent} afterwards so consumers can release resources.
	 *
	 * @param _Index the tab to close
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void CloseTab(int _Index)
	{
		if (_Index < 0 || _Index >= TabButtons.size())
			throw new IndexOutOfBoundsException("Tab index out of range: " + _Index);

		TabButton __Button = TabButtons.remove(_Index);
		Node __RemovedContent = TabContents.remove(_Index);
		Header.getChildren().remove(__Button);
		boolean __WasSelected = SelectedIndex == _Index;
		if (_Index < SelectedIndex)
			SelectedIndex--;

		if (__WasSelected)
		{
			SelectedIndex = -1;
			if (!TabButtons.isEmpty())
				SelectTab(Math.min(_Index, TabButtons.size() - 1));
			else
				Content.getChildren().clear();
		}

		FireTabClosed(_Index, __RemovedContent);
	}

	/**
	 * Switch the selected tab by index. Updates the face selected states,
	 * swaps the content stack, and fires selection listeners. No-op if the
	 * index is already selected.
	 *
	 * @param _Index the tab to select (must be in range)
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void SelectTab(int _Index)
	{
		if (_Index == SelectedIndex)
			return;
		if (_Index < 0 || _Index >= TabButtons.size())
			throw new IndexOutOfBoundsException("Tab index out of range: " + _Index);

		for (int __I = 0; __I < TabButtons.size(); __I++)
			TabButtons.get(__I).SetSelected(__I == _Index);

		SelectedIndex = _Index;
		Node __Content = TabContents.get(_Index);
		Content.getChildren().setAll(__Content);

		FireSelection(_Index, __Content);
	}

	/**
	 * @return the zero-based index of the currently selected tab, or -1 if
	 *         nothing is selected
	 */
	public int GetSelectedIndex()
	{
		return SelectedIndex;
	}

	/**
	 * Resolve the live index of the tab hosting the given content node.
	 * Indexes shift on reorder/close, so consumers holding a content
	 * reference should resolve through this at invocation time instead of
	 * caching an index.
	 *
	 * @param _Content the content node previously passed to {@link #AddTab}
	 * @return the current index of that tab, or -1 if it is not present
	 */
	public int IndexOf(Node _Content)
	{
		return TabContents.indexOf(_Content);
	}

	/**
	 * Change the label of the tab at the given index (e.g. when a hosted
	 * view's title changes). Does not affect selection or ordering.
	 *
	 * @param _Index the tab to retitle
	 * @param _Title the new label
	 * @throws IndexOutOfBoundsException if _Index is out of range
	 */
	public void SetTabTitle(int _Index, String _Title)
	{
		TabButtons.get(_Index).SetTitle(_Title);
	}

	/**
	 * @return the number of tabs in the widget (the {@code "+"} affix excluded)
	 */
	public int GetTabCount()
	{
		return TabButtons.size();
	}

	/**
	 * @return true when the user may add, close and reorder tabs
	 */
	public boolean IsModifiable()
	{
		return Modifiable;
	}

	// ── Tab face sizing ──────────────────────────────────────────────────

	/**
	 * Set the minimum width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the default computed minimum.
	 *
	 * @param _Width the minimum width in pixels
	 */
	public void SetTabMinWidth(double _Width)
	{
		TabMinWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Set the maximum width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the default computed maximum (unlimited).
	 *
	 * @param _Width the maximum width in pixels
	 */
	public void SetTabMaxWidth(double _Width)
	{
		TabMaxWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Set the preferred width applied to every tab face (including the
	 * {@code "+"} affix). Pass {@link Control#USE_COMPUTED_SIZE} to revert
	 * to the default computed preferred width (natural size).
	 *
	 * @param _Width the preferred width in pixels
	 */
	public void SetTabPrefWidth(double _Width)
	{
		TabPrefWidth = _Width;
		ApplyTabSizes();
	}

	/**
	 * Apply the current min/max/pref widths to a single tab face.
	 *
	 * @param _Button the face to configure
	 */
	private void ApplyTabSizes(TabButton _Button)
	{
		_Button.setMinWidth(TabMinWidth);
		_Button.setMaxWidth(TabMaxWidth);
		_Button.setPrefWidth(TabPrefWidth);
	}

	/**
	 * Apply the current min/max/pref widths to all existing faces (tab faces
	 * and the {@code "+"} affix).
	 */
	private void ApplyTabSizes()
	{
		for (TabButton __Button : TabButtons)
			ApplyTabSizes(__Button);
		if (btn_AddTab != null)
			ApplyTabSizes(btn_AddTab);
	}

	// ── Event registration ───────────────────────────────────────────────

	/**
	 * Register a selection listener. Held weakly, so a listener that is
	 * garbage-collected without unsubscribing is pruned on the next fire.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _Event the listener to notify on tab selection changes
	 */
	public void AddSelectionEvent(ISubTabSelectionEvent _Event)
	{
		SelectionListeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Register a listener for {@code "+"} clicks. Fired only in modifiable
	 * mode; respond by calling {@link #AddTab}.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _Event the listener to notify on new-tab requests
	 */
	public void AddNewTabRequestEvent(INewTabRequestEvent _Event)
	{
		NewTabRequestListeners.add(new WeakReference<>(_Event));
	}

	/**
	 * Register a listener fired after the user closes a tab.
	 *
	 * @apiNote Must be called on the FX thread.
	 *
	 * @param _Event the listener to notify on tab closes
	 */
	public void AddTabCloseEvent(ITabCloseEvent _Event)
	{
		TabCloseListeners.add(new WeakReference<>(_Event));
	}

	// ── Event broadcast ──────────────────────────────────────────────────

	/**
	 * Notify selection listeners, pruning dead references inline.
	 */
	private void FireSelection(int _Index, Node _Content)
	{
		int __I = 0;
		while (__I < SelectionListeners.size())
		{
			ISubTabSelectionEvent __Event = SelectionListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_Index, _Content);
				__I++;
			}
			else
				SelectionListeners.remove(__I);
		}
	}

	/**
	 * Notify new-tab-request listeners, pruning dead references inline.
	 */
	private void FireNewTabRequest()
	{
		int __I = 0;
		while (__I < NewTabRequestListeners.size())
		{
			INewTabRequestEvent __Event = NewTabRequestListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event();
				__I++;
			}
			else
				NewTabRequestListeners.remove(__I);
		}
	}

	/**
	 * Notify tab-closed listeners, pruning dead references inline.
	 */
	private void FireTabClosed(int _Index, Node _Content)
	{
		int __I = 0;
		while (__I < TabCloseListeners.size())
		{
			ITabCloseEvent __Event = TabCloseListeners.get(__I).get();
			if (__Event != null)
			{
				__Event.Event(_Index, _Content);
				__I++;
			}
			else
				TabCloseListeners.remove(__I);
		}
	}

	// ── Theme ────────────────────────────────────────────────────────────

	/**
	 * Theme-change push: re-bake the skin with the new palette's colors.
	 * The single stylesheet on this VBox cascades to all descendants, so
	 * header strip, tab faces, close buttons and hairline re-bake together.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin();
	}

	/**
	 * Replace the inline skin stylesheet with one baked from the active
	 * palette for this widget's {@link TabVariant}. The data-URI URL changes
	 * whenever the colors do, so JavaFX re-parses the new skin. The selector
	 * chains target the descendant classes ({@code .a-tab-header},
	 * {@code .a-tab-button}, {@code .a-tab-close}, {@code .a-tab-content}) so
	 * the single sheet covers all four node kinds.
	 */
	private void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetTabButtonStylesheets(TabVariant));
	}

	// ── Private tab face component ───────────────────────────────────────

	/**
	 * One tab face of the header row: an {@link HBox} carrying the title
	 * {@link Label} and — for closable tabs — the {@code ×} close
	 * {@link ATabButton}. Styled entirely by the cascading tab skin through the
	 * {@code a-tab-button} style class (background shades, padding, cursor,
	 * hover/pressed states); the selected and pressed states are custom
	 * {@code :selected} / {@code :pressed} pseudo-class toggles matching the
	 * palette shades (an HBox never enters Control's armed state, so pressed
	 * is tracked manually). The face is focus-traversable and activates on
	 * primary click or Enter/Space. The close button carries the
	 * {@code a-tab-close} class (muted normally, primary on hover), is not
	 * focus-traversable, and consumes its mouse events so pressing it neither
	 * selects the tab nor starts a drag-reorder.
	 */
	private final class TabButton extends HBox
	{
		/** The pseudo-class marking the selected (active) tab face */
		private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
		/** The pseudo-class marking a face with a primary button held down on it */
		private static final PseudoClass PRESSED = PseudoClass.getPseudoClass("pressed");

		/** The tab title */
		private final Label lbl_Title;
		/** The {@code ×} close face; null when the face is not closable */
		private ATabButton btn_Close;
		/** Invoked on primary click or Enter/Space; null until SetOnAction */
		private Runnable OnActionHandler;
		/** Invoked when the close button is clicked; null when not closable */
		private Runnable OnCloseHandler;

		/**
		 * Create a tab face.
		 *
		 * @param _Title    the tab label
		 * @param _Closable true to include the {@code ×} close button
		 */
		TabButton(String _Title, boolean _Closable)
		{
			getStyleClass().add("a-tab-button");
			setSpacing(2);
			// Keyboard parity with the previous Button-based faces: reachable
			// via Tab traversal, activated with Enter/Space.
			setFocusTraversable(true);
			setOnKeyPressed(__Event ->
			{
				if (__Event.getCode() == KeyCode.ENTER || __Event.getCode() == KeyCode.SPACE)
				{
					__Event.consume();
					FireAction();
				}
			});

			lbl_Title = new Label(_Title);
			getChildren().add(lbl_Title);

			if (_Closable)
			{
				// ATabButton attaches no stylesheet of its own — this widget's
				// cascading TabButtonSkin sheet styles it at the strip's
				// variant; the additional a-tab-close class (with the skin's
				// compound .a-tab-button.a-tab-close rules) flattens the
				// Modena chrome.
				btn_Close = new ATabButton("×");
				btn_Close.getStyleClass().add("a-tab-close");
				// The old close face was a non-focusable graphic; keep it out
				// of Tab traversal so it does not precede real controls.
				btn_Close.setFocusTraversable(false);
				// Consume press + click + drag so the close neither selects
				// the tab nor arms a drag-reorder on the host face (dragged
				// events are retargeted to the press node, so consuming here
				// keeps the whole gesture off the face's handlers).
				btn_Close.setOnMousePressed(MouseEvent::consume);
				btn_Close.setOnMouseClicked(MouseEvent::consume);
				btn_Close.setOnMouseDragged(MouseEvent::consume);
				btn_Close.setOnAction(__Event ->
				{
					__Event.consume();
					if (OnCloseHandler != null)
						OnCloseHandler.run();
				});
				getChildren().add(btn_Close);
			}

			// Primary click only — secondary/middle clicks must not select.
			setOnMouseClicked(__Event ->
			{
				if (__Event.getButton() == MouseButton.PRIMARY)
					FireAction();
			});

			// Manual pressed feedback (filters run before the drag handlers,
			// which need their own onMousePressed). Pressing the close button
			// must not light up the face.
			addEventFilter(MouseEvent.MOUSE_PRESSED, __Event ->
			{
				if (__Event.getButton() == MouseButton.PRIMARY && __Event.getTarget() != btn_Close)
					pseudoClassStateChanged(PRESSED, true);
			});
			addEventFilter(MouseEvent.MOUSE_RELEASED, __Event -> pseudoClassStateChanged(PRESSED, false));
		}

		/**
		 * Register the activation action (select this tab, or request a new
		 * one for the {@code "+"} affix).
		 *
		 * @param _OnAction invoked on primary click or Enter/Space
		 */
		void SetOnAction(Runnable _OnAction)
		{
			OnActionHandler = _OnAction;
		}

		/**
		 * Register the close action. Ignored on faces created without a
		 * close button (e.g. the {@code "+"} affix).
		 *
		 * @param _OnClose invoked when the user clicks the close button
		 */
		void SetOnClose(Runnable _OnClose)
		{
			OnCloseHandler = _OnClose;
		}

		/**
		 * Mark this face as the selected (active) tab. The {@code :selected}
		 * pseudo-class drives the brighter selected skin; unselecting
		 * restores the inactive tab look.
		 *
		 * @param _Selected true to select, false to deselect
		 */
		void SetSelected(boolean _Selected)
		{
			pseudoClassStateChanged(SELECTED, _Selected);
		}

		/**
		 * Change the tab label.
		 *
		 * @param _Title the new label
		 */
		void SetTitle(String _Title)
		{
			lbl_Title.setText(_Title);
		}

		/**
		 * Cap this face's height. Used by the {@link ETabButtonVariant#SMALL}
		 * variant to force the tab strip shorter than the NORMAL main-tab
		 * strip; the {@link Label} inside will ellipsize if the text exceeds
		 * the available height.
		 *
		 * @param _MaxHeight the maximum height in pixels
		 */
		void SetMaxFaceHeight(double _MaxHeight)
		{
			setMaxHeight(_MaxHeight);
		}

		/** Run the activation action if one is registered. */
		private void FireAction()
		{
			if (OnActionHandler != null)
				OnActionHandler.run();
		}
	}
}
