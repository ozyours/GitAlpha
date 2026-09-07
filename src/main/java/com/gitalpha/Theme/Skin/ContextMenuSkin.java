package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The context-menu popup skin: palette background with a border hairline and
 * rounded corners, palette text on the items, a passive-highlight (hover) row,
 * an accent focus ring on the focused item, and a border hairline on
 * separators. Disabled items use the muted text color.
 * <p>
 * Like other node-level skins, colors are inlined (hex values baked at
 * construction) because {@code ContextMenu} lives in its own scene and cannot
 * resolve scene-level {@code -gitalpha-*} variables. Apply via
 * {@code contextMenu.getStylesheets().add(skin.Bake(palette))}.
 * <p>
 * Placeholder order: background, border, separator border, text, muted text
 * (disabled), passive highlight (hover), primary (focus ring background),
 * text alternate (focus text), primary (focus ring border).
 */
public final class ContextMenuSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.context-menu {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-border-color: %s;
			    -fx-border-width: 1;
			    -fx-border-radius: 4;
			    -fx-padding: 4 0 4 0;
			}
			.context-menu > .separator > .line {
			    -fx-border-color: %s;
			    -fx-border-width: 1 0 0 0;
			}
			.context-menu .menu-item {
			    -fx-background-color: transparent;
			    -fx-padding: 4 10 4 10;
			}
			.context-menu .menu-item > .label {
			    -fx-text-fill: %s;
			    -fx-font-size: 12px;
			}
			.context-menu .menu-item:disabled > .label {
			    -fx-text-fill: %s;
			}
			.context-menu .menu-item:hover {
			    -fx-background-color: %s;
			}
			.context-menu .menu-item:focused {
			    -fx-background-color: %s;
			    -fx-text-fill: %s;
			    -fx-border-color: %s;
			    -fx-border-width: 1;
			    -fx-border-radius: 3;
			}
			""";

	/**
	 * @return the context-menu CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the nine placeholder colors: background, border, separator
	 * border, text, muted text (disabled), passive highlight (hover), primary
	 * (focus ring background), alternate text (focus text), primary (focus
	 * ring border).
	 *
	 * @param _Palette the palette to read colors from
	 * @return the nine placeholder values
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				_Palette.GetBackgroundColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup),
				_Palette.GetPassiveHighlightColor().GetHex(__Lookup),
				_Palette.GetTextAlternateColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}
