package com.gitalpha.UI.Components;

import javafx.scene.Node;

/**
 * Callback fired after the user closed a tab of a modifiable
 * {@link ATabWidget}. The tab is already removed from the widget when this
 * fires; the listener exists so the consumer can release resources tied to
 * the closed view. Listeners are held as weak references by the widget and
 * pruned on broadcast, matching the event-list pattern used by
 * {@link com.gitalpha.Theme.ThemeManager}.
 */
public interface ITabCloseEvent
{
	/**
	 * Called on the FX thread after a tab was closed.
	 *
	 * @param _Index   the zero-based position the tab occupied before removal
	 * @param _Content the content node that was hosted by the closed tab
	 */
	void Event(int _Index, Node _Content);
}
