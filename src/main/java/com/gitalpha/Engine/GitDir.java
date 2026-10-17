package com.gitalpha.Engine;

import com.gitalpha.Constant.GitCMDConstant;
import com.gitalpha.Engine.GitDirContainer.IScannedFilesUpdatedEvent;
import com.gitalpha.Type.FileChange;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ISerializable;
import javafx.util.Pair;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GitDir manages a .git directory, providing operations to list branches, detect file changes,
 * run git commands, and retrieve diffs.
 */
public class GitDir implements ISerializable
{
	/**
	 * Default constructor
	 */
	public GitDir()
	{
		GitDirPath = null;
		Watcher = new GitWatcher(this, () ->
			AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("file-watcher", this));
	}

	/**
	 * Constructor with path
	 *
	 * @param GitDirPath The path of .git folder
	 */
	public GitDir(Path GitDirPath)
	{
		this.GitDirPath = GitDirPath;
		Watcher = new GitWatcher(this, () ->
			AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("file-watcher", this));
	}

	/** Path to the .git directory */
	private Path GitDirPath;

	/** Accumulated file changes detected during the last refresh */
	private final List<FileChange> ChangedFiles = new ArrayList<>();
	/**
	 * Weak references to FileChange entries whose {@code ScannedModified} was
	 * updated during the most recent diff-merge. Accumulated by
	 * {@link com.gitalpha.Engine.GitOperator#DiffMergeChanges} and broadcast
	 * via {@link #BroadcastIScannedFilesUpdatedEvent(List)} after refresh.
	 * Cleared at the start of each refresh cycle.
	 */
	private final List<WeakReference<FileChange>> ScannedUpdates = new ArrayList<>();

	/**
	 * Weak event listeners notified when existing FileChange entries had their
	 * scanned mtime updated during a refresh (pruned on dead refs).
	 * Scoped to this repository — only subscribers interested in this repo's
	 * changes need to register here.
	 */
	private final List<WeakReference<IScannedFilesUpdatedEvent>> ScannedFilesUpdatedEventList = new ArrayList<>();
	/** All branches (local + remote) parsed from `git branch -a`, with local upstream links and their unpushed (`ahead`) / unpulled (`behind`) counts merged in by the per-refresh `for-each-ref` query */
	private final List<GitBranch> Branches = new ArrayList<>();
	/**
	 * Full path (namespace/name) of the currently checked-out branch.
	 * Volatile because it is written by the GitOperator runner thread (checkout /
	 * refresh) and read by the JavaFX thread, e.g. in BranchWidget's cell factory.
	 */
	private volatile String ActiveBranch = "";

	/**
	 * Operator that manages git operations and refresh for this GitDir.
	 * All git operations should go through this operator rather than calling
	 * {@link #Refresh(IGitOperationCallback)} or {@link #ChangeBranch(String, IGitOperationCallback)}
	 * directly, to get
	 * queuing, batching, and interruptible refresh behavior.
	 */
	private final GitOperator Operator = new GitOperator(this, AlphaEngine.Instance);

	/**
	 * Stash facade for this repository: queues stash mutations through
	 * {@link #Operator} and runs read-only stash queries synchronously.
	 */
	private final GitStashOperator StashOperator = new GitStashOperator(this);

	/**
	 * Filesystem watcher that monitors the working tree for changes and
	 * triggers a debounced refresh. Created in the constructor; lifecycle
	 * managed via {@link #StartWatching()} and {@link #StopWatching()}.
	 */
	private final GitWatcher Watcher;

	/** @return the stored .git directory path */
	public Path GetGitDirPath()
	{
		return GitDirPath;
	}

	/**
	 * Derives the repository name from the parent of the .git path.
	 * @return parent folder name, or empty string if unavailable
	 */
	public String GetRepoName()
	{
		if (GitDirPath == null)
			return "";

		Path parent = GitDirPath.getParent();
		if (parent != null)
		{
			return parent.getFileName().toString();
		}
		return ""; // no parent (e.g., root path)
	}

	/**
	 * @return the working-tree root (parent of .git), or empty path if GitDirPath is null
	 */
	public Path GetRepoRootPath()
	{
		if (GitDirPath == null)
			return Path.of("");

		return GitDirPath.getParent();
	}

	/**
	 * @return list of changed files collected during the last refresh
	 */
	public List<FileChange> GetChangedFiles()
	{
		return ChangedFiles;
	}

	/**
	 * @return weak references to FileChange entries whose scanned mtime was updated in the last refresh
	 */
	public List<WeakReference<FileChange>> GetScannedUpdates()
	{
		return ScannedUpdates;
	}

	/**
	 * Registers a scanned-files-updated listener (held weakly; no unsubscribe required).
	 * The listener fires only for this repository's scanned-file updates.
	 *
	 * @param _Event the listener to register
	 */
	public void AddIScannedFilesUpdatedEvent(IScannedFilesUpdatedEvent _Event)
	{
		ScannedFilesUpdatedEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregisters a scanned-files-updated listener (optional — dead references are pruned on broadcast).
	 *
	 * @param _Event the listener to remove
	 */
	public void RemoveIScannedFilesUpdatedEvent(IScannedFilesUpdatedEvent _Event)
	{
		int __I = 0;
		while (__I < ScannedFilesUpdatedEventList.size())
		{
			if (Objects.equals(ScannedFilesUpdatedEventList.get(__I).get(), _Event))
			{
				ScannedFilesUpdatedEventList.remove(__I);
				break;
			}
			__I++;
		}
	}

	/**
	 * Notifies every live scanned-files-updated listener for this repository,
	 * pruning dead weak references inline.
	 *
	 * @param _UpdatedFiles the FileChange entries whose scanned mtime was refreshed
	 */
	public void BroadcastIScannedFilesUpdatedEvent(List<FileChange> _UpdatedFiles)
	{
		int __I = 0;
		while (__I < ScannedFilesUpdatedEventList.size())
		{
			var __E = ScannedFilesUpdatedEventList.get(__I);
			if (__E.get() != null)
			{
				__E.get().Event(_UpdatedFiles);
				__I++;
			}
			else
			{
				ScannedFilesUpdatedEventList.remove(__I);
			}
		}
	}

	/**
	 * Clears the scanned-updates accumulator. Called at the start of each
	 * refresh cycle before diff-merge repopulates it.
	 */
	void ClearScannedUpdates()
	{
		ScannedUpdates.clear();
	}

	/** @return list of all known branches (local and remote) */
	public List<GitBranch> GetBranches()
	{
		return Branches;
	}

	/** @return full path (namespace/name) of the currently checked-out branch */
	public String GetActiveBranch()
	{
		return ActiveBranch;
	}

	/**
	 * Package-private setter for ActiveBranch. The value is the FULL branch path
	 * (namespace/name joined), never the bare leaf name, so branches that share a
	 * leaf (e.g. "feature/foo" vs "hotfix/foo") stay distinguishable.
	 * Called from the GitOperator runner thread; volatile makes the new value
	 * visible to the JavaFX thread immediately.
	 */
	void SetActiveBranch(String _Name)
	{
		ActiveBranch = _Name;
	}

	/**
	 * @return the {@link GitOperator} that manages git operations for this GitDir
	 */
	public GitOperator GetOperator()
	{
		return Operator;
	}

	/**
	 * @return the {@link GitStashOperator} that manages stash operations for this GitDir
	 */
	public GitStashOperator GetStashOperator()
	{
		return StashOperator;
	}

	/**
	 * @return the {@link GitWatcher} that monitors the working tree for changes
	 */
	public GitWatcher GetWatcher()
	{
		return Watcher;
	}

	/**
	 * Start watching the working tree for filesystem changes. When changes are
	 * detected, a debounced refresh is triggered via
	 * {@link AlphaEngine#AttemptSaveAndBroadcastRefresh}.
	 * No-op if the root path is invalid (e.g. default-constructed GitDir).
	 */
	public void StartWatching()
	{
		Watcher.Start();
	}

	/**
	 * Stop watching the working tree and release all OS resources.
	 * Safe to call multiple times.
	 */
	public void StopWatching()
	{
		Watcher.Stop();
	}

	/**
	 * Refreshes branch list and file changes using the internal {@link GitOperator}.
	 * Delegates to the operator with {@link ERefreshPolicy#REFRESH_DATA_ONLY}
	 * so the caller controls the UI update.
	 *
	 * @param _Callback callback fired after the refresh completes (may be null)
	 */
	public void Refresh(IGitOperationCallback _Callback)
	{
		Operator.Refresh(ERefreshPolicy.REFRESH_DATA_ONLY, _Callback);
	}

	/**
	 * Checks out the given branch via git checkout, then refreshes branches and file changes.
	 * Delegates to the internal {@link GitOperator} which handles queuing, batching, and interruptible refresh.
	 *
	 * @param _Branch   branch name to switch to
	 * @param _Callback callback fired after the checkout and refresh complete (may be null)
	 */
	public void ChangeBranch(String _Branch, IGitOperationCallback _Callback)
	{
		if (_Branch == null || _Branch.isBlank())
			throw new RuntimeException("No branch specified to change to");

		Operator.RunGitOp(List.of("checkout", _Branch), ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Links a local branch to a remote-tracking branch (sets its upstream) via
	 * {@code git branch --set-upstream-to=<upstream> <branch>}. Git overwrites
	 * any existing link in place — an {@code --unset-upstream} is never needed
	 * before re-linking — and validates the target ref before writing, so a
	 * failed call leaves the old link intact. Runs through the operator with
	 * {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI}: the refresh re-parses
	 * upstreams and the broadcast repaints every branch view.
	 *
	 * @param _LocalBranch the local branch full path to link (e.g. {@code main})
	 * @param _Upstream    the remote-tracking branch full path (e.g.
	 *                     {@code origin/main})
	 * @param _Callback    fires after the link + refresh complete (may be null)
	 */
	public void SetUpstream(String _LocalBranch, String _Upstream, IGitOperationCallback _Callback)
	{
		if (_LocalBranch == null || _LocalBranch.isBlank())
			throw new RuntimeException("No local branch specified to link");
		// The name is a bare argv element: a leading dash would make git
		// parse it as an option instead of a branch name.
		if (_LocalBranch.startsWith("-"))
			throw new RuntimeException("Invalid branch name (leading '-'): " + _LocalBranch);
		if (_Upstream == null || _Upstream.isBlank())
			throw new RuntimeException("No upstream branch specified to link to");

		Operator.RunGitOp(List.of("branch", "--set-upstream-to=" + _Upstream, _LocalBranch),
				ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Removes a local branch's upstream link via
	 * {@code git branch --unset-upstream <branch>}. Only meaningful on a
	 * linked branch — git errors when none is configured, which surfaces
	 * through the callback. Runs through the operator with
	 * {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI} like
	 * {@link #SetUpstream}.
	 *
	 * @param _LocalBranch the local branch full path to unlink
	 * @param _Callback    fires after the unlink + refresh complete (may be null)
	 */
	public void UnsetUpstream(String _LocalBranch, IGitOperationCallback _Callback)
	{
		if (_LocalBranch == null || _LocalBranch.isBlank())
			throw new RuntimeException("No branch specified to unlink");
		// Bare argv element — same leading-dash guard as SetUpstream.
		if (_LocalBranch.startsWith("-"))
			throw new RuntimeException("Invalid branch name (leading '-'): " + _LocalBranch);

		Operator.RunGitOp(List.of("branch", "--unset-upstream", _LocalBranch),
				ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Fetches every configured remote ({@code git fetch --all}) — the branch
	 * manager's Fetch button. Fetch only updates remote-tracking refs (no
	 * local branch or working tree is touched), so it is always safe; the
	 * follow-up refresh with {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI}
	 * re-parses the branch list so new/removed remote branches appear.
	 *
	 * @param _Callback fires after the fetch + refresh complete (may be null)
	 */
	public void FetchAll(IGitOperationCallback _Callback)
	{
		Operator.RunGitOp(GitCMDConstant.Fetch_All, ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Pushes the checked-out branch to its upstream ({@code git push}) — the
	 * quick-action button's Push. Run through the operator with
	 * {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI} so the follow-up refresh
	 * re-parses the track counts and clears the "ahead" state. Takes no
	 * branch argument: push operates on HEAD's configured upstream.
	 *
	 * @param _Callback fires after the push + refresh complete (may be null)
	 */
	public void PushBranch(IGitOperationCallback _Callback)
	{
		Operator.RunGitOp(GitCMDConstant.Push, ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Pulls the checked-out branch from its upstream ({@code git pull}) — the
	 * quick-action button's Pull. Run through the operator with
	 * {@link ERefreshPolicy#REFRESH_AND_UPDATE_UI} so the follow-up refresh
	 * re-parses the track counts and clears the "behind" state. Takes no
	 * branch argument: pull operates on HEAD's configured upstream.
	 *
	 * @param _Callback fires after the pull + refresh complete (may be null)
	 */
	public void PullBranch(IGitOperationCallback _Callback)
	{
		Operator.RunGitOp(GitCMDConstant.Pull, ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Synchronously executes a git command in the repository root.
	 * Delegates to RunCMDUtil.RunCMD with the repo directory as working directory.
	 * @return Pair of (exitCode, output)
	 */
	public Pair<Integer, String> RunCMD(List<String> args) throws IOException, InterruptedException
	{
		return RunCMDUtil.RunCMD(GitDirPath.getParent().toFile(), args);
	}

	/**
	 * Serializes GitDirPath to JSON under key "P".
	 * @return JSONObject containing the serialized state
	 */
	@Override
	public JSONObject OnSerialize()
	{
		var __JSON = new JSONObject();
		__JSON.put("P", GitDirPath.toString());
		return __JSON;
	}

	/**
	 * Restores GitDirPath from JSON (key "P").
	 * @param JSON previously serialized state
	 */
	@Override
	public void OnDeserialize(JSONObject JSON)
	{
		GitDirPath = Path.of(JSON.get("P").toString());
	}
}
