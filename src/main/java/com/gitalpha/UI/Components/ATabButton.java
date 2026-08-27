package com.gitalpha.UI.Components;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;

/**
 * Themed tab button: extends {@link Button} directly (not {@code AButton})
 * and carries the {@code a-tab-button} style class. It owns no stylesheet of
 * its own — it is always hosted under an {@link ATabWidget}, whose single
 * cascading tab skin ({@code TabButtonSkin}) styles it, including the
 * variant metrics ({@code NORMAL} vs {@code SMALL}) of that strip. Keeping
 * the styling on the widget matters for the compound face: a node-level
 * stylesheet would outrank the ancestor's cascade and force main-tab metrics
 * onto the {@code ×} close faces of small sub-tab strips.
 * <p>
 * The selected state is a custom {@code :selected} pseudo-class toggled via
 * {@link #SetSelected(boolean)}; the CSS itself is owned centrally by the
 * theme layer ({@code TabButtonSkin}), so every tab button shares one style
 * source and re-applies on palette switches through its hosting widget's
 * {@code IThemeChangeEvent} registration.
 */
public class ATabButton extends Button
{
	/** The pseudo-class marking the selected (active) tab */
	private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

	/**
	 * Create a tab button with the {@code a-tab-button} style class. Must be
	 * hosted under an {@link ATabWidget} (or another ancestor carrying the
	 * cascading tab stylesheet) to be visually themed.
	 *
	 * @param _Text the button label
	 */
	public ATabButton(String _Text)
	{
		super(_Text);
		getStyleClass().add("a-tab-button");
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
}
