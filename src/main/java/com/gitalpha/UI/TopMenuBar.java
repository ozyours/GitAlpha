package com.gitalpha.UI;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import com.gitalpha.UI.Stash.StashWidget;
import javafx.scene.control.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Top application menu bar (File / Git / Settings / Help). Entries that have
 * been implemented (Stash) open their respective UI; remaining entries are
 * placeholders that show a "not implemented yet" notice.
 */
public class TopMenuBar extends MenuBar
{
	/** Tracks open stash windows per repository to prevent duplicate windows */
	private static final Map<GitDir, StashWidget> OpenStashWindows = new HashMap<>();

	/** Assemble the four top-level menus (File, Git, Settings, Help) in order. */
	public TopMenuBar()
	{
		super();
		getMenus().addAll(CreateFileMenu(), CreateGitMenu(), CreateSettingsMenu(), CreateHelpMenu());
	}

	private static Menu CreateFileMenu()
	{
		Menu __FileMenu = new Menu("File");
		__FileMenu.getItems().addAll(
				PlaceholderItem("Open Project..."),
				PlaceholderItem("Close Tab"),
				new SeparatorMenuItem(),
				PlaceholderItem("Quit"));
		return __FileMenu;
	}

	/** Build the Git menu: fetch/pull/push, branch management, stash, and tags. */
	private static Menu CreateGitMenu()
	{
		Menu __GitMenu = new Menu("Git");
		__GitMenu.getItems().addAll(
				PlaceholderItem("Fetch"),
				PlaceholderItem("Pull"),
				PlaceholderItem("Push"),
				new SeparatorMenuItem(),
				PlaceholderItem("Create Branch..."),
				PlaceholderItem("Delete Branch"),
				new SeparatorMenuItem(),
				CreateStashItem(),
				PlaceholderItem("Tags..."));
		return __GitMenu;
	}

	/**
	 * Opens the stash management window for the currently selected project.
	 * If a stash window is already open for that repository, brings it to
	 * front instead of creating a duplicate.
	 */
	private static MenuItem CreateStashItem()
	{
		MenuItem __Item = new MenuItem("Stash...");
		__Item.setOnAction(__Event ->
		{
			GitDir __GitDir = GetSelectedGitDir();
			if (__GitDir == null)
			{
				PlaceholderNotice.ShowNotImplemented("Stash (no project open)");
				return;
			}
			StashWidget __Existing = OpenStashWindows.get(__GitDir);
			if (__Existing != null && __Existing.isShowing())
			{
				__Existing.toFront();
				return;
			}
			StashWidget __Widget = new StashWidget(__GitDir);
			OpenStashWindows.put(__GitDir, __Widget);
			__Widget.setOnHidden(__e -> OpenStashWindows.remove(__GitDir));
		});
		return __Item;
	}

	/**
	 * Returns the {@link GitDir} of the currently selected tab, or null if the
	 * active tab has no open project.
	 */
	private static GitDir GetSelectedGitDir()
	{
		if (AlphaUI.Instance == null)
			return null;
		var __TabPane = AlphaUI.Instance.GetTabPaneInstance();
		if (__TabPane == null)
			return null;
		var __Selected = __TabPane.getSelectionModel().getSelectedItem();
		if (__Selected instanceof GitDirTabButton __Tab)
			return __Tab.GetGitDirTarget();
		return null;
	}

	/** Build the Settings menu (currently a single placeholder entry). */
	private static Menu CreateSettingsMenu()
	{
		Menu __SettingsMenu = new Menu("Settings");
		__SettingsMenu.getItems().add(PlaceholderItem("Settings..."));
		return __SettingsMenu;
	}

	/** Build the Help menu (currently a single About placeholder). */
	private static Menu CreateHelpMenu()
	{
		Menu __HelpMenu = new Menu("Help");
		__HelpMenu.getItems().add(PlaceholderItem("About Git Alpha"));
		return __HelpMenu;
	}

	/**
	 * Build a menu entry that does nothing yet: the action only reports that
	 * the feature is a planned placeholder.
	 */
	private static MenuItem PlaceholderItem(String _Text)
	{
		MenuItem __Item = new MenuItem(_Text);
		__Item.setOnAction(__Event -> PlaceholderNotice.ShowNotImplemented(_Text));
		return __Item;
	}
}
