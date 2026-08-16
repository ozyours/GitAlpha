package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The minimalist check-box skin: a flat, single-layer box (no Modena gradient
 * stack) with a subtle secondary fill and border that fills with the
 * palette's active highlight when selected, a white check mark and an accent
 * focus ring. The check mark is an intentional literal: white reads on both
 * the light and dark accents (same convention as DANGER buttons' white
 * text).
 * <p>
 * Placeholder order: box background, box border, hover background (passive
 * highlight), hover border, pressed background, selected background (active
 * highlight), selected border, check mark, focus border.
 */
public final class CheckBoxSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-check-box {
			    -fx-padding: 0;
			    -fx-cursor: hand;
			}
			.a-check-box > .box {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 3;
			    -fx-border-color: %s;
			    -fx-border-insets: 0;
			    -fx-border-radius: 3;
			    -fx-padding: 2;
			}
			.a-check-box:hover > .box {
			    -fx-background-color: %s;
			    -fx-border-color: %s;
			}
			.a-check-box:armed > .box {
			    -fx-background-color: %s;
			}
			.a-check-box:selected > .box {
			    -fx-background-color: %s;
			    -fx-border-color: %s;
			}
			.a-check-box:selected > .box > .mark {
			    -fx-background-color: %s;
			}
			.a-check-box:focused > .box {
			    -fx-border-color: %s;
			}
			""";

	/**
	 * @return the check-box CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the nine placeholder colors: box fill/border, hover
	 * background/border, pressed background, selected background/border,
	 * the literal white mark and the focus border.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the nine placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetSecondaryColor().GetHex(__Lookup), _Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup), _Palette.GetPrimaryColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetActiveHighlightColor().GetHex(__Lookup), _Palette.GetPrimaryColor().GetHex(__Lookup),
				"#ffffff",
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}