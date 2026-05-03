package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Function.GitDirFunction;

import java.nio.file.Path;
import java.util.Objects;

public class RecentGitDirContainer extends GitDirContainer
{
    public RecentGitDirContainer(AlphaEngine _AlphaEngineParent)
    {
        super(_AlphaEngineParent);
    }

	public void AddGitDir(GitDir _GitDir)
	{
		if (_GitDir == null || _GitDir.GetGitDirPath() == null)
			return;

		Path __GitPath = GitDirFunction.TryFixGitDirPath(_GitDir.GetGitDirPath());

		for (int i = 0; i < GetGitDirs().size(); ++i)
		{
			var __Existing = GetGitDirs().get(i);
			if (__Existing == null || __Existing.GetGitDirPath() == null)
				continue;

			if (Objects.equals(GitDirFunction.TryFixGitDirPath(__Existing.GetGitDirPath()), __GitPath))
			{
				GetGitDirs().remove(i);
				break;
			}
		}

		GetGitDirs().add(0, new GitDir(__GitPath));

		var __RecentCapEntry = GetAlphaEngineParent().GetSettings().GetSettingEntry(com.gitalpha.Engine.AlphaSettings.RecentSizeName);
		int __RecentCap = __RecentCapEntry != null ? __RecentCapEntry.GetValue_AsInteger() : 8;
		while (GetGitDirs().size() > __RecentCap)
		{
			GetGitDirs().remove(GetGitDirs().size() - 1);
		}
	}
}
