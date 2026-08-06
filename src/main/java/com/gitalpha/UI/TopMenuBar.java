package com.gitalpha.UI;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * Top application menu bar (File / Git / Settings / Help). All entries are
 * placeholders for planned work (see ROADMAP.md) — invoking one shows a
 * "not implemented yet" notice instead of running any real command.
 */
public class TopMenuBar extends MenuBar
{
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
				PlaceholderItem("Stash..."),
				PlaceholderItem("Tags..."));
		return __GitMenu;
	}

	private static Menu CreateSettingsMenu()
	{
		Menu __SettingsMenu = new Menu("Settings");
		__SettingsMenu.getItems().add(PlaceholderItem("Settings..."));
		return __SettingsMenu;
	}

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
