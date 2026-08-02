package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;

import javafx.scene.layout.StackPane;

/**
 * Shared base for the widgets inside a project view: stores the repository and
 * the owning {@link GitDirWidget} so subclasses can reach git state and the
 * parent project widget (e.g. to route diff-viewer selection).
 */
class BaseWidget extends StackPane
{
	/**
	 * @param _GitDirTarget       the repository this widget operates on
	 * @param _GitDirWidgetTarget the hosting project widget
	 */
	public BaseWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		GitDirTarget = _GitDirTarget;
		GitDirWidgetTarget = _GitDirWidgetTarget;
	}

	private final GitDir GitDirTarget;
	private final GitDirWidget GitDirWidgetTarget;

	/** @return the repository this widget displays */
	protected final GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	/** @return the owning project widget (routes UI, e.g. the diff viewer) */
	protected final GitDirWidget GetGitDirWidgetTarget()
	{
		return GitDirWidgetTarget;
	}

}
