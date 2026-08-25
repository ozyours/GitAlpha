package com.gitalpha.UI.Components;

import javafx.scene.Node;

/**
 * Callback fired when the active tab changes in an {@link ATabWidget}
 * (or the deprecated {@link ASubTabPane}). Listeners are held as weak
 * references by the pane and pruned on broadcast, matching the event-list
 * pattern used by {@link com.gitalpha.Theme.ThemeManager} and
 * {@link com.gitalpha.Engine.AlphaEngine}.
 */
public interface ISubTabSelectionEvent
{
	/**
	 * Called on the FX thread when the selected tab changes.
	 *
	 * @param _Index   the zero-based index of the newly selected tab
	 * @param _Content the content node now shown in the pane
	 */
	void Event(int _Index, Node _Content);
}
