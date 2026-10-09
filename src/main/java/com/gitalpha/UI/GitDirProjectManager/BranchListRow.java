package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Type.GitBranch;

/**
 * One row in a branch tree list (the picker popover or a branch-manager
 * column): a section label ("Local"/"Remote"), a namespace-tree header
 * (collapsible), or a branch entry. Exactly one payload field
 * ({@code Text} or {@code Branch}) is non-null for a given {@link RowKind}.
 * {@code Path} is the header's full namespace path (the collapse key; null
 * for other kinds) and {@code Depth} is the tree level used for the left
 * indent (0 = section top level).
 * <p>
 * Shared by {@link BranchPickerPopover} and {@link BranchManagerWidget} so
 * both render the same row shapes; each consumer keeps its own cell factory
 * (the popover activates branches, the manager appends link suffixes).
 *
 * @param Kind   the row shape
 * @param Text   section label or namespace segment (null for branches)
 * @param Branch the branch the row renders (null for label/header rows)
 * @param Path   full namespace path for headers (collapse key)
 * @param Depth  tree depth level for indentation
 */
record BranchListRow(RowKind Kind, String Text, GitBranch Branch, String Path, int Depth)
{
	/** The three row shapes a branch tree can render. */
	enum RowKind
	{
		/** Bold muted section label ("Local" / "Remote") — inert */
		SECTION,
		/** Namespace-tree header with collapse arrow */
		NAMESPACE,
		/** Selectable branch row */
		BRANCH
	}

	/**
	 * Build a section label row.
	 *
	 * @param _Label the label text ("Local" / "Remote")
	 * @return the label row (depth 0)
	 */
	static BranchListRow Section(String _Label)
	{
		return new BranchListRow(RowKind.SECTION, _Label, null, null, 0);
	}

	/**
	 * Build a namespace-tree header row.
	 *
	 * @param _Segment  the single path segment shown as the header text
	 * @param _FullPath the full namespace path (collapse key)
	 * @param _Depth    tree depth level for indentation
	 * @return the header row
	 */
	static BranchListRow Namespace(String _Segment, String _FullPath, int _Depth)
	{
		return new BranchListRow(RowKind.NAMESPACE, _Segment, null, _FullPath, _Depth);
	}

	/**
	 * Build a branch row.
	 *
	 * @param _Entry the branch the row renders
	 * @param _Depth tree depth level for indentation (0 = root-level branch)
	 * @return the branch row
	 */
	static BranchListRow Branch(GitBranch _Entry, int _Depth)
	{
		return new BranchListRow(RowKind.BRANCH, null, _Entry, null, _Depth);
	}

	/** @return true when this row is a selectable branch */
	boolean IsBranch()
	{
		return Kind == RowKind.BRANCH;
	}
}
