package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The list-view skin: a flat palette background (cells, odd cells and the
 * border ring all use the background colour, so even/odd stripes and the
 * default border disappear) plus a minimalist scrollbar — transparent track,
 * a thin rounded thumb whose colour comes from the palette's muted-text slot,
 * and no increment/decrement arrows. The scrollbar rules mirror the standalone
 * {@code .a-scroll-bar} skin (see {@link ScrollBarSkin}), so a standalone
 * scrollbar and the list's own bars look identical.
 * <p>
 * The container itself is frameless — no border or shadow frames the whole
 * list (the default ring is suppressed by setting the ring colour to the
 * palette background), so the skin's only border lives on the rows.
 * <p>
 * Each row (cell) carries that border as a hairline bottom separator in the
 * palette's border colour, drawn within the cell's own bounds so the pinned
 * row height is unaffected. Hovered rows fill with the palette's passive
 * highlight; selected rows fill with the active highlight and flip to white
 * text (same white-on-accent convention as the combo popup's selected row).
 * The hover/selected rules mirror Modena's full {@code .virtual-flow} path —
 * like the combo popup, the short {@code .a-list-view .list-cell:selected}
 * form loses the cascade to Modena's more specific {@code :filled:selected}
 * rules, so the palette values need the full path to win at equal
 * specificity.
 * <p>
 * Placeholder order: list background (x3: cells, odd cells, border), row
 * separator, hover cell, selected cell, thumb, hover thumb, pressed thumb.
 * Hover/pressed shades are computed in CSS with {@code derive()} so the skin
 * needs nothing but the palette hex values.
 */
public final class ListViewSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-list-view {
			    -fx-control-inner-background: %s;
			    -fx-control-inner-background-alt: %s;
			    -fx-box-border: %s;
			}
			.a-list-view .list-cell {
			    -fx-border-color: %s;
			    -fx-border-width: 0 0 1 0;
			    -fx-border-insets: 0;
			}
			.a-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:hover {
			    -fx-background-color: %s;
			}
			.a-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:filled:selected {
			    -fx-background-color: %s;
			    -fx-text-fill: #ffffff;
			}
			.a-list-view .scroll-bar {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0;
			}
			.a-list-view .scroll-bar:vertical {
			    -fx-pref-width: 8;
			}
			.a-list-view .scroll-bar:horizontal {
			    -fx-pref-height: 8;
			}
			.a-list-view .scroll-bar > .thumb {
			    -fx-background-color: %s;
			    -fx-background-insets: 1;
			    -fx-background-radius: 3;
			}
			.a-list-view .scroll-bar > .thumb:hover {
			    -fx-background-color: %s;
			}
			.a-list-view .scroll-bar > .thumb:pressed {
			    -fx-background-color: %s;
			}
			.a-list-view .scroll-bar > .increment-button,
			.a-list-view .scroll-bar > .decrement-button {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-padding: 0;
			    -fx-pref-width: 0;
			    -fx-pref-height: 0;
			}
			.a-list-view .scroll-bar > .increment-button > .increment-arrow,
			.a-list-view .scroll-bar > .decrement-button > .decrement-arrow {
			    -fx-background-color: transparent;
			    -fx-padding: 0;
			}
			""";

	/**
	 * @return the list-view CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the nine placeholder colors: the background repeated for the
	 * cells/odd-cells/border ring, the border hairline, hover/selected cells,
	 * and the scrollbar thumb with its {@code derive()} hover/pressed shades.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the nine placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Background = _Palette.GetBackgroundColor().GetHex(__Lookup);
		String __Thumb = _Palette.GetMutedTextColor().GetHex(__Lookup);
		return new Object[] {
				__Background, __Background, __Background,
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup), _Palette.GetActiveHighlightColor().GetHex(__Lookup),
				__Thumb, "derive(" + __Thumb + ", -15%)", "derive(" + __Thumb + ", -25%)" };
	}
}