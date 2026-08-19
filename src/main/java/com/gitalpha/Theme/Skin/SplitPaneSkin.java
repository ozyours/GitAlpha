package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The flat split-pane skin: the split pane itself is transparent (the scene
 * background shows through) and its divider renders as a thin vertical
 * hairline in the palette border color — the visible border between the two
 * panes — widening to the passive highlight on hover so the draggable divider
 * reads as interactive. The divider's thickness comes from its padding
 * ({@code 0 1 0 1}), so only the background color is baked here. Selector
 * chains mirror Modena's full paths so the palette values win the cascade at
 * equal specificity.
 * <p>
 * Placeholder order: divider border, divider hover.
 */
public final class SplitPaneSkin extends ThemeSkin
{
	/**
	 * Two placeholders in order: divider border color, divider hover color.
	 * The divider's thickness comes from its padding ({@code 0 1 0 1}) in the
	 * format, not from a baked size, so the whole skin is color-only.
	 */
	private static final String CSS_FORMAT = """
			.a-split-pane {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0;
			}
			.a-split-pane > .split-pane-divider {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-padding: 0 1 0 1;
			}
			.a-split-pane > .split-pane-divider:hover {
			    -fx-background-color: %s;
			}
			""";

	/**
	 * @return the split-pane CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the two placeholder colors: the divider's resting border color
	 * and its passive highlight on hover.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the two placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup) };
	}
}