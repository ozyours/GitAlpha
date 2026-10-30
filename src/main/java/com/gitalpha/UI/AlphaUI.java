package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.AlphaSettings;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.ICloseGitDirEvent;
import com.gitalpha.Engine.GitDirContainer.IOpenGitDirEvent;
import com.gitalpha.Function.GitDirFunction;
// Widgets and theming come from the AtlasUI framework module (com.atlasui).
import com.atlasui.UI.Components.ATabWidget;
import com.atlasui.UI.Components.INewTabRequestEvent;
import com.atlasui.UI.Components.ISubTabSelectionEvent;
import com.atlasui.UI.Components.ITabCloseEvent;
import com.atlasui.UI.Components.ITabOrderChangeEvent;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root layout of the main window: a BorderPane whose top chrome holds the
 * menu + quick command bars and whose center holds the open-project
 * {@link ATabWidget} (modifiable: {@code "+"} affix, per-tab close faces and
 * drag-to-reorder). Owns the tab-per-project binding map — keyed by the tabs'
 * stable root nodes, so lookups survive reordering — and restores the saved
 * tabs on startup. A drag-reorder is mirrored onto the engine's open-repository
 * list, which is what the session stores, so the restored strip replays the
 * user's arrangement left to right.
 */
public class AlphaUI extends BorderPane
{
	/** Singleton root UI, assigned in the constructor; the app reaches the window layout through this. */
	public static AlphaUI Instance;

	/**
	 * Quiet period after the last slot crossing before the reordered session is
	 * written, so a drag gesture costs one session write instead of one per
	 * crossed boundary.
	 */
	private static final double TAB_ORDER_SAVE_DELAY_MS = 250;

