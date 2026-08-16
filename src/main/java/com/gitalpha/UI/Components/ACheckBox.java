package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.CheckBox;

/**
 * Themed check box: a {@link CheckBox} carrying a flat, minimalist skin
 * (subtle secondary fill + border, accent-filled checked state, white mark,
 * accent focus ring, pointer cursor) as an inline data-URI stylesheet, so it
 * re-bakes whenever the palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer
 * ({@link ThemeManager#GetCheckBoxStylesheets} / {@code ThemeSkin}), so every
 * themed check box shares one style source, and the skin re-applies on
 * palette switches via {@link IThemeChangeEvent}.
 */
public class ACheckBox extends CheckBox implements IThemeChangeEvent
{
	/**
	 * Create a themed check box with the minimalist skin. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 */
	public ACheckBox()
	{
		super();
		getStyleClass().add("a-check-box");
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
		getStylesheets().addAll(ThemeManager.Instance.GetCheckBoxStylesheets());
	}
}
