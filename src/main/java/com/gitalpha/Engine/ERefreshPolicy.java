package com.gitalpha.Engine;

/**
 * Controls what happens after a git operation completes.
 * Ordered by increasing side-effect: NO_REFRESH < REFRESH_DATA_ONLY < REFRESH_AND_UPDATE_UI.
 * When multiple operations are batched in the queue, the maximum policy wins.
 */
public enum ERefreshPolicy
{
	/**
	 * Run the git command and do nothing else.
	 * Use for batch operations where the caller will trigger a refresh manually later.
	 */
	NO_REFRESH,

	/**
	 * After the git command completes, refresh the internal GitDir state
	 * (branches, changed files) but do not broadcast any UI update event.
	 * Use for background or silent operations.
	 */
	REFRESH_DATA_ONLY,

	/**
	 * After the git command completes, refresh internal GitDir state,
	 * save the session, and broadcast a UI refresh event so all widgets update.
	 * Use for user-visible operations (stage, unstage, checkout, commit, etc.).
	 */
	REFRESH_AND_UPDATE_UI
}
