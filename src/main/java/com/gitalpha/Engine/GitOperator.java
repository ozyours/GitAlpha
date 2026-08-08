package com.gitalpha.Engine;

import com.gitalpha.Constant.GitCMDConstant;
import com.gitalpha.Function.StringFunction;
import com.gitalpha.Type.EFileChangeScope;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.FileChange;
import com.gitalpha.Type.GitBranch;
import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Orchestrates git operations for a single GitDir.
 * <p>
 * <ul>
 *   <li>Queues incoming git operations and executes them sequentially on a single runner thread.
 *       Each queued item is an {@link IGitTask} — either a single git command
 *       ({@link #RunGitOp}) or a caller-supplied multi-step task
 *       ({@link #QueueGitTask}) that must not interleave with other operations.</li>
 *   <li>When the queue is empty, runs an interruptible refresh to update the internal GitDir state.</li>
 *   <li>If a new operation arrives while a refresh is in progress, the refresh is cancelled cooperatively
 *       so the new operation can execute immediately, followed by a fresh refresh.</li>
 *   <li>Batching: when the queue drains multiple operations, the highest {@link ERefreshPolicy} across
 *       all drained operations determines the post-drain behavior (no refresh / data-only / full UI update).</li>
 *   <li>Callbacks: after the batch completes (or aborts due to failure), all collected callbacks are
 *       fired with their individual operation result and the current GitDir state.</li>
 * </ul>
 */
public class GitOperator implements AutoCloseable
{
	// ---------------------------------------------------------------------------
	// Fields
	// ---------------------------------------------------------------------------

	private final GitDir GitDirTarget;
	private final AlphaEngine Engine;
	private final BlockingQueue<QueuedOperation> OperationQueue;
	private final Thread RunnerThread;

	/**
	 * Flag checked by {@link #RunRefresh()} between each sub-command.
	 * When set, the refresh bails out early so the runner can process a new operation.
	 */
	private volatile boolean RefreshCanceled;

	/**
	 * Tracks whether at least one completed operation requested a refresh.
	 * Reset after a refresh completes (or is cancelled and restarted).
	 */
	private boolean RefreshDirty;

	/**
	 * The maximum policy seen across the current batch of drained operations.
	 * Used after the queue empties to decide what kind of refresh to run.
	 */
	private ERefreshPolicy PendingPolicy;

	/**
	 * Holds the currently running git Process during refresh, so it can be
	 * forcibly destroyed when cancellation is requested.
	 */
	private volatile Process ActiveRefreshProcess;

	// ---------------------------------------------------------------------------
	// Inner records
	// ---------------------------------------------------------------------------

	/**
	 * One unit of queued work: the {@link IGitTask} to execute on the runner
	 * thread (a single git command wrapped by {@link SingleCommand}, a
	 * caller-supplied multi-step task from {@link #QueueGitTask}, or null for a
	 * pure refresh request), the refresh policy to accumulate on success, and
	 * the callback to fire after the batch completes.
	 */
	private record QueuedOperation(IGitTask Task, ERefreshPolicy Policy, IGitOperationCallback Callback)
	{
	}

	private record OperationResult(IGitOperationCallback Callback, boolean Succeeded, String ErrorMessage)
	{
	}

	// ---------------------------------------------------------------------------
	// Construction
	// ---------------------------------------------------------------------------

	/**
	 * @param _GitDirTarget the GitDir whose data this operator manages
	 * @param _Engine       the AlphaEngine singleton (for session save + broadcast)
	 */
	public GitOperator(GitDir _GitDirTarget, AlphaEngine _Engine)
	{
		GitDirTarget = _GitDirTarget;
		Engine = _Engine;
		OperationQueue = new LinkedBlockingQueue<>();
		RefreshCanceled = false;
		RefreshDirty = false;
		PendingPolicy = ERefreshPolicy.NO_REFRESH;
		ActiveRefreshProcess = null;

		// Start the single runner thread.
		// Using a virtual thread (Java 21) keeps resource usage minimal.
		RunnerThread = Thread.startVirtualThread(this::RunnerLoop);
	}

	// ---------------------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------------------

	/**
	 * Enqueue a single git command to be executed on the runner thread.
	 * <p>
	 * If a refresh is currently in progress, it will be cancelled so this operation
	 * can execute immediately. After the operation (and any others queued before it)
	 * complete, a refresh runs if the effective policy requires it and no operation
	 * in the batch failed. All collected callbacks are then fired.
	 *
	 * @param _Cmd      the git command arguments (e.g. {@code ["checkout", "main"]})
	 * @param _Policy   what to do after execution
	 * @param _Callback callback to fire after the batch completes (may be null)
	 */
	public void RunGitOp(List<String> _Cmd, ERefreshPolicy _Policy, IGitOperationCallback _Callback)
	{
		OperationQueue.add(new QueuedOperation(new SingleCommand(new ArrayList<>(_Cmd)), _Policy, _Callback));
		SignalRefreshCancel();
	}

	/**
	 * Enqueue a caller-supplied multi-step task to be executed on the runner
	 * thread. The task runs to completion before any other queued operation or
	 * the refresh, so its git work is atomic with respect to the rest of the
	 * queue — use this for sequences that must not interleave (e.g. the stash
	 * rename's five commands).
	 *
	 * @param _Task     the work to run on the runner thread
	 * @param _Policy   what to do after execution
	 * @param _Callback callback to fire after the batch completes (may be null)
	 */
	public void QueueGitTask(IGitTask _Task, ERefreshPolicy _Policy, IGitOperationCallback _Callback)
	{
		OperationQueue.add(new QueuedOperation(_Task, _Policy, _Callback));
		SignalRefreshCancel();
	}

	/**
	 * Request a refresh of the GitDir state without executing any git operation.
	 * <p>
	 * Enqueues a no-command operation that sets the pending refresh policy.
	 * When the queue is drained and this operation is processed, the accumulated
	 * policy triggers the appropriate refresh level.
	 *
	 * @param _Policy   what to do after refresh
	 * @param _Callback callback to fire after the refresh completes (may be null)
	 */
	public void Refresh(ERefreshPolicy _Policy, IGitOperationCallback _Callback)
	{
		// Null task signals "refresh only, no git work to execute".
		var __Op = new QueuedOperation(null, _Policy, _Callback);
		OperationQueue.add(__Op);
		SignalRefreshCancel();
	}

	/**
	 * Request a refresh without a callback.
	 *
	 * @param _Policy what to do after refresh
	 */
	public void Refresh(ERefreshPolicy _Policy)
	{
		Refresh(_Policy, null);
	}

	/**
	 * Convenience shortcut: request a full refresh with UI update, no callback.
	 * Equivalent to {@code Refresh(ERefreshPolicy.REFRESH_AND_UPDATE_UI, null)}.
	 */
	public void Refresh()
	{
		Refresh(ERefreshPolicy.REFRESH_AND_UPDATE_UI, null);
	}

	// ---------------------------------------------------------------------------
	// Runner loop
	// ---------------------------------------------------------------------------

	/**
	 * The single-thread runner loop. Runs on a dedicated virtual thread.
	 * <p>
	 * Behaviour:
	 * <ol>
	 *   <li>Drain the queue — execute each queued operation's task in FIFO order.
	 *       Accumulate the highest {@link ERefreshPolicy} seen.</li>
	 *   <li>After draining, if all operations succeeded and a refresh was requested,
	 *       run {@link #RunRefresh()}.</li>
	 *   <li>Fire all collected callbacks with their per-operation result.</li>
	 *   <li>If any operation failed, skip the refresh and fire callbacks with errors.</li>
	 *   <li>If the queue is empty and no refresh is needed, block until a new operation
	 *       arrives (or the thread is interrupted for shutdown).</li>
	 * </ol>
	 */
	private void RunnerLoop()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				// Phase 1: drain all currently available operations.
				var __Results = DrainQueue();

				// Phase 2: process collected results (refresh if possible, fire callbacks).
				ProcessResults(__Results);

				// Phase 3: block until a new operation arrives (or we're interrupted for shutdown).
				if (__Results.isEmpty() && !RefreshDirty && !Thread.currentThread().isInterrupted())
				{
					try
					{
						// take() blocks until an element arrives, but CONSUMES it from the queue.
						// We re-add it immediately so DrainQueue/poll() can find it in the next iteration.
						var __Element = OperationQueue.take();
						OperationQueue.add(__Element);
					}
					catch (InterruptedException __Ex)
					{
						Thread.currentThread().interrupt();
						break;
					}
				}
				// Loop back: if we processed results or a refresh became pending, re-check the queue.
			}
			catch (Exception __Ex)
			{
				// Catch-all: prevent silent virtual-thread death from unhandled exceptions.
				// Log and continue so the runner stays alive for future operations.
				__Ex.printStackTrace();

				// Reset dirty flags to prevent infinite retry of a broken refresh.
				RefreshDirty = false;
				PendingPolicy = ERefreshPolicy.NO_REFRESH;
			}
		}
	}

	/**
	 * Process a batch of operation results:
	 * <ol>
	 *   <li>Check if any operation failed — if so, skip the refresh.</li>
	 *   <li>If all succeeded and a refresh is pending, run {@link #RunRefresh()}.</li>
	 *   <li>Fire all collected callbacks with their per-operation result.</li>
	 * </ol>
	 * Callbacks are always fired even if the refresh failed or was skipped,
	 * so callers never hang indefinitely.
	 */
	private void ProcessResults(List<OperationResult> _Results)
	{
		// Run refresh if any succeeded operation requested one.
		// (Failed operations in the same batch don't suppress the refresh.)
		if (RefreshDirty)
		{
			try
			{
				RunRefresh();
			}
			catch (InterruptedException __Ex)
			{
				// Shutdown requested during refresh. Preserve interrupt flag and return;
				// callbacks below still fire so callers aren't left hanging.
				Thread.currentThread().interrupt();
			}
			catch (RuntimeException __Ex)
			{
				// Refresh failed (e.g., a git command inside the refresh failed).
				// Reset the dirty flags to prevent an infinite retry loop.
				// Data may be stale, but callbacks still fire so callers aren't left hanging.
				RefreshDirty = false;
				PendingPolicy = ERefreshPolicy.NO_REFRESH;
				__Ex.printStackTrace();
			}
		}

		// Fire all collected callbacks. Each caller receives their per-operation result
		// and the current (possibly stale) GitDir.
		for (var __R : _Results)
		{
			if (__R.Callback() != null)
			{
				__R.Callback().OnCompleted(__R.Succeeded(), __R.ErrorMessage(), GitDirTarget);
			}
		}
	}

	// ---------------------------------------------------------------------------
	// Queue draining
	// ---------------------------------------------------------------------------

	/**
	 * Drain all currently available operations from the queue and execute them.
	 * Results are collected and returned (callbacks are NOT fired here).
	 * <p>
	 * If the runner thread is interrupted during execution (shutdown),
	 * any results collected so far are still returned so their callbacks
	 * can be fired by the caller.
	 *
	 * @return list of per-operation results, empty if the queue had no items
	 */
	private List<OperationResult> DrainQueue()
	{
		var __Results = new ArrayList<OperationResult>();
		while (true)
		{
			QueuedOperation __Op = OperationQueue.poll();
			if (__Op == null)
				break;

			try
			{
				OperationResult __Result = RunOperation(__Op);
				__Results.add(__Result);

				if (__Result.Succeeded())
				{
					AccumulatePolicy(__Op.Policy());
				}
			}
			catch (InterruptedException __Ex)
			{
				// Shutdown requested mid-operation. Return partial results so
				// the caller can still fire callbacks for completed operations.
				Thread.currentThread().interrupt();
				break;
			}
		}
		return __Results;
	}

	// ---------------------------------------------------------------------------
	// Single operation execution
	// ---------------------------------------------------------------------------

	/**
	 * Executes a single queued operation's {@link IGitTask} on the runner thread.
	 * A null task is a pure refresh request and always succeeds. Any task
	 * exception other than {@link InterruptedException} is captured as a failed
	 * result instead of propagating, so one failing operation does not abort the
	 * batch drain — the remaining queued operations still run.
	 *
	 * @param _Op the queued operation to execute
	 * @return the result (callback, succeeded flag, error message)
	 * @throws InterruptedException if the runner thread is interrupted (shutdown)
	 */
	private OperationResult RunOperation(QueuedOperation _Op) throws InterruptedException
	{
		// Null task = pure refresh request, no git work to execute.
		if (_Op.Task() == null)
		{
			return new OperationResult(_Op.Callback(), true, null);
		}

		try
		{
			_Op.Task().Execute();
			return new OperationResult(_Op.Callback(), true, null);
		}
		catch (InterruptedException __Ex)
		{
			throw __Ex;
		}
		catch (Exception __Ex)
		{
			return new OperationResult(_Op.Callback(), false, __Ex.getMessage());
		}
	}

	/**
	 * Wraps a single git command as a queued task: runs it on the runner thread,
	 * propagates failures as exceptions, and updates the active branch right
	 * after a successful checkout so the UI reflects it before the follow-up
	 * refresh re-parses it.
	 */
	private class SingleCommand implements IGitTask
	{
		private final List<String> Command;

		SingleCommand(List<String> _Command)
		{
			Command = _Command;
		}

		@Override
		public void Execute() throws Exception
		{
			var __Res = RunCMD(Command, false);
			if (__Res.getKey() != 0)
			{
				throw new RuntimeException("git " + String.join(" ", Command) + " failed:\n" + __Res.getValue());
			}

			if (IsCheckoutCommand(Command))
			{
				String __BranchName = ExtractBranchName(Command);
				if (__BranchName != null)
				{
					// Update the active branch immediately so the UI reflects the
					// checkout even before the follow-up refresh re-parses it.
					GitDirTarget.SetActiveBranch(__BranchName);
				}
			}
		}
	}

	/**
	 * @return true if the command list starts with {@code "checkout"}
	 */
	private static boolean IsCheckoutCommand(List<String> _Cmd)
	{
		return _Cmd.size() >= 1 && "checkout".equals(_Cmd.get(0));
	}

	/**
	 * Extract the branch name from a checkout command.
	 * <ul>
	 *   <li>{@code ["checkout", "main"]} → {@code "main"}</li>
	 *   <li>{@code ["checkout", "-b", "new-branch"]} → {@code "new-branch"}</li>
	 *   <li>Other forms → {@code null}</li>
	 * </ul>
	 */
	private static String ExtractBranchName(List<String> _Cmd)
	{
		if (_Cmd.size() == 2 && IsCheckoutCommand(_Cmd))
			return _Cmd.get(1); // ["checkout", "branch"]

		if (_Cmd.size() == 3 && IsCheckoutCommand(_Cmd) && "-b".equals(_Cmd.get(1)))
			return _Cmd.get(2); // ["checkout", "-b", "new-branch"]

		return null;
	}

	// ---------------------------------------------------------------------------
	// Interruptible refresh
	// ---------------------------------------------------------------------------

	/**
	 * Refresh the internal GitDir state (branches + changed files).
	 * <p>
	 * Checks {@link #RefreshCanceled} between each sub-command.
	 * If set, the refresh bails out immediately.
	 * <p>
	 * After a successful (non-cancelled) refresh, saves the session and broadcasts
	 * a UI refresh event if {@link #PendingPolicy} is {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI}.
	 *
	 * @throws InterruptedException if the runner thread is interrupted (shutdown)
	 */
	private void RunRefresh() throws InterruptedException
	{
		// Reset the cancellation flag so this refresh can proceed.
		// (If another cancellation comes in during the refresh, RefreshCanceled will be set again.)
		RefreshCanceled = false;

		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException();

		// ── Step 1: list branches ──
		Pair<Integer, String> __BranchRes;
		try
		{
			__BranchRes = RunCMD(GitCMDConstant.Branches, true);
		}
		catch (IOException __Ex)
		{
			throw new RuntimeException("git branch list failed", __Ex);
		}
		if (RefreshCanceled)
			return;
		if (__BranchRes.getKey() != 0)
			throw new RuntimeException("git branch list failed: " + __BranchRes.getValue());

		List<GitBranch> __ParsedBranches = ParseBranchesOutput(__BranchRes.getValue());
		if (RefreshCanceled)
			return;

		// Update GitDir's branch list and active branch.
		GitDirTarget.GetBranches().clear();
		GitDirTarget.GetBranches().addAll(__ParsedBranches);

		// ── Step 2: collect changed files ──
		var __NewChanges = new ArrayList<FileChange>();
		CollectChangesByScope(EFileChangeScope.STAGED, __NewChanges);
		if (RefreshCanceled)
			return;

		CollectChangesByScope(EFileChangeScope.UNSTAGED, __NewChanges);
		if (RefreshCanceled)
			return;

		// ── Step 3: diff-merge with existing changed files ──
		DiffMergeChanges(__NewChanges);

		// ── Step 4: save session and broadcast if the policy requires it ──
		if (PendingPolicy == ERefreshPolicy.REFRESH_AND_UPDATE_UI)
		{
			Engine.AttemptSaveAndBroadcastRefresh("git-operation-completed", GitDirTarget);
		}

		// ── Step 5: reset dirty flags ──
		RefreshDirty = false;
		PendingPolicy = ERefreshPolicy.NO_REFRESH;
	}

	// ---------------------------------------------------------------------------
	// Branch parsing
	// ---------------------------------------------------------------------------

	/**
	 * Parse the output of {@code git branch -a} into a list of {@link GitBranch} objects.
	 * Also updates {@link GitDir#SetActiveBranch(String)} for the currently checked-out branch.
	 * <p>
	 * Non-branch lines are skipped early: symbolic refs ("remotes/origin/HEAD -> ...")
	 * and detached-HEAD markers ("(HEAD detached at ...)").
	 * A branch is remote iff its line carries the {@code remotes/} prefix — a local
	 * branch may legitimately contain slashes (e.g. "feature/foo"), so the parts count
	 * is not used for classification.
	 * The starred (checked-out) branch is stored as its FULL path (namespace/name),
	 * not the leaf name.
	 */
	private List<GitBranch> ParseBranchesOutput(String _Output)
	{
		var __Branches = new ArrayList<GitBranch>();
		var __Lines = _Output.split("\n");

		for (var __RawLine : __Lines)
		{
			String __Line = StringFunction.FixCMDString(__RawLine);
			__Line = __Line.trim();
			__Line = __Line.replace('\\', '/');

			boolean __Starred = false;
			if (__Line.startsWith("*"))
			{
				__Starred = true;
				__Line = __Line.substring(1).trim();
			}

			// Skip symbolic refs, HEAD references and detached-HEAD markers.
			// "remotes/origin/HEAD -> origin/main" and "(HEAD detached at ...)"
			// are not branch names and must not reach the branch tree.
			if (__Line.contains("->") || __Line.startsWith("(") || __Line.contains("HEAD"))
				continue;

			// Remember whether this was a remote branch before removing the prefix;
			// a local branch may also contain slashes (e.g. "feature/foo").
			boolean __IsRemote = __Line.startsWith("remotes/");
			if (__IsRemote)
				__Line = __Line.substring("remotes/".length());

			// Split namespace and name.
			String[] __Parts = __Line.split("/");
			if (__Parts.length == 0)
				continue;

			String __Name = __Parts[__Parts.length - 1];
			__Name = __Name.replace("*", "").trim();

			var __Namespace = new ArrayList<String>();
			for (int __Idx = 0; __Idx < __Parts.length - 1; ++__Idx)
				__Namespace.add(__Parts[__Idx]);

			if (__Starred)
			{
				// Store the full branch path (namespace + name) so that branches
				// sharing a leaf name (e.g. "feature/foo" vs "hotfix/foo") do not
				// collide when the active branch is matched.
				String __FullName = __Namespace.isEmpty() ? __Name : String.join("/", __Namespace) + "/" + __Name;
				GitDirTarget.SetActiveBranch(__FullName);
			}

			__Branches.add(new GitBranch(__Name, __Namespace, __IsRemote));
		}

		return __Branches;
	}

	// ---------------------------------------------------------------------------
	// Change collection (mirrors GitDir.CollectChangesByScope / CollectChangesByStatus)
	// ---------------------------------------------------------------------------

	/**
	 * Collects added / modified / removed file changes for a given scope.
	 * For UNSTAGED scope, also collects untracked files.
	 * Results are appended to the target list.
	 */
	private void CollectChangesByScope(EFileChangeScope _Scope, List<FileChange> _Target) throws InterruptedException
	{
		boolean __IsStaged = _Scope == EFileChangeScope.STAGED;

		List<String> __AddedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Added : GitCMDConstant.Changed_Unstaged_Added;
		List<String> __ModifiedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Modified : GitCMDConstant.Changed_Unstaged_Modified;
		List<String> __RemovedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Removed : GitCMDConstant.Changed_Unstaged_Removed;

		CollectChangesByStatus(_Scope, EFileChangeStatus.Added, __AddedCmd, _Target);
		if (RefreshCanceled)
			return;

		CollectChangesByStatus(_Scope, EFileChangeStatus.Modified, __ModifiedCmd, _Target);
		if (RefreshCanceled)
			return;

		CollectChangesByStatus(_Scope, EFileChangeStatus.Removed, __RemovedCmd, _Target);
		if (RefreshCanceled)
			return;

		if (!__IsStaged)
		{
			CollectChangesByStatus(_Scope, EFileChangeStatus.Added, GitCMDConstant.Changed_Unstaged_Untracked, _Target);
		}
	}

	/**
	 * Runs a git command to list files of a given change status, parses the output,
	 * and appends {@link FileChange} entries to the target list.
	 */
	private void CollectChangesByStatus(EFileChangeScope _Scope, EFileChangeStatus _Status, List<String> _ListCmd, List<FileChange> _Target) throws InterruptedException
	{
		Pair<Integer, String> __Res;
		try
		{
			__Res = RunCMD(_ListCmd, true);
		}
		catch (IOException __Ex)
		{
			throw new RuntimeException("git change listing failed", __Ex);
		}
		if (__Res.getKey() != 0)
		{
			// If the process was killed by a cancellation, bail out silently.
			// The caller's "if (RefreshCanceled) return;" check handles the early exit.
			if (RefreshCanceled)
				return;
			throw new RuntimeException("git change listing failed: " + __Res.getValue());
		}

		var __Lines = __Res.getValue().split("\n");
		for (var __RawLine : __Lines)
		{
			String __Line = StringFunction.FixCMDString(__RawLine);
			__Line = __Line.replace('\\', '/');
			if (__Line.isBlank())
				continue;

			Path __Path = GitDirTarget.GetRepoRootPath().resolve(__Line);
			_Target.add(new FileChange(__Path, _Status, _Scope, GitDirTarget));
		}
	}

	// ---------------------------------------------------------------------------
	// Diff-merge
	// ---------------------------------------------------------------------------

	/**
	 * Diff-merges a newly collected change list against GitDir's existing ChangedFiles.
	 * <p>
	 * Existing {@link FileChange} objects are preserved when the same
	 * (path, scope, status) still exists, retaining their cached diffs.
	 * Entries that no longer exist are removed.
	 * Genuinely new entries (not found in the old list) are appended.
	 */
	private void DiffMergeChanges(List<FileChange> _NewChanges)
	{
		var __ExistingList = GitDirTarget.GetChangedFiles();

		// Mark-and-sweep: match existing entries against new ones.
		var __ExistingIter = __ExistingList.iterator();
		while (__ExistingIter.hasNext())
		{
			var __Existing = __ExistingIter.next();
			boolean __Found = false;

			var __NewIter = _NewChanges.iterator();
			while (__NewIter.hasNext())
			{
				var __New = __NewIter.next();
				if (__Existing.GetFilePath().equals(__New.GetFilePath()) && __Existing.GetScope() == __New.GetScope() && __Existing.GetStatus() == __New.GetStatus())
				{
					__NewIter.remove(); // matched; do not add as new
					__Found = true;
					break;
				}
			}

			if (!__Found)
			{
				__ExistingIter.remove(); // no longer present
			}
		}

		// Leftovers in _NewChanges are genuinely new entries.
		__ExistingList.addAll(_NewChanges);
	}

	// ---------------------------------------------------------------------------
	// Git command execution (mirrors RunCMDUtil.RunCMD)
	// ---------------------------------------------------------------------------

	/**
	 * Synchronously execute a git command in the repository root directory.
	 *
	 * @param _Args      command arguments (e.g. {@code ["status", "--porcelain"]})
	 * @param _Trackable if true, the spawned Process is stored in
	 *                   {@link #ActiveRefreshProcess} for external cancellation
	 * @return Pair of (exitCode, output)
	 * @throws IOException          if process creation or I/O fails
	 * @throws InterruptedException if the runner thread is interrupted
	 */
	private Pair<Integer, String> RunCMD(List<String> _Args, boolean _Trackable) throws IOException, InterruptedException
	{
		String __GitExe = AlphaSettings.Get().GetSettingEntry(AlphaSettings.GitPathName).GetValue_AsString();

		var __Command = new ArrayList<String>();
		__Command.add(__GitExe);
		__Command.addAll(_Args);

		ProcessBuilder __Builder = new ProcessBuilder(__Command);
		var __WorkDir = GitDirTarget.GetRepoRootPath().toFile();
		if (__WorkDir != null)
			__Builder.directory(__WorkDir);
		__Builder.redirectErrorStream(false);

		Process __Process = __Builder.start();

		if (_Trackable)
			ActiveRefreshProcess = __Process;

		try
		{
			// Capture stderr on a separate thread to prevent pipe deadlocks.
			var __ErrorOutput = new StringBuilder();
			Thread __ErrorReader = new Thread(() ->
			{
				try (var __Reader = new BufferedReader(new InputStreamReader(__Process.getErrorStream())))
				{
					String __Line;
					while ((__Line = __Reader.readLine()) != null)
					{
						__ErrorOutput.append(__Line).append(System.lineSeparator());
					}
				}
				catch (IOException __Ex)
				{
					// stream closed normally
				}
			});
			__ErrorReader.start();

			// Read stdout.
			var __Output = new StringBuilder();
			try (var __Reader = new BufferedReader(new InputStreamReader(__Process.getInputStream())))
			{
				String __Line;
				while ((__Line = __Reader.readLine()) != null)
				{
					__Output.append(__Line).append(System.lineSeparator());
				}
			}

			__ErrorReader.join();
			int __ExitCode = __Process.waitFor();

			// If the command failed, include stderr in the output for error reporting.
			if (__ExitCode != 0 && !__ErrorOutput.isEmpty())
			{
				__Output.append(__ErrorOutput.toString());
			}

			return new Pair<>(__ExitCode, __Output.toString());
		}
		finally
		{
			if (_Trackable)
				ActiveRefreshProcess = null;
		}
	}

	// ---------------------------------------------------------------------------
	// Cancellation signaling
	// ---------------------------------------------------------------------------

	/**
	 * Signal the runner to cancel any in-flight refresh.
	 * Called from any thread (typically the JavaFX thread when a new operation is enqueued).
	 */
	private void SignalRefreshCancel()
	{
		RefreshCanceled = true;
		Process __Proc = ActiveRefreshProcess;
		if (__Proc != null)
		{
			__Proc.destroyForcibly();
		}
	}

	// ---------------------------------------------------------------------------
	// Policy accumulation
	// ---------------------------------------------------------------------------

	/**
	 * Merge a new policy into the accumulated {@link #PendingPolicy}.
	 * The effective policy is the maximum ordinal (NO_REFRESH &lt; DATA_ONLY &lt; UPDATE_UI).
	 */
	private void AccumulatePolicy(ERefreshPolicy _NewPolicy)
	{
		if (_NewPolicy.ordinal() > PendingPolicy.ordinal())
		{
			PendingPolicy = _NewPolicy;
		}
		if (_NewPolicy != ERefreshPolicy.NO_REFRESH)
		{
			RefreshDirty = true;
		}
	}

	// ---------------------------------------------------------------------------
	// Cleanup
	// ---------------------------------------------------------------------------

	/**
	 * Shut down the runner thread and release any resources.
	 * After disposal, this operator must not be used.
	 */
	@Override
	public void close()
	{
		RunnerThread.interrupt();
		SignalRefreshCancel();

		// Fire callbacks for any remaining queued operations with an error.
		var __Remaining = new ArrayList<QueuedOperation>();
		OperationQueue.drainTo(__Remaining);
		for (var __Op : __Remaining)
		{
			if (__Op.Callback() != null)
			{
				__Op.Callback().OnCompleted(false, "GitOperator was disposed", GitDirTarget);
			}
		}
	}

	/**
	 * Shutdown alias matching the existing Dispose() pattern in the codebase.
	 */
	public void Dispose()
	{
		close();
	}
}
