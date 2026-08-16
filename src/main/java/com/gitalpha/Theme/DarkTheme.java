package com.gitalpha.Theme;

/**
 * Dark system theme: near-black background, light text, a lighter accent and
 * darker diff tints. The status bases stay close to the light theme's — the
 * derived shades differ because they mix toward the dark background instead
 * of white.
 */
public class DarkTheme extends ColorPalette
{
	/**
	 * Populate the 11 base colors that define this theme (see the class doc for the derived-theme design).
	 */
	public DarkTheme()
	{
		SetPrimaryColor("#58a6ff");
		SetSecondaryColor("#161b22");
		SetTextColor("#e6edf3");
		SetMutedTextColor("#8b949e");
		SetActiveHighlightColor("#58a6ff");
		SetPassiveHighlightColor("#1f6feb");
		SetBorderColor("#30363d");
		SetBackgroundColor("#0d1117");
		SetAddedColor("#3fb950");
		SetRemovedColor("#f85149");
		SetModifiedColor("#d29922");
	}
}
