package com.gitalpha.Theme.Themes;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

/**
 * Light system theme: white background, near-black text, a light-blue accent
 * (a lighter take on #1a73e8) and a light-blue secondary fill, with
 * GitHub-style diff tints.
 */
public class LightTheme extends ColorPalette
{
	/**
	 * Populate the 11 base colors that define this theme as sRGB float
	 * literals (each historical hex channel, scaled to 0-1). See the
	 * {@link ColorPalette} class doc for the derived-theme design.
	 */
	public LightTheme()
	{
		SetPrimaryColor(new ThemeColor("Primary", 0.23529412f, 0.53333336f, 0.92156863f));
		SetSecondaryColor(new ThemeColor("Secondary", 0.8f, 0.8980392f, 1.0f));
		SetTextColor(new ThemeColor("Text", 0.12156863f, 0.13725491f, 0.15686275f));
		SetMutedTextColor(new ThemeColor("MutedText", 0.43137255f, 0.46666667f, 0.5058824f));
		SetActiveHighlightColor(new ThemeColor("ActiveHighlight", 0.23529412f, 0.53333336f, 0.92156863f));
		SetPassiveHighlightColor(new ThemeColor("PassiveHighlight", 0.8666667f, 0.95686275f, 1.0f));
		SetBorderColor(new ThemeColor("Border", 0.8156863f, 0.84313726f, 0.87058824f));
		SetBackgroundColor(new ThemeColor("Background", 1.0f, 1.0f, 1.0f));
		SetAddedColor(new ThemeColor("Added", 0.1764706f, 0.6431373f, 0.30588236f));
		SetRemovedColor(new ThemeColor("Removed", 0.8117647f, 0.13333334f, 0.18039216f));
		SetModifiedColor(new ThemeColor("Modified", 0.6039216f, 0.40392157f, 0.0f));
	}
}
