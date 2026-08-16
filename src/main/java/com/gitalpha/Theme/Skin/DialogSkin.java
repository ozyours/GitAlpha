package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.EButtonVariant;
import com.gitalpha.Type.ThemeColor;
import com.gitalpha.Theme.ThemeManager;

import java.util.Map;

/**
 * The message-box (dialog) skin applied to a dialog pane by
 * {@link ThemeManager#ApplyThemeToDialog}: palette background with a border
 * hairline, a secondary header panel with the bold palette header text,
 * palette content text, buttons matching the app's NORMAL themed buttons
 * (the default button — e.g. OK — is filled with the accent/primary and
 * white text) and a text field matching the app's text-input skin for input
 * dialogs. Colors are inlined because the skin is attached to the pane node,
 * not the scene, so the scene's {@code -gitalpha-*} lookups would not
 * resolve (same convention as the button/text-field skins).
 * <p>
 * Button selectors mirror Modena's full
 * {@code .dialog-pane > .button-bar > .container > .button} path so the
 * palette values win the cascade at equal specificity.
 * <p>
 * Placeholder order: pane background, pane border, header background, header
 * text, content text, the six NORMAL button colors (background, text, border,
 * hover, pressed, focus border), default button background, default button
 * border, default hover, text-field background, text-field border,
 * text-field text, text-field prompt, text-field selection (active
 * highlight), text-field focus border.
 */
public final class DialogSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.dialog-pane {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-border-color: %s;
			    -fx-border-width: 1;
			    -fx-padding: 12;
			}
			.dialog-pane:header .header-panel {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-padding: 0 0 8 0;
			}
			.dialog-pane:header .header-panel .label {
			    -fx-text-fill: %s;
			    -fx-font-size: 15px;
			    -fx-font-weight: bold;
			}
			.dialog-pane > .content.label {
			    -fx-text-fill: %s;
			    -fx-font-size: 12px;
			}
			.dialog-pane > .button-bar > .container > .button {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-text-fill: %s;
			    -fx-border-color: %s;
			    -fx-border-radius: 4;
			    -fx-border-insets: 0;
			    -fx-padding: 4 12 4 12;
			    -fx-font-size: 12px;
			    -fx-cursor: hand;
			}
			.dialog-pane > .button-bar > .container > .button:hover {
			    -fx-background-color: %s;
			}
			.dialog-pane > .button-bar > .container > .button:pressed {
			    -fx-background-color: %s;
			}
			.dialog-pane > .button-bar > .container > .button:focused {
			    -fx-border-color: %s;
			}
			.dialog-pane > .button-bar > .container > .button:default {
			    -fx-background-color: %s;
			    -fx-text-fill: #ffffff;
			    -fx-border-color: %s;
			}
			.dialog-pane > .button-bar > .container > .button:default:hover {
			    -fx-background-color: %s;
			}
			.dialog-pane .text-field {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-border-color: %s;
			    -fx-border-radius: 4;
			    -fx-border-insets: 0;
			    -fx-text-fill: %s;
			    -fx-prompt-text-fill: %s;
			    -fx-highlight-fill: %s;
			    -fx-highlight-text-fill: #ffffff;
			    -fx-cursor: text;
			}
			.dialog-pane .text-field:focused {
			    -fx-border-color: %s;
			}
			""";

	/**
	 * @return the dialog CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the twenty placeholder colors: pane/header/content colors, the
	 * six NORMAL button colors, the default (OK) button colors, and the
	 * text-field colors.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the twenty placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		// Build the full argument list explicitly: an array passed to a
		// varargs method is only spread when it is the sole trailing
		// argument, so the NORMAL button colors are unfolded here instead
		// of being handed over as one array (which would starve the format
		// of placeholders).
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String[] __ButtonColors = EButtonVariant.NORMAL.ResolveSkinColors(_Palette);
		String __Primary = _Palette.GetPrimaryColor().GetHex(__Lookup);
		return new Object[] {
				_Palette.GetBackgroundColor().GetHex(__Lookup), _Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetSecondaryColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup), _Palette.GetTextColor().GetHex(__Lookup),
				__ButtonColors[0], __ButtonColors[1], __ButtonColors[2],
				__ButtonColors[3], __ButtonColors[4], __ButtonColors[5],
				__Primary, __Primary, "derive(" + __Primary + ", -10%)",
				_Palette.GetSecondaryColor().GetHex(__Lookup), _Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup), _Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetActiveHighlightColor().GetHex(__Lookup), __Primary };
	}
}