package com.gitalpha.Theme;

import com.gitalpha.Type.ThemeColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base for the app's themeable colors, kept as a plain data class
 * (no JavaFX dependency) so it can later serialize into the session file like
 * the other state objects. Widgets read their colors from here instead of
 * hardcoding them inline, so a single point can customize them (presets, user
 * overrides, live preview).
 * <p>
 * Two semantic roles: core UI colors (primary, text, muted text, border,
 * background, ...) and git status colors (added / removed / modified). The
 * highlight is split into an active slot (selected states: list/combo
 * selection, checked fill, text selection) and a passive slot (hover
 * states), so selected and hover feedback can be themed independently.
 * Each status color is a single user-facing base; the diff shades (row
 * background, intra-line highlight) are derived from the added/removed
 * bases by mixing toward the palette background, so the same base yields
 * correct tints in both light and dark themes.
 * <p>
 * Colors are stored as {@link ThemeColor} values (sRGB floats + brightness,
 * direct or derived form). Every slot carries a hard-coded name that other
 * slots can derive from; {@link #GetColorLookup()} exposes the name-to-color
 * map used to resolve derived slots.
 * <p>
 * Concrete themes are derived classes that populate the colors in their
 * constructor: {@link com.gitalpha.Theme.Themes.LightTheme},
 * {@link com.gitalpha.Theme.Themes.DarkTheme}, and the user-customized
 * {@link CustomColorPalette} (which adds session serialization).
 */
public abstract class ColorPalette
{
	/**
	 * Default no-arg constructor for derived themes that populate their own colors.
	 */
	protected ColorPalette()
	{
	}

	/**
	 * Copy every color value from another palette. Used by derived classes that
	 * start from an existing theme and then override a subset (e.g. a user
	 * customization seeded from the Light theme). Values are copied, not
	 * reference-shared, so mutating one palette never leaks into the other.
	 *
	 * @param _Source the palette to copy colors from
	 */
	protected void CopyFrom(ColorPalette _Source)
	{
		PrimaryColor = _Source.PrimaryColor.Copy();
		SecondaryColor = _Source.SecondaryColor.Copy();
		TextColor = _Source.TextColor.Copy();
		MutedTextColor = _Source.MutedTextColor.Copy();
		ActiveHighlightColor = _Source.ActiveHighlightColor.Copy();
		PassiveHighlightColor = _Source.PassiveHighlightColor.Copy();
		BorderColor = _Source.BorderColor.Copy();
		Background1Color = _Source.Background1Color.Copy();
		Background2Color = _Source.Background2Color.Copy();
		AddedColor = _Source.AddedColor.Copy();
		RemovedColor = _Source.RemovedColor.Copy();
		ModifiedColor = _Source.ModifiedColor.Copy();
	}

	// --- Core UI colors ---
	private ThemeColor PrimaryColor;
	private ThemeColor SecondaryColor;
	private ThemeColor TextColor;
	private ThemeColor MutedTextColor;
	private ThemeColor ActiveHighlightColor;
	private ThemeColor PassiveHighlightColor;
	private ThemeColor BorderColor;
	private ThemeColor Background1Color;
	/** Secondary background: the scene/window-level backdrop (e.g. the root
	 *  background), while {@link #Background1Color} is the content/panel-level
	 *  background. Kept separate so the window frame can differ slightly from
	 *  the panels it hosts (e.g. greyish white around white content). */
	private ThemeColor Background2Color;
	// --- Git status colors (bases; shades derived via MixToward) ---
	private ThemeColor AddedColor;
	private ThemeColor RemovedColor;
	private ThemeColor ModifiedColor;

	/**
	 * @return the primary color
	 */
	public ThemeColor GetPrimaryColor()
	{
		return PrimaryColor;
	}

	/**
	 * @return the secondary color
	 */
	public ThemeColor GetSecondaryColor()
	{
		return SecondaryColor;
	}

	/**
	 * @return the text color
	 */
	public ThemeColor GetTextColor()
	{
		return TextColor;
	}

	/**
	 * @return the muted text color
	 */
	public ThemeColor GetMutedTextColor()
	{
		return MutedTextColor;
	}

	/**
	 * @return the active (selected) highlight color
	 */
	public ThemeColor GetActiveHighlightColor()
	{
		return ActiveHighlightColor;
	}

	/**
	 * @return the passive (hover) highlight color
	 */
	public ThemeColor GetPassiveHighlightColor()
	{
		return PassiveHighlightColor;
	}

	/**
	 * @return the border color
	 */
	public ThemeColor GetBorderColor()
	{
		return BorderColor;
	}

	/**
	 * @return the background color (content/panel level)
	 */
	public ThemeColor GetBackgroundColor()
	{
		return Background1Color;
	}

	/**
	 * @return the secondary background color (window-level backdrop)
	 */
	public ThemeColor GetBackground2Color()
	{
		return Background2Color;
	}

	/**
	 * @return the added-status base color
	 */
	public ThemeColor GetAddedColor()
	{
		return AddedColor;
	}

	/**
	 * @return the removed-status base color
	 */
	public ThemeColor GetRemovedColor()
	{
		return RemovedColor;
	}

	/**
	 * @return the modified-status base color
	 */
	public ThemeColor GetModifiedColor()
	{
		return ModifiedColor;
	}

	/**
	 * Set the primary color. The {@link ThemeColor} carries its own hard-coded
	 * slot name, which is what other slots use to derive from it.
	 *
	 * @param _Color the color to store
	 */
	public void SetPrimaryColor(ThemeColor _Color)
	{
		PrimaryColor = _Color;
	}

	/**
	 * Set the secondary color.
	 *
	 * @param _Color the color to store
	 */
	public void SetSecondaryColor(ThemeColor _Color)
	{
		SecondaryColor = _Color;
	}

	/**
	 * Set the text color.
	 *
	 * @param _Color the color to store
	 */
	public void SetTextColor(ThemeColor _Color)
	{
		TextColor = _Color;
	}

	/**
	 * Set the muted text color.
	 *
	 * @param _Color the color to store
	 */
	public void SetMutedTextColor(ThemeColor _Color)
	{
		MutedTextColor = _Color;
	}

	/**
	 * Set the active (selected) highlight color.
	 *
	 * @param _Color the color to store
	 */
	public void SetActiveHighlightColor(ThemeColor _Color)
	{
		ActiveHighlightColor = _Color;
	}

	/**
	 * Set the passive (hover) highlight color.
	 *
	 * @param _Color the color to store
	 */
	public void SetPassiveHighlightColor(ThemeColor _Color)
	{
		PassiveHighlightColor = _Color;
	}

	/**
	 * Set the border color.
	 *
	 * @param _Color the color to store
	 */
	public void SetBorderColor(ThemeColor _Color)
	{
		BorderColor = _Color;
	}

	/**
	 * Set the background color (content/panel level).
	 *
	 * @param _Color the color to store
	 */
	public void SetBackground1Color(ThemeColor _Color)
	{
		Background1Color = _Color;
	}

	/**
	 * Set the secondary background color (window-level backdrop).
	 *
	 * @param _Color the color to store
	 */
	public void SetBackground2Color(ThemeColor _Color)
	{
		Background2Color = _Color;
	}

	/**
	 * Set the added-status base color.
	 *
	 * @param _Color the color to store
	 */
	public void SetAddedColor(ThemeColor _Color)
	{
		AddedColor = _Color;
	}

	/**
	 * Set the removed-status base color.
	 *
	 * @param _Color the color to store
	 */
	public void SetRemovedColor(ThemeColor _Color)
	{
		RemovedColor = _Color;
	}

	/**
	 * Set the modified-status base color.
	 *
	 * @param _Color the color to store
	 */
	public void SetModifiedColor(ThemeColor _Color)
	{
		ModifiedColor = _Color;
	}

	/**
	 * Build the name-to-color lookup used to resolve derived slots: every slot
	 * registers under its hard-coded {@link ThemeColor#GetName()}. Built fresh
	 * on each call so palette mutations are always reflected. All 12 slots are
	 * populated by the concrete themes; a null slot is skipped defensively (a
	 * derived slot referencing it then fails with a clear "unknown source"
	 * error at resolve time instead of an opaque NPE here).
	 *
	 * @return a map of slot name to color
	 */
	public Map<String, ThemeColor> GetColorLookup()
	{
		Map<String, ThemeColor> __Lookup = new HashMap<>();
		PutSlot(__Lookup, PrimaryColor);
		PutSlot(__Lookup, SecondaryColor);
		PutSlot(__Lookup, TextColor);
		PutSlot(__Lookup, MutedTextColor);
		PutSlot(__Lookup, ActiveHighlightColor);
		PutSlot(__Lookup, PassiveHighlightColor);
		PutSlot(__Lookup, BorderColor);
		PutSlot(__Lookup, Background1Color);
		PutSlot(__Lookup, Background2Color);
		PutSlot(__Lookup, AddedColor);
		PutSlot(__Lookup, RemovedColor);
		PutSlot(__Lookup, ModifiedColor);
		return __Lookup;
	}

	private static void PutSlot(Map<String, ThemeColor> _Lookup, ThemeColor _Color)
	{
		if (_Color != null)
			_Lookup.put(_Color.GetName(), _Color);
	}

	// --- Derived diff shades ---
	// The row background is the base strongly tinted toward the palette
	// background; the intra-line highlight sits between the two. Mixing toward
	// Background1Color (not white) keeps the same base correct in dark themes.

	/**
	 * Added-line row background: {@link #GetAddedColor()} tinted toward the background
	 */
	public String GetAddedBackground()
	{
		return MixToward(AddedColor, Background1Color, 0.90);
	}

	/**
	 * Changed-character highlight inside an added line (deeper than the row background)
	 */
	public String GetAddedIntra()
	{
		return MixToward(AddedColor, Background1Color, 0.70);
	}

	/**
	 * Removed-line row background: {@link #GetRemovedColor()} tinted toward the background
	 */
	public String GetRemovedBackground()
	{
		return MixToward(RemovedColor, Background1Color, 0.90);
	}

	/**
	 * Changed-character highlight inside a removed line (deeper than the row background)
	 */
	public String GetRemovedIntra()
	{
		return MixToward(RemovedColor, Background1Color, 0.70);
	}

	/**
	 * Render the whole palette as CSS lookup-variable overrides on {@code .root}.
	 * Designed to be appended after the base theme (or the default JavaFX
	 * styling) so the variables cascade to every control; app-specific
	 * variables use the {@code -gitalpha-} prefix to avoid colliding with the
	 * base theme's own lookups.
	 *
	 * @return a CSS snippet declaring one {@code -gitalpha-*} variable per base
	 *         color plus the four derived diff shades (added/removed row
	 *         backgrounds and intra-line highlights)
	 */
	public String GetCssOverrides()
	{
		Map<String, ThemeColor> __Lookup = GetColorLookup();
		StringBuilder __Css = new StringBuilder();
		__Css.append(".root {\n");
		AppendVar(__Css, "-gitalpha-primary", PrimaryColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-secondary", SecondaryColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-text", TextColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-muted-text", MutedTextColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-active-highlight", ActiveHighlightColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-passive-highlight", PassiveHighlightColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-border", BorderColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-background", Background1Color.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-background-2", Background2Color.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-added", AddedColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-added-bg", MixToward(AddedColor, Background1Color, 0.90, __Lookup));
		AppendVar(__Css, "-gitalpha-added-intra", MixToward(AddedColor, Background1Color, 0.70, __Lookup));
		AppendVar(__Css, "-gitalpha-removed", RemovedColor.GetHex(__Lookup));
		AppendVar(__Css, "-gitalpha-removed-bg", MixToward(RemovedColor, Background1Color, 0.90, __Lookup));
		AppendVar(__Css, "-gitalpha-removed-intra", MixToward(RemovedColor, Background1Color, 0.70, __Lookup));
		AppendVar(__Css, "-gitalpha-modified", ModifiedColor.GetHex(__Lookup));
		__Css.append("}\n");
		return __Css.toString();
	}

	private static void AppendVar(StringBuilder _Css, String _Name, String _Value)
	{
		_Css.append("    ").append(_Name).append(": ").append(_Value).append(";\n");
	}

	/**
	 * Linearly mix two colors toward the second: {@code _T = 0.0} yields the
	 * first color, {@code _T = 1.0} yields the second. Both inputs are resolved
	 * through the palette lookup, so derived slots mix by their resolved color.
	 *
	 * @param _From start color
	 * @param _To   end color
	 * @param _T    mix factor; clamped to [0, 1]
	 * @return the mixed color as {@code #rrggbb}
	 */
	private String MixToward(ThemeColor _From, ThemeColor _To, double _T)
	{
		return MixToward(_From, _To, _T, GetColorLookup());
	}

	/**
	 * Mixing variant that reuses an existing lookup instead of rebuilding one,
	 * for callers that already hold the map (e.g. {@link #GetCssOverrides}).
	 *
	 * @param _From   start color
	 * @param _To     end color
	 * @param _T      mix factor; clamped to [0, 1]
	 * @param _Lookup the palette lookup to resolve both inputs through
	 * @return the mixed color as {@code #rrggbb}
	 */
	private String MixToward(ThemeColor _From, ThemeColor _To, double _T, Map<String, ThemeColor> _Lookup)
	{
		int __From = ParseHex(_From.GetHex(_Lookup));
		int __To = ParseHex(_To.GetHex(_Lookup));
		// Clamp so callers can't extrapolate past the two endpoints (which would
		// produce out-of-range channels and malformed CSS).
		double __T = Math.max(0.0, Math.min(1.0, _T));
		int __R = Lerp((__From >> 16) & 0xFF, (__To >> 16) & 0xFF, __T);
		int __G = Lerp((__From >> 8) & 0xFF, (__To >> 8) & 0xFF, __T);
		int __B = Lerp(__From & 0xFF, __To & 0xFF, __T);
		return String.format("#%02x%02x%02x", __R, __G, __B);
	}

	/**
	 * Parse a {@code #rrggbb} hex color into a packed RGB int. Strict about the
	 * format so a malformed color fails fast when a derived shade is computed
	 * (via {@link #MixToward}) rather than silently producing broken CSS.
	 * Package-private so derived classes can validate stored values (e.g. hex
	 * loaded from the session file) before they reach the render path.
	 *
	 * @param _Hex color as {@code #rrggbb}
	 * @return packed RGB (0xRRGGBB)
	 */
	static int ParseHex(String _Hex)
	{
		if (_Hex == null || _Hex.length() != 7 || _Hex.charAt(0) != '#')
			throw new IllegalArgumentException("Color must be #rrggbb, got: " + _Hex);
		try
		{
			return Integer.parseInt(_Hex.substring(1), 16);
		}
		catch (NumberFormatException __BadHex)
		{
			throw new IllegalArgumentException("Color must be #rrggbb, got: " + _Hex);
		}
	}

	private static int Lerp(int _From, int _To, double _T)
	{
		return (int) Math.round(_From + (_To - _From) * _T);
	}
}