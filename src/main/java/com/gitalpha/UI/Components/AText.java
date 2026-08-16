package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Theme.ETextVariant;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.text.Text;

/**
 * Theme-aware text label: a {@link Text} that carries its style inline via
 * {@code setStyle}, resolved from the active palette by an
 * {@link ETextVariant}. Unlike {@link AButton}, a {@code Text} node is not a
 * {@code Parent} and owns no stylesheet list, so the skin is applied as an
 * inline CSS string instead of a data-URI stylesheet.
 * <p>
 * Colors are baked in as resolved hex values rather than CSS lookups because
 * the palette is a plain-data Java object (see {@link ColorPalette}): the
 * label needs nothing but the palette, and the active theme's colors are
 * always reflected without depending on any stylesheet being attached.
 * An optional explicit font size (see
 * {@link #AText(String, ETextVariant, double)}) is appended to the resolved
 * style; 0 keeps the variant's default size.
 * <p>
 * The style re-bakes whenever the palette changes (push via
 * {@link IThemeChangeEvent}), so every themed label follows the active theme.
 */
public class AText extends Text implements IThemeChangeEvent
{
	/** The visual variant whose style this label carries (body by default) */
	private final ETextVariant Variant;
	/** Font size override (px); 0 keeps the variant's default size */
	private final double FontSize;

	/**
	 * Create a themed label with the default (body) variant. Registers as a
	 * theme listener so the style re-bakes on palette switches.
	 *
	 * @param _Text the label text
	 */
	public AText(String _Text)
	{
		this(_Text, ETextVariant.BODY);
	}

	/**
	 * Create a themed label with the style of the given variant. Registers as
	 * a theme listener so the style re-bakes on palette switches.
	 *
	 * @param _Text    the label text
	 * @param _Variant the visual variant whose style to use
	 */
	public AText(String _Text, ETextVariant _Variant)
	{
		this(_Text, _Variant, 0);
	}

	/**
	 * Create a themed label with the style of the given variant at an explicit
	 * font size. Registers as a theme listener so the style re-bakes on palette
	 * switches. Used by labels that need a size the variant does not encode
	 * (e.g. the diff stats header's 16px mono counters).
	 *
	 * @param _Text      the label text
	 * @param _Variant   the visual variant whose style to use
	 * @param _FontSize  the font size in px; 0 keeps the variant's default
	 */
	public AText(String _Text, ETextVariant _Variant, double _FontSize)
	{
		super(_Text);
		Variant = _Variant;
		FontSize = _FontSize;
		ApplySkin(ThemeManager.Instance.GetPalette());
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Theme-change push: re-bake the inline style with the new palette's colors.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin(_Palette);
	}

	/**
	 * Replace the inline CSS style with one resolved from the given palette for
	 * this label's variant, appending the explicit font size when one was set.
	 *
	 * @param _Palette the palette to read colors from
	 */
	private void ApplySkin(ColorPalette _Palette)
	{
		String __Css = Variant.ResolveCss(_Palette);
		if (FontSize > 0)
			__Css += " -fx-font-size: " + FontSize + "px;";
		setStyle(__Css);
	}
}