	public AlphaUI()
	{
		super();
		Instance = this;

		TabsByRoot = new HashMap<>();
		OpenTabsByProjectPath = new HashMap<>();

		// Top chrome: the placeholder menu bar and quick command bar sit above
		// the tab widget (BorderPane: top = chrome, center = tabs). The quick
		// command bar carries the chrome's own vertical spacing via its
		// margins; the chrome VBox itself has no padding.
		VBox __TopChrome = new VBox(new TopMenuBar(), new QuickCommandBar());
		setTop(__TopChrome);

		// Publish the persisted tab-face width (AlphaSettings.TabMaxSize) as the
		// framework default before any tab widget exists: ATabWidget samples that
		// default only in its constructor, so the main strip created below and
		// every project's SMALL sub-tab strip inherit the same width (150
		// otherwise) without the framework knowing about host settings.
		ATabWidget.SetDefaultTabMaxWidth(AlphaSettings.Get().GetSettingEntry(AlphaSettings.TabMaxSize).GetValue_AsInteger());

		TabWidgetInstance = new ATabWidget(true);
		setCenter(TabWidgetInstance);

		// Wire the widget events before adding tabs, so the auto-selection of
		// the first AddTab already resolves through TabsByRoot.
		// Each listener is stored as a strong field so the WeakReference
		// inside ATabWidget does not allow GC to collect it.
		// "+": create a fresh empty project tab and switch to it.
		NewTabRequestListener = () ->
		{
			GitDirTabButton __Tab = CreateProjectTab(null);
			int __Index = TabWidgetInstance.IndexOf(__Tab.GetRoot());
			if (__Index >= 0)
				TabWidgetInstance.SelectTab(__Index);
		};
		TabWidgetInstance.AddNewTabRequestEvent(NewTabRequestListener);
		// Close face: route back to the owning tab object (dispose project,
		// unbind and close the repository). The tab is already removed here.
		// Closing the selected last tab clears the content without firing a
		// selection event, so drop the stale root reference explicitly.
		TabCloseListener = (_Index, _Content) ->
		{
			if (SelectedRoot == _Content)
				SelectedRoot = null;
			GitDirTabButton __Tab = TabsByRoot.remove(_Content);
			if (__Tab != null)
				__Tab.OnClosed();
		};
		TabWidgetInstance.AddTabCloseEvent(TabCloseListener);
		// Selection: broadcast a refresh for the newly shown project (empty
		// "New Tab" selections only update SelectedRoot).
		SelectionListener = (_Index, _Content) ->
		{
			SelectedRoot = _Content;
			GitDirTabButton __Tab = TabsByRoot.get(_Content);
			if (__Tab != null && __Tab.GetGitDirTarget() != null)
			{
				AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("project-tab-selected", __Tab.GetGitDirTarget());
			}
		};
		TabWidgetInstance.AddSelectionEvent(SelectionListener);
		// Drag-reorder: mirror the new tab order onto the engine's open-repository
		// list, which is what the session stores and what RestoreOpenTabs replays
		// (so a reordered strip comes back in the same order next launch). The
		// event fires once per slot crossing during a drag, so the list reorder
		// runs inline (cheap) while the session write is debounced to one call
		// per settled gesture.
		TabOrderSaveTimer = new PauseTransition(Duration.millis(TAB_ORDER_SAVE_DELAY_MS));
		TabOrderSaveTimer.setOnFinished(_Event -> AlphaEngine.Instance.SaveSession());
		TabOrderChangeListener = (_From, _To) -> SyncOpenGitDirOrderToTabs();
		TabWidgetInstance.AddTabOrderChangeEvent(TabOrderChangeListener);

		InitialTab = CreateProjectTab(null); // first AddTab is auto-selected

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

	private final ATabWidget TabWidgetInstance;
	/**
	 * Content-root → tab-object map. Keys are the stable root StackPanes
	 * registered with the ATabWidget, so entries stay valid across drag
	 * reorders; removal happens on tab close.
	 */
	private final Map<StackPane, GitDirTabButton> TabsByRoot;
	/** The content root of the currently selected tab (kept by the selection listener) */
	private Node SelectedRoot;
	/** The empty tab created at startup; reused for the first restored project */
	private final GitDirTabButton InitialTab;
	/** Strong reference to the open-project event handler; prevents WeakReference GC by the engine */
	private final IOpenGitDirEvent OpenGitDirEventListener;
	/** Strong reference to the close-project event handler; prevents WeakReference GC by the engine */
	private final ICloseGitDirEvent CloseGitDirEventListener;
	/** Strong reference to the tab "+" handler; prevents WeakReference GC by the widget */
	private final INewTabRequestEvent NewTabRequestListener;
	/** Strong reference to the tab "×" close handler; prevents WeakReference GC by the widget */
	private final ITabCloseEvent TabCloseListener;
	/** Strong reference to the tab selection handler; prevents WeakReference GC by the widget */
	private final ISubTabSelectionEvent SelectionListener;
	/** Strong reference to the tab drag-reorder handler; prevents WeakReference GC by the widget */
	private final ITabOrderChangeEvent TabOrderChangeListener;
	/** Debounces the session write that follows a drag-reorder: restarted on every slot crossing so a whole gesture costs one write */
	private final PauseTransition TabOrderSaveTimer;

	private final Map<Path, GitDirTabButton> OpenTabsByProjectPath;

	/**
	 * Create a tab object, register its stable root and append it to the
	 * widget. Does not select it (the caller decides; the very first tab is
	 * auto-selected by the widget).
	 */
	private GitDirTabButton CreateProjectTab(GitDir _GitDir)
	{
		GitDirTabButton __Tab = new GitDirTabButton(this, _GitDir);
		TabsByRoot.put(__Tab.GetRoot(), __Tab);
		TabWidgetInstance.AddTab(__Tab.GetTitle(), __Tab.GetRoot());
		return __Tab;
	}

	/**
	 * Look up the tab object for the given repository. Resolves the
	 * repository root path and delegates to the path overload.
	 *
	 * @param _GitDir the repository to look up, or null
	 * @return the matching tab, or null if the repository has no open tab
	 */
	public GitDirTabButton TryGetCurrentlyOpenGitDirWithTabButton(GitDir _GitDir)
	{
		if (_GitDir == null)
			return null;

		return TryGetOpenTabByPath(_GitDir.GetGitDirPath());
	}

	/**
	 * Look up the tab object for the given project path (already
	 * normalized via {@link GitDirFunction#TryFixGitDirPath}).
	 *
	 * @param _ProjectPath the path to look up, or null
	 * @return the matching tab, or null if the path has no open tab
	 */
	public GitDirTabButton TryGetOpenTabByPath(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return null;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		return OpenTabsByProjectPath.get(_GitPath);
	}

	/**
	 * Bring the given project tab to the front (select it in the widget).
	 * No-op if the tab was already closed.
	 */
	public void SelectProjectTab(GitDirTabButton _TabButton)
	{
		if (_TabButton == null)
			return;

		int __Index = TabWidgetInstance.IndexOf(_TabButton.GetRoot());
		if (__Index >= 0)
			TabWidgetInstance.SelectTab(__Index);
	}

	/**
	 * @return the tab object of the currently selected tab, or null if
	 *         nothing is selected (empty window)
	 */
	public GitDirTabButton TryGetSelectedProjectTab()
	{
		if (SelectedRoot == null)
			return null;

		return TabsByRoot.get(SelectedRoot);
	}

	/**
	 * Push a fresh label for the given tab into the widget. Resolves the
	 * live index at invocation time so retitles survive reorder/close.
	 */
	public void UpdateProjectTabTitle(GitDirTabButton _TabButton)
	{
		int __Index = TabWidgetInstance.IndexOf(_TabButton.GetRoot());
		if (__Index >= 0)
			TabWidgetInstance.SetTabTitle(__Index, _TabButton.GetTitle());
	}

	/**
	 * Mirror the current tab order onto the engine's open-repository list, so
	 * "open repositories" means "open tabs, left to right". Handles a
	 * drag-reorder: the widget has already completed the permutation when this
	 * runs, so reading {@link ATabWidget#GetTabContents()} yields the new
	 * sequence.
	 * <p>
	 * Tabs without a repository (an empty "New Tab" still showing
	 * {@link com.gitalpha.UI.GitDirTab.ProjectBrowser}, or one whose project
	 * widget is still being built on the next pulse) contribute no entry, so a
	 * repository can never be dropped from the open list by a partial view —
	 * the engine appends the unmentioned ones after the ordered sequence.
	 * The session write is deferred through {@link #TabOrderSaveTimer} because
	 * a single drag fires this once per slot crossing.
	 */
	private void SyncOpenGitDirOrderToTabs()
	{
		List<GitDir> __OrderedGitDirs = new ArrayList<>();
		for (Node __Content : TabWidgetInstance.GetTabContents())
		{
			GitDirTabButton __Tab = TabsByRoot.get(__Content);
			if (__Tab == null)
				continue;

			GitDir __GitDir = __Tab.GetGitDirTarget();
			if (__GitDir == null)
				continue;

			__OrderedGitDirs.add(__GitDir);
		}

		if (!AlphaEngine.Instance.ReorderOpenGitDirs(__OrderedGitDirs))
			return;

		// Restart on every crossing: the write happens once the strip settles.
		TabOrderSaveTimer.playFromStart();
	}

	/**
	 * Register the open-tab binding so future path lookups resolve to the
	 * tab object for the given repository. Called when a project is opened
	 * or restored; the inverse is {@link #UnbindOpenProjectTab(Path)}.
	 *
	 * @param _GitDir    the opened repository (must be non-null with a valid path)
	 * @param _TabButton the tab hosting the repository
	 */
	public void BindOpenProjectTab(GitDir _GitDir, GitDirTabButton _TabButton)
	{
		if (_GitDir == null || _TabButton == null)
			return;

		Path _GitPath = _GitDir.GetGitDirPath();
		if (_GitPath == null)
			return;

		OpenTabsByProjectPath.put(_GitPath, _TabButton);
	}

	/**
	 * Remove the open-tab binding for the given repository so future path
	 * lookups no longer resolve to a tab. Delegates to the path overload
	 * after resolving the repository root.
	 *
	 * @param _GitDir the repository whose binding to remove (null-safe)
	 */
	public void UnbindOpenProjectTab(GitDir _GitDir)
	{
		if (_GitDir == null)
			return;

		UnbindOpenProjectTab(_GitDir.GetGitDirPath());
	}

	/**
	 * Remove the open-tab binding for the given project path (already
	 * normalized via {@link GitDirFunction#TryFixGitDirPath}). The
	 * overloads keep the {@link Path} and {@link GitDir} entry points
	 * symmetric.
	 *
	 * @param _ProjectPath the path whose binding to remove (null-safe)
	 */
	public void UnbindOpenProjectTab(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		OpenTabsByProjectPath.remove(_GitPath);
	}

	/**
	 * Restore the previously open project tabs from the persisted session.
	 * The first restored project reuses the startup empty tab; subsequent
	 * projects get fresh tabs appended before the {@code "+"} affix.
	 */
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

			// Reuse the initial empty tab for the first restored project;
			// later projects get fresh tabs appended before the "+" affix.
			GitDirTabButton __TabButton = (i == 0) ? InitialTab : CreateProjectTab(null);
			__TabButton.OpenProject(__GitDir);
		}
	}
}
