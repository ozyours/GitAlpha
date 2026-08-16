package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.ScrollBar;

/**
 * Themed scroll bar: a {@link ScrollBar} carrying the minimalist scrollbar
 * skin (transparent track, thin rounded thumb, no increment/decrement arrows)
 * as an inline data-URI stylesheet, so it re-bakes whenever the palette
 * changes. Used for standalone scrollbars that live outside a themed list —
 * e.g. the diff viewer's bottom horizontal pan bar — so they match the list
 * views' own scrollbars.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer
 * ({@link ThemeManager#GetScrollBarStylesheets} / {@code ThemeSkin}), so every
 * themed scroll bar shares one style source, and the skin re-applies on
 * palette switches via {@link IThemeChangeEvent}.
 */
public class AScrollBar extends ScrollBar implements IThemeChangeEvent
{
	/**
	 * Create a themed scroll bar with the minimalist skin. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 */
	public AScrollBar()
	{
		super();
		getStyleClass().add("a-scroll-bar");
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
		getStylesheets().addAll(ThemeManager.Instance.GetScrollBarStylesheets());
	}
}