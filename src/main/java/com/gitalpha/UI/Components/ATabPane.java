package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * Themed tab pane: a {@link TabPane} carrying a flat tab-header skin (active
 * tab brighter, inactive tabs darker — both derived from the palette's
 * background) as an inline data-URI stylesheet, so it re-bakes whenever the
 * palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer ({@link ThemeManager#GetTabPaneStylesheets}
 * / {@code ThemeSkin}), so every themed tab pane shares one style source, and
 * the skin re-applies on palette switches via {@link IThemeChangeEvent}.
 *
 * @deprecated Superseded by {@link ATabWidget}, which provides the same
 *             themed tab strip without JavaFX TabPane machinery plus
 *             optional add/close/reorder support. No live consumers remain
 *             (the outer project tab pane migrated to {@code ATabWidget});
 *             kept only so the paired {@link ThemeManager#GetTabPaneStylesheets}
 *             / {@code TabPaneSkin} chain stays compilable until removal.
 */
@Deprecated(forRemoval = true)
public class ATabPane extends TabPane implements IThemeChangeEvent
{
	/**
	 * Create a themed tab pane with the flat tab-header skin. Registers as a
	 * theme listener so the skin re-bakes on palette switches.
	 */
	public ATabPane()
	{
		super();
		InitSkin();
	}

	/**
	 * Create a themed tab pane pre-populated with the given tabs (mirrors
	 * {@link TabPane#TabPane(Tab...)}). Registers as a theme listener so the
	 * skin re-bakes on palette switches.
	 *
	 * @param _Tabs the tabs to show initially
	 */
	public ATabPane(Tab... _Tabs)
	{
		super(_Tabs);
		InitSkin();
	}

	/** Shared skin setup for both constructors. */
	private void InitSkin()
	{
		getStyleClass().add("a-tab-pane");
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
		getStylesheets().addAll(ThemeManager.Instance.GetTabPaneStylesheets());
	}
}
