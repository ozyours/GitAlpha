package com.gitalpha.Theme;

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
	 * hover background, pressed background, focus border.
	 *
	 * @param _Palette the active palette to read colors from
	 * @return the six colors as CSS strings ({@code #rrggbb} hex, or
	 *         {@code transparent} for GHOST's empty slots)
	 */
	public String[] ResolveSkinColors(ColorPalette _Palette)
	{
		return switch (this)
		{
case NORMAL -> new String[] {
				_Palette.GetSecondaryColor(), _Palette.GetTextColor(), _Palette.GetBorderColor(),
				_Palette.GetPassiveHighlightColor(), _Palette.GetBorderColor(), _Palette.GetPrimaryColor() };
			case DANGER -> new String[] {
					_Palette.GetRemovedColor(), "#ffffff", _Palette.GetRemovedColor(),
					_Palette.GetRemovedIntra(), _Palette.GetRemovedBackground(), _Palette.GetRemovedColor() };
			case GHOST -> new String[] {
					"transparent", _Palette.GetTextColor(), "transparent",
					_Palette.GetPassiveHighlightColor(), _Palette.GetBorderColor(), _Palette.GetPrimaryColor() };
		};
	}
}