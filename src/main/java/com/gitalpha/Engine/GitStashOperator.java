package com.gitalpha.Engine;

import com.gitalpha.Constant.GitCMDConstant;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.EStashMode;
import com.gitalpha.Type.StashEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stash domain facade over {@link GitDir} and its {@link GitOperator}.
 * <p>
 * Mutations (push, pop, drop, apply, rename) are queued on the repository's
 * single GitOperator runner thread, so they are serialized against every other
 * git operation (stage/unstage, commit, checkout) and the refresh — the queue
 * is the lock. The rename sequence in particular must not interleave with other
 * stash mutations, because it relies on stash indices shifting after the store.
 * <p>
 * Read-only queries (stash list, file list, per-file diff) deliberately do NOT
 * use the queue and run synchronously via {@link GitDir#RunCMD} — callers must
 * invoke them off the JavaFX thread (e.g. on a {@code CompletableFuture}).
 * Because reads bypass the queue, they may observe a partially-applied queued
 * sequence (e.g. a duplicate stash between the rename's store and drop steps);
 * this is accepted for responsiveness and only affects transient display state.
 */
public class GitStashOperator
{
	/**
	 * The repository this stash operator manages stashes for
	 */
	private final GitDir GitDirTarget;

	/**
	 * Pattern for parsing {@code git stash list} output: {@code stash@{N}: On branch: message}
	 */
	private static final Pattern STASH_LINE_PATTERN = Pattern.compile("^stash@\\{(\\d+)\\}:\\s+On\\s+(\\S+):\\s*(.*)$");
	/**
	 * Fallback index extraction for stash lines that do not match {@link #STASH_LINE_PATTERN}
	 */
	private static final Pattern STASH_INDEX_PATTERN = Pattern.compile("stash@\\{(\\d+)\\}");

	/**
	 * @param _GitDir the repository whose stashes this operator manages
	 */
	public GitStashOperator(GitDir _GitDir)
	{
		GitDirTarget = _GitDir;
	}

	// ---------- Mutations (queued through the GitOperator runner thread) ----------

	/**
	 * Creates a new stash via {@code git stash push}, appending the selected
	 * mode's flags and an optional message. Runs through the operator queue so
	 * it is serialized against other git operations.
	 *
	 * @param _Mode     which files to stash (see {@link EStashMode})
	 * @param _Message  optional stash description ({@code -m}); blank/null skips it
	 * @param _Callback callback fired on the runner thread after the operation
	 */
	public void Push(EStashMode _Mode, String _Message, IGitOperationCallback _Callback)
	{
		List<String> __Cmd = new ArrayList<>(GitCMDConstant.Stash_Push);
		if (_Mode != null)
			__Cmd.addAll(_Mode.GetArgs());
		if (_Message != null && !_Message.isBlank())
		{
			__Cmd.add("-m");
			__Cmd.add(_Message);
		}
		GitDirTarget.GetOperator().RunGitOp(__Cmd, ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Applies and removes the given stash via {@code git stash pop}.
	 */
	public void Pop(StashEntry _Stash, IGitOperationCallback _Callback)
	{
		GitDirTarget.GetOperator().RunGitOp(Concat(GitCMDConstant.Stash_Pop, _Stash.GetStashRef()), ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Removes the given stash via {@code git stash drop}.
	 */
	public void Drop(StashEntry _Stash, IGitOperationCallback _Callback)
	{
		GitDirTarget.GetOperator().RunGitOp(Concat(GitCMDConstant.Stash_Drop, _Stash.GetStashRef()), ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Applies the given stash without removing it via {@code git stash apply}.
	 */
	public void Apply(StashEntry _Stash, IGitOperationCallback _Callback)
	{
		GitDirTarget.GetOperator().RunGitOp(Concat(GitCMDConstant.Stash_Apply, _Stash.GetStashRef()), ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * Renames the given stash. Git has no native stash rename, so the stash
	 * commit is recreated with the new message: the commit's tree and parents
	 * are copied into a new commit via {@code commit-tree}, registered as a
	 * stash via {@code stash store}, and the original is dropped. This preserves
	 * the stashed changes — a plain drop + {@code stash push} would instead
	 * stash whatever the working tree currently holds.
	 * <p>
	 * The five git commands run as ONE queued task on the GitOperator runner
	 * thread, so no other operation (stage, commit, refresh, another stash
	 * mutation) can interleave and break the sequence's atomicity.
	 *
	 * @param _Stash       the stash to rename (captured by the caller on the FX thread)
	 * @param _NewMessage  the new stash description
	 * @param _Callback    callback fired on the runner thread after the operation
	 */
	public void Rename(StashEntry _Stash, String _NewMessage, IGitOperationCallback _Callback)
	{
		GitDirTarget.GetOperator().QueueGitTask(() -> RenameSequence(_Stash, _NewMessage), ERefreshPolicy.REFRESH_AND_UPDATE_UI, _Callback);
	}

	/**
	 * The commit-preserving rename sequence: resolve the stash commit, collect
	 * its parents, recreate it with the new message, register it as a stash
	 * (becomes {@code stash@{0}}), then drop the original — the store shifted
	 * every existing stash one index back, so the original is now {@code N+1}.
	 * On drop failure the store is rolled back so the rename fails cleanly
	 * instead of leaving a duplicate (renamed copy at the head plus the
	 * original still present).
	 */
	private void RenameSequence(StashEntry _Stash, String _NewMessage) throws Exception
	{
		String __StashRef = _Stash.GetStashRef();
		int __OriginalIndex = _Stash.GetIndex();

		// 1. Resolve the stash commit hash.
		List<String> __RevParse = RunGitCommand(Concat(GitCMDConstant.Stash_RevParse, __StashRef));
		if (__RevParse.isEmpty())
			throw new RuntimeException("cannot resolve " + __StashRef);
		String __Hash = __RevParse.get(0);

		// 2. Collect the stash commit's parents (a stash commit can have up to
		//    three: HEAD, the index state, untracked).
		List<String> __RevList = RunGitCommand(Concat(GitCMDConstant.Stash_RevList_Parents, __StashRef));
		if (__RevList.isEmpty())
			throw new RuntimeException("cannot resolve " + __StashRef + " parents");
		List<String> __Parents = new ArrayList<>(List.of(__RevList.get(0).split("\\s+")));
		__Parents.remove(0);   // first token is the commit itself

		// 3. Create a new commit with the same tree and parents but the new message.
		List<String> __CommitTreeCmd = new ArrayList<>(GitCMDConstant.Stash_CommitTree);
		__CommitTreeCmd.add(__Hash + "^{tree}");
		for (String __Parent : __Parents)
		{
			__CommitTreeCmd.add("-p");
			__CommitTreeCmd.add(__Parent);
		}
		__CommitTreeCmd.add("-m");
		__CommitTreeCmd.add(_NewMessage);
		List<String> __NewCommitOutput = RunGitCommand(__CommitTreeCmd);
		if (__NewCommitOutput.isEmpty())
			throw new RuntimeException("commit-tree produced no output for " + __StashRef);
		String __NewHash = __NewCommitOutput.get(0);

		// 4. Register the new commit as a stash (becomes stash@{0}).
		List<String> __StoreCmd = new ArrayList<>(GitCMDConstant.Stash_Store);
		__StoreCmd.add("-m");
		__StoreCmd.add(_NewMessage);
		__StoreCmd.add(__NewHash);
		RunGitCommand(__StoreCmd);

		// 5. Drop the original; the store above shifted every existing stash one
		//    index back, so the original is now N+1.
		try
		{
			List<String> __DropCmd = new ArrayList<>(GitCMDConstant.Stash_Drop);
			__DropCmd.add("stash@{" + (__OriginalIndex + 1) + "}");
			RunGitCommand(__DropCmd);
		}
		catch (Exception __DropEx)
		{
			// Undo the store so the rename fails cleanly instead of leaving a
			// duplicate (renamed copy at the head plus the original still present).
			try
			{
				RunGitCommand(Concat(GitCMDConstant.Stash_Drop, "stash@{0}"));
			}
			catch (Exception __RollbackEx)
			{
				throw new RuntimeException("rename stored but original drop failed and rollback failed: " + __DropEx.getMessage() + " / " + __RollbackEx.getMessage());
			}
			throw __DropEx;
		}
	}

	// ---------- Reads (synchronous, NOT queued) ----------

	/**
	 * Parses {@code git stash list} output into stash entries.
	 * Runs synchronously via {@link GitDir#RunCMD} — must not be called on the
	 * FX thread.
	 */
	public List<StashEntry> ListStashes() throws Exception
	{
		List<String> __Output = RunGitCommand(GitCMDConstant.Stash_List);
		List<StashEntry> __Entries = new ArrayList<>();
		for (String __Line : __Output)
		{
			Matcher __Match = STASH_LINE_PATTERN.matcher(__Line);
			if (__Match.matches())
			{
				int __Idx = Integer.parseInt(__Match.group(1));
				String __Branch = __Match.group(2);
				String __Desc = __Match.group(3).trim();
				__Entries.add(new StashEntry(__Idx, __Branch, __Desc, __Line));
			}
			else if (__Line.startsWith("stash@{"))
			{
				// Fallback: extract index from the line
				Matcher __Fallback = STASH_INDEX_PATTERN.matcher(__Line);
				int __Idx = __Fallback.find() ? Integer.parseInt(__Fallback.group(1)) : -1;
				if (__Idx < 0)
					continue;   // no parseable index — skip the line
				__Entries.add(new StashEntry(__Idx, "", __Line, __Line));
			}
		}
		return __Entries;
	}

	/**
	 * Lists the files changed by the given stash via
	 * {@code git stash show --name-status}. Runs synchronously — must not be
	 * called on the FX thread.
	 */
	public List<StashEntry.StashFile> GetFiles(StashEntry _Stash) throws Exception
	{
		List<String> __Cmd = new ArrayList<>(GitCMDConstant.Stash_Show_NameStatus);
		__Cmd.add(_Stash.GetStashRef());
		List<String> __Output = RunGitCommand(__Cmd);
		List<StashEntry.StashFile> __Files = new ArrayList<>();
		for (String __Line : __Output)
		{
			if (__Line.isBlank())
				continue;
			// Format: "M  path/file.java" or "A  path/file.java".
			// Rename lines carry a score and a tab-separated pair
			// ("R100 old<TAB>new") — the status comes from the first
			// segment and the shown path is the new one.
			String __StatusToken;
			String __Path;
			String[] __TabParts = __Line.split("\\t");
			if (__TabParts.length > 1)
			{
				__StatusToken = __TabParts[0].trim();
				__Path = __TabParts[__TabParts.length - 1].trim();
			}
			else
			{
				String[] __StatusAndPath = __Line.split("\\s+", 2);
				if (__StatusAndPath.length != 2)
					continue;
				__StatusToken = __StatusAndPath[0].trim();
				__Path = __StatusAndPath[1].trim();
			}
			__Files.add(new StashEntry.StashFile(__Path, ParseStatusCode(__StatusToken)));
		}
		return __Files;
	}

	/**
	 * Returns the unified diff of a single file within the given stash.
	 * {@code git stash show -p} accepts no pathspec — git treats the path as a
	 * second revision and fails with "Too many revisions" — so the stash is
	 * diffed against its parent commit instead, restricted to the file.
	 * Runs synchronously — must not be called on the FX thread.
	 */
	public String GetFileDiff(StashEntry _Stash, String _Path) throws Exception
	{
		List<String> __Cmd = new ArrayList<>(GitCMDConstant.Stash_Diff_File);
		__Cmd.add(_Stash.GetStashRef() + "^");
		__Cmd.add(_Stash.GetStashRef());
		__Cmd.add("--");
		__Cmd.add(_Path);
		return String.join("\n", RunGitCommand(__Cmd));
	}

	// ---------- Helpers ----------

	/**
	 * Runs a git command synchronously against this repo and returns the output
	 * lines. Delegates to {@link GitDir#RunCMD} so working directory, stderr
	 * draining and error diagnostics stay consistent with the rest of the app.
	 */
	private List<String> RunGitCommand(List<String> _Cmd) throws Exception
	{
		var __Res = GitDirTarget.RunCMD(_Cmd);
		if (__Res.getKey() != 0)
			throw new RuntimeException("git " + _Cmd.get(0) + " failed (code " + __Res.getKey() + "): " + __Res.getValue());
		String __Output = __Res.getValue();
		if (__Output == null || __Output.isBlank())
			return List.of();
		return __Output.lines().toList();
	}

	/**
	 * Returns a fresh command list built from a base fragment plus appended arguments.
	 */
	private static List<String> Concat(List<String> _Base, String... _Args)
	{
		List<String> __Res = new ArrayList<>(_Base);
		Collections.addAll(__Res, _Args);
		return __Res;
	}

	/**
	 * Maps a single-letter status code from {@code git stash show --name-status} to an enum.
	 */
	private static EFileChangeStatus ParseStatusCode(String _Code)
	{
		if (_Code.startsWith("A"))
			return EFileChangeStatus.Added;
		if (_Code.startsWith("D"))
			return EFileChangeStatus.Removed;
		return EFileChangeStatus.Modified;
	}
}