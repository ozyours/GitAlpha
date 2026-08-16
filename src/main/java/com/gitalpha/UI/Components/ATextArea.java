package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.TextArea;

/**
 * Themed text area: a {@link TextArea} carrying a flat, minimalist skin
 * (secondary fill, palette border that turns accent on focus, muted prompt
 * text, accent selection highlight) as an inline data-URI stylesheet, so it
 * re-bakes whenever the palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer
 * ({@link ThemeManager#GetTextAreaStylesheets} / {@code ThemeSkin}), so every
 * themed text area shares one style source, and the skin re-applies on
 * palette switches via {@link IThemeChangeEvent}.
 */
public class ATextArea extends TextArea implements IThemeChangeEvent
{
	/**
	 * Create a themed text area with the minimalist skin. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 */
	public ATextArea()
	{
		super();
		InitSkin();
	}

	/**
	 * Create a themed text area pre-populated with the given text (mirrors
	 * {@link TextArea#TextArea(String)}). Registers as a theme listener so the
	 * skin re-bakes on palette switches.
	 *
	 * @param _Text the initial text
	 */
	public ATextArea(String _Text)
	{
		super(_Text);
		InitSkin();
	}

	/** Shared skin setup for both constructors. */
	private void InitSkin()
	{
		getStyleClass().add("a-text-area");
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
		getStylesheets().addAll(ThemeManager.Instance.GetTextAreaStylesheets());
	}
}
