package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;

/**
 * Themed combo box: a {@link ComboBox} carrying a flat, minimalist skin
 * (secondary fill, palette border that turns accent on focus, palette text and
 * a muted down arrow) as an inline data-URI stylesheet, so it re-bakes whenever
 * the palette changes.
 * <p>
 * The drop-down popup is themed at the scene level (baked into
 * {@link ThemeManager#RegisterScene}'s base stylesheet), because the popup
 * lives in its own scene that node-level stylesheets cannot reach — popups
 * inherit the owner scene's stylesheets, so the {@code .combo-box-popup} rules
 * resolve their colors from the scene's {@code -gitalpha-*} variables.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer
 * ({@link ThemeManager#GetComboBoxStylesheets} / {@code ThemeSkin}), so every
 * themed combo box shares one style source, and the skin re-applies on palette
 * switches via {@link IThemeChangeEvent}.
 *
 * @param <T> the item type
 */
public class AComboBox<T> extends ComboBox<T> implements IThemeChangeEvent
{
	/**
	 * Create a themed combo box with the minimalist skin. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 */
	public AComboBox()
	{
		super();
		InitSkin();
	}

	/**
	 * Create a themed combo box pre-populated with the given items (mirrors
	 * {@link ComboBox#ComboBox(ObservableList)}). Registers as a theme listener
	 * so the skin re-bakes on palette switches.
	 *
	 * @param _Items the items to show initially
	 */
	public AComboBox(ObservableList<T> _Items)
	{
		super(_Items);
		InitSkin();
	}

	/** Shared skin setup for both constructors. */
	private void InitSkin()
	{
		getStyleClass().add("a-combo-box");
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
		getStylesheets().addAll(ThemeManager.Instance.GetComboBoxStylesheets());
	}
}