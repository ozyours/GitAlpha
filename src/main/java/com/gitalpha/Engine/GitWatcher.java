package com.gitalpha.Engine;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base class for the repository change watchers: the shared contract every
 * detection mechanism implements, so a {@link GitDir} can be given whichever
 * mechanism suits the platform and the workload.
 * <p>
 * A watcher exists to answer one question — "did the working tree change?" — and
 * to report it through the change callback it was constructed with. How it
 * answers is the subclass's business: {@link java.nio.file.WatchService}
 * registrations, a polling scan, or anything else. This class owns only the
 * parts that do not depend on the mechanism:
 * <ul>
 *   <li>the {@link GitDir} being watched and the callback to fire,</li>
 *   <li>the start / stop / running lifecycle contract,</li>
 *   <li>a shared trailing-edge debounce timer, because every mechanism sees
 *       bursts (a checkout touching hundreds of files) rather than isolated
 *       events, and firing once per burst is what the refresh pipeline expects.</li>
 * </ul>
 * <p>
 * <b>Contract for subclasses:</b> {@code Start()} acquires whatever OS or
 * scheduling resources the mechanism needs and is a no-op when it cannot watch
 * (missing root, disabled by setting). {@code Stop()} releases all of them,
 * cancels any pending debounce, and is safe to call more than once and from any
 * thread. {@code IsRunning()} reports whether observation is currently active,
 * derived from live state rather than a flag that can drift. Change
 * notifications go through {@link #FireChange()} (immediate, on the caller's
 * thread) or {@link #ScheduleChange()} (debounced, on the shared debounce
 * daemon thread) — never assume the JavaFX thread; the callback is responsible
 * for marshalling to the UI thread.
 * <p>
 * Subclasses own their detection thread; this class provides no thread of its
 * own except the lazily opened debounce timer.
 */
public abstract class GitWatcher
{
	/**
	 * Name of the daemon thread that runs the shared debounce timer. Daemon, so
	 * a watcher that is never stopped cannot keep the JVM alive.
	 */
	private static final String DEBOUNCE_THREAD_NAME = "GitWatcher-debounce";

	/**
	 * Default quiet period (ms) after the last detected change before the
	 * callback fires. A burst of filesystem activity — checkout, rebase, mass
	 * save — therefore produces one refresh instead of one per file.
	 */
	protected static final long DEFAULT_DEBOUNCE_MILLIS = 500;

	/**
	 * Create a stopped watcher. No resources are acquired here: nothing is
	 * watched, no thread exists, and the callback cannot fire until
	 * {@link #Start()} is called.
	 *
	 * @param _GitDirTarget the repository to watch; may be null, and its root is
	 *                      read through {@link #GetWatchedRoot()} at
	 *                      {@link #Start()} time rather than here, so a
	 *                      session-restored {@link GitDir} whose path is filled
	 *                      in after construction still works
	 * @param _OnChange     callback fired when changes are detected; may be null,
	 *                      in which case notifications are discarded
	 */
	protected GitWatcher(GitDir _GitDirTarget, Runnable _OnChange)
	{
		GitDirTarget = _GitDirTarget;
		OnChange = _OnChange;
	}

	/** The repository this watcher reports changes for */
	private final GitDir GitDirTarget;

	/** Consumer notified by {@link #FireChange()}; null-tolerant */
	private final Runnable OnChange;

	/** Debounce scheduler; null while stopped (or before the first {@link #Start()}) */
	private volatile ScheduledExecutorService DebounceTimer;

