package com.gitalpha.Type;

import com.gitalpha.Theme.ColorPalette;

import java.util.Map;

/**
 * Visual variants for themed buttons. Each variant maps the button skin's six
 * color placeholders (background, text, border, hover background, pressed
 * background, focus border) to semantic palette slots so the palette-driven
 * slots re-theme when the palette switches. Hover backgrounds read the
 * passive highlight slot (the hover color), matching the hover feedback of
 * the other themed controls. A few slots are intentional literals rather
 * than palette colors: DANGER's white text and GHOST's transparent fills —
 * they are part of the variant's identity, not themeable.
 */
public enum EButtonVariant
{
	/** Neutral: secondary background, palette border, accent (primary) focus ring */
	NORMAL,
	/** Destructive: removed-red background and border with white text */
	DANGER,
	/** Quiet: transparent background and border; fills on hover/press and gains a focus ring */
	GHOST;

	/**
	 * Resolve the six skin color placeholders from the given palette, in the
	 * order the button CSS template expects them: background, text, border,
	 * hover background, pressed background, focus border. Palette slots are
	 * resolved to hex through the palette's lookup, so derived slots render by
	 * their resolved color.
	 *
	 * @param _Palette the active palette to read colors from
	 * @return the six colors as CSS strings ({@code #rrggbb} hex, or
	 *         {@code transparent} for GHOST's empty slots)
	 */
	public String[] ResolveSkinColors(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return switch (this)
		{
			case NORMAL -> new String[] {
					_Palette.GetSecondaryColor().GetHex(__Lookup),
					_Palette.GetTextColor().GetHex(__Lookup),
					_Palette.GetBorderColor().GetHex(__Lookup),
					_Palette.GetPassiveHighlightColor().GetHex(__Lookup),
					_Palette.GetBorderColor().GetHex(__Lookup),
					_Palette.GetPrimaryColor().GetHex(__Lookup) };
			case DANGER -> new String[] {
					_Palette.GetRemovedColor().GetHex(__Lookup), "#ffffff",
					_Palette.GetRemovedColor().GetHex(__Lookup),
					_Palette.GetRemovedIntra(), _Palette.GetRemovedBackground(),
					_Palette.GetRemovedColor().GetHex(__Lookup) };
			case GHOST -> new String[] {
					"transparent", _Palette.GetTextColor().GetHex(__Lookup), "transparent",
					_Palette.GetPassiveHighlightColor().GetHex(__Lookup),
					_Palette.GetBorderColor().GetHex(__Lookup),
					_Palette.GetPrimaryColor().GetHex(__Lookup) };
		};
	}
}