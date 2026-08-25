package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite tab panel: a row of flat {@link ATabButton}s above a
 * {@link StackPane} content swap, all styled by a single tab skin that
 * cascades from the root. Owns the selection model, the palette-change
 * listener, and the stylesheet attachment so consumers just call
 * {@link #AddTab} / {@link #SelectTab}.
 * <p>
 * One stylesheet (baked by {@code SubTabButtonSkin}) is attached to this
 * root {@link VBox} once; its descendant selectors ({@code .a-tab-header},
 * {@code .a-tab-button}, {@code .a-tab-content}) cascade to every child, so
 * the header strip, buttons and hairline re-bake together on palette
 * switches. {@link ATabButton} also bakes its own sheet (for standalone
 * use); inside the pane both apply, which is harmless.
 * <p>
 * Selection listeners are held as {@link WeakReference}s and pruned on
 * broadcast, matching the event-list pattern used by
 * {@link com.gitalpha.Theme.ThemeManager} and
 * {@link com.gitalpha.Engine.AlphaEngine}. The widget itself is a
 * {@link VBox} in the scene graph, so the weak ref to {@code this} (the
 * {@link IThemeChangeEvent} registration) survives as long as the widget
 * is in the scene.
 *
 * @deprecated Superseded by {@link ATabWidget} in non-modifiable mode, which
 *             behaves identically (fixed tabs, selection model, single
 *             cascading stylesheet) and additionally supports modifiable
 *             operation. Use {@link ATabWidget#ATabWidget(boolean)} with
 *             {@code false} instead.
 */
@Deprecated
public class ASubTabPane extends VBox implements IThemeChangeEvent
{
	/** The header row holding the tab buttons */
	private final HBox Header;
	/** The content stack: whichever child was last set via setAll is visible */
	private final StackPane Content;
	/** All tab buttons, in insertion order */
	private final List<ATabButton> TabButtons;
	/** All content nodes, parallel to TabButtons */
	private final List<Node> TabContents;
	/** The currently selected tab index, or -1 if nothing is selected */
	private int SelectedIndex = -1;
	/** Selection listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<ISubTabSelectionEvent>> SelectionListeners;

	/**
	 * Create an empty tab panel. Register selection listeners via
	 * {@link #AddSelectionEvent} before or after adding tabs; the
	 * listener list is pruned of dead references on every broadcast.
	 */
	public ASubTabPane()
	{
		super();

		TabButtons = new ArrayList<>();
		TabContents = new ArrayList<>();
		SelectionListeners = new ArrayList<>();

		Header = new HBox();
		Header.getStyleClass().add("a-tab-header");

		Content = new StackPane();
		Content.getStyleClass().add("a-tab-content");

		getChildren().addAll(Header, Content);
		// Content must grow to fill the VBox; the header stays at its natural height
		VBox.setVgrow(Content, Priority.ALWAYS);

		ApplySkin();
		// Weak-reference registration: cleanup is automatic when the widget leaves the scene graph
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Add a tab to the panel. The content node is kept alive for the
	 * panel's lifetime (never removed from the scene graph when deselected;
	 * the StackPane shows whichever child was last swapped in). The first
	 * tab added is selected automatically; subsequent tabs are not selected
	 * until {@link #SelectTab} is called.
	 *
	 * @param _Title   the tab label
	 * @param _Content the view to show when this tab is selected
	 * @return the zero-based index of the newly added tab
	 */
	public int AddTab(String _Title, Node _Content)
	{
		int __Index = TabButtons.size();
		ATabButton __Button = new ATabButton(_Title);
		__Button.setOnAction(__Event -> SelectTab(__Index));
		TabButtons.add(__Button);
		Header.getChildren().add(__Button);
		TabContents.add(_Content);

		// Select the first tab automatically so the content is never empty
		// after the first AddTab; later tabs require an explicit SelectTab.
		if (__Index == 0)
			SelectTab(0);

		return __Index;
	}

	/**
	 * Switch the selected tab by index. Updates the button selected states,
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

		// Update button selected states
		for (int __I = 0; __I < TabButtons.size(); __I++)
			TabButtons.get(__I).SetSelected(__I == _Index);

		// Swap content
		SelectedIndex = _Index;
		Node __Content = TabContents.get(_Index);
		Content.getChildren().setAll(__Content);

		// Fire listeners (prune dead refs inline)
		int __I = 0;
		while (__I < SelectionListeners.size())
		{
			WeakReference<ISubTabSelectionEvent> __Ref = SelectionListeners.get(__I);
			ISubTabSelectionEvent __Event = __Ref.get();
			if (__Event != null)
			{
				__Event.Event(_Index, __Content);
				__I++;
			}
			else
				SelectionListeners.remove(__I);
		}
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
	 * @return the number of tabs in the panel
	 */
	public int GetTabCount()
	{
		return TabButtons.size();
	}

	/**
	 * Register a selection listener. Held weakly, so a listener that is
	 * garbage-collected without unsubscribing is pruned on the next
	 * broadcast.
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
	 * Theme-change push: re-bake the skin with the new palette's colors.
	 * The single stylesheet on this VBox cascades to all descendants, so
	 * the header strip, buttons and hairline re-bake together.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin();
	}

	/**
	 * Replace the inline skin stylesheet with one baked from the active
	 * palette. The data-URI URL changes whenever the colors do, so JavaFX
	 * re-parses the new skin. The selector chains target the descendant
	 * classes ({@code .a-tab-header}, {@code .a-tab-button},
	 * {@code .a-tab-content}) so the single sheet covers all three nodes.
	 */
	private void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetSubTabButtonStylesheets());
	}
}