package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.AlphaUI;
import com.gitalpha.UI.GitDirProjectManager.GitDirProjectManagerWidget;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;

public class GitDirTabButton extends Tab implements IObject
{
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
	private GitDirProjectManagerWidget ProjectManagerInstance = null;

	private String GetGitDirTabName()
	{
		if (GitDirTarget == null)
			return "New Tab";

		return String.format("%s - %s", GitDirTarget.GetRepoName(), GitDirTarget.GetRepoRootPath());
	}

	public void UpdateTabLabel()
	{
		setText(GetGitDirTabName());
	}

	public void OpenProject(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;

		DisposeProjectManager();
		GitDirTarget = _GitDir;
		UpdateTabLabel();
		Platform.runLater(() ->
		{
			ProjectManagerInstance = new GitDirProjectManagerWidget(this, GitDirTarget);
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

