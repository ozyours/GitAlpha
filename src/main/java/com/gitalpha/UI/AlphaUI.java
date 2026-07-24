package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.AlphaSettings;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.ICloseGitDirEvent;
import com.gitalpha.Engine.GitDirContainer.IOpenGitDirEvent;
import com.gitalpha.Function.GitDirFunction;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AlphaUI extends StackPane
{
	public static AlphaUI Instance;

	public AlphaUI()
	{
		super();
		Instance = this;

		OpenTabsByProjectPath = new HashMap<>();

		TabPaneInstance = new TabPane();
		getChildren().add(TabPaneInstance);

		TabPaneInstance.getTabs().add(new GitDirTabButton(this, null));
		TabPaneInstance.setTabMaxWidth(AlphaSettings.Get().GetSettingEntry(AlphaSettings.TabMaxSize).GetDefaultValue_AsInteger());

		// Create a special "+" tab
		Tab addTab = new Tab("+");
		addTab.setClosable(false);
		// Add "+" tab to tabpane
		TabPaneInstance.getTabs().add(addTab);
		// Handle add-tab click
		TabPaneInstance.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
		{
			if (newTab == addTab)
			{
				Tab newUserTab = NewTab(null);
				TabPaneInstance.getTabs().add(TabPaneInstance.getTabs().size() - 1, newUserTab); // insert before "+"
				TabPaneInstance.getSelectionModel().select(newUserTab); // switch to new tab
				return;
			}

			if (newTab instanceof GitDirTabButton)
			{
				GitDirTabButton __GitTab = (GitDirTabButton) newTab;
				if (__GitTab.GetGitDirTarget() != null)
				{
					AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("project-tab-selected", __GitTab.GetGitDirTarget());
				}
			}
		});

		OpenGitDirEventListener = (_GitDirTarget) -> Platform.runLater(() ->
		{
			// Engine is source of truth for open projects; UI tab binding is explicit via BindOpenProjectTab.
			// Keep this listener to allow future external-open flows without implicit scans.
		});
		CloseGitDirEventListener = (_GitDirTarget) -> Platform.runLater(() ->
		{
			UnbindOpenProjectTab(_GitDirTarget);
		});
		AlphaEngine.Instance.AddIOpenGitDirEvent(OpenGitDirEventListener);
		AlphaEngine.Instance.AddICloseGitDirEvent(CloseGitDirEventListener);

		RestoreOpenTabs();
	}

	private GitDirTabButton NewTab(GitDir _GitDir)
	{
		return new GitDirTabButton(this, _GitDir);
	}

	private final TabPane TabPaneInstance;
	private final IOpenGitDirEvent OpenGitDirEventListener;
	private final ICloseGitDirEvent CloseGitDirEventListener;

	private final Map<Path, GitDirTabButton> OpenTabsByProjectPath;

	public GitDirTabButton TryGetCurrentlyOpenGitDirWithTabButton(GitDir _GitDir)
	{
		if (_GitDir == null)
			return null;

		return TryGetOpenTabByPath(_GitDir.GetGitDirPath());
	}

	public GitDirTabButton TryGetOpenTabByPath(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return null;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		return OpenTabsByProjectPath.get(_GitPath);
	}

	public void BindOpenProjectTab(GitDir _GitDir, GitDirTabButton _TabButton)
	{
		if (_GitDir == null || _TabButton == null)
			return;

		Path _GitPath = _GitDir.GetGitDirPath();
		if (_GitPath == null)
			return;

		OpenTabsByProjectPath.put(_GitPath, _TabButton);
	}

	public void UnbindOpenProjectTab(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;

		UnbindOpenProjectTab(_GitDir.GetGitDirPath());
	}

	public void UnbindOpenProjectTab(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		OpenTabsByProjectPath.remove(_GitPath);
	}

	public TabPane GetTabPaneInstance()
	{
		return TabPaneInstance;
	}

	private void RestoreOpenTabs()
	{
		var __SavedOpenGitDirs = AlphaEngine.Instance.GetOpenGitDirs();
		if (__SavedOpenGitDirs.isEmpty())
			return;

		for (int i = 0; i < __SavedOpenGitDirs.size(); ++i)
		{
			GitDir __GitDir = __SavedOpenGitDirs.get(i);
			if (__GitDir == null)
				continue;

			GitDirTabButton __TabButton;
			if (i == 0 && !TabPaneInstance.getTabs().isEmpty() && TabPaneInstance.getTabs().get(0) instanceof GitDirTabButton)
			{
				__TabButton = (GitDirTabButton) TabPaneInstance.getTabs().get(0);
			}
			else
			{
				__TabButton = NewTab(null);
				TabPaneInstance.getTabs().add(TabPaneInstance.getTabs().size() - 1, __TabButton);
			}

			__TabButton.OpenProject(__GitDir);
		}
	}
}
