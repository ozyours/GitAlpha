package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

import com.gitalpha.Type.GitBranch;

/**
 * Branch entry — the only selectable row kind (its base
 * {@link BranchListEntry#IsSelectable()} default is {@code false}; this
 * class overrides it to {@code true} so the widget reports it through
 * {@code SetOnSelect} on primary click / Enter).
 * <p>
 * Display format: {@code <branch name> (<unpushed commits>) (<linked branch>)}
 * e.g. {@code master (2) (origin/master)} — each segment is gated by its
 * argument: the count renders only when {@code ShowUnpushed} and the branch
 * is ahead of its upstream (reads {@link GitBranch#UnpushedCommits()}, filled
 * by the refresh's for-each-ref track query — never a second copy), the
 * linked branch only when {@code ShowLinkedBranch} and the branch actually
 * has an upstream.
 */
public class BranchListEntry_Branch extends BranchListEntry
{
	private final GitBranch Branch;
	private final boolean ShowUnpushed;
	private final boolean ShowLinkedBranch;

	/**
	 * @param _Branch           the branch this row renders (carries both the
	 *                          upstream link and its unpushed count)
	 * @param _ShowUnpushed     render the {@code (<count>)} segment when ahead
	 * @param _ShowLinkedBranch render the {@code (<upstream>)} segment when linked
	 */
	public BranchListEntry_Branch(GitBranch _Branch, boolean _ShowUnpushed,
			boolean _ShowLinkedBranch)
	{
		Branch = _Branch;
		ShowUnpushed = _ShowUnpushed;
		ShowLinkedBranch = _ShowLinkedBranch;
	}

	/** @return the underlying branch data */
	public GitBranch Branch()
	{
		return Branch;
	}

	/** Branch rows are the selectable kind. */
	@Override
	public boolean IsSelectable()
	{
		return true;
	}

	/**
	 * Leaf name first (the namespace is already the header above when
	 * nested), then the optional unpushed and linked segments.
	 *
	 * @return the rendered row text, e.g. {@code master (2) (origin/master)}
	 */
	@Override
	public String GetDisplayText()
	{
		StringBuilder __Text = new StringBuilder(Branch.Name());
		if (ShowUnpushed && Branch.UnpushedCommits() > 0)
			__Text.append(" (").append(Branch.UnpushedCommits()).append(")");
		if (ShowLinkedBranch && Branch.HasUpstream())
			__Text.append(" (").append(Branch.Upstream()).append(")");
		return __Text.toString();
	}

	/** @return the full path (differs from the leaf under a namespace header) */
	@Override
	public String GetTooltip()
	{
		return Branch.FullPath();
	}
}
