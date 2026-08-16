package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The standalone scrollbar skin, applied to every themed
 * {@link com.gitalpha.UI.Components.AScrollBar}: a minimalist bar matching
 * the list-view skin's own scrollbars — transparent track, a thin rounded
 * thumb from the palette's muted-text slot, and no increment/decrement
 * arrows. Mirror of the {@code .a-list-view .scroll-bar} rules in
 * {@link ListViewSkin}, scoped to the {@code .a-scroll-bar} class because
 * the standalone bar lives outside any list (e.g. the diff viewer's bottom
 * pan bar) and carries this stylesheet on its own node.
 * <p>
 * Placeholder order: thumb, hover thumb, pressed thumb. Hover/pressed shades
 * are computed in CSS with {@code derive()} so the skin needs nothing but the
 * palette hex values.
 */
public final class ScrollBarSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-scroll-bar {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0;
			}
			.a-scroll-bar:vertical {
			    -fx-pref-width: 8;
			}
			.a-scroll-bar:horizontal {
			    -fx-pref-height: 8;
			}
			.a-scroll-bar > .thumb {
			    -fx-background-color: %s;
			    -fx-background-insets: 1;
			    -fx-background-radius: 3;
			}
			.a-scroll-bar > .thumb:hover {
			    -fx-background-color: %s;
			}
			.a-scroll-bar > .thumb:pressed {
			    -fx-background-color: %s;
			}
			.a-scroll-bar > .increment-button,
			.a-scroll-bar > .decrement-button {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0;
			    -fx-pref-width: 0;
			    -fx-pref-height: 0;
			}
			.a-scroll-bar > .increment-button > .increment-arrow,
			.a-scroll-bar > .decrement-button > .decrement-arrow {
			    -fx-background-color: transparent;
			    -fx-padding: 0;
			}
			""";

	/**
	 * @return the standalone scrollbar CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the three placeholder colors: the thumb with its
	 * {@code derive()} hover/pressed shades.
	 *
	 * @param _Palette the palette to read colors from
	 * @return thumb, hover thumb, pressed thumb
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Thumb = _Palette.GetMutedTextColor().GetHex(__Lookup);
		return new Object[] {
				__Thumb, "derive(" + __Thumb + ", -15%)", "derive(" + __Thumb + ", -25%)" };
	}
}