package com.gitalpha.Type;

import java.nio.file.Path;
import java.util.List;

public record FileChanges(Path _FilePath, EFileChangeStatus _Status, EFileChangeScope _Scope, String _Diff, List<LineChange> _DiffLines)
{
	@Override
	public String toString()
	{
		return String.format("[%s] %s: %s", _Scope, _FilePath, _Status);
	}

	public static record LineChange(Integer oldLineNumber, Integer newLineNumber, char prefix, String text) {}
}
