package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The combo-box skin: a flat single-layer fill (no Modena shadow/border
 * stack) with a palette border that switches to the accent (primary) on
 * focus — the same text-input skin family, with the value cell and the arrow
 * button flattened to transparent so the control's own fill shows through.
 * The arrow is a plain muted triangle (Modena's shape, recolored).
 * <p>
 * The drop-down popup is intentionally NOT styled here: it lives in its own
 * scene that node-level stylesheets cannot reach, so its rules are baked
 * into the scene base stylesheet instead (see {@link BaseSkin}).
 * <p>
 * Placeholder order: fill, border, text, arrow, focus border.
 */
public final class ComboBoxSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-combo-box {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-border-color: %s;
			    -fx-border-radius: 4;
			    -fx-border-insets: 0;
			    -fx-font-size: 12px;
			    -fx-cursor: hand;
			}
			.a-combo-box > .list-cell {
			    -fx-background-color: transparent;
			    -fx-text-fill: %s;
			    -fx-padding: 4 8 4 8;
			}
			.a-combo-box > .arrow-button {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0 8 0 0;
			}
			.a-combo-box > .arrow-button > .arrow {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-padding: 4;
			    -fx-shape: "M 0 0 H 7 L 3.5 4 z";
			}
			.a-combo-box:focused {
			    -fx-border-color: %s;
			}
			.a-combo-box:disabled {
			    -fx-opacity: 0.4;
			}
			""";

	/**
	 * @return the combo-box CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the five placeholder colors: fill, border, text, arrow, focus
	 * border.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the five placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetSecondaryColor().GetHex(__Lookup), _Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup), _Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}