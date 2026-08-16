package com.gitalpha.Theme;

/**
 * Light system theme: white background, near-black text, a slightly light
 * blue accent derived from #1a73e8 and a light-blue secondary fill, with
 * GitHub-style diff tints.
 */
public class LightTheme extends ColorPalette
{
	/**
	 * Populate the 11 base colors that define this theme (see the ColorPalette class doc for the derived-theme design).
	 */
	public LightTheme()
	{
		SetPrimaryColor("#3c88eb");
		SetSecondaryColor("#cce5ff");
		SetTextColor("#1f2328");
		SetMutedTextColor("#6e7781");
		SetActiveHighlightColor("#3c88eb");
		SetPassiveHighlightColor("#ddf4ff");
		SetBorderColor("#d0d7de");
		SetBackgroundColor("#ffffff");
		SetAddedColor("#2da44e");
		SetRemovedColor("#cf222e");
		SetModifiedColor("#9a6700");
	}
}
