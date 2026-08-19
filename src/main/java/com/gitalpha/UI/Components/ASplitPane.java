package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

/**
 * Themed split pane: a {@link SplitPane} carrying a flat skin (transparent
 * background, thin palette-border divider that widens to the passive highlight
 * on hover) as an inline data-URI stylesheet, so it re-bakes whenever the
 * palette changes.
 * <p>
 * Follows the {@link AButton} pattern: the skin is not baked here — it is
 * owned centrally by the theme layer ({@link ThemeManager#GetSplitPaneStylesheets}
 * / {@code ThemeSkin}), so every themed split pane shares one style source,
 * and the skin re-applies on palette switches via {@link IThemeChangeEvent}.
 */
public class ASplitPane extends SplitPane implements IThemeChangeEvent
{
	/**
	 * Create a themed split pane with the flat divider skin. Registers as a
	 * theme listener so the skin re-bakes on palette switches.
	 */
	public ASplitPane()
	{
		super();
		InitSkin();
	}

	/**
	 * Create a themed split pane pre-populated with the given items (mirrors
	 * {@link SplitPane#SplitPane(Node...)}). Registers as a theme listener so
	 * the skin re-bakes on palette switches.
	 *
	 * @param _Items the items to show initially
	 */
	public ASplitPane(Node... _Items)
	{
		super(_Items);
		InitSkin();
	}

	/** Shared skin setup for both constructors. */
	private void InitSkin()
	{
		getStyleClass().add("a-split-pane");
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
		getStylesheets().addAll(ThemeManager.Instance.GetSplitPaneStylesheets());
	}
}