package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.AlphaUI;
import com.gitalpha.UI.GitDirProjectManager.GitDirWidget;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;

/**
 * A tab in the project TabPane: shows a {@link ProjectBrowser} until a project
 * is opened, then hosts a {@link GitDirWidget}. Closing the tab disposes the
 * project widget and unbinds/removes the repository from the engine.
 */
public class GitDirTabButton extends Tab implements IObject
{
	/**
	 * @param _Parent the owning AlphaUI (used to bind/unbind open projects)
	 * @param _GitDir the repository this tab starts with (may be null for a new tab)
	 */
	public GitDirTabButton(AlphaUI _Parent, GitDir _GitDir)
	{
		super();

		Parent = _Parent;
		AlphaUIInstance = _Parent;
		GitDirTarget = _GitDir;
		UpdateTabLabel();
		setClosable(true);
		setOnClosed(event ->
		{
			DisposeProjectManager();

			if (GitDirTarget != null && GitDirTarget.GetGitDirPath() != null)
			{
				AlphaUIInstance.UnbindOpenProjectTab(GitDirTarget.GetGitDirPath());
				AlphaEngine.Instance.TryCloseGitDir(GitDirTarget.GetGitDirPath());
			}
		});

		setContent(new ProjectBrowser(this, this, AlphaUIInstance));
	}

	private Object Parent;
	private final AlphaUI AlphaUIInstance;
	private GitDir GitDirTarget;
	private GitDirWidget ProjectManagerInstance = null;

	private String GetGitDirTabName()
	{
		if (GitDirTarget == null)
			return "New Tab";

		return String.format("%s - %s", GitDirTarget.GetRepoName(), GitDirTarget.GetRepoRootPath());
	}

	/** Refresh the tab text from the current repository (name + path, or "New Tab"). */
	public void UpdateTabLabel()
	{
		setText(GetGitDirTabName());
	}

	/**
	 * Open the given repository in this tab: dispose any previous project,
	 * host a fresh {@link GitDirWidget} and bind the tab to the engine.
	 */
	public void OpenProject(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;

		DisposeProjectManager();
		GitDirTarget = _GitDir;
		UpdateTabLabel();
		Platform.runLater(() ->
		{
			ProjectManagerInstance = new GitDirWidget(this, GitDirTarget);
			setContent(new StackPane(ProjectManagerInstance));
			AlphaUIInstance.BindOpenProjectTab(GitDirTarget, this);
			getTabPane().requestLayout();
		});
	}

	private void DisposeProjectManager()
	{
		if (ProjectManagerInstance == null)
			return;

		ProjectManagerInstance.Dispose();
		ProjectManagerInstance = null;
	}

	/** @return the repository open in this tab, or null before a project is opened */
	public GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	@Override
	public Object GetParent()
	{
		return Parent;
	}
}

