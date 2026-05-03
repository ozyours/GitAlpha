package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Engine.GitDir;

public interface RefreshGitDirEvent
{
	void Event(GitDir _GitDirTarget, String _Reason);
}
