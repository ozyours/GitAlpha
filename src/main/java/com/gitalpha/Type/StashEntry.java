package com.gitalpha.Type;

import java.util.Collections;
import java.util.List;

/**
 * Data holder for a single stash entry parsed from {@code git stash list}.
 * Each entry carries its stash index (e.g. {@code stash@{0}}), the branch
 * it was created on, and an optional description (the stash message).
 * File lists and diffs are fetched on demand when the user selects a stash.
 */
public class StashEntry
{
	/** Zero-based index in the stash list (maps to {@code stash@{index}}) */
	private final int Index;
	/** Branch the stash was created on (from the reflog summary line) */
	private final String Branch;
	/** Optional stash description (the {@code -m} message), may be empty */
	private final String Description;
	/** Full raw line from {@code git stash list}, kept for display/debug */
	private final String RawLine;

	/** Parsed file paths from {@code git stash show --name-status}; populated on demand. Volatile for cross-thread visibility. */
	private volatile List<StashFile> Files;

	/**
	 * Create a stash entry from parsed {@code git stash list} output.
	 *
	 * @param _Index       zero-based stash index (maps to {@code stash@{index}})
	 * @param _Branch      branch the stash was created on (may be empty if unparseable)
	 * @param _Description stash message ({@code -m} argument), may be empty
	 * @param _RawLine     original unmodified line from {@code git stash list}
	 */
	public StashEntry(int _Index, String _Branch, String _Description, String _RawLine)
	{
		Index = _Index;
		Branch = _Branch;
		Description = _Description;
		RawLine = _RawLine;
	}

	/** @return zero-based index that identifies this stash within its list */
	public int GetIndex() { return Index; }

	/** @return branch the stash was created on, or empty if unavailable */
	public String GetBranch() { return Branch; }

	/** @return stash description message, or empty if none was provided */
	public String GetDescription() { return Description; }

	/** @return raw output line from {@code git stash list}, useful for display or debug */
	public String GetRawLine() { return RawLine; }

	/** The stash ref used in git commands, e.g. {@code stash@{0}} */
	public String GetStashRef() { return "stash@{" + Index + "}"; }

	/** @return unmodifiable file list for this stash, or null if not yet populated */
	public List<StashFile> GetFiles() { return Files != null ? Collections.unmodifiableList(Files) : null; }

	/** Cache the parsed file list so subsequent reads avoid re-running git. */
	public void SetFiles(List<StashFile> _Files) { Files = _Files != null ? List.copyOf(_Files) : null; }

	@Override
	public String toString()
	{
		if (Description != null && !Description.isEmpty())
			return "stash@{" + Index + "}: " + Description;
		return "stash@{" + Index + "}: " + Branch;
	}

	/**
	 * A single file entry inside a stash, parsed from
	 * {@code git stash show --name-status}.
	 */
	public static record StashFile(String Path, EFileChangeStatus Status) {}
}
