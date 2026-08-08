package com.gitalpha.Engine;

/**
 * A unit of git work queued on a {@link GitOperator}'s single runner thread.
 * <p>
 * Implementations run exclusively on the runner thread, which serializes them
 * against every other queued operation and the refresh — the queue is the lock
 * that prevents git mutations from interleaving (e.g. a multi-step stash rename
 * must not be interrupted by a stage/unstage or commit). Tasks must not touch
 * the UI; they report failure by throwing an exception.
 */
public interface IGitTask
{
	/**
	 * Executes the task's git work on the runner thread.
	 *
	 * @throws Exception when the work fails; the queue records the message as
	 *                   the operation error (a failed task does not by itself
	 *                   trigger a post-batch refresh)
	 */
	void Execute() throws Exception;
}