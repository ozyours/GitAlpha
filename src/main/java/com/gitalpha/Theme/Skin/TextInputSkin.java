package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The text-input skin shared by the text field and the text area: a flat
 * single-layer fill (no Modena shadow/border stack) with a palette border
 * that switches to the accent (primary) on focus, palette text, muted prompt
 * text and an active-highlight selection with white text (same white-on-accent
 * convention as the check mark). Text areas additionally flatten their
 * internal scroll-pane/viewport/content to transparent so the control's own
 * fill shows through.
 * <p>
 * Placeholder order: fill, border, text, prompt text, selection fill, focus
 * border.
 */
public final class TextInputSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-text-field,
			.a-text-area {
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
			.a-text-field:focused,
			.a-text-area:focused {
			    -fx-border-color: %s;
			}
			.a-text-field:disabled,
			.a-text-area:disabled {
			    -fx-opacity: 0.4;
			}
			.a-text-area > .scroll-pane {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			}
			.a-text-area > .scroll-pane > .viewport,
			.a-text-area > .scroll-pane > .viewport > .content {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			}
			""";

	/**
	 * @return the text-input CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the six placeholder colors: fill, border, text, prompt text,
	 * selection fill, focus border.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the six placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetSecondaryColor().GetHex(__Lookup), _Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup), _Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetActiveHighlightColor().GetHex(__Lookup), _Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}