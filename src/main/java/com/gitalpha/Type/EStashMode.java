package com.gitalpha.Type;

import java.util.List;

/**
 * Stash push modes for the Save operation: each mode maps to the flags
 * appended to {@code git stash push}. The dropdown shows each entry's scope
 * (what gets stashed) only — the former "{@code : <mode>}" suffix naming the
 * mode was removed from the display text. Default appends none.
 */
public enum EStashMode
{
	Default("Tracked", List.of()),
	IncludeUntracked("Tracked, Untracked", List.of("--include-untracked")),
	IncludeAll("Tracked, Untracked, Ignore", List.of("--all")),
	KeepIndex("Unstaged (Tracked)", List.of("--keep-index")),
	KeepIndexIncludeUntracked("Unstaged (Tracked, Untracked)", List.of("--keep-index", "--include-untracked"));

	private final String DisplayText;
	private final List<String> Args;

	EStashMode(String _DisplayText, List<String> _Args)
	{
		DisplayText = _DisplayText;
		Args = _Args;
	}

	/**
	 * Git flags appended to {@code git stash push} for this mode
	 */
	public List<String> GetArgs()
	{
		return Args;
	}

	/**
	 * Scope-only display text for the mode dropdown: the former full text
	 * ("{@code <scope>: <mode>}") was trimmed by removing the
	 * "{@code : <mode>}" suffix.
	 */
	@Override
	public String toString()
	{
		return DisplayText;
	}
}