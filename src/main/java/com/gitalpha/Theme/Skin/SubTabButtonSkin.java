package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * Tab style for the sub-tab header buttons ({@link com.gitalpha.UI.Components.ATabButton}):
 * the buttons render as flat tabs — square corners, generous {@code em} padding
 * and a larger label — sitting on the window backdrop (Background 2), while
 * the tab faces themselves derive from the panel background (Background 1):
 * inactive tabs darker (-12%), hover warmer (-6%), the selected tab brighter
 * (+12%) and labelled in the body text colour (unselected in muted), with a
 * pressed shade and content hairline. Mirrors the palette treatment of
 * {@link TabPaneSkin} so the button header is visually interchangeable with a
 * real {@code TabPane} header. No focus ring is drawn — tab buttons are
 * navigation controls, not primary actions.
 * <p>
 * Eight {@code %s} placeholders in CSS order: header strip Background2,
 * inactive tab derive -12%, inactive label muted, hover tab derive -6%,
 * selected tab derive +12%, selected label text, pressed shade, content
 * hairline border. Shades are computed in CSS with {@code derive()} so the
 * skin needs only palette hex values plus derived expressions.
 */
public final class SubTabButtonSkin extends ThemeSkin
{
	/**
	 * CSS template with eight {@code %s} placeholders in CSS order: header
	 * strip Background2, inactive tab, inactive label, hover tab, selected
	 * tab, selected label, pressed shade, and content hairline — as consumed
	 * by {@link #GetColorArguments}.
	 */
	private static final String CSS_FORMAT = """
			.a-tab-header {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			}
			.a-tab-button {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-border-radius: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0.28em 1.0em 0.27em 1.0em;
			    -fx-font-size: 1.2em;
			    -fx-cursor: hand;
			}
			.a-tab-button:hover {
			    -fx-background-color: %s;
			}
			.a-tab-button:selected {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-text-fill: %s;
			}
			.a-tab-button:pressed {
			    -fx-background-color: %s;
			}
			.a-tab-content {
			    -fx-border-color: %s transparent transparent transparent;
			    -fx-border-width: 1 0 0 0;
			}
			""";

	/**
	 * @return the tab-button CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the eight {@link #CSS_FORMAT} placeholders in CSS order: header
	 * strip Background2, inactive tab derive -12% from Background, inactive
	 * label muted, hover tab derive -6%, selected tab derive +12%, selected
	 * label text, pressed shade, and content hairline border.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the eight placeholder values in CSS order
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		return new Object[] {
				_Palette.GetBackground2Color().GetHex(__Lookup),
				"derive(" + __Bg + ", -12%)",
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				"derive(" + __Bg + ", -6%)",
				"derive(" + __Bg + ", +12%)",
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup) };
	}
}
