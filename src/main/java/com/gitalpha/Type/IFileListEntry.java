package com.gitalpha.Type;

/**
 * Minimal contract for a row in a read-only file-change list (rendered by
 * {@link com.gitalpha.UI.GitDirProjectManager.ImmutableChangesWidget}): an
 * entry exposes its path and change status so the shared cell factory can
 * render it without knowing the concrete type (stash files, commit files, ...).
 */
public interface IFileListEntry
{
	/** @return the file path (relative to the repository root) */
	String GetPath();

	/** @return the change status of this file */
	EFileChangeStatus GetStatus();
}
