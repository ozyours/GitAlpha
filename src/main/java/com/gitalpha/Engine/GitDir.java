package com.gitalpha.Engine;

import com.gitalpha.Constant.GitCMDConstant;
import com.gitalpha.Function.StringFunction;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.EFileChangeScope;
import com.gitalpha.Type.FileChange;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ISerializable;
import javafx.util.Pair;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
	/** Prevents overlapping async operations */
	private boolean IsBusy = false;

	/** Accumulated file changes detected during the last refresh */
	private final List<FileChange> ChangedFiles = new ArrayList<>();
	/** All branches (local + remote) parsed from `git branch -a` */
	private final List<GitBranch> Branches = new ArrayList<>();
	/** Name of the currently checked-out branch */
	private String ActiveBranch = "";

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
	 * @throws RuntimeException if a refresh is currently in progress
	 */
	public List<FileChange> GetChangedFiles() throws RuntimeException
	{
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");

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
	 * Asynchronously refreshes branch list and file changes using a diff-merge strategy.
	 * Existing FileChange objects are preserved when possible to retain cached diffs.
	 * @return a CompletableFuture that completes when refresh is done
	 * @throws RuntimeException if already busy
	 */
	public CompletableFuture<Void> Refresh() throws RuntimeException
	{
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");
		IsBusy = true;

		return Refresh_Internal().whenComplete((__Unused, __Err) -> IsBusy = false);
	}

	/**
	 * Asynchronously checks out the given branch via git checkout.
	 * Updates ActiveBranch only on success.
	 * @param _Branch branch name to switch to
	 * @return a CompletableFuture that completes when checkout finishes
	 */
	public CompletableFuture<Void> ChangeBranch(String _Branch)
	{
		if (_Branch == null || _Branch.isBlank())
			throw new RuntimeException("No branch specified to change to");
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");
		IsBusy = true;

		var __args = new java.util.ArrayList<String>(GitCMDConstant.Checkout);
		__args.add(_Branch);

		return RunCMDAsync(__args).thenAccept((Pair<Integer, String> Results) ->
		{
			if (Results.getKey() != 0)
				throw new RuntimeException(Results.getValue());

			// set the active branch only after a successful checkout
			ActiveBranch = _Branch;
		}).whenComplete((__Unused, __Err) -> IsBusy = false);
	}

	/**
	 * Internal async refresh: lists branches, collects new changes into a temp list,
	 * then diff-merges with the existing ChangedFiles list.
	 * Existing FileChange objects are preserved when the same (Path, Scope, Status) still exists,
	 * so that their cached diffs are retained.
	 */
	private CompletableFuture<Void> Refresh_Internal()
	{
		return CompletableFuture.runAsync(() ->
		{
			// List branches
			{
				try
				{
					var __Res = RunCMD(GitCMDConstant.Branches);
					if (__Res.getKey() != 0)
						throw new IOException("git branch list failed: " + __Res.getValue());
					var __String = __Res.getValue();
					var __List = __String.split("\n");
					System.out.printf("Branches: %d\n", __List.length);
					System.out.println(__String);
					Branches.clear(); // Clear existing branches

					for (var e : __List)
					{
						// normalize and remove leading marker
						e = StringFunction.FixCMDString(e);
						String line = e.trim();
						line = line.replace('\\', '/');
						boolean starred = false;
						if (line.startsWith("*"))
						{
							starred = true;
							line = line.substring(1).trim();
						}

						// skip symbolic refs and HEAD references
						if (line.contains("->") || line.equals("HEAD") || line.endsWith("/HEAD"))
							continue;

						// remove "remotes/" prefix if present
						if (line.startsWith("remotes/"))
							line = line.substring("remotes/".length());

						// split namespace and name
						String[] parts = line.split("/");
						if (parts.length == 0)
							continue;

						String name = parts[parts.length - 1];
						// Ensure branch name does not contain any stray markers like '*'
						name = name.replace("*", "").trim();
						java.util.List<String> namespace = new java.util.ArrayList<>();
						for (int i = 0; i < parts.length - 1; ++i)
							namespace.add(parts[i]);

						boolean isRemote = parts.length > 1; // if contains '/', treat as remote/namespace

						if (starred)
						{
							ActiveBranch = name;
						}

						Branches.add(new GitBranch(name, namespace, isRemote));
					}
				}
				catch (IOException | InterruptedException e)
				{
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				System.out.printf("Branches: %d\n", Branches.size());
			}

			// Collect new changes into a temp list
			var __NewChanges = new java.util.ArrayList<FileChange>();
			CollectChangesByScope(EFileChangeScope.STAGED, __NewChanges);
			CollectChangesByScope(EFileChangeScope.UNSTAGED, __NewChanges);

			// Diff-merge: match existing against new
			var __ExistingIt = ChangedFiles.iterator();
			while (__ExistingIt.hasNext())
			{
				var __Existing = __ExistingIt.next();
				boolean __Found = false;
				var __NewIt = __NewChanges.iterator();
				while (__NewIt.hasNext())
				{
					var __New = __NewIt.next();
					if (__Existing._FilePath().equals(__New._FilePath())
						&& __Existing._Scope() == __New._Scope()
						&& __Existing._Status() == __New._Status())
					{
						__NewIt.remove();
						__Found = true;
						break;
					}
				}
				if (!__Found)
				{
					__ExistingIt.remove();
				}
			}

			// Leftovers from new list are genuinely new changes
			ChangedFiles.addAll(__NewChanges);
		});
	}

	/**
	 * Collects added/modified/removed file changes for a given scope (STAGED or UNSTAGED).
	 * For UNSTAGED scope, also collects untracked files.
	 * Results are appended to the target list.
	 */
	private void CollectChangesByScope(EFileChangeScope _Scope, List<FileChange> _Target)
	{
		try
		{
			boolean __IsStaged = _Scope == EFileChangeScope.STAGED;
			List<String> __AddedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Added : GitCMDConstant.Changed_Unstaged_Added;
			List<String> __ModifiedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Modified : GitCMDConstant.Changed_Unstaged_Modified;
			List<String> __RemovedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Removed : GitCMDConstant.Changed_Unstaged_Removed;

			CollectChangesByStatus(_Scope, EFileChangeStatus.Added, __AddedCmd, _Target);
			CollectChangesByStatus(_Scope, EFileChangeStatus.Modified, __ModifiedCmd, _Target);
			CollectChangesByStatus(_Scope, EFileChangeStatus.Removed, __RemovedCmd, _Target);

			if (!__IsStaged)
			{
				CollectChangesByStatus(_Scope, EFileChangeStatus.Added, GitCMDConstant.Changed_Unstaged_Untracked, _Target);
			}
		}
		catch (IOException | InterruptedException __Ex)
		{
			throw new RuntimeException(__Ex);
		}
	}

	/**
	 * Runs a git command to list files of a given change status, parses the output,
	 * and appends FileChange entries to the target list.
	 */
	private void CollectChangesByStatus(EFileChangeScope _Scope, EFileChangeStatus _Status, List<String> _ListCmd, List<FileChange> _Target) throws IOException, InterruptedException
	{
		var __Res = RunCMD(_ListCmd);
		if (__Res.getKey() != 0)
			throw new IOException("git change listing failed: " + __Res.getValue());

		var __List = __Res.getValue().split("\n");
		for (var e : __List)
		{
			e = StringFunction.FixCMDString(e);
			e = e.replace('\\', '/');
			if (e.isBlank())
				continue;

			var path = GetRepoRootPath().resolve(e);
			_Target.add(new FileChange(path, _Status, _Scope, this));
		}
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
	 * Asynchronously executes a git command via RunCMD on a common ForkJoinPool.
	 * @return a CompletableFuture yielding (exitCode, output)
	 */
	public CompletableFuture<Pair<Integer, String>> RunCMDAsync(List<String> args)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			try
			{
				return RunCMD(args);
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Runs a git command on a virtual-thread executor and returns a Future.
	 * Note: the executor is closed immediately, so the Future must be consumed before completion.
	 * @return Future yielding (exitCode, output)
	 */
	public Future<Pair<Integer, String>> CoRunCMD(List<String> args)
	{
		try (var __Future = Executors.newVirtualThreadPerTaskExecutor())
		{
			return __Future.submit(() -> RunCMD(args));
		}
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
