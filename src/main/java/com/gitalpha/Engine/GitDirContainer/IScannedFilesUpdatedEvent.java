package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Type.FileChange;

import java.util.List;

/**
 * Event fired after a GitDir refresh when one or more existing
 * {@link FileChange} entries had their scanned mtime updated.
 * <p>
 * During the diff-merge phase, preserved entries (those that already existed
 * before the refresh) get their {@code ScannedModified} timestamp advanced
 * to the current scan. This event notifies listeners that the scan timestamp
 * has changed, allowing downstream cache invalidation or UI updates that
 * depend on knowing when a file was last observed.
 * <p>
 * The event carries only the entries whose scan timestamp was refreshed,
 * not the full list of changes. New or removed entries are not included.
 * Listeners are held via weak references by {@link com.gitalpha.Engine.AlphaEngine}
 * and do not need to unsubscribe.
 */
public interface IScannedFilesUpdatedEvent
{
	/**
	 * Called when one or more existing FileChange entries had their scanned mtime updated.
	 *
	 * @param _UpdatedFiles the list of FileChange entries whose {@code ScannedModified}
	 *                      was refreshed during the diff-merge phase; never null but may be empty
	 */
	void Event(List<FileChange> _UpdatedFiles);
}
