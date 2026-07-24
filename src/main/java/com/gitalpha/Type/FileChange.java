package com.gitalpha.Type;

import com.gitalpha.Engine.GitDir;
import javafx.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FileChange
{
	private final Path FilePath;
	private final EFileChangeStatus Status;
	private final EFileChangeScope Scope;
	private final GitDir Owner;

	private List<LineChange> CachedDiff;
	private FileTime LastKnownModified;

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

	public CompletableFuture<List<LineChange>> GetDiffLines()
	{
		return CompletableFuture.supplyAsync(() ->
		{
			try
			{
				boolean fileExists;
				FileTime currentLastModified;
				try
				{
					currentLastModified = Files.getLastModifiedTime(FilePath);
					fileExists = true;
				}
				catch (IOException e)
				{
					currentLastModified = null;
					fileExists = false;
				}

				if (fileExists && CachedDiff != null && LastKnownModified != null && LastKnownModified.equals(currentLastModified))
					return CachedDiff;

				Path relativePath = Owner.GetRepoRootPath().relativize(FilePath);
				String diff;

				boolean isUnstagedAdded = Scope == EFileChangeScope.UNSTAGED && Status == EFileChangeStatus.Added;
				if (isUnstagedAdded && fileExists)
				{
					try
					{
						var content = Files.readString(FilePath);
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
					}
					catch (IOException ex)
					{
						var __diffArgs = new ArrayList<String>();
						__diffArgs.add("diff");
						__diffArgs.add("--");
						__diffArgs.add(relativePath.toString());
						var __DiffRes = Owner.RunCMD(__diffArgs);
						if (__DiffRes.getKey() != 0)
							throw new IOException("git diff failed: " + __DiffRes.getValue());
						diff = __DiffRes.getValue();
					}
				}
				else
				{
					var __diffArgs = new ArrayList<String>();
					if (Scope == EFileChangeScope.STAGED)
					{
						__diffArgs.add("diff");
						__diffArgs.add("--cached");
					}
					else
					{
						__diffArgs.add("diff");
					}
					__diffArgs.add("--");
					__diffArgs.add(relativePath.toString());
					var __DiffRes = Owner.RunCMD(__diffArgs);
					if (__DiffRes.getKey() != 0)
						throw new IOException("git diff failed: " + __DiffRes.getValue());
					diff = __DiffRes.getValue();
				}

				var diffLines = ParseDiffPerFile(diff);

				if (fileExists)
				{
					LastKnownModified = currentLastModified;
				}
				else
				{
					LastKnownModified = null;
				}
				CachedDiff = diffLines;

				return diffLines;
			}
			catch (IOException | InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		});
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
}
