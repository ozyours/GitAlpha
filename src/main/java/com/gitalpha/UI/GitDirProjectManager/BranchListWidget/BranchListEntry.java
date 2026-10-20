package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

/**
 * Base class of every branch-list row entry. Provides the virtual methods
 * subclasses override to specialize behavior per row kind — the key one is
 * {@link #IsSelectable()}, whose base default is {@code false} so only
 * kinds that opt in (currently {@link BranchListEntry_Branch}) fire the
 * widget's {@code SetOnSelect} callback on click.
 * <p>
 * Derived kinds: {@link BranchListEntry_Branch} (selectable branch),
 * {@link BranchListEntry_Namespace} (collapsible header),
 * {@link BranchListEntry_Section} (Local/Remote category label).
 */
public abstract class BranchListEntry
{
	/**
	 * Whether a click on this row counts as a selection: the widget saves
	 * the entry and fires {@code SetOnSelect} only when this returns true.
	 * Default {@code false}; overridden to {@code true} by branch entries.
	 *
	 * @return true when the entry is selectable
	 */
	public boolean IsSelectable()
	{
		return false;
	}

	/** @return the rendered row text shown in the list cell */
	public abstract String GetDisplayText();

	/** @return optional tooltip for the row, or null for none */
	public String GetTooltip()
	{
		return null;
	}
}
