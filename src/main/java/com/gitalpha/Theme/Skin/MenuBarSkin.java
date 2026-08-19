package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The menu-bar skin applied to {@link com.gitalpha.UI.Components.ATopMenuBar}:
 * a flat background-fill strip with the menu buttons kept transparent until
 * hovered (passive highlight) or opened (active highlight). Labels follow the
 * palette text color; the opened button switches to white text so it reads
 * against the active-highlight fill. A hairline border runs along the bottom
 * edge, separating the strip from the content below.
 * <p>
 * Selector chains mirror Modena's full {@code .menu-bar > .container >
 * .menu-button} path so the palette values win the cascade at equal
 * specificity.
 * <p>
 * The drop-down context menu is intentionally NOT styled here: it lives in
 * its own scene that node-level stylesheets cannot reach, so its rules are
 * baked into the scene base stylesheet instead (see {@link BaseSkin}).
 * <p>
 * Placeholder order: strip background, bottom hairline, menu-button label,
 * hover background, focused label text, open background.
 */
public final class MenuBarSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-menu-bar {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-padding: 0 8 0 8;
			    -fx-border-color: transparent transparent %s transparent;
			    -fx-border-width: 0 0 1 0;
			}
			.a-menu-bar > .container > .menu-button {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-padding: 4 10 4 10;
			}
			.a-menu-bar > .container > .menu-button > .label {
			    -fx-text-fill: %s;
			    -fx-font-size: 12px;
			}
			.a-menu-bar > .container > .menu-button:hover {
			    -fx-background-color: %s;
			}
			/* Kill Modena's selection-bar fill on the focused button (its
			   .menu-bar > .container > .menu-button:focused rule would otherwise
			   paint a blue selection highlight that the hover/showing overrides
			   leave uncovered). */
			.a-menu-bar > .container > .menu-button:focused {
			    -fx-background-color: transparent;
			}
			.a-menu-bar > .container > .menu-button:focused > .label {
			    -fx-text-fill: %s;
			}
			.a-menu-bar > .container > .menu-button:showing {
			    -fx-background-color: %s;
			}
			.a-menu-bar > .container > .menu-button:showing > .label {
			    -fx-text-fill: #ffffff;
			}
			""";

	/**
	 * @return the menu-bar CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the six placeholder colors: the strip fill, the bottom hairline,
	 * the label text, the hover background (passive highlight), the focused
	 * label text (kept on the palette text so the label does not flip to white
	 * while the focused background is transparent), and the open background
	 * (active highlight).
	 *
	 * @param _Palette the palette to read colors from
	 * @return the six placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetBackgroundColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetActiveHighlightColor().GetHex(__Lookup) };
	}
}