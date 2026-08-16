package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

/**
 * Themed list view: a {@link ListView} that carries a flat palette
 * background (no alternating-row stripes) and a minimalist scrollbar skin
 * (thin, rounded, arrow-free) as an inline data-URI stylesheet, so both
 * re-bake whenever the palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer ({@link ThemeManager#GetListViewStylesheets}
 * / {@code ThemeSkin}), so every themed list view shares one style source, and
 * the skin re-applies on palette switches via {@link IThemeChangeEvent}.
 *
 * @param <T> the list item type
 */
public class AListView<T> extends ListView<T> implements IThemeChangeEvent
{
	/**
	 * Create a themed list view with the minimalist scrollbar skin. Registers
	 * as a theme listener so the skin re-bakes on palette switches.
	 */
	public AListView()
	{
		super();
		InitSkin();
	}

	/**
	 * Create a themed list view pre-populated with the given items (mirrors
	 * {@link ListView#ListView(ObservableList)}). Registers as a theme listener
	 * so the skin re-bakes on palette switches.
	 *
	 * @param _Items the items to show initially
	 */
	public AListView(ObservableList<T> _Items)
	{
		super(_Items);
		InitSkin();
	}

	/** Shared skin setup for both constructors. */
	private void InitSkin()
	{
		getStyleClass().add("a-list-view");
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
		getStylesheets().addAll(ThemeManager.Instance.GetListViewStylesheets());
	}
}
