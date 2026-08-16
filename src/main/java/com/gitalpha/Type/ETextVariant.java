package com.gitalpha.Type;

import com.gitalpha.Theme.ColorPalette;

import java.util.Map;

/**
 * Visual variants for themed text ({@link com.gitalpha.UI.Components.AText}).
 * Each variant maps the text's CSS style to semantic palette slots — mostly
 * palette-driven so the colors re-theme, with a few intentional literals
 * (Consolas for the {@link #MONO} family — {@link #MONO_MUTED},
 * {@link #MONO_ADDED}, {@link #MONO_REMOVED} — and 16px bold for
 * {@link #TITLE}) that are part of the variant's identity.
 * <p>
 * Unlike {@link EButtonVariant}, which resolves placeholder colors for a
 * shared skin template, each variant here resolves a complete inline CSS
 * string ready to be applied via {@code setStyle} (an optional font-size
 * suffix may be appended by {@link com.gitalpha.UI.Components.AText}).
 */
public enum ETextVariant
{
	/** Default body text: palette text color */
	BODY,
	/** Secondary/informational text: muted text color */
	MUTED,
	/** Emphasized or clickable text: primary (accent) color */
	ACCENT,
	/** Destructive/error text: removed color */
	ERROR,
	/** Success text: added color */
	SUCCESS,
	/** Modified-state text (status labels): modified color */
	MODIFIED,
	/** Monospace text (diff viewer, paths): text color + Consolas */
	MONO,
	/** Monospace muted text (diff stats label): muted color + Consolas */
	MONO_MUTED,
	/** Monospace added-count text (diff stats): added color + Consolas */
	MONO_ADDED,
	/** Monospace removed-count text (diff stats): removed color + Consolas */
	MONO_REMOVED,
	/** Section heading: text color, 16px bold */
	TITLE,
	/** Bold label text (e.g. form captions): palette text color + bold weight */
	BOLD;

	/**
	 * Resolve the inline CSS style for this variant from the given palette.
	 * The returned string is applied via {@code setStyle} (with an optional
	 * font-size suffix appended by {@link com.gitalpha.UI.Components.AText}),
	 * so each case must emit a complete, self-contained CSS declaration that
	 * depends on nothing but the palette. Palette slots are resolved to hex
	 * through the palette's lookup, so derived slots render by their resolved
	 * color.
	 *
	 * @param _Palette the active palette to read colors from
	 * @return the CSS style string for this variant
	 */
	public String ResolveCss(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return switch (this)
		{
			case BODY -> "-fx-fill: " + _Palette.GetTextColor().GetHex(__Lookup) + ";";
			case MUTED -> "-fx-fill: " + _Palette.GetMutedTextColor().GetHex(__Lookup) + ";";
			case ACCENT -> "-fx-fill: " + _Palette.GetPrimaryColor().GetHex(__Lookup) + ";";
			case ERROR -> "-fx-fill: " + _Palette.GetRemovedColor().GetHex(__Lookup) + ";";
			case SUCCESS -> "-fx-fill: " + _Palette.GetAddedColor().GetHex(__Lookup) + ";";
			case MODIFIED -> "-fx-fill: " + _Palette.GetModifiedColor().GetHex(__Lookup) + ";";
			case MONO -> "-fx-fill: " + _Palette.GetTextColor().GetHex(__Lookup) + "; -fx-font-family: Consolas;";
			case MONO_MUTED -> "-fx-fill: " + _Palette.GetMutedTextColor().GetHex(__Lookup) + "; -fx-font-family: Consolas;";
			case MONO_ADDED -> "-fx-fill: " + _Palette.GetAddedColor().GetHex(__Lookup) + "; -fx-font-family: Consolas;";
			case MONO_REMOVED -> "-fx-fill: " + _Palette.GetRemovedColor().GetHex(__Lookup) + "; -fx-font-family: Consolas;";
			case TITLE -> "-fx-fill: " + _Palette.GetTextColor().GetHex(__Lookup) + "; -fx-font-size: 16px; -fx-font-weight: bold;";
			case BOLD -> "-fx-fill: " + _Palette.GetTextColor().GetHex(__Lookup) + "; -fx-font-weight: bold;";
		};
	}
}