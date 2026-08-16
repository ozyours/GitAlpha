package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.EButtonVariant;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base class for the app's per-element CSS skins. Each skin is one subclass
 * that owns its CSS format string and the palette resolution that fills it;
 * the base class provides the shared bake step (format + encode to an inline
 * data-URI URL).
 * <p>
 * Three methods make up a skin:
 * <ul>
 * <li>{@link #GetCssFormat()} (abstract) — the CSS text block with
 * {@code %s} placeholders where the palette colors go.</li>
 * <li>{@link #GetColorArguments(ColorPalette)} (abstract) — the palette
 * values filling those placeholders, in order.</li>
 * <li>{@link #Bake(ColorPalette)} (concrete) — runs the format through
 * {@link String#format} with the arguments and wraps the result as a base64
 * data-URI stylesheet URL. The URL changes whenever the colors do, so JavaFX
 * re-parses the new skin (and keeps the old one cached until dropped).</li>
 * </ul>
 * <p>
 * A skin subclass is stateless except for constructor arguments it needs to
 * bake (e.g. {@link ButtonSkin} holds its {@link EButtonVariant}); callers
 * construct a fresh instance per bake.
 * <p>
 * The scene-level {@link BaseSkin} is the exception: it composes three fixed
 * fragments around the palette's dynamic CSS variables instead of a single
 * format, so it overrides {@link #Bake} rather than filling placeholders.
 */
public abstract class ThemeSkin
{
	/**
	 * Define the CSS format for this skin: the stylesheet text block with
	 * {@code %s} placeholders where {@link #GetColorArguments} values go.
	 *
	 * @return the CSS format string (never null)
	 */
	protected abstract String GetCssFormat();

	/**
	 * Resolve the palette into the placeholder arguments, in the order the
	 * format declares them. The returned array is spread into
	 * {@link String#format} by {@link #Bake}.
	 *
	 * @param _Palette the palette to read colors from
	 * @return one argument per {@code %s} placeholder in {@link #GetCssFormat()}
	 */
	protected abstract Object[] GetColorArguments(ColorPalette _Palette);

	/**
	 * Bake the skin as an inline data-URI stylesheet URL: fill the CSS format
	 * with the palette arguments and base64-encode the result.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the data-URI stylesheet URL
	 */
	public String Bake(ColorPalette _Palette)
	{
		String __Css = String.format(GetCssFormat(), GetColorArguments(_Palette));
		return ToDataUri(__Css);
	}

	/**
	 * Wrap raw CSS as a base64 data-URI stylesheet URL.
	 *
	 * @param _Css the stylesheet text
	 * @return the {@code data:text/css;base64,...} URL
	 */
	protected static String ToDataUri(String _Css)
	{
		return "data:text/css;base64," + Base64.getEncoder().encodeToString(_Css.getBytes(StandardCharsets.UTF_8));
	}
}