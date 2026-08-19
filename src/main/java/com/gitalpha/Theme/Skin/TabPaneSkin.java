package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The flat tab-pane skin: the header strip behind the buttons uses
 * Background 2 (the window-level backdrop, same as the scene root), while
 * inactive tabs are derived darker and the active (selected) tab derived
 * brighter from Background 1 (the panel background), so the selected tab
 * reads as the lit one in both themes. Labels follow the palette (muted for
 * inactive, body
 * text for active) and the close button is muted, accent on hover. The tab
 * button is roomier than Modena's default (generous vertical and horizontal
 * padding, so the label sits clear of the tab edges) and its label larger,
 * both set in {@code em} so they scale with the base font. Selector chains
 * mirror Modena's full paths so the palette values win the cascade at equal
 * specificity.
 * <p>
 * A hairline runs across the top of the content canvas, separating the tab
 * buttons from the content below.
 * <p>
 * Placeholder order: strip background, inactive tab, hover tab, active tab,
 * inactive label, active label, close button, close hover, header/content
 * separator. Shades are computed in CSS with {@code derive()} so the skin
 * needs nothing but the palette hex values.
 */
public final class TabPaneSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-tab-pane > .tab-header-area > .tab-header-background {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			}
			.a-tab-pane > .tab-header-area > .headers-region > .tab {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    /* Roomier than Modena's default: generous em padding makes the tab button bigger and keeps the label clear of the tab edges. */
			    -fx-padding: 0.28em 1.0em 0.27em 1.0em;
			}
			.a-tab-pane > .tab-header-area > .headers-region > .tab:hover {
			    -fx-background-color: %s;
			}
			.a-tab-pane > .tab-header-area > .headers-region > .tab:selected {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			}
			.a-tab-pane > .tab-header-area > .headers-region > .tab > .tab-container > .tab-label {
			    -fx-text-fill: %s;
			    /* Larger than Modena's default so the label reads clearly at the bigger button size. */
			    -fx-font-size: 1.2em;
			}
			.a-tab-pane > .tab-header-area > .headers-region > .tab:selected > .tab-container > .tab-label,
			.a-tab-pane > .tab-header-area > .headers-region > .tab:selected:focus > .tab-container > .tab-label {
			    -fx-text-fill: %s;
			}
			.a-tab-pane .tab-close-button {
			    -fx-background-color: %s;
			}
			.a-tab-pane .tab-close-button:hover {
			    -fx-background-color: %s;
			}
			.a-tab-pane > .tab-content-area {
			    -fx-border-color: %s transparent transparent transparent;
			    -fx-border-width: 1 0 0 0;
			}
			""";

	/**
	 * @return the tab-pane CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the nine placeholder colors: the strip background (Background 2),
	 * the {@code derive()}d tab shades from Background 1, the labels, the close
	 * button and the header/content separator.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the nine placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		// The strip behind the buttons is the window-level backdrop
		// (Background 2), so the empty header space matches the scene root;
		// the tab buttons themselves derive from Background 1 (the panel
		// background), so the buttons and the strip stay distinct.
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		return new Object[] {
				_Palette.GetBackground2Color().GetHex(__Lookup),
				"derive(" + __Bg + ", -12%)",
				"derive(" + __Bg + ", -6%)",
				"derive(" + __Bg + ", +12%)",
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup) };
	}
}