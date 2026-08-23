package com.gitalpha.UI.Components;

import com.gitalpha.Theme.ColorPalette;
import com.gitalpha.Type.EButtonVariant;
import com.gitalpha.Theme.IThemeChangeEvent;
import com.gitalpha.Theme.ThemeManager;
import javafx.scene.control.Button;

/**
 * Fully customized template button: a {@link Button} that carries its own skin
 * as an inline data-URI stylesheet, so no base theme (Modena) contributes to
 * its look.
 * <p>
 * This is the app-wide themed button component: the only themed button in the
 * UI (used by the quick-command bar and the commit form) lives here in the
 * shared {@code com.gitalpha.UI.Components} package, so all other widgets
 * reuse it instead of duplicating skin logic.
 * <p>
 * The skin is not baked here — it is owned centrally by the theme layer
 * ({@link ThemeManager#GetButtonStylesheets} / {@code ThemeSkin}), so every
 * themed button shares one style source. The skin colors are re-baked
 * whenever the palette changes (push via {@link IThemeChangeEvent}).
 * <p>
 * Hex values are inlined rather than referenced as CSS lookups on purpose:
 * lookups defined on the scene's {@code .root} are not visible to a stylesheet
 * attached to a single node, so JavaFX would fail to resolve them. Inlining
 * keeps the template fully self-contained — it needs nothing but the palette.
 * <p>
 * Each palette state produces a distinct data-URI URL, so the CSS parser
 * re-parses only when the colors actually change.
 */
public class AButton extends Button implements IThemeChangeEvent
{
	/** The visual variant whose skin this button carries (normal by default) */
	private final EButtonVariant Variant;

	/**
	 * Create a template button with the default (normal) skin. Registers as a
	 * theme listener so the skin re-bakes on palette switches.
	 *
	 * @param _Text the button label
	 */
	public AButton(String _Text)
	{
		this(_Text, EButtonVariant.NORMAL);
	}

	/**
	 * Create a template button with the skin of the given variant. Registers as
	 * a theme listener so the skin re-bakes on palette switches.
	 *
	 * @param _Text    the button label
	 * @param _Variant the visual variant whose skin to use
	 */
	public AButton(String _Text, EButtonVariant _Variant)
	{
		super(_Text);
		Variant = _Variant;
		getStyleClass().add("a-button");
		ApplySkin();
		ThemeManager.Instance.AddIThemeChangeEvent(this);
	}

	/**
	 * Theme-change push: re-bake the skin with the new palette's colors.
	 */
	@Override
	public void Event(ColorPalette _Palette)
	{
		ApplySkin();
	}

	/**
	 * Replace the inline skin stylesheet with one baked from the active palette
	 * for this button's variant. The data-URI URL changes whenever the colors
	 * do, so JavaFX re-parses the new skin (and keeps the old one cached until
	 * it is dropped). Protected so subclasses (e.g. {@link ATabButton}) can
	 * extend the skin set with their own state skins.
	 */
	protected void ApplySkin()
	{
		getStylesheets().clear();
		getStylesheets().addAll(ThemeManager.Instance.GetButtonStylesheets(Variant));
	}
}