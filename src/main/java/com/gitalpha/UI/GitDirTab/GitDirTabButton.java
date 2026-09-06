package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.AlphaUI;
import com.gitalpha.UI.GitDirProjectManager.GitDirWidget;
import com.gitalpha.UI.IObject;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

/**
 * One project tab of the main window's {@link com.gitalpha.UI.Components.ATabWidget}:
 * a plain controller object (no JavaFX {@code Tab}) that owns a stable root
 * {@link StackPane} — the node registered as the widget tab's content. The
 * root hosts a {@link ProjectBrowser} until a project is opened, then hosts a
 * {@link GitDirWidget}. Closing the tab (via the widget's close face) is
 * routed back here by AlphaUI through {@link #OnClosed()}, which disposes the
 * project widget and unbinds/removes the repository from the engine.
 * <p>
 * The root node doubles as the identity used for tab lookup: AlphaUI maps it
 * to this object, so selection, retitling and close handling survive the
 * widget's drag-to-reorder permutation.
 */
public class GitDirTabButton implements IObject
{
	/**
	 * @param _Parent the owning AlphaUI (used to bind/unbind open projects)
	 * @param _GitDir the repository this tab starts with (may be null for a new tab)
	 */
	public GitDirTabButton(AlphaUI _Parent, GitDir _GitDir)
	{
		Parent = _Parent;
		AlphaUIInstance = _Parent;
		GitDirTarget = _GitDir;

		// Stable content root: its identity in the ATabWidget never changes,
		// only the child inside it swaps between browser and project manager.
		Root = new StackPane();
		Root.getChildren().add(new ProjectBrowser(this, this, AlphaUIInstance));
		// No-op before the tab is added to the widget (IndexOf == -1); kept so
		// label state is consistent if construction ever happens post-add.
		UpdateTabLabel();
	}

	private Object Parent;
	private final AlphaUI AlphaUIInstance;
	private GitDir GitDirTarget;
	private GitDirWidget ProjectManagerInstance = null;
	/** The stable content node registered with the ATabWidget; hosts the active view */
	private final StackPane Root;

	private String GetGitDirTabName()
	{
		if (GitDirTarget == null)
			return "New Tab";

		return String.format("%s - %s", GitDirTarget.GetRepoName(), GitDirTarget.GetRepoRootPath());
	}

	/**
	 * Refresh the tab label from the current repository (name + path, or
	 * "New Tab"). The retitle goes through AlphaUI so the live tab index is
	 * resolved at invocation time (indexes shift on reorder/close).
	 */
	public void UpdateTabLabel()
	{
		AlphaUIInstance.UpdateProjectTabTitle(this);
	}

	/** @return the stable content node registered as this tab's ATabWidget content */
	public StackPane GetRoot()
	{
		return Root;
	}

	/** @return the current tab label ("New Tab" or "name - path") */
	public String GetTitle()
	{
		return GetGitDirTabName();
	}

	/**
	 * Open the given repository in this tab: dispose any previous project,
	 * host a fresh {@link GitDirWidget} inside the stable root and bind the
	 * tab to the engine. The watcher is started after the widget is wired
	 * so the initial callback fires on the next debounced refresh, not
	 * before the UI is ready to consume it.
	 */
	public void OpenProject(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;

		DisposeProjectManager();
		GitDirTarget = _GitDir;
		UpdateTabLabel();
		// Capture the parameter (not the field): two rapid OpenProject calls
		// must each build the repo they were called with, not whatever the
		// field holds when the queued runnables finally execute.
		GitDir __Dir = _GitDir;
		Platform.runLater(() ->
		{
			// Dispose again inside the runnable: an earlier queued OpenProject
			// runnable may have created a widget after our synchronous dispose.
			DisposeProjectManager();
			ProjectManagerInstance = new GitDirWidget(this, __Dir);
			Root.getChildren().setAll(ProjectManagerInstance);
			AlphaUIInstance.BindOpenProjectTab(__Dir, this);
			// Start the filesystem watcher only after the widget is wired and
			// registered — the debounced callback fires
			// AttemptSaveAndBroadcastRefresh which drives UI rebuilds.
			__Dir.StartWatching();
		});
	}

	/**
	 * Tab-closed entry point, invoked by AlphaUI after the ATabWidget removed
	 * this tab: disposes the project widget and unbinds/closes the repository.
	 */
	public void OnClosed()
	{
		DisposeProjectManager();

		if (GitDirTarget != null && GitDirTarget.GetGitDirPath() != null)
		{
			AlphaUIInstance.UnbindOpenProjectTab(GitDirTarget.GetGitDirPath());
			AlphaEngine.Instance.TryCloseGitDir(GitDirTarget.GetGitDirPath());
		}
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
