package com.gitalpha.Theme;

/**
 * Abstract base for the app's themeable colors, kept as a plain data class
 * (hex strings only, no JavaFX dependency) so it can later serialize into the
 * session file like the other state objects. Widgets read their colors from
 * here instead of hardcoding them inline, so a single point can customize them
 * (presets, user overrides, live preview).
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
 * Concrete themes are derived classes that populate the colors in their
 * constructor: {@link LightTheme}, {@link DarkTheme}, and the user-customized
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
	 * customization seeded from the Light theme).
	 *
	 * @param _Source the palette to copy colors from
	 */
	protected void CopyFrom(ColorPalette _Source)
	{
		PrimaryColor = _Source.PrimaryColor;
		SecondaryColor = _Source.SecondaryColor;
		TextColor = _Source.TextColor;
		MutedTextColor = _Source.MutedTextColor;
		ActiveHighlightColor = _Source.ActiveHighlightColor;
		PassiveHighlightColor = _Source.PassiveHighlightColor;
		BorderColor = _Source.BorderColor;
		BackgroundColor = _Source.BackgroundColor;
		AddedColor = _Source.AddedColor;
		RemovedColor = _Source.RemovedColor;
		ModifiedColor = _Source.ModifiedColor;
	}

	// --- Core UI colors ---
	private String PrimaryColor;
	private String SecondaryColor;
	private String TextColor;
	private String MutedTextColor;
	private String ActiveHighlightColor;
	private String PassiveHighlightColor;
	private String BorderColor;
	private String BackgroundColor;
	// --- Git status colors (bases; shades derived via MixToward) ---
	private String AddedColor;
	private String RemovedColor;
	private String ModifiedColor;

	/**
	 * @return the primary color as {@code #rrggbb} hex
	 */
	public String GetPrimaryColor()
	{
		return PrimaryColor;
	}

	/**
	 * @return the secondary color as {@code #rrggbb} hex
	 */
	public String GetSecondaryColor()
	{
		return SecondaryColor;
	}

	/**
	 * @return the text color as {@code #rrggbb} hex
	 */
	public String GetTextColor()
	{
		return TextColor;
	}

	/**
	 * @return the muted text color as {@code #rrggbb} hex
	 */
	public String GetMutedTextColor()
	{
		return MutedTextColor;
	}

	/**
	 * @return the active (selected) highlight color as {@code #rrggbb} hex
	 */
	public String GetActiveHighlightColor()
	{
		return ActiveHighlightColor;
	}

	/**
	 * @return the passive (hover) highlight color as {@code #rrggbb} hex
	 */
	public String GetPassiveHighlightColor()
	{
		return PassiveHighlightColor;
	}

	/**
	 * @return the border color as {@code #rrggbb} hex
	 */
	public String GetBorderColor()
	{
		return BorderColor;
	}

	/**
	 * @return the background color as {@code #rrggbb} hex
	 */
	public String GetBackgroundColor()
	{
		return BackgroundColor;
	}

	/**
	 * @return the added-status base color as {@code #rrggbb} hex
	 */
	public String GetAddedColor()
	{
		return AddedColor;
	}

	/**
	 * @return the removed-status base color as {@code #rrggbb} hex
	 */
	public String GetRemovedColor()
	{
		return RemovedColor;
	}

	/**
	 * @return the modified-status base color as {@code #rrggbb} hex
	 */
	public String GetModifiedColor()
	{
		return ModifiedColor;
	}

	/**
	 * Set the primary color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetPrimaryColor(String _Hex)
	{
		PrimaryColor = _Hex;
	}

	/**
	 * Set the secondary color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetSecondaryColor(String _Hex)
	{
		SecondaryColor = _Hex;
	}

	/**
	 * Set the text color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetTextColor(String _Hex)
	{
		TextColor = _Hex;
	}

	/**
	 * Set the muted text color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetMutedTextColor(String _Hex)
	{
		MutedTextColor = _Hex;
	}

	/**
	 * Set the active (selected) highlight color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetActiveHighlightColor(String _Hex)
	{
		ActiveHighlightColor = _Hex;
	}

	/**
	 * Set the passive (hover) highlight color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetPassiveHighlightColor(String _Hex)
	{
		PassiveHighlightColor = _Hex;
	}

	/**
	 * Set the border color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetBorderColor(String _Hex)
	{
		BorderColor = _Hex;
	}

	/**
	 * Set the background color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetBackgroundColor(String _Hex)
	{
		BackgroundColor = _Hex;
	}

	/**
	 * Set the added-status base color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetAddedColor(String _Hex)
	{
		AddedColor = _Hex;
	}

	/**
	 * Set the removed-status base color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetRemovedColor(String _Hex)
	{
		RemovedColor = _Hex;
	}

	/**
	 * Set the modified-status base color as {@code #rrggbb} hex.
	 * Not validated here; malformed values are rejected by {@link #ParseHex} when a derived shade is computed.
	 */
	public void SetModifiedColor(String _Hex)
	{
		ModifiedColor = _Hex;
	}

	// --- Derived diff shades ---
	// The row background is the base strongly tinted toward the palette
	// background; the intra-line highlight sits between the two. Mixing toward
	// BackgroundColor (not white) keeps the same base correct in dark themes.

	/**
	 * Added-line row background: {@link #GetAddedColor()} tinted toward the background
	 */
	public String GetAddedBackground()
	{
		return MixToward(AddedColor, BackgroundColor, 0.90);
	}

	/**
	 * Changed-character highlight inside an added line (deeper than the row background)
	 */
	public String GetAddedIntra()
	{
		return MixToward(AddedColor, BackgroundColor, 0.70);
	}

	/**
	 * Removed-line row background: {@link #GetRemovedColor()} tinted toward the background
	 */
	public String GetRemovedBackground()
	{
		return MixToward(RemovedColor, BackgroundColor, 0.90);
	}

	/**
	 * Changed-character highlight inside a removed line (deeper than the row background)
	 */
	public String GetRemovedIntra()
	{
		return MixToward(RemovedColor, BackgroundColor, 0.70);
	}

	/**
	 * Render the whole palette as CSS lookup-variable overrides on {@code .root}.
	 * Designed to be appended after the base theme (or the default JavaFX
	 * styling) so the variables cascade to every control; app-specific
	 * variables use the {@code -gitalpha-} prefix to avoid colliding with the
	 * base theme's own lookups.
	 *
	 * @return a CSS snippet declaring one {@code -gitalpha-*} variable per color
	 */
	public String GetCssOverrides()
	{
		StringBuilder __Css = new StringBuilder();
		__Css.append(".root {\n");
		AppendVar(__Css, "-gitalpha-primary", PrimaryColor);
		AppendVar(__Css, "-gitalpha-secondary", SecondaryColor);
		AppendVar(__Css, "-gitalpha-text", TextColor);
		AppendVar(__Css, "-gitalpha-muted-text", MutedTextColor);
		AppendVar(__Css, "-gitalpha-active-highlight", ActiveHighlightColor);
		AppendVar(__Css, "-gitalpha-passive-highlight", PassiveHighlightColor);
		AppendVar(__Css, "-gitalpha-border", BorderColor);
		AppendVar(__Css, "-gitalpha-background", BackgroundColor);
		AppendVar(__Css, "-gitalpha-added", AddedColor);
		AppendVar(__Css, "-gitalpha-added-bg", GetAddedBackground());
		AppendVar(__Css, "-gitalpha-added-intra", GetAddedIntra());
		AppendVar(__Css, "-gitalpha-removed", RemovedColor);
		AppendVar(__Css, "-gitalpha-removed-bg", GetRemovedBackground());
		AppendVar(__Css, "-gitalpha-removed-intra", GetRemovedIntra());
		AppendVar(__Css, "-gitalpha-modified", ModifiedColor);
		__Css.append("}\n");
		return __Css.toString();
	}

	private static void AppendVar(StringBuilder _Css, String _Name, String _Value)
	{
		_Css.append("    ").append(_Name).append(": ").append(_Value).append(";\n");
	}

	/**
	 * Linearly mix two hex colors toward the second: {@code _T = 0.0} yields the
	 * first color, {@code _T = 1.0} yields the second.
	 *
	 * @param _FromHex start color as {@code #rrggbb}
	 * @param _ToHex   end color as {@code #rrggbb}
	 * @param _T       mix factor; clamped to [0, 1]
	 * @return the mixed color as {@code #rrggbb}
	 */
	private static String MixToward(String _FromHex, String _ToHex, double _T)
	{
		int __From = ParseHex(_FromHex);
		int __To = ParseHex(_ToHex);
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
