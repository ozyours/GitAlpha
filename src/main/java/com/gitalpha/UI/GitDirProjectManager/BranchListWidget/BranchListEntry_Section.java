package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

/**
 * Section label entry ({@code Local} / {@code Remote}) — bold muted row
 * that separates the two sides of a list showing both. Not selectable
 * (base default {@code false}); shown only when both sides have visible
 * content (a single side needs no divider).
 */
public class BranchListEntry_Section extends BranchListEntry
{
	private final String Title;

	/**
	 * @param _Title the section label text ("Local" / "Remote")
	 */
	public BranchListEntry_Section(String _Title)
	{
		Title = _Title;
	}

	/** @return the section title ("Local" / "Remote") as rendered */
	@Override
	public String GetDisplayText()
	{
		return Title;
	}
}
