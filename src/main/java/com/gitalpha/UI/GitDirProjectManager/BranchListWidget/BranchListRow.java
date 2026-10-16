package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

/**
 * One row in the branch list view: a section label, a namespace header, or
 * a branch. Holds the prebuilt display text (re-emitted on every rebuild,
 * so namespace arrows track collapse state), the entry payload the click
 * handlers instanceof-check, an optional tooltip slot, and the tree depth
 * used for the left indent. Cells render the entry's tooltip, so rows
 * emitted by {@link BranchListWidget} pass null for this slot.
 *
 * @param Kind    the row shape
 * @param Text    the rendered display text
 * @param Entry   the entry this row renders (never null for emitted rows)
 * @param Tooltip reserved tooltip text — the cell reads
 *                {@link BranchListEntry#GetTooltip()} instead (null for
 *                emitted rows)
 * @param Depth   tree depth level for indentation (0 = side top level)
 */
public record BranchListRow(RowKind Kind, String Text, BranchListEntry Entry, String Tooltip, int Depth)
{
	/** The three row shapes the list can render. */
	public enum RowKind
	{
		/** Bold muted section label ("Local" / "Remote") — inert */
		SECTION,
		/** Namespace-tree header with collapse arrow — click toggles */
		NAMESPACE,
		/** Branch row — selectable when its entry opts in */
		BRANCH
	}
}
