package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * The RichTextFX diff-viewer skin, applied to the {@code CodeArea} in the
 * diff viewer via {@code codeArea.getStylesheets().add(...)} and re-applied
 * on palette switches: paragraph backgrounds for added/removed lines,
 * intra-line word highlights, and the selection highlight in the palette
 * primary.
 * <p>
 * The diff row colors are fixed hex literals (GitHub-style green/red, kept
 * identical across themes); only the selection fill is palette-driven.
 * <p>
 * The background properties intentionally differ — do not normalize them:
 * {@code diff-line-*} targets paragraph Regions via {@code setParagraphStyle}
 * (honors {@code -fx-background-color}); {@code diff-intra-*} targets inline
 * Text/Shape nodes via {@code setStyle} (ignores {@code -fx-background-color},
 * needs RichTextFX's {@code -rtfx-background-color}); {@code .selection}
 * targets RichTextFX's selection {@code Path} (a Shape, so {@code -fx-fill}).
 * <p>
 * Placeholder order: added line background, removed line background, added
 * intra-line background, removed intra-line background, primary (selection).
 */
public final class RichTextSkin extends ThemeSkin
{
	/** Fixed background colour for added lines (identical across themes). */
	private static final String ADDED_BG = "#e6ffec";
	/** Fixed background colour for removed lines (identical across themes). */
	private static final String REMOVED_BG = "#ffebe9";
	/** Fixed background for changed characters within an added line (deeper green, identical across themes). */
	private static final String ADDED_INTRA_BG = "#abf2bc";
	/** Fixed background for changed characters within a removed line (deeper red, identical across themes). */
	private static final String REMOVED_INTRA_BG = "#fbbcb6";

	private static final String CSS_FORMAT = """
			.diff-line-added   { -fx-background-color: %s; }
			.diff-line-removed { -fx-background-color: %s; }
			.diff-intra-added   { -rtfx-background-color: %s; }
			.diff-intra-removed { -rtfx-background-color: %s; }
			.styled-text-area .selection {
			    -fx-fill: %s;
			}
			""";

	/**
	 * @return the rich-text CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the five placeholder colors: the fixed diff row colors plus
	 * the palette primary for the selection highlight.
	 *
	 * @param _Palette the palette to read the primary color from
	 * @return added line bg, removed line bg, added intra bg, removed intra
	 * bg, primary (selection)
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		return new Object[] {
				ADDED_BG, REMOVED_BG,
				ADDED_INTRA_BG, REMOVED_INTRA_BG,
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}
