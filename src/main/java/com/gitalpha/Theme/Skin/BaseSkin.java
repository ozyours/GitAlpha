package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;

/**
 * The scene-level base stylesheet: a single {@code .root} rule that disables
 * the default Modena focus ring app-wide. JavaFX focus colors are looked-up
 * colors, so every control referencing {@code -fx-focus-color} /
 * {@code -fx-faint-focus-color} resolves them up the scene graph to this root
 * value — one rule covers all controls in every registered scene. Explicit
 * {@code :focused} borders baked by the widget skins (buttons, text inputs,
 * check boxes) are unaffected: they set {@code -fx-border-color} directly,
 * not via the focus lookups.
 * <p>
 * Unlike the per-element skins this class is a composition, not a single
 * format: it concatenates the focus-ring kill, the palette's dynamic
 * {@code -gitalpha-*} CSS variables from {@link ColorPalette#GetCssOverrides}
 * (two {@code .root} rules that CSS merges) and the combo-box popup skin.
 * The popup renders in its own scene that node-level skins cannot reach, but
 * inherits this scene stylesheet, so its rules resolve the scene's variables
 * — the only way to theme it. Consequently {@link #Bake} is overridden
 * instead of filling placeholders, and the abstract hooks return the focus
 * ring fragment / no arguments for consistency.
 */
public final class BaseSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.root {
			    -fx-focus-color: transparent;
			    -fx-faint-focus-color: transparent;
			}
			""";

	/**
	 * The combo-box popup skin: matches the closed control's flat look —
	 * palette background and border around the list, palette text with a
	 * passive-highlight (hover) row and an active-highlight (selected) row
	 * with white text. The popup background is the palette background rather
	 * than the secondary fill so the passive-colored hover row stays visible
	 * against it. The cell selector mirrors Modena's full {@code .virtual-flow}
	 * path so the palette values win the cascade at equal specificity.
	 */
	private static final String COMBO_BOX_POPUP_CSS_FORMAT = """
			.combo-box-popup > .list-view {
			    -fx-background-color: -gitalpha-background;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-border-color: -gitalpha-border;
			    -fx-border-width: 1;
			    -fx-border-radius: 4;
			}
			.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell {
			    -fx-background-color: transparent;
			    -fx-padding: 4 10 4 10;
			    -fx-text-fill: -gitalpha-text;
			}
			.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:hover {
			    -fx-background-color: -gitalpha-passive-highlight;
			}
			.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:selected {
			    -fx-background-color: -gitalpha-active-highlight;
			    -fx-text-fill: #ffffff;
			}
			""";

	/**
	 * @return the focus-ring fragment ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * The composition carries no placeholders.
	 *
	 * @param _Palette unused (the CSS variables resolve at render time)
	 * @return an empty argument list
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		return new Object[0];
	}

	/**
	 * Bake the scene base stylesheet: the focus-ring kill plus the palette's
	 * {@code -gitalpha-*} CSS variables and the combo-box popup skin.
	 *
	 * @param _Palette the palette whose CSS variables are inlined
	 * @return the data-URI stylesheet URL
	 */
	@Override
	public String Bake(ColorPalette _Palette)
	{
		String __Css = CSS_FORMAT + "\n" + _Palette.GetCssOverrides() + COMBO_BOX_POPUP_CSS_FORMAT;
		return ToDataUri(__Css);
	}
}