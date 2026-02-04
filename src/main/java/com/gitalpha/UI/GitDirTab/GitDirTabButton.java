package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.GitDirProjectManager.GitDirProjectManager;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;

public class GitDirTabButton extends Tab
{
	public GitDirTabButton(GitDir _GitDir)
	{
		super();

		GitDirTarget = _GitDir;
		UpdateTabLabel();

		setContent(new ProjectBrowser(this));
	}

	private GitDir GitDirTarget;

	private String GetGitDirTabName()
	{
		if (GitDirTarget == null)
			return "New Tab";

		return String.format("%s - %s", GitDirTarget.GetProjectFolderName(), GitDirTarget.GetGitDirParentPath());
	}

	public void UpdateTabLabel()
	{
		setText(GetGitDirTabName());
	}

	public void OpenProject(Path _ProjectPath)
	{
		GitDirTarget = new GitDir(_ProjectPath);
		UpdateTabLabel();
		Platform.runLater(() ->
		{
			var ProjectManager = new StackPane(new GitDirProjectManager(this, GitDirTarget));
			setContent(ProjectManager);
			getTabPane().requestLayout();
		});
		AlphaEngine.Instance.BroadcastOpenGitDirEvent(GitDirTarget);
	}
}

