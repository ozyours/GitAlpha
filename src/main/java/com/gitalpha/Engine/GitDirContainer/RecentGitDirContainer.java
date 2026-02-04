package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;

public class RecentGitDirContainer extends GitDirContainer
{
    public RecentGitDirContainer(AlphaEngine _AlphaEngineParent)
    {
        super(_AlphaEngineParent);
    }

    public void AddGitDir(GitDir _GitDir)
    {
        GetGitDirs().contains(_GitDir);
    }
}
