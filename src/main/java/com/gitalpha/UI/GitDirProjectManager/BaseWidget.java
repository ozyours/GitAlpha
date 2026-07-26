package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;

import javafx.scene.layout.StackPane;

class BaseWidget extends StackPane
{
	public BaseWidget(GitDir _GitDirTarget, GitDirProjectManagerWidget _GitDirProjectManagerWidgetTarget)
	{
		GitDirTarget = _GitDirTarget;
		GitDirProjectManagerWidgetTarget = _GitDirProjectManagerWidgetTarget;
	}

	private final GitDir GitDirTarget;
	private final GitDirProjectManagerWidget GitDirProjectManagerWidgetTarget;

	protected final GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	protected final GitDirProjectManagerWidget GetGitDirProjectManagerWidgetTarget()
	{
		return GitDirProjectManagerWidgetTarget;
	}

}
