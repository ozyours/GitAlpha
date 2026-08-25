package com.gitalpha.Theme.Skin;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.ThemeColor;

import java.util.Map;

/**
 * Tab style for the sub-tab header buttons ({@link com.gitalpha.UI.Components.ATabButton}):
 * the buttons render as flat tabs — square corners, generous {@code em} padding
 * and a larger label — sitting on the window backdrop (Background 2), while
 * the tab faces themselves derive from the panel background (Background 1):
 * inactive tabs darker (-12%), hover warmer (-6%), the selected tab brighter
 * (+12%) and labelled in the body text colour (unselected in muted), with a
 * pressed shade and content hairline. Mirrors the palette treatment of the
 * deprecated {@link TabPaneSkin} so the button header is visually
 * interchangeable with a real {@code TabPane} header (and remains so after
 * that skin's removal). No focus ring is drawn — tab buttons are
 * navigation controls, not primary actions.
 * <p>
 * The {@code .a-tab-close} rules style the {@code ×} close
 * {@link com.gitalpha.UI.Components.ATabButton} that
 * {@link com.gitalpha.UI.Components.ATabWidget} places inside each tab face
 * in modifiable mode: the Modena button chrome (background and border, with
 * their insets) is reset to transparent so it reads as a flat face — muted
 * normally, primary on hover as the affordance cue; the hover rule re-asserts
 * the transparent background so only the label colour changes. Because the
 * close button also carries {@code .a-tab-button} (inherited from
 * {@code ATabButton}), whose {@code :hover}/{@code :pressed} rules would
 * otherwise out-specify the flat reset, the compound
 * {@code .a-tab-button.a-tab-close} rules at the end re-flatten those states.
 * The {@code ×} glyph intentionally inherits the tab face's {@code 1.2em}
 * font size, matching the original close-graphic design.
 * <p>
	 * Twelve {@code %s} placeholders in CSS order: header strip Background2,
	 * inactive tab derive -12%, inactive label muted, hover tab derive -6%,
	 * selected tab derive +12%, selected label text, pressed shade, content
	 * hairline border, close face muted, close face hover, then muted and hover
	 * repeated for the compound close-button state rules. Shades are computed
	 * in CSS with {@code derive()} so the skin needs only palette hex values
	 * plus derived expressions.
	 * <p>
	 * The tab header's horizontal scroll bar is suppressed by setting
	 * {@link javafx.scene.control.ScrollPane.ScrollBarPolicy#NEVER} on its
	 * {@link javafx.scene.control.ScrollPane} in
	 * {@link com.gitalpha.UI.Components.ATabWidget}, rather than hiding a
	 * post-attach skin node with CSS; programmatic and wheel scrolling remain
	 * available.
	 */
public final class SubTabButtonSkin extends ThemeSkin
{
	/**
	 * CSS template with twelve {@code %s} placeholders in CSS order: header
	 * strip Background2, inactive tab, inactive label, hover tab, selected
	 * tab, selected label, pressed shade, content hairline, close face
	 * normal, close face hover, then muted and hover repeated for the
	 * compound {@code .a-tab-button.a-tab-close} state rules — as consumed by
	 * {@link #GetColorArguments}. The tab header's horizontal bar is omitted
	 * by setting {@link javafx.scene.control.ScrollPane.ScrollBarPolicy#NEVER}
	 * on the pane in {@link com.gitalpha.UI.Components.ATabWidget}; this skin
	 * does not need CSS rules to hide a mounted bar.
	 */
	private static final String CSS_FORMAT = """
			.a-tab-header {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			}
			.a-tab-button {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-border-radius: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0.28em 1.0em 0.27em 1.0em;
			    -fx-font-size: 1.2em;
			    -fx-cursor: hand;
			}
			.a-tab-button:hover {
			    -fx-background-color: %s;
			}
			.a-tab-button:selected {
			    -fx-background-color: %s;
			    -fx-background-insets: 0;
			    -fx-background-radius: 0;
			    -fx-text-fill: %s;
			}
			.a-tab-button:pressed {
			    -fx-background-color: %s;
			}
			.a-tab-content {
			    -fx-border-color: %s transparent transparent transparent;
			    -fx-border-width: 1 0 0 0;
			}
			.a-tab-close {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0 0 0 4;
			    -fx-cursor: hand;
			}
			.a-tab-close:hover {
			    -fx-background-color: transparent;
			    -fx-text-fill: %s;
			}
			.a-tab-button.a-tab-close {
			    -fx-background-color: transparent;
			    -fx-background-insets: 0;
			    -fx-border-color: transparent;
			    -fx-border-insets: 0;
			    -fx-text-fill: %s;
			    -fx-padding: 0 0 0 4;
			    -fx-cursor: hand;
			}
			.a-tab-button.a-tab-close:hover {
			    -fx-background-color: transparent;
			    -fx-text-fill: %s;
			}
			.a-tab-button.a-tab-close:pressed {
			    -fx-background-color: transparent;
			}
			""";

	/**
	 * @return the tab-button CSS format ({@link #CSS_FORMAT})
	 */
	@Override
	protected String GetCssFormat()
	{
		return CSS_FORMAT;
	}

	/**
	 * Resolve the twelve {@link #CSS_FORMAT} placeholders in CSS order: header
	 * strip Background2, inactive tab derive -12% from Background, inactive
	 * label muted, hover tab derive -6%, selected tab derive +12%, selected
	 * label text, pressed shade, content hairline border, close face muted,
	 * close face hover, then muted and hover repeated for the compound
	 * close-button state rules.
	 *
	 * @param _Palette the palette to read colors from
	 * @return the twelve placeholder values in CSS order
	 */
	@Override
	protected Object[] GetColorArguments(ColorPalette _Palette)
	{
		Map<String, ThemeColor> __Lookup = _Palette.GetColorLookup();
		String __Bg = _Palette.GetBackgroundColor().GetHex(__Lookup);
		return new Object[] {
				_Palette.GetBackground2Color().GetHex(__Lookup),
				"derive(" + __Bg + ", -12%)",
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				"derive(" + __Bg + ", -6%)",
				"derive(" + __Bg + ", +12%)",
				_Palette.GetTextColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetBorderColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup),
				_Palette.GetMutedTextColor().GetHex(__Lookup),
				_Palette.GetPrimaryColor().GetHex(__Lookup) };
	}
}
