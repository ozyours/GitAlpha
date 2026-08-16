package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.EButtonVariant;

/**
 * The button skin: density (small padding, 12px text), GitHub-style border
 * and a focus ring. The variant (NORMAL/DANGER/GHOST) selects which of the
 * six color slots fill the placeholders.
 * <p>
 * Placeholder order — background, text, border, hover background, pressed
 * background, focus border.
 */
public final class ButtonSkin extends ThemeSkin
{
	private static final String CSS_FORMAT = """
			.a-button {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 4;
			    -fx-text-fill: %s;
			    -fx-border-color: %s;
			    -fx-border-radius: 4;
			    -fx-border-insets: 0;
			    -fx-padding: 4 12 4 12;
			    -fx-font-size: 12px;
			    -fx-cursor: hand;
			}
			.a-button:hover {
			    -fx-background-color: %s;
			}
			.a-button:pressed {
			    -fx-background-color: %s;
			}
			.a-button:focused {
			    -fx-border-color: %s;
			}
			.a-button:disabled {
			    -fx-opacity: 0.4;
			}
			""";

	/**
	 * The variant whose color slots fill the placeholders
	 */
	private final EButtonVariant Variant;

	/**
	 * @param _Variant the button variant whose skin to bake
	 */
	public ButtonSkin(EButtonVariant _Variant)
	{
		Variant = _Variant;
	}

	/**
	 * @return the button CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the six placeholder colors from the variant's slot mapping.
	 *
	 * @param _Palette the palette to read colors from
	 * @return background, text, border, hover, pressed, focus border
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		return Variant.ResolveSkinColors(_Palette);
	}
}