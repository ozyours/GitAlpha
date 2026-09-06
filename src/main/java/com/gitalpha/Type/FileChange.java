package com.gitalpha.Type;

import com.gitalpha.Engine.GitDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data holder for a single changed file: path, status, scope and the owning GitDir.
 * Diff lines are computed on demand (on the ForkJoinPool) and cached by file mtime,
 * subject to the file-load guards (binary / large file).
 */
public class FileChange
{
	/** Files at or below this size (bytes) are auto-loaded; larger ones need an explicit user action */
	public static final long LARGE_FILE_THRESHOLD_BYTES = 1024 * 1024;
	/** Number of leading bytes sniffed to decide whether content is binary (mirrors git's heuristic) */
	private static final int BINARY_SNIFF_BYTES = 8000;

	private final Path FilePath;
	private final EFileChangeStatus Status;
	private final EFileChangeScope Scope;
	private final GitDir Owner;

	/**
	 * File mtime captured by GitDir refresh when this change was scanned via
	 * {@code git status --porcelain}. Volatile because it is written by the
	 * GitOperator runner thread and read by the JavaFX thread.
	 * Null until the first refresh populates it.
	 */
	private volatile FileTime ScannedModified;

	/**
	 * Immutable (lines, mtime) pair published atomically for cache reads/writes.
	 * Public so callers (e.g. diff viewer) can inspect cache validity without
	 * reaching into {@code FileChange} internals.
	 */
	public static class CacheEntry
	{
		/** Parsed diff lines from the last successful diff load */
		private final List<LineChange> Lines;
		/** File mtime on disk when the diff was computed; null for deleted files */
		private final FileTime RetrievedModified;

		/**
		 * @param _Lines             parsed diff lines
		 * @param _RetrievedModified file modification time at the time of the diff load
		 */
		public CacheEntry(List<LineChange> _Lines, FileTime _RetrievedModified)
		{
			Lines = _Lines;
			RetrievedModified = _RetrievedModified;
		}

		/** @return the cached diff lines */
		public List<LineChange> GetLines() { return Lines; }

		/** @return the file mtime snapshot, or null if the file was absent */
		public FileTime GetRetrievedModified() { return RetrievedModified; }
	}

	/**
	 * Parsed-diff cache keyed by file mtime. Volatile + immutable so concurrent
	 * diff loads (run on the ForkJoinPool) publish and read the pair atomically.
	 */
	private volatile CacheEntry Cache = null;

	public FileChange(Path _FilePath, EFileChangeStatus _Status, EFileChangeScope _Scope, GitDir _Owner)
	{
		FilePath = _FilePath;
		Status = _Status;
		Scope = _Scope;
		Owner = _Owner;
	}

	public Path GetFilePath() { return FilePath; }
	public EFileChangeStatus GetStatus() { return Status; }
	public EFileChangeScope GetScope() { return Scope; }

	/** @return the file mtime snapshot taken during the last GitDir refresh, or null if not yet scanned */
	public FileTime GetScannedModified() { return ScannedModified; }

	/**
	 * Updates the scanned mtime. Called by {@link com.gitalpha.Engine.GitOperator}
	 * during refresh to record when this file was last seen by {@code git status}.
	 *
	 * @param _ScannedModified the file's mtime at the time of the status scan
	 */
	public void SetScannedModified(FileTime _ScannedModified) { ScannedModified = _ScannedModified; }

	/**
	 * Loads the parsed diff lines for this change, applying the file-load guards
	 * (binary files are refused; large files require {@link #GetDiffLinesForce()}).
	 * Reuses the cached diff when the file's mtime is unchanged.
	 */
	public CompletableFuture<DiffLoadResult> GetDiffLines()
	{
		return GetDiffLinesInternal(false);
	}

	/**
	 * Loads the diff bypassing the large-file guard. Binary files are still refused.
	 * Used by the diff viewer when the user explicitly asks to load a large file.
	 */
	public CompletableFuture<DiffLoadResult> GetDiffLinesForce()
	{
		return GetDiffLinesInternal(true);
	}

