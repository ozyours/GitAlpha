package com.gitalpha.Engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polling {@link GitWatcher}: instead of watching the filesystem, it periodically
 * asks git itself — {@code git status --porcelain} — and reports a change when
 * the answer differs from the previous tick.
 * <p>
 * <b>Why poll:</b> a {@link java.nio.file.WatchService} keeps an OS directory
 * handle open per watched directory, and on Windows that leaves the project
 * folder in delete-pending state, so it cannot be renamed or deleted until the
 * watcher stops (see {@link GW_WatchService}). This mechanism holds no handle on
 * the working tree at all — only the short-lived git process of each tick — so
 * the repository stays freely movable. The price is a git process per interval
 * while a repository is open, plus latency: a tracked edit is seen up to one
 * interval late, an untracked file up to one full-scan period.
 * <p>
 * <b>What counts as a change:</b> git's verdict, not a file timestamp. Each tick
 * builds a fingerprint from the current {@code HEAD} (read straight from the
 * git directory, no process needed, so a branch switch on identical content is
 * still noticed) plus the porcelain status output, and compares it with the
 * previous one. Two fingerprints are kept because two commands are used:
 * <ul>
 *   <li><b>fast tick</b> — {@code status --porcelain -uno}: tracks staged and
 *       unstaged modifications, additions and deletions of <i>tracked</i> files,
 *       without walking untracked trees;</li>
 *   <li><b>full tick</b> — every {@link #FULL_SCAN_TICK_EVERY} ticks,
 *       {@code status --porcelain -uall}, the same command the refresh pipeline
 *       runs, so untracked files are picked up too.</li>
 * </ul>
 * Splitting the two keeps the common tick cheap in repositories with large
 * untracked or ignored trees (node_modules, build output): the refresh pipeline
 * pays for {@code -uall} once per user action, while polling would otherwise pay
 * for it every few seconds.
 * <p>
 * <b>Failures are skipped ticks, not errors:</b> a scan that exits non-zero, is
 * killed by {@link #SCAN_TIMEOUT_MILLIS}, or cannot start at all reports nothing
 * and keeps its baseline, so a repository mid-operation (rebase, checkout, lock
 * held by another tool) neither fires a spurious change nor loses a real one —
 * the next successful scan compares against the last good answer.
 * <p>
 * <b>No locks:</b> the child process runs with {@code GIT_OPTIONAL_LOCKS=0}, so a
 * status scan never takes {@code .git/index.lock} and cannot collide with a real
 * git operation running on the {@link GitOperator} runner.
 * <p>
 * <b>Notification:</b> the tick already coalesces bursts (one answer per
 * interval), so it calls {@link #FireChange()} directly rather than the base
 * class's trailing-edge debounce — {@link #OpenDebounceTimer()} is never called
 * and this watcher therefore owns no debounce thread. The callback runs on the
 * poll thread, never on the JavaFX thread, so it must marshal to the UI itself;
 * a null callback is tolerated, in which case notifications are dropped. A tick
 * in flight when {@link #Stop()} runs notices the cleared timer and drops its
 * notification, so stop-then-deliver cannot happen through that path (see
 * {@link #Stop()} for the residual race).
 * <p>
 * Polling is on by default: {@link #POLLER_ENABLED} is an opt-out switch read
 * once at class initialization. The interval is
 * {@code -Dgitalpha.poll.intervalMillis=<ms>} (default
 * {@link #DEFAULT_POLL_INTERVAL_MILLIS}).
 * <p>
 * Usage: construct with a {@link GitDir} and change callback, call {@link #Start()},
 * and {@link #Stop()} to release the scheduler. This is the mechanism {@link GitDir}
 * binds by default: {@link GitDir#OnOpen()} creates it on the first open of a
 * repository (not the constructors) and re-arms that same instance on later opens.
 * One line there swaps it to {@link GW_WatchService} — still present as the
 * event-driven alternative, just no longer bound — or to a future mechanism.
 */
public class GW_GitPoll extends GitWatcher
{
	/** Name of the daemon thread that drives the tick; daemon, so a leaked watcher cannot keep the JVM alive */
	private static final String POLL_THREAD_NAME = "GW_GitPoll";

	/** Name of the file whose content identifies the checked-out branch */
	private static final String HEAD_FILE_NAME = "HEAD";

	/**
	 * Default interval (ms) between ticks. A tick spawns a git process, so this is
	 * a cost knob as much as a latency knob: 3 s keeps changes made by other
	 * programs feeling prompt while holding the git-process rate low enough to go
	 * unnoticed.
	 */
	private static final long DEFAULT_POLL_INTERVAL_MILLIS = 3000;

	/**
	 * Interval between ticks, from {@code -Dgitalpha.poll.intervalMillis=<ms>}.
	 * Read once at class initialization, so the property must be on the command
	 * line; non-positive values fall back to the default. A tick shorter than the
	 * time a scan takes simply runs back to back (the scheduler is fixed-delay, so
	 * ticks never overlap).
	 */
	private static final long POLL_INTERVAL_MILLIS = ResolvePollInterval();

	/**
	 * Opt-out switch for the polling mechanism — polling runs unless the JVM was
	 * started with {@code -Dgitalpha.poll.enabled=false}. Read once at class
	 * initialization ({@code static final}), so setting it later has no effect;
	 * with polling off the watcher records no state and spawns no process.
	 */
	private static final boolean POLLER_ENABLED =
		Boolean.parseBoolean(System.getProperty("gitalpha.poll.enabled", "true"));

	/**
	 * One tick in this many runs the full status (untracked files included); the
	 * rest use the fast, untracked-free form. Five fast ticks per full scan is
	 * roughly 15 s of untracked latency at the default interval, against a 3 s
	 * latency for tracked edits.
	 */
	private static final int FULL_SCAN_TICK_EVERY = 5;

	/** Fast tick: tracked-only status — the cheap form used for most ticks */
	private static final List<String> STATUS_FAST = List.of("status", "--porcelain", "-uno");

	/** Full tick: same command the refresh pipeline uses, untracked files included */
	private static final List<String> STATUS_FULL = List.of("status", "--porcelain", "-uall");

	/**
	 * Upper bound (ms) on the wait for a git process to exit, applied by
	 * {@link #RunStatus(Path, List)} after its stdout has been drained. A run that
	 * exceeds it is destroyed in that method's {@code finally} block and the tick is
	 * skipped, so a slow git cannot park the poll loop for long.
	 * <p>
	 * The bound covers the exit wait only: the stdout read is a blocking pipe read
	 * that neither this timeout nor {@link #Stop()}'s interrupt can abort, so a
	 * child that stalls mid-output holds the poll thread until git exits on its own.
	 */
	private static final long SCAN_TIMEOUT_MILLIS = 15000;

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Scheduler that drives {@link #Tick()}; null while stopped (or before the first
	 * {@link #Start()}). Volatile because {@link #Stop()} clears it from an arbitrary
	 * thread while the poll thread reads it as its "still wanted" signal.
	 */
	private volatile ScheduledExecutorService PollTimer;

	/**
	 * Fingerprint seen on the previous fast tick, or null before the first one.
	 * Null means "not primed yet": the first result of each kind seeds the
	 * baseline instead of being reported as a change, so starting a watcher on an
	 * already-dirty repository does not fire a refresh of its own.
	 * <p>
	 * Volatile like {@link #PollTimer}: the poll thread is the only reader, but
	 * {@link #Stop()} clears it from an arbitrary thread.
	 */
	private volatile String LastFastFingerprint;

	/**
	 * Fingerprint seen on the previous full tick, or null before the first one.
	 * Kept separately from {@link #LastFastFingerprint} because the fast and full
	 * statuses legitimately differ (the full one lists untracked files) — a single
	 * baseline would report a change on every switch of tick kind.
	 */
	private volatile String LastFullFingerprint;

	/**
	 * Number of ticks performed since the last {@link #Start()}, used to pick the
	 * full-scan tick. Only touched on the poll thread, reset by {@link #Start()}
	 * before the scheduler exists; plain rather than volatile because that reset is
	 * published to the poll thread by the executor's own hand-off.
	 */
	private long TickIndex;

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	/**
	 * Create a stopped watcher. Both arguments are handed straight to
	 * {@link GitWatcher}: no process, thread or state is created here, and polling
	 * only begins on {@link #Start()}.
	 *
	 * @param _GitDirTarget the repository to poll; may be null or path-less at this
	 *                      point because the root is resolved at {@link #Start()}
	 *                      time and re-checked on every tick
	 * @param _OnChange     callback fired when git reports a different state; it
	 *                      runs on the poll thread, so it must marshal to the UI
	 *                      itself (unlike the WatchService mechanism it is not
	 *                      passed through a debounce timer); may be null, in which
	 *                      case notifications are discarded
	 */
	public GW_GitPoll(GitDir _GitDirTarget, Runnable _OnChange)
	{
		super(_GitDirTarget, _OnChange);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Start polling. Resolves the root path through {@link #GetWatchedRoot()} at
	 * call time, so a session-restored {@link GitDir} whose path is filled in
	 * after construction is handled correctly, and returns immediately: no git
	 * process runs at start-up, the first tick happens one interval later on the
	 * poll thread, never on the caller's.
	 * <p>
	 * No-op if the root path does not exist or is not a directory, or if the
	 * {@code -Dgitalpha.poll.enabled=false} opt-out is in effect (polling is
	 * otherwise on by default); {@link #IsRunning()} then stays false. Nothing is
	 * allocated in that case — no scheduler, no thread, no state.
	 * <p>
	 * Not idempotent: each call re-seeds the tick counter and baselines and creates
	 * a fresh scheduler, replacing {@link #PollTimer} without shutting the previous
	 * one down. A second call without an intervening {@link #Stop()} therefore
	 * leaves the old scheduler ticking with no reference left to cancel it (it is
	 * daemon, so it does not block JVM exit, but it keeps spawning git processes).
	 * Call it once per watcher; a {@link #Start()} after {@link #Stop()} is the
	 * supported re-arm.
	 */
	public void Start()
	{
		if (!POLLER_ENABLED)
			return;

		Path __RootPath = GetWatchedRoot();
		if (__RootPath == null || !Files.isDirectory(__RootPath))
			return;

		// Reset before the scheduler exists: the first tick of a new run must seed
		// a fresh baseline rather than compare against the previous run's state.
		TickIndex = 0;
		LastFastFingerprint = null;
		LastFullFingerprint = null;

		ScheduledExecutorService __Timer = Executors.newSingleThreadScheduledExecutor(__R ->
		{
			Thread __T = new Thread(__R, POLL_THREAD_NAME);
			__T.setDaemon(true);
			return __T;
		});

		PollTimer = __Timer;

		// Fixed delay, not fixed rate: a slow scan pushes the next tick back
		// instead of queueing them, so ticks can never pile up.
		__Timer.scheduleWithFixedDelay(this::Tick, POLL_INTERVAL_MILLIS, POLL_INTERVAL_MILLIS,
			TimeUnit.MILLISECONDS);
	}

	/**
	 * Stop polling and release the scheduler. Safe to call multiple times and from
	 * any thread, including on a watcher that never started: {@link #PollTimer} is
	 * cleared first, so {@link #IsRunning()} turns false before any teardown and an
	 * in-flight tick's notification check fails. The timer is then interrupted with
	 * {@code shutdownNow()}: a tick parked in the scan's exit wait throws
	 * {@link InterruptedException} and unwinds, and the process it was waiting on is
	 * destroyed in {@code RunStatus}'s {@code finally} block.
	 * <p>
	 * That suppression is best effort rather than a guarantee — {@code Stop()} can
	 * return while a tick sits between its {@link #PollTimer} read and
	 * {@link #FireChange()}, so one notification may still land just after the call.
	 * The fingerprint such a late tick writes is harmless: the baselines are dropped
	 * here and re-seeded by the next {@link #Start()}, so nothing stale survives and
	 * no change that happened while polling was off is reported. Nothing debounced
	 * is pending either, since this watcher never opens the base class's debounce
	 * timer.
	 */
	public void Stop()
	{
		ScheduledExecutorService __Timer = PollTimer;
		PollTimer = null;

		LastFastFingerprint = null;
		LastFullFingerprint = null;

		if (__Timer != null)
			__Timer.shutdownNow();
	}

	/**
	 * Report whether the scheduler is live. Says nothing about the repository: a
	 * root that disappears keeps the scheduler alive (each tick then returns
	 * without scanning, so no further change is reported) until the owner calls
	 * {@link #Stop()}.
	 *
	 * @return true while a poll scheduler exists, i.e. between a successful
	 *         {@link #Start()} and the next {@link #Stop()}; false for an opted-out
	 *         or never-started watcher
	 */
	public boolean IsRunning()
	{
		return PollTimer != null;
	}

	// -------------------------------------------------------------------------
	// Polling
	// -------------------------------------------------------------------------

	/**
	 * One poll iteration: ask git, compare, report. Runs on the poll thread and is
	 * never re-entered (fixed delay on a single-threaded executor, so a slow scan
	 * pushes the next tick back instead of queueing one).
	 * <p>
	 * Re-resolves the root here rather than caching it, so a repository moved or
	 * deleted while the app runs stops reporting instead of being scanned at a stale
	 * path; {@link #IsRunning()} still says true in that case, so the owner is the
	 * one who has to call {@link #Stop()}.
	 * <p>
	 * A failed or timed-out scan is a skipped tick, not an error: the previous
	 * baseline is kept, so a transient failure (repository mid-operation, git
	 * missing) neither fires a spurious change nor loses a real one — the next
	 * successful scan compares against the last good answer.
	 * <p>
	 * Deliberately never throws: an exception escaping a fixed-delay task cancels
	 * every future execution, which would leave {@link #IsRunning()} reporting true
	 * while nothing polls.
	 */
	private void Tick()
	{
		Path __RootPath = GetWatchedRoot();
		if (__RootPath == null || !Files.isDirectory(__RootPath))
			return;

		TickIndex++;
		boolean __FullScan = TickIndex % FULL_SCAN_TICK_EVERY == 0;

		String __Status;
		try
		{
			__Status = RunStatus(__RootPath, __FullScan ? STATUS_FULL : STATUS_FAST);
		}
		catch (InterruptedException __Ex)
		{
			// Stop() interrupted the scan; report nothing and let the thread die.
			Thread.currentThread().interrupt();
			return;
		}
		catch (IOException __Ex)
		{
			return;                        // git could not be started — retry next tick
		}

		if (__Status == null)
			return;                        // non-zero exit or timeout — keep the baseline

		String __Fingerprint = ReadHeadRevision() + '\n' + __Status;

		if (__FullScan)
		{
			String __Previous = LastFullFingerprint;
			LastFullFingerprint = __Fingerprint;
			ReportIfChanged(__Previous, __Fingerprint);
		}
		else
		{
			String __Previous = LastFastFingerprint;
			LastFastFingerprint = __Fingerprint;
			ReportIfChanged(__Previous, __Fingerprint);
		}
	}

	/**
	 * Notify the consumer when a primed baseline of the same tick kind differs from
	 * the current fingerprint. {@link #Tick()} has stored {@code _Current} as the new
	 * baseline before calling this, so a suppressed comparison (priming tick,
	 * unchanged tick, or a tick that finished after {@link #Stop()}) still leaves the
	 * next tick of that kind comparing against this answer — the same difference is
	 * reported once, not again on every later tick.
	 *
	 * @param _Previous baseline from the same kind of tick; null on the first run
	 *         of that kind, which seeds the baseline instead of reporting
	 * @param _Current  fingerprint of this tick, already stored as the new baseline
	 */
	private void ReportIfChanged(String _Previous, String _Current)
	{
		// Priming tick: adopt the baseline silently.
		if (_Previous == null || _Previous.equals(_Current))
			return;

		// Stopped while the scan was in flight — swallow the notification.
		if (PollTimer == null)
			return;

		FireChange();
	}

	/**
	 * Run one status command in the repository and capture its stdout.
	 * <p>
	 * Safe to run beside the rest of the app: the executable comes from the
	 * {@code GitPath} setting, the child runs in the working-tree root with
	 * {@code GIT_OPTIONAL_LOCKS=0} (so it never takes {@code .git/index.lock} and
	 * cannot collide with an operation on the {@link GitOperator} runner) and with
	 * stderr discarded. The process is destroyed in the {@code finally} block
	 * whenever it is still alive, so no timeout leaves an orphan behind.
	 *
	 * @param _RootPath working-tree root, used as the process working directory
	 * @param _Args     status command arguments
	 * @return stdout of a successful run; null when git exited non-zero or did not
	 *         finish inside {@link #SCAN_TIMEOUT_MILLIS} — the caller treats both as
	 *         a skipped tick and retries with the next one
	 * @throws IOException          if the process cannot be started or its output
	 *                              cannot be read
	 * @throws InterruptedException if {@link #Stop()} interrupted the exit wait
	 */
	private String RunStatus(Path _RootPath, List<String> _Args) throws IOException, InterruptedException
	{
		String __GitExe = AlphaSettings.Get().GetSettingEntry(AlphaSettings.GitPathName).GetValue_AsString();

		var __Command = new ArrayList<String>();
		__Command.add(__GitExe);
		__Command.addAll(_Args);

		ProcessBuilder __Builder = new ProcessBuilder(__Command);
		__Builder.directory(_RootPath.toFile());
		// Discard stderr instead of merging it into stdout: git's advisory warnings
		// (CRLF conversion, fsmonitor) are not part of the working-tree state and
		// must not perturb the fingerprint, and an unread pipe could block the child.
		__Builder.redirectError(ProcessBuilder.Redirect.DISCARD);

		// A read-only scan must not take .git/index.lock: this watcher shares the
		// repository with the operator's runner, which may be mid-operation.
		__Builder.environment().put("GIT_OPTIONAL_LOCKS", "0");

		Process __Process = __Builder.start();

		try
		{
			// git status reads no stdin; closing it stops the child from inheriting
			// a pipe that outlives the scan.
			try
			{
				__Process.getOutputStream().close();
			}
			catch (IOException __Ex)
			{
				// already closed — the scan does not depend on it
			}

			var __Output = new StringBuilder();
			try (var __Reader = new BufferedReader(new InputStreamReader(__Process.getInputStream())))
			{
				String __Line;
				while ((__Line = __Reader.readLine()) != null)
					__Output.append(__Line).append('\n');
			}

			// stderr is discarded by the builder; only the exit code matters
			if (!__Process.waitFor(SCAN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
				return null;

			return __Process.exitValue() == 0 ? __Output.toString() : null;
		}
		finally
		{
			if (__Process.isAlive())
				__Process.destroyForcibly();
		}
	}

	/**
	 * Read the checked-out revision marker for the fingerprint ({@code .git/HEAD},
	 * resolved from {@link GitDir#GetGitDirPath()}). Reading the file directly is
	 * deliberate: it costs a file read instead of a git process, and it is what makes
	 * a branch switch detectable on its own — two branches with identical
	 * working-tree state produce identical status output.
	 * <p>
	 * Never throws. An unreadable HEAD (unset target or path, and in principle a
	 * linked worktree whose {@code .git} is a file, or a repository being rewritten
	 * mid-scan) degrades to the empty string, which is itself a stable fingerprint
	 * part, so detection falls back to status-only for that tick rather than failing
	 * it.
	 *
	 * @return trimmed {@code HEAD} content ({@code ref: refs/heads/...}, or the commit
	 *         hash when detached), or an empty string when it cannot be read
	 */
	private String ReadHeadRevision()
	{
		GitDir __Target = GetGitDirTarget();
		Path __GitDirPath = __Target == null ? null : __Target.GetGitDirPath();
		if (__GitDirPath == null)
			return "";

		try
		{
			return Files.readString(__GitDirPath.resolve(HEAD_FILE_NAME)).trim();
		}
		catch (IOException | RuntimeException __Ex)
		{
			return "";
		}
	}

	/**
	 * Resolve the tick interval from {@code -Dgitalpha.poll.intervalMillis}. The
	 * property is read with an empty default, so "absent", "non-numeric" and
	 * "non-positive" all fall back to {@link #DEFAULT_POLL_INTERVAL_MILLIS} rather
	 * than handing the scheduler a zero (which would spin) or a negative delay
	 * (which it rejects).
	 *
	 * @return interval in milliseconds, always positive
	 */
	private static long ResolvePollInterval()
	{
		try
		{
			long __Millis = Long.parseLong(System.getProperty("gitalpha.poll.intervalMillis", ""));
			return __Millis > 0 ? __Millis : DEFAULT_POLL_INTERVAL_MILLIS;
		}
		catch (NumberFormatException __Ex)
		{
			return DEFAULT_POLL_INTERVAL_MILLIS;
		}
	}
}
