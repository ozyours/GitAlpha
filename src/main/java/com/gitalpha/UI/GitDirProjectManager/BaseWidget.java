package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;

import javafx.scene.layout.StackPane;

class BaseWidget extends StackPane
{
	public BaseWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		GitDirTarget = _GitDirTarget;
		GitDirProjectManagerTarget = _GitDirProjectManagerTarget;
	}

	private final GitDir GitDirTarget;
	private final GitDirProjectManager GitDirProjectManagerTarget;

	protected final GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	protected final GitDirProjectManager GetGitDirProjectManagerTarget()
	{
		return GitDirProjectManagerTarget;
	}

}
