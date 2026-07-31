package com.gitalpha.Engine;

/**
 * Callback interface for git operation completion.
 * <p>
 * When enqueuing a git operation via {@link GitOperator#RunGitOp(List, ERefreshPolicy, IGitOperationCallback)},
 * the provided callback is collected and fired after the entire batch of queued
 * operations completes:
 * <ul>
 *   <li>If ALL operations in the batch succeed, a refresh runs and each callback
 *       receives {@code (true, null, fresh GitDir)}.</li>
 *   <li>If ANY operation fails, no refresh runs. Each callback receives
 *       its own success/failure status. Successful callers still get
 *       {@code (true, null, possibly-stale GitDir)}.</li>
 * </ul>
 *
 * @see GitOperator#RunGitOp(List, ERefreshPolicy, IGitOperationCallback)
 */
@FunctionalInterface
public interface IGitOperationCallback
{
	/**
	 * Called after the batch of queued operations completes (or aborts).
	 *
	 * @param _OperationSucceeded true if this specific operation's git command exited with code 0
	 * @param _ErrorMessage       null if {@code _OperationSucceeded} is true, or an error description
	 * @param _GitDirTarget       the GitDir (may have stale data if the batch failed)
	 */
	void OnCompleted(boolean _OperationSucceeded, String _ErrorMessage, GitDir _GitDirTarget);
}