	/**
	 * The pending debounced notification, if a debounce window is open. Volatile
	 * because the debounce task clears it on its own thread while callers may
	 * cancel it.
	 */
	private volatile ScheduledFuture<?> PendingDebounce;

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Begin observing the repository. Acquires the mechanism's resources and
	 * returns promptly; long-running setup (a recursive directory walk, for
	 * instance) and the detection loop itself belong on the subclass's own thread
	 * so the caller is never blocked.
	 * <p>
	 * No-op when the mechanism cannot watch the repository (invalid or missing
	 * root, disabled by a setting) — implementations must not throw in that case,
	 * and {@link #IsRunning()} then stays false.
	 */
	public abstract void Start();

	/**
	 * Stop observing and release every resource. Idempotent and callable from any
	 * thread, including on a watcher that never started; after it returns, no
	 * further change notification may be delivered. Implementations that debounce
	 * must call {@link #CloseDebounceTimer()} here so a pending window cannot fire
	 * a stale change.
	 */
	public abstract void Stop();

	/**
	 * @return true if the watcher is currently observing the repository, derived
	 *         from live mechanism state (not a flag that can drift out of sync, so
	 *         a watcher that lost its directory reports false without an explicit
	 *         {@link #Stop()})
	 */
	public abstract boolean IsRunning();

	// -------------------------------------------------------------------------
	// Shared state
	// -------------------------------------------------------------------------

	/**
	 * @return the {@link GitDir} this watcher reports changes for, or null when it
	 *         was constructed without a target
	 */
	public GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	/**
	 * Resolve the working-tree root to observe. Re-read on every call instead of
	 * cached, because a session-restored {@link GitDir} may still have no path when
	 * the watcher is constructed.
	 *
	 * @return the working-tree root, or null when there is no {@link GitDir} target
	 *         or its path has no parent — note that a target whose path is still
	 *         unset reports {@link GitDir#GetRepoRootPath()}'s empty path, so the
	 *         result is not necessarily an existing directory and callers must
	 *         validate it before watching
	 */
	protected final Path GetWatchedRoot()
	{
		return GitDirTarget == null ? null : GitDirTarget.GetRepoRootPath();
	}

	// -------------------------------------------------------------------------
	// Change notification
	// -------------------------------------------------------------------------

	/**
	 * Notify the consumer immediately, on the calling thread. Used by mechanisms
	 * that already coalesce their own events; no-op when no callback was supplied.
	 */
	protected final void FireChange()
	{
		Runnable __OnChange = OnChange;
		if (__OnChange != null)
			__OnChange.run();
	}

	/**
	 * Trailing-edge debounce: (re)schedule the change callback
	 * {@link #DEFAULT_DEBOUNCE_MILLIS} after the last detected change, so a burst
	 * yields one notification.
	 * <p>
	 * No-op until {@link #OpenDebounceTimer()} has been called, and after
	 * {@link #CloseDebounceTimer()}.
	 */
	protected final void ScheduleChange()
	{
		ScheduleChange(DEFAULT_DEBOUNCE_MILLIS);
	}

	/**
	 * Trailing-edge debounce with an explicit quiet period: cancels the previous
	 * pending notification and schedules a new one, so only the last event of a
	 * burst reaches the callback.
	 * <p>
	 * Safe to call from any thread and as often as events arrive; the callback
	 * runs on the shared {@code GitWatcher-debounce} daemon, never on the caller's
	 * thread. No-op when no debounce timer is open, so a mechanism that debounces
	 * must open it in {@link #Start()}; it is not synchronized against
	 * {@link #CloseDebounceTimer()}, so a mechanism must stop reporting once it has
	 * been stopped rather than racing {@link #Stop()}.
	 *
	 * @param _DelayMillis quiet period in milliseconds before the callback runs
	 */
	protected final void ScheduleChange(long _DelayMillis)
	{
		ScheduledExecutorService __Timer = DebounceTimer;
		if (__Timer == null)
			return;

		CancelScheduledChange();
		PendingDebounce = __Timer.schedule(() ->
		{
			PendingDebounce = null;
			FireChange();
		}, _DelayMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Drop the pending debounced notification, if any. A callback that has already
	 * started running is not interrupted. Called by {@link #CloseDebounceTimer()}
	 * so a stop cannot deliver a stale change.
	 */
	protected final void CancelScheduledChange()
	{
		ScheduledFuture<?> __Pending = PendingDebounce;
		PendingDebounce = null;
		if (__Pending != null)
			__Pending.cancel(false);
	}

	/**
	 * Open the shared debounce timer on first use; later calls are no-ops, so it is
	 * safe to call at the top of every {@link #Start()}. Mechanisms that use
	 * {@link #ScheduleChange()} must call this once from {@link #Start()} — a
	 * watcher that is constructed but never started therefore costs no thread.
	 * <p>
	 * Deliberately not synchronized: call it from the lifecycle path, not from the
	 * per-event hot path.
	 */
	protected final void OpenDebounceTimer()
	{
		if (DebounceTimer != null)
			return;

		DebounceTimer = Executors.newSingleThreadScheduledExecutor(__R ->
		{
			Thread __T = new Thread(__R, DEBOUNCE_THREAD_NAME);
			__T.setDaemon(true);
			return __T;
		});
	}

	/**
	 * Cancel any pending notification and shut the debounce timer down; safe to
	 * call when no timer was ever opened, and callable more than once (the
	 * reference is cleared, so a later {@link #ScheduleChange()} is a no-op). Call
	 * from {@link #Stop()} so the daemon thread and its queue are released and the
	 * timer can be reopened by a subsequent {@link #Start()}.
	 */
	protected final void CloseDebounceTimer()
	{
		CancelScheduledChange();

		ScheduledExecutorService __Timer = DebounceTimer;
		DebounceTimer = null;
		if (__Timer != null)
			__Timer.shutdownNow();
	}
}
