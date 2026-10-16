package com.gitalpha.UI.GitDirProjectManager.BranchListWidget;

/**
 * Namespace header entry — a collapsible segment of the branch tree
 * ({@code origin}, {@code feature}, ...). Not selectable (base default
 * {@code false}); a primary click on its row tells the widget to fold/unfold
 * the subtree keyed by {@link #GetFullPath()}.
 * <p>
 * The display text carries the toggle arrow ({@code ▾} / {@code ▸}) so the
 * row re-renders correctly when the widget rebuilds after a toggle.
 */
public class BranchListEntry_Namespace extends BranchListEntry
{
	/** Unicode right-pointing triangle shown on collapsed headers */
	private static final String ARROW_COLLAPSED = "\u25B8"; // "▸"
	/** Unicode down-pointing triangle shown on expanded headers */
	private static final String ARROW_EXPANDED = "\u25BE"; // "▾"

	private final String Segment;
	private final String FullPath;
	private final int Depth;
	private final boolean IsExpanded;

	/**
	 * @param _Segment   this level's path segment (header text)
	 * @param _FullPath  full path from the side root (the collapse key)
	 * @param _Depth     tree depth for indentation
	 * @param _Expanded  current expansion state (drives the arrow)
	 */
	public BranchListEntry_Namespace(String _Segment, String _FullPath, int _Depth, boolean _Expanded)
	{
		Segment = _Segment;
		FullPath = _FullPath;
		Depth = _Depth;
		IsExpanded = _Expanded;
	}

	/** @return this level's path segment (header text without the arrow) */
	public String GetSegment()
	{
		return Segment;
	}

	/** @return full path from the side root — the collapse key */
	public String GetFullPath()
	{
		return FullPath;
	}

	/** @return tree depth for indentation */
	public int GetDepth()
	{
		return Depth;
	}

	/** @return true when the subtree under this header is visible */
	public boolean IsExpanded()
	{
		return IsExpanded;
	}

	/** Headers are toggled by click, never emitted via {@code SetOnSelect}. */
	@Override
	public boolean IsSelectable()
	{
		return false;
	}

	/** Arrow + segment, e.g. {@code ▾ feature}. */
	@Override
	public String GetDisplayText()
	{
		return (IsExpanded ? ARROW_EXPANDED : ARROW_COLLAPSED) + " " + Segment;
	}
}
