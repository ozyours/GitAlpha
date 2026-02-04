package com.gitalpha.Function;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public final class GitDirFunction
{
    private GitDirFunction()
    {
    }

    public static Path TryFixGitDirPath(String _GitDir)
    {
        assert _GitDir != null;
        return TryFixGitDirPath(Path.of(_GitDir));
    }

    public static Path TryFixGitDirPath(Path _GitDir)
    {
        assert _GitDir != null;
        if (!Objects.equals(_GitDir.getFileName().toString(), ".git"))
            _GitDir = _GitDir.resolve(".git");
        return _GitDir;
    }

    public static boolean CheckGitDirValidity(String _GitDir)
    {
        assert _GitDir != null;
        return CheckGitDirValidity(Path.of(_GitDir));
    }

    public static boolean CheckGitDirValidity(Path _GitDir)
    {
        assert _GitDir != null;
        return CheckGitDirValidity(_GitDir.toFile());
    }

    public static boolean CheckGitDirValidity(File _GitDir)
    {
        assert _GitDir != null;
        if (!_GitDir.exists() || !_GitDir.isDirectory())
            return false;

        File head = new File(_GitDir, "HEAD");
        File config = new File(_GitDir, "config");
        File objects = new File(_GitDir, "objects");
        File refs = new File(_GitDir, "refs");

        return head.isFile() && config.isFile() && objects.isDirectory() && refs.isDirectory();
    }
}
