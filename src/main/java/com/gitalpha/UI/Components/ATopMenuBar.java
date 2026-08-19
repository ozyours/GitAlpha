package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.MenuBar;

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
 * The drop-down context menu (menu items) is styled by the scene-level base
 * stylesheet ({@code BaseSkin}) because popups live in their own scene that
 * node-level stylesheets cannot reach — the same convention as the combo-box
 * popup.
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