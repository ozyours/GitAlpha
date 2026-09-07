package com.gitalpha.UI.Components;

import javafx.scene.control.ContextMenu;

/**
 * Themed context menu: a convenience {@link ContextMenu} subclass that
 * carries the {@code .a-context-menu} style class for programmatic
 * identification.
 * <p>
 * Context-menu styling is universal: the scene-level stylesheet
 * ({@link com.gitalpha.Theme.Skin.BaseSkin}) targets the built-in
 * {@code .context-menu} class with palette-driven CSS rules, so every
 * context menu in the app — right-click menus, menu-bar drop-downs, and
 * any other {@code ContextMenu} — is themed automatically from the owner
 * scene's stylesheet. This class adds {@code .a-context-menu} as an
 * identifier but the styling does not depend on it.
 * <p>
 * Palette changes re-apply through the scene stylesheet, so no
 * {@link com.gitalpha.Theme.IThemeChangeEvent} listener is needed.
 */
public class AContextMenu extends ContextMenu
{
	/**
	 * Create a themed context menu with the {@code .a-context-menu} style class.
	 */
	public AContextMenu()
	{
		super();
		getStyleClass().add("a-context-menu");
	}

	/**
	 * Create a themed context menu pre-populated with the given items.
	 *
	 * @param _Items the menu items to add
	 */
	public AContextMenu(javafx.scene.control.MenuItem... _Items)
	{
		super(_Items);
		getStyleClass().add("a-context-menu");
	}
}