	/**
	 * Shared diff-load pipeline behind {@link #GetDiffLines()} and {@link #GetDiffLinesForce()}.
	 * {@code _Force} bypasses only the large-file guard; the binary guard always applies.
	 */
	private CompletableFuture<DiffLoadResult> GetDiffLinesInternal(boolean _Force)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			try
			{
				boolean __FileExists;
				FileTime __CurrentLastModified;
				try
				{
					__CurrentLastModified = Files.getLastModifiedTime(FilePath);
					__FileExists = true;
				}
				catch (IOException e)
				{
					__CurrentLastModified = null;
					__FileExists = false;
				}

				// Fast path: return the cached diff when the file has not changed on disk.
				CacheEntry __Cache = Cache;
				if (__FileExists && __Cache != null && __Cache.GetRetrievedModified() != null && __Cache.GetRetrievedModified().equals(__CurrentLastModified))
					return new DiffLoadResult(__Cache.GetLines(), EFileLoadGuard.NONE);

				Path __RelativePath = Owner.GetRepoRootPath().relativize(FilePath);
				String __Diff;

				boolean __IsUnstagedAdded = Scope == EFileChangeScope.UNSTAGED && Status == EFileChangeStatus.Added;
				if (__IsUnstagedAdded && __FileExists)
				{
					EFileLoadGuard __Guard = CheckLoadGuard();
					if (__Guard == EFileLoadGuard.BINARY || (__Guard == EFileLoadGuard.LARGE_FILE && !_Force))
						return new DiffLoadResult(null, __Guard);
					try
					{
						// Lenient UTF-8 decode: an untracked file may hold raw bytes that
						// are not valid UTF-8 — replacement characters beat a blank view.
						String __Content = ReadFileLenient(FilePath);
						String[] __FileLines = __Content.split("\\r?\\n", -1);
						int __LineCount = __FileLines.length;
						// A trailing newline produces a phantom empty element; drop it so the
						// synthesized hunk matches what `git diff` would report.
						if (__Content.endsWith("\n") && __LineCount > 0 && __FileLines[__LineCount - 1].isEmpty())
							__LineCount--;
						var __SB = new StringBuilder();
						__SB.append(String.format("@@ -0,0 +1,%d @@\n", __LineCount));
						for (int __Idx = 0; __Idx < __LineCount; ++__Idx)
						{
							__SB.append('+');
							__SB.append(__FileLines[__Idx]);
							__SB.append('\n');
						}
						__Diff = __SB.toString();
					}
					catch (IOException ex)
					{
						// git diff never reports untracked files, so falling back to it
						// would silently render a blank view — surface the read failure instead.
						throw new IOException("Failed to read untracked file: " + FilePath, ex);
					}
				}
				else
				{
					// The guard inspects the working-tree file even for staged diffs; the
					// staged blob content itself is left to `git diff --cached` to decide.
					if (__FileExists)
					{
						EFileLoadGuard __Guard = CheckLoadGuard();
						if (__Guard == EFileLoadGuard.BINARY || (__Guard == EFileLoadGuard.LARGE_FILE && !_Force))
							return new DiffLoadResult(null, __Guard);
					}
					var __DiffArgs = new ArrayList<String>();
					if (Scope == EFileChangeScope.STAGED)
					{
						__DiffArgs.add("diff");
						__DiffArgs.add("--cached");
					}
					else
					{
						__DiffArgs.add("diff");
					}
					__DiffArgs.add("--");
					__DiffArgs.add(__RelativePath.toString());
					var __DiffRes = Owner.RunCMD(__DiffArgs);
					if (__DiffRes.getKey() != 0)
						throw new IOException("git diff failed: " + __DiffRes.getValue());
					__Diff = __DiffRes.getValue();
				}

				var __DiffLines = ParseDiffPerFile(__Diff);

				// Publish the new cache entry for future mtime-matched reads.
				Cache = new CacheEntry(__DiffLines, __FileExists ? __CurrentLastModified : null);

				return new DiffLoadResult(__DiffLines, EFileLoadGuard.NONE);
			}
			catch (IOException | InterruptedException e)
			{
				if (e instanceof InterruptedException)
					Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Reads a file as UTF-8 with replacement characters for malformed input, so
	 * an untracked file with raw (non-UTF-8) bytes still yields a viewable diff.
	 */
	private static String ReadFileLenient(Path _FilePath) throws IOException
	{
		byte[] __Bytes = Files.readAllBytes(_FilePath);
		return StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE)
			.decode(ByteBuffer.wrap(__Bytes))
			.toString();
	}

	/**
	 * Applies the file-load guards to the on-disk content: content sniffing for
	 * binary (NUL byte in the leading bytes) and the size threshold for large
	 * files. Returns {@link EFileLoadGuard#BINARY} or {@link EFileLoadGuard#LARGE_FILE}
	 * when a guard trips, otherwise {@link EFileLoadGuard#NONE}.
	 */
	private EFileLoadGuard CheckLoadGuard() throws IOException
	{
		try (InputStream __In = Files.newInputStream(FilePath))
		{
			byte[] __Buffer = new byte[BINARY_SNIFF_BYTES];
			int __Read = __In.read(__Buffer);
			for (int __Idx = 0; __Idx < __Read; ++__Idx)
			{
				if (__Buffer[__Idx] == 0)
					return EFileLoadGuard.BINARY;
			}
		}

		if (Files.size(FilePath) > LARGE_FILE_THRESHOLD_BYTES)
			return EFileLoadGuard.LARGE_FILE;

		return EFileLoadGuard.NONE;
	}

	private List<LineChange> ParseDiffPerFile(String diffChunk)
	{
		return ParseDiff(diffChunk);
	}

	/**
	 * Parses raw unified-diff text into {@link LineChange} records: hunks
	 * ({@code @@}) reset the line counters, {@code +}/{@code -} lines advance
	 * only their own counter, context and blank lines advance both. Header
	 * lines (before the first {@code @@}) are skipped. Static so any diff
	 * source (working tree, stash, log) can reuse the same parser.
	 */
	public static List<LineChange> ParseDiff(String _DiffText)
	{
		List<LineChange> __Out = new ArrayList<>();
		if (_DiffText == null || _DiffText.isBlank())
			return __Out;

		String __DiffText = _DiffText;
		int __HunkIdx = __DiffText.indexOf("@@");
		if (__HunkIdx > 0)
		{
			int __LineStart = __DiffText.lastIndexOf('\n', __HunkIdx);
			if (__LineStart >= 0)
				__DiffText = __DiffText.substring(__LineStart + 1);
			else
				__DiffText = __DiffText.substring(__HunkIdx);
		}

		String[] __Lines = __DiffText.split("\\r?\\n", -1);
		// A trailing newline leaves a phantom empty element; it is not a diff line.
		int __LineCount = __Lines.length;
		if (__LineCount > 0 && __Lines[__LineCount - 1].isEmpty())
			__LineCount--;
		Integer __OldLine = null;
		Integer __NewLine = null;

		for (int __I = 0; __I < __LineCount; ++__I)
		{
			String __Line = __Lines[__I];
			if (__Line.startsWith("@@"))
			{
				var __M = java.util.regex.Pattern.compile("@@\\s+-(\\d+)(,\\d+)?\\s+\\+(\\d+)(,\\d+)?\\s+@@").matcher(__Line);
				if (__M.find())
				{
					__OldLine = Integer.parseInt(__M.group(1));
					__NewLine = Integer.parseInt(__M.group(3));
				}
				continue;
			}

			if (__Line.isEmpty())
			{
				__Out.add(new LineChange(__OldLine, __NewLine, ' ', ""));
				if (__OldLine != null)
					__OldLine++;
				if (__NewLine != null)
					__NewLine++;
				continue;
			}

			char __P = __Line.charAt(0);
			String __Text = __Line.length() > 1 ? __Line.substring(1) : "";
			if (__P == ' ')
			{
				__Out.add(new LineChange(__OldLine, __NewLine, __P, __Text));
				if (__OldLine != null)
					__OldLine++;
				if (__NewLine != null)
					__NewLine++;
			}
			else if (__P == '+')
			{
				__Out.add(new LineChange(null, __NewLine, __P, __Text));
				if (__NewLine != null)
					__NewLine++;
			}
			else if (__P == '-')
			{
				__Out.add(new LineChange(__OldLine, null, __P, __Text));
				if (__OldLine != null)
					__OldLine++;
			}
			else
			{
				// ignore other lines
			}
		}

		return __Out;
	}

	@Override
	public String toString()
	{
		return String.format("[%s] %s: %s", Scope, FilePath, Status);
	}

	public static record LineChange(Integer oldLineNumber, Integer newLineNumber, char prefix, String text) {}

	/**
	 * Result of a guarded diff load: the parsed lines plus the guard that applied.
	 * {@code Lines} is {@code null} when a guard blocked the load.
	 */
	public static record DiffLoadResult(List<LineChange> Lines, EFileLoadGuard Guard) {}
}
