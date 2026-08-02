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

	/** Immutable (lines, mtime) pair published atomically for cache reads/writes */
	private static record CacheEntry(List<LineChange> Lines, FileTime Modified) {}

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

				CacheEntry __Cache = Cache;
				if (__FileExists && __Cache != null && __Cache.Modified() != null && __Cache.Modified().equals(__CurrentLastModified))
					return new DiffLoadResult(__Cache.Lines(), EFileLoadGuard.NONE);

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
		var out = new ArrayList<LineChange>();
		if (diffChunk == null || diffChunk.isBlank())
			return out;

		int hunkIdx = diffChunk.indexOf("@@");
		if (hunkIdx > 0)
		{
			int lineStart = diffChunk.lastIndexOf('\n', hunkIdx);
			if (lineStart >= 0)
				diffChunk = diffChunk.substring(lineStart + 1);
			else
				diffChunk = diffChunk.substring(hunkIdx);
		}

		String[] lines = diffChunk.split("\\r?\\n", -1);
		// A trailing newline leaves a phantom empty element; it is not a diff line.
		int __LineCount = lines.length;
		if (__LineCount > 0 && lines[__LineCount - 1].isEmpty())
			__LineCount--;
		Integer oldLine = null;
		Integer newLine = null;

		for (int i = 0; i < __LineCount; ++i)
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
				out.add(new LineChange(oldLine, newLine, ' ', ""));
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
				out.add(new LineChange(oldLine, newLine, p, text));
				if (oldLine != null)
					oldLine++;
				if (newLine != null)
					newLine++;
			}
			else if (p == '+')
			{
				out.add(new LineChange(null, newLine, p, text));
				if (newLine != null)
					newLine++;
			}
			else if (p == '-')
			{
				out.add(new LineChange(oldLine, null, p, text));
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
