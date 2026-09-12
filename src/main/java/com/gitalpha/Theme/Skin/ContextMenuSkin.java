package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The context-menu popup skin, and the single source of its CSS: palette
 * background with a border hairline and rounded corners, palette text on the
 * items, a passive-highlight (hover) row, an accent focus ring on the focused
 * item, and a border hairline on separators. Disabled items use the muted
 * text color.
 * <p>
 * Targets the {@code .a-context-menu} class added by
 * {@link com.gitalpha.UI.Components.AContextMenu} and by
 * {@link com.gitalpha.UI.Components.ATopMenuBar} on each Menu's internal
 * popup, so only themed context menus (right-click + menu-bar drop-downs)
 * inherit the palette; unstyled menus keep the Modena default. Selector
 * mirrors Modena's full {@code .context-menu .menu-item} path so the palette
 * values win the cascade at equal specificity.
 * <p>
 * Consumed two ways: {@link #Bake} fills the placeholders with baked hex
 * values, while {@link #GetSceneCss} fills them with the scene's
 * {@code -gitalpha-*} variables for composition into {@link BaseSkin} (the
 * live path — popups inherit the owner scene's stylesheet, the only way to
 * theme them).
 * <p>
 * Placeholder order: background, border, separator border, text, muted text
 * (disabled), passive highlight (hover), passive highlight (focus row),
 * text alternate (focus text), primary (focus ring border).
 */
public final class ContextMenuSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-context-menu {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-border-color: %s;
			    -fx-border-width: 1;
			    -fx-border-radius: 4;
			    -fx-padding: 4 0 4 0;
			}
			.a-context-menu > .separator > .line {
			    -fx-border-color: %s;
			    -fx-border-width: 1 0 0 0;
			}
			.a-context-menu .menu-item {
			    -fx-background-color: transparent;
			    -fx-padding: 4 10 4 10;
			}
			.a-context-menu .menu-item > .label {
			    -fx-text-fill: %s;
			    -fx-font-size: 12px;
			}
			.a-context-menu .menu-item:disabled > .label {
			    -fx-text-fill: %s;
			}
			.a-context-menu .menu-item:hover {
			    -fx-background-color: %s;
			}
			.a-context-menu .menu-item:focused {
			    -fx-background-color: %s;
			    -fx-text-fill: %s;
			    -fx-border-color: %s;
			    -fx-border-width: 1;
			    -fx-border-radius: 3;
			}
			""";

	/**
	 * The scene-variable rendering of the skin, for composition into the
	 * scene-level stylesheet ({@link BaseSkin}): the same rules with the
	 * {@code -gitalpha-*} lookups in placeholder order, so the live popup
	 * styling follows palette switches with the scene re-bake.
	 *
	 * @return the context-menu CSS with scene variables (no baking needed)
	 */
	public static String GetSceneCss()
	{
		return CSS_FORMAT.formatted(
				"-gitalpha-background", "-gitalpha-border", "-gitalpha-border",
				"-gitalpha-text", "-gitalpha-muted-text",
				"-gitalpha-passive-highlight", "-gitalpha-passive-highlight",
				"-gitalpha-text-alternate", "-gitalpha-primary");
	}

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
	 * border, text, muted text (disabled), passive highlight (hover), passive
	 * highlight (focus row), text alternate (focus text), primary (focus
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
