package com.gitalpha.UI.Components;

import javafx.scene.Node;

/**
 * Callback fired when the user clicks the {@code "+"} affix of a modifiable
 * {@link ATabWidget}. The widget does not create content itself — the
 * listener responds by calling {@link ATabWidget#AddTab} with the new view.
 * Listeners are held as weak references by the widget and pruned on
 * broadcast, matching the event-list pattern used by
 * {@link com.gitalpha.Theme.ThemeManager}.
 */
public interface INewTabRequestEvent
{
	/**
	 * Called on the FX thread when the user requests a new tab.
	 */
	void Event();
}
