package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;

/**
 * Themed tab button: extends {@link Button} directly (not {@code AButton})
 * and carries style class {@code a-tab-button} with a full flat tab-header
 * skin — square corners, generous {@code em} padding, larger label, and
 * inactive (-12%) / hover (-6%) / selected (+12%) shades derived from the
 * palette background plus muted vs text labels, hairline, focus ring and
 * pressed shade — as an inline data-URI stylesheet baked via
 * {@link ThemeManager#GetSubTabButtonStylesheets()}, so it re-bakes whenever
 * the palette changes.
 * <p>
 * The selected state is a custom {@code :selected} pseudo-class toggled via
 * {@link #SetSelected(boolean)}; the CSS itself is owned centrally by the
 * theme layer ({@code SubTabButtonSkin}), so every tab button shares one
 * style source and re-applies on palette switches via {@link IThemeChangeEvent}.
 */
public class ATabButton extends Button implements IThemeChangeEvent
{
	/** The pseudo-class marking the selected (active) tab */
	private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

	/**
	 * Create a tab button with style class {@code a-tab-button} and the full
	 * flat tab skin baked from the active palette. Registers as a theme
	 * listener so the skin re-bakes on palette switches.
	 *
	 * @param _Text the tab label
	 */
	public ATabButton(String _Text)
	{
		super(_Text);
		getStyleClass().add("a-tab-button");
		ApplySkin();
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Mark this button as the selected (active) tab. The {@code :selected}
	 * pseudo-class drives the brighter selected skin; unselecting restores the
	 * inactive tab look.
	 *
	 * @param _Selected true to select, false to deselect
	 */
	public void SetSelected(boolean _Selected)
	{
		pseudoClassStateChanged(SELECTED, _Selected);
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
	 * Replace the inline skin stylesheet with the full tab skin baked from
	 * the active palette via {@link ThemeManager#GetSubTabButtonStylesheets()}.
	 * The data-URI URL changes whenever the colors do, so JavaFX re-parses
	 * the new skin.
	 */
	private void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetSubTabButtonStylesheets());
	}
}
