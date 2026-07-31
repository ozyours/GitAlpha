package com.gitalpha.Engine;

import com.gitalpha.Type.FileChange;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ISerializable;
import javafx.util.Pair;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
	}

	/**
	 * Constructor with path
	 *
	 * @param GitDirPath The path of .git folder
	 */
	public GitDir(Path GitDirPath)
	{
		this.GitDirPath = GitDirPath;
	}

	/** Path to the .git directory */
	private Path GitDirPath;

	/** Accumulated file changes detected during the last refresh */
	private final List<FileChange> ChangedFiles = new ArrayList<>();
	/** All branches (local + remote) parsed from `git branch -a` */
	private final List<GitBranch> Branches = new ArrayList<>();
	/** Name of the currently checked-out branch */
	private String ActiveBranch = "";

	/**
	 * Operator that manages git operations and refresh for this GitDir.
	 * All git operations should go through this operator rather than calling
	 * {@link #Refresh(IGitOperationCallback)} or {@link #ChangeBranch(String, IGitOperationCallback)}
	 * directly, to get
	 * queuing, batching, and interruptible refresh behavior.
	 */
	private final GitOperator Operator = new GitOperator(this, AlphaEngine.Instance);

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

	/** @return list of all known branches (local and remote) */
	public List<GitBranch> GetBranches()
	{
		return Branches;
	}

	/** @return name of the currently checked-out branch */
	public String GetActiveBranch()
	{
		return ActiveBranch;
	}

	/**
	 * Package-private setter for ActiveBranch.
	 * Used by {@link GitOperator} to update the active branch after git operations.
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
