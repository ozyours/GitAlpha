package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Engine.GitDir;

public interface IRefreshGitDirEvent
{
	void Event(GitDir _GitDirTarget, String _Reason);
}
