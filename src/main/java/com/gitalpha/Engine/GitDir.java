package com.gitalpha.Engine;

import com.gitalpha.Constant.GitCMDConstant;
import com.gitalpha.Function.StringFunction;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.EFileChangeScope;
import com.gitalpha.Type.FileChanges;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Type.ISerializable;
import javafx.util.Pair;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class GitDir implements ISerializable
{
	/**
	 * Default constructor
	 */
	public GitDir()
	{
		GitDirPath = null;
	}

	// parse unified diff output for a single file into list of LineChange(oldLine, newLine, prefix, text)
	private List<FileChanges.LineChange> parseDiffPerFile(String diffChunk)
	{
		var out = new ArrayList<FileChanges.LineChange>();
		if (diffChunk == null || diffChunk.isBlank())
			return out;

		// Trim any leading diff metadata (diff --git, index, ---/+++) so parsing starts at the first hunk header '@@'
		int hunkIdx = diffChunk.indexOf("@@");
		if (hunkIdx > 0)
		{
			// ensure we start at line boundary
			int lineStart = diffChunk.lastIndexOf('\n', hunkIdx);
			if (lineStart >= 0)
				diffChunk = diffChunk.substring(lineStart + 1);
			else
				diffChunk = diffChunk.substring(hunkIdx);
		}

		String[] lines = diffChunk.split("\\r?\\n", -1);
		Integer oldLine = null;
		Integer newLine = null;

		for (int i = 0; i < lines.length; ++i)
		{
			String line = lines[i];
			if (line.startsWith("@@"))
			{
				var m = java.util.regex.Pattern.compile("@@\\s+-(\\d+)(,\\d+)?\\s+\\+(\\d+)(,\\d+)?\\s+@@").matcher(line);
				if (m.find())
				{
					oldLine = Integer.parseInt(m.group(1));
					newLine = Integer.parseInt(m.group(3));
				}
				continue;
			}

			if (line.length() == 0)
			{
				// context empty line
				out.add(new com.gitalpha.Type.FileChanges.LineChange(oldLine, newLine, ' ', ""));
				if (oldLine != null)
					oldLine++;
				if (newLine != null)
					newLine++;
				continue;
			}

			char p = line.charAt(0);
			String text = line.length() > 1 ? line.substring(1) : "";
			if (p == ' ')
			{
				out.add(new com.gitalpha.Type.FileChanges.LineChange(oldLine, newLine, p, text));
				if (oldLine != null)
					oldLine++;
				if (newLine != null)
					newLine++;
			}
			else if (p == '+')
			{
				out.add(new com.gitalpha.Type.FileChanges.LineChange(null, newLine, p, text));
				if (newLine != null)
					newLine++;
			}
			else if (p == '-')
			{
				out.add(new com.gitalpha.Type.FileChanges.LineChange(oldLine, null, p, text));
				if (oldLine != null)
					oldLine++;
			}
			else
			{
				// ignore other lines
			}
		}

		return out;
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

	private Path GitDirPath;
	private boolean IsBusy = false;

	private final List<FileChanges> ChangedFiles = new ArrayList<>();
	private final List<GitBranch> Branches = new ArrayList<>();
	private String ActiveBranch = "";

	public Path GetGitDirPath()
	{
		return GitDirPath;
	}

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

	public Path GetRepoRootPath()
	{
		if (GitDirPath == null)
			return Path.of("");

		return GitDirPath.getParent();
	}

	public List<FileChanges> GetChangedFiles() throws RuntimeException
	{
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");

		return ChangedFiles;
	}

	public List<GitBranch> GetBranches()
	{
		return Branches;
	}

	public String GetActiveBranch()
	{
		return ActiveBranch;
	}

	public CompletableFuture<Void> Refresh() throws RuntimeException
	{
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");
		IsBusy = true;

		ChangedFiles.clear();
		return Refresh_Internal().whenComplete((__Unused, __Err) -> IsBusy = false);
	}

	public CompletableFuture<Void> ChangeBranch(String _Branch)
	{
		if (_Branch == null || _Branch.isBlank())
			throw new RuntimeException("No branch specified to change to");
		if (IsBusy)
			throw new RuntimeException("GitDir is busy");
		IsBusy = true;

		var __args = new java.util.ArrayList<String>(GitCMDConstant.Checkout);
		__args.add(_Branch);

		return RunCMDAsync(__args)
				.thenAccept((Pair<Integer, String> Results) ->
				{
					if (Results.getKey() != 0)
						throw new RuntimeException(Results.getValue());

					// set the active branch only after a successful checkout
					ActiveBranch = _Branch;
				})
				.whenComplete((__Unused, __Err) -> IsBusy = false);
	}

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

				CollectChangesByScope(EFileChangeScope.STAGED);
				CollectChangesByScope(EFileChangeScope.UNSTAGED);
			});
		}

	private void CollectChangesByScope(EFileChangeScope _Scope)
	{
		try
		{
			boolean __IsStaged = _Scope == EFileChangeScope.STAGED;
			List<String> __AddedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Added : GitCMDConstant.Changed_Unstaged_Added;
			List<String> __ModifiedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Modified : GitCMDConstant.Changed_Unstaged_Modified;
			List<String> __RemovedCmd = __IsStaged ? GitCMDConstant.Changed_Staged_Removed : GitCMDConstant.Changed_Unstaged_Removed;
			List<String> __DiffCmd = __IsStaged ? GitCMDConstant.Diff_Staged_File : GitCMDConstant.Diff_Unstaged_File;

			CollectChangesByStatus(_Scope, EFileChangeStatus.Added, __AddedCmd, __DiffCmd, !__IsStaged);
			CollectChangesByStatus(_Scope, EFileChangeStatus.Modified, __ModifiedCmd, __DiffCmd, false);
			CollectChangesByStatus(_Scope, EFileChangeStatus.Removed, __RemovedCmd, __DiffCmd, false);

			if (!__IsStaged)
			{
				CollectChangesByStatus(_Scope, EFileChangeStatus.Added, GitCMDConstant.Changed_Unstaged_Untracked, __DiffCmd, true);
			}
		}
		catch (IOException | InterruptedException __Ex)
		{
			throw new RuntimeException(__Ex);
		}
	}

	private void CollectChangesByStatus(EFileChangeScope _Scope, EFileChangeStatus _Status, List<String> _ListCmd, List<String> _DiffCmd, boolean _PreferReadForAdded) throws IOException, InterruptedException
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
			String diff;
			java.util.List<com.gitalpha.Type.FileChanges.LineChange> diffLines;

			if (_Status == EFileChangeStatus.Added && _PreferReadForAdded)
			{
				try
				{
					var content = java.nio.file.Files.readString(path);
					var fileLines = content.split("\\r?\\n", -1);
					var sb = new StringBuilder();
					sb.append(String.format("@@ -0,0 +1,%d @@\n", fileLines.length));
					for (var line : fileLines)
					{
						sb.append('+');
						sb.append(line);
						sb.append('\n');
					}
					diff = sb.toString();
					diffLines = parseDiffPerFile(diff);
				}
				catch (IOException ex)
				{
					var __diffArgs = new java.util.ArrayList<String>(_DiffCmd);
					__diffArgs.add("--");
					__diffArgs.add(e);
					var __DiffRes = RunCMD(__diffArgs);
					if (__DiffRes.getKey() != 0)
						throw new IOException("git diff failed: " + __DiffRes.getValue());
					diff = __DiffRes.getValue();
					diffLines = parseDiffPerFile(diff);
				}
			}
			else
			{
				var __diffArgs = new java.util.ArrayList<String>(_DiffCmd);
				__diffArgs.add("--");
				__diffArgs.add(e);
				var __DiffRes = RunCMD(__diffArgs);
				if (__DiffRes.getKey() != 0)
					throw new IOException("git diff failed: " + __DiffRes.getValue());
				diff = __DiffRes.getValue();
				diffLines = parseDiffPerFile(diff);
			}

			ChangedFiles.add(new FileChanges(path, _Status, _Scope, diff, diffLines));
		}
	}

	public Pair<Integer, String> RunCMD(List<String> args) throws IOException, InterruptedException
	{
		return RunCMDUtil.RunCMD(GitDirPath.getParent().toFile(), args);
	}

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

	public Future<Pair<Integer, String>> CoRunCMD(List<String> args)
	{
		try (var __Future = Executors.newVirtualThreadPerTaskExecutor())
		{
			return __Future.submit(() -> RunCMD(args));
		}
	}

	@Override
	public JSONObject OnSerialize()
	{
		var __JSON = new JSONObject();
		__JSON.put("P", GitDirPath.toString());
		return __JSON;
	}

	@Override
	public void OnDeserialize(JSONObject JSON)
	{
		GitDirPath = Path.of(JSON.get("P").toString());
	}
}
