package com.gitalpha.Theme.Themes;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

/**
 * Dark system theme: near-black background, light text, a lighter accent and
 * darker diff tints. The status bases stay close to the light theme's — the
 * derived shades differ because they mix toward the dark background instead
 * of white.
 */
public class DarkTheme extends ColorPalette
{
	/**
	 * Populate the 11 base colors that define this theme as sRGB float
	 * literals (each historical hex channel, scaled to 0-1). See the
	 * {@link ColorPalette} class doc for the derived-theme design.
	 */
	public DarkTheme()
	{
		SetPrimaryColor(new ThemeColor("Primary", 0.34509805f, 0.6509804f, 1.0f));
		SetSecondaryColor(new ThemeColor("Secondary", 0.08627451f, 0.105882354f, 0.13333334f));
		SetTextColor(new ThemeColor("Text", 0.9019608f, 0.92941177f, 0.9529412f));
		SetMutedTextColor(new ThemeColor("MutedText", 0.54509807f, 0.5803922f, 0.61960787f));
		SetActiveHighlightColor(new ThemeColor("ActiveHighlight", 0.34509805f, 0.6509804f, 1.0f));
		SetPassiveHighlightColor(new ThemeColor("PassiveHighlight", 0.12156863f, 0.43529412f, 0.92156863f));
		SetBorderColor(new ThemeColor("Border", 0.1882353f, 0.21176471f, 0.23921569f));
		SetBackgroundColor(new ThemeColor("Background", 0.050980393f, 0.06666667f, 0.09019608f));
		SetAddedColor(new ThemeColor("Added", 0.24705882f, 0.7254902f, 0.3137255f));
		SetRemovedColor(new ThemeColor("Removed", 0.972549f, 0.31764707f, 0.28627452f));
		SetModifiedColor(new ThemeColor("Modified", 0.8235294f, 0.6f, 0.13333334f));
	}
}
