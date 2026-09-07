package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.stage.Window;

/**
 * Themed menu bar: a {@link MenuBar} carrying a flat secondary-fill skin
 * (hover/opened menu buttons, palette labels, bottom hairline) as an inline
 * data-URI stylesheet, so it re-bakes whenever the palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer ({@link ThemeManager#GetMenuBarStylesheets}
 * / {@code ThemeSkin}), so every themed menu bar shares one style source, and
 * the skin re-applies on palette switches via {@link IThemeChangeEvent}.
 * <p>
 * Each {@link Menu}'s internal popup is tagged with the
 * {@code .a-context-menu} style class via {@link Menu#setOnShown} so the
 * scene-level context-menu stylesheet ({@code BaseSkin}) themes it. The tag
 * is applied once per show; {@code setOnShown} fires after the popup is
 * visible, so iterating {@link Window#getWindows()} finds the showing
 * {@link ContextMenu}.
 */
public class ATopMenuBar extends MenuBar implements IThemeChangeEvent
{
	/**
	 * Create a themed menu bar with the flat skin. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 */
	public ATopMenuBar()
	{
		super();
		getStyleClass().add("a-menu-bar");
		ApplySkin();
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Tag each child {@link Menu}'s internal popup with
	 * {@code .a-context-menu} so the scene-level context-menu stylesheet
	 * themes it. Called after menus are added to the bar (e.g. from
	 * {@code getMenus().addAll(...)}).
	 *
	 * @param _Menus the menus whose dropdowns should be themed
	 */
	protected void TagMenuPopups(Menu... _Menus)
	{
		for (Menu __Menu : _Menus)
		{
			__Menu.setOnShown(__Event ->
			{
				for (Window __Win : Window.getWindows())
				{
					if (__Win instanceof ContextMenu __Popup && __Popup.isShowing())
					{
						if (!__Popup.getStyleClass().contains("a-context-menu"))
							__Popup.getStyleClass().add("a-context-menu");
					}
				}
			});
		}
	}

	/**
	 * Theme-change push: re-bake the skin with the new palette's colors.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin();
	}

	/**
	 * Replace the inline skin stylesheet with one baked from the active
	 * palette. The data-URI URL changes whenever the colors do, so JavaFX
	 * re-parses the new skin (and keeps the old one cached until it is
	 * dropped).
	 */
	private void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetMenuBarStylesheets());
	}
}