package com.gitalpha.Theme;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Singleton owner of the app's active color palette and the hub that pushes
 * theme changes to the UI. Widgets pull colors at construction via
 * {@link #GetPalette()}; when the palette is replaced (preset switch or user
 * override) {@link #SetActivePalette} re-applies the theme to every registered
 * {@link Scene} and broadcasts to the registered {@link IThemeChangeEvent}
 * listeners so Java-driven colors re-apply.
 * <p>
 * Theme application is two-tier: scenes opt in via {@link #RegisterScene} and
 * receive the scene-level base stylesheet (the {@code .root} focus-ring kill
 * plus the palette's {@code -gitalpha-*} CSS variables) immediately and again
 * on every palette change, while widget-level skins are Java-driven and
 * re-apply through the {@link IThemeChangeEvent} broadcast.
 * <p>
 * Mirrors the {@code AlphaEngine} event pattern: listeners and scenes are held
 * as {@link WeakReference}s and pruned on broadcast, so implementers never
 * need to unsubscribe — dead references are dropped the next time an event
 * fires.
 */
public class ThemeManager
{
	/**
	 * App-wide theme manager (singleton style matches {@code AlphaEngine.Instance}).
	 */
	public static final ThemeManager Instance = new ThemeManager();

	/** The active palette; never null (SetActivePalette rejects null) */
	private volatile ColorPalette ActivePalette = new LightTheme();

	/** Registered theme-change listeners (WeakReference, pruned on broadcast) */
	private final List<WeakReference<IThemeChangeEvent>> ThemeChangeEventList = new ArrayList<>();

	/** Registered scenes that get the theme CSS re-applied on palette change (WeakReference) */
	private final List<WeakReference<Scene>> SceneList = new ArrayList<>();

	/** @return the currently active palette, never null (call on the FX thread) */
	public ColorPalette GetPalette()
	{
		return ActivePalette;
	}

	/**
	 * Replace the active palette and push the change to the UI: re-apply the
	 * theme to every registered scene, then broadcast to every registered
	 * {@link IThemeChangeEvent} listener. Call on the FX thread — listeners
	 * re-apply Java colors directly.
	 *
	 * @param _Palette the new active palette
	 * @throws IllegalArgumentException if _Palette is null
	 */
	public void SetActivePalette(ColorPalette _Palette)
	{
		if (_Palette == null)
			throw new IllegalArgumentException("Palette must not be null");
		ActivePalette = _Palette;
		ApplyThemeToScenes();
		BroadcastThemeChange();
	}

	/**
	 * Stylesheets for a themed button in the given variant, baked from the
	 * active palette as an inline data-URI stylesheet. Delegates the CSS
	 * authoring to the private {@link ThemeSkin} so ThemeManager stays a thin
	 * coordinator.
	 *
	 * @param _Variant the button variant whose skin to bake
	 * @return the stylesheet URL for the baked skin
	 */
	public List<String> GetButtonStylesheets(EButtonVariant _Variant)
	{
		return List.of(ThemeSkin.BakeButtonCss(ActivePalette, _Variant));
	}

	/**
	 * Stylesheet for a themed list view ({@link com.gitalpha.UI.Components.AListView}),
	 * baked from the active palette as an inline data-URI stylesheet: minimalist
	 * scrollbars (thin rounded thumb, no arrows). See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked list-view skin
	 */
	public List<String> GetListViewStylesheets()
	{
		return List.of(ThemeSkin.BakeListViewCss(ActivePalette));
	}

	/**
	 * Stylesheet for a standalone themed scroll bar
	 * ({@link com.gitalpha.UI.Components.AScrollBar}), baked from the active
	 * palette as an inline data-URI stylesheet: the same minimalist scrollbar
	 * skin the list view carries (transparent track, thin rounded thumb, no
	 * arrows). See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked scroll-bar skin
	 */
	public List<String> GetScrollBarStylesheets()
	{
		return List.of(ThemeSkin.BakeScrollBarCss(ActivePalette));
	}

	/**
	 * Stylesheet for a themed check box ({@link com.gitalpha.UI.Components.ACheckBox}),
	 * baked from the active palette as an inline data-URI stylesheet: flat,
	 * minimalist box with an accent-filled checked state. See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked check-box skin
	 */
	public List<String> GetCheckBoxStylesheets()
	{
		return List.of(ThemeSkin.BakeCheckBoxCss(ActivePalette));
	}

	/**
	 * Stylesheet for a themed combo box ({@link com.gitalpha.UI.Components.AComboBox}),
	 * baked from the active palette as an inline data-URI stylesheet: flat
	 * secondary fill, palette border, palette text and a muted down arrow,
	 * with an accent focus ring (same skin family as the text field). See
	 * {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked combo-box skin
	 */
	public List<String> GetComboBoxStylesheets()
	{
		return List.of(ThemeSkin.BakeComboBoxCss(ActivePalette));
	}

	/**
	 * Stylesheet for a themed text field ({@link com.gitalpha.UI.Components.ATextField}),
	 * baked from the active palette as an inline data-URI stylesheet: flat
	 * secondary fill, palette border, accent focus ring. See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked text-field skin
	 */
	public List<String> GetTextFieldStylesheets()
	{
		return List.of(ThemeSkin.BakeTextInputCss(ActivePalette));
	}

	/**
	 * Stylesheet for a themed text area ({@link com.gitalpha.UI.Components.ATextArea}),
	 * baked from the active palette as an inline data-URI stylesheet: flat
	 * secondary fill, palette border, accent focus ring (same skin family as
	 * the text field). See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked text-area skin
	 */
	public List<String> GetTextAreaStylesheets()
	{
		return List.of(ThemeSkin.BakeTextInputCss(ActivePalette));
	}

	/**
	 * Stylesheet for a themed tab pane ({@link com.gitalpha.UI.Components.ATabPane}),
	 * baked from the active palette as an inline data-URI stylesheet: flat tab
	 * header with the active tab brighter and the inactive tabs darker than
	 * the palette background. See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked tab-pane skin
	 */
	public List<String> GetTabPaneStylesheets()
	{
		return List.of(ThemeSkin.BakeTabPaneCss(ActivePalette));
	}

	/**
	 * Key under which the re-bake listener for a themed dialog pane is stored
	 * in the pane's {@code properties} map, so the listener lives exactly as
	 * long as the pane (ThemeManager only holds it weakly).
	 */
	private static final Object DialogThemeListenerKey = new Object();

	/**
	 * Apply the active theme to a message box (an {@link Alert}, a
	 * {@link javafx.scene.control.TextInputDialog} or any other
	 * {@link Dialog}): the pane gets the baked dialog skin (palette background,
	 * text and buttons matching the app's themed buttons) and its scene is
	 * registered so the base theme (focus-ring kill + palette variables)
	 * applies there too. Dialogs run in their own stage and scene, which do
	 * not inherit the owner window's stylesheets unless {@code initOwner} is
	 * used, so the skin must be applied to the pane explicitly — call this
	 * right after constructing the dialog. The skin re-bakes on palette
	 * switches while the dialog is alive.
	 *
	 * @param _Dialog the dialog to theme
	 */
	public void ApplyThemeToDialog(Dialog<?> _Dialog)
	{
		DialogPane __Pane = _Dialog.getDialogPane();
		ApplyDialogSkin(__Pane);
		// The dialog's own scene is not one of the registered app scenes (it is
		// created by the dialog machinery), so register it to receive the base
		// theme now and on palette switches.
		Scene __Scene = __Pane.getScene();
		if (__Scene != null)
			RegisterScene(__Scene);
		// Re-bake the skin URL on palette switches. The listener is stored in
		// the pane's properties so it lives as long as the pane (dialogs are
		// transient; ThemeManager's weak reference alone would let it be
		// collected before the dialog closes).
		if (__Pane.getProperties().get(DialogThemeListenerKey) == null)
		{
			IThemeChangeEvent __Listener = _Palette -> ApplyDialogSkin(__Pane);
			__Pane.getProperties().put(DialogThemeListenerKey, __Listener);
			AddIThemeChangeEvent(__Listener);
		}
	}

	/**
	 * Swap the dialog pane's inline skin stylesheet for one baked from the
	 * active palette. The data-URI URL changes whenever the colors do, so
	 * JavaFX re-parses the new skin. The pane owns the dialog-skin slot (its
	 * stylesheet list is otherwise empty), so it is cleared before re-adding.
	 */
	private void ApplyDialogSkin(DialogPane _Pane)
	{
		ObservableList<String> __Sheets = _Pane.getStylesheets();
		__Sheets.clear();
		__Sheets.add(ThemeSkin.BakeDialogCss(ActivePalette));
	}

	/**
	 * Register a theme-change listener. Held weakly, so a listener that is
	 * garbage-collected without unsubscribing is pruned on the next broadcast.
	 * Call on the FX thread (the list is mutated without synchronization).
	 *
	 * @param _Event the listener to notify on palette changes
	 */
	public void AddIThemeChangeEvent(IThemeChangeEvent _Event)
	{
		ThemeChangeEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregister a theme-change listener (optional — dead references are
	 * pruned automatically on the next broadcast). Call on the FX thread
	 * (the list is mutated without synchronization).
	 *
	 * @param _Event the listener to stop notifying
	 */
	public void RemoveIThemeChangeEvent(IThemeChangeEvent _Event)
	{
		int i = 0;
		while (i < ThemeChangeEventList.size())
		{
			if (ThemeChangeEventList.get(i).get() == _Event)
			{
				ThemeChangeEventList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Register a scene so the base theme (the {@code .root} focus-ring kill
	 * plus the palette CSS variables) is applied to it immediately and
	 * re-applied whenever the palette changes. Held weakly; scenes are pruned
	 * once garbage-collected. Call on the FX thread (the list is mutated
	 * without synchronization).
	 *
	 * @param _Scene the scene to re-apply the theme to
	 */
	public void RegisterScene(Scene _Scene)
	{
		SceneList.add(new WeakReference<>(_Scene));
		ApplyThemeToScenes();
	}

	/**
	 * Unregister a scene (optional — dead references are pruned automatically
	 * on the next apply). Call on the FX thread (the list is mutated without
	 * synchronization).
	 *
	 * @param _Scene the scene to stop re-applying the theme to
	 */
	public void UnregisterScene(Scene _Scene)
	{
		int i = 0;
		while (i < SceneList.size())
		{
			if (SceneList.get(i).get() == _Scene)
			{
				SceneList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Re-apply the active palette's base CSS to every registered scene. Called
	 * from {@link #SetActivePalette} (re-theme existing scenes) and
	 * {@link #RegisterScene} (theme a scene the moment it is registered). The
	 * scene-level stylesheet is baked by
	 * {@link ThemeSkin#BakeBaseCss(ColorPalette)} as an inline data-URI URL and
	 * swapped into each scene's stylesheet list (owned in the first slot,
	 * index 0, so repeated re-applies replace rather than accumulate). Its
	 * content is the {@code .root} focus-ring kill — JavaFX focus colors are
	 * looked-up colors, so a single root value disables the Modena ring for
	 * every control in the scene — plus the palette's {@code -gitalpha-*}
	 * CSS variables (see {@link ColorPalette#GetCssOverrides()}). Widget-level
	 * skins are Java-driven and re-apply through {@link IThemeChangeEvent}, so
	 * they need no scene involvement. Dead scene references are pruned here
	 * regardless, so the registry never grows stale.
	 */
	private void ApplyThemeToScenes()
	{
		String __BaseUrl = ThemeSkin.BakeBaseCss(ActivePalette);
		int i = 0;
		while (i < SceneList.size())
		{
			Scene __Scene = SceneList.get(i).get();
			if (__Scene == null)
			{
				SceneList.remove(i);
			}
			else
			{
				// The base stylesheet owns the first slot; replace it in place
				// so repeated palette switches don't accumulate stale URLs.
				ObservableList<String> __Sheets = __Scene.getStylesheets();
				if (!__Sheets.isEmpty() && __Sheets.get(0).startsWith("data:text/css;base64,"))
					__Sheets.set(0, __BaseUrl);
				else
					__Sheets.add(0, __BaseUrl);
				i++;
			}
		}
	}

	/**
	 * Notify every live registered {@link IThemeChangeEvent} listener with the
	 * active palette, pruning dead WeakReferences inline so the registry never
	 * grows stale. Runs on the FX thread from {@link #SetActivePalette}, so
	 * listeners can re-apply Java colors directly without a
	 * {@code Platform.runLater} hop.
	 */
	private void BroadcastThemeChange()
	{
		int i = 0;
		while (i < ThemeChangeEventList.size())
		{
			WeakReference<IThemeChangeEvent> __Ref = ThemeChangeEventList.get(i);
			IThemeChangeEvent __Event = __Ref.get();
			if (__Event != null)
			{
				__Event.Event(ActivePalette);
				i++;
			}
			else
			{
				ThemeChangeEventList.remove(i);
			}
		}
	}

	/**
	 * CSS-authoring implementation detail, kept private inside ThemeManager so
	 * the manager stays a thin coordinator. Owns the skin templates and bakes
	 * them from the active palette; promoted to its own file if it outgrows
	 * this one.
	 */
	private static final class ThemeSkin
	{
		/**
		 * The button skin: density (small padding, 12px text), GitHub-style
		 * border and a focus ring. Colors are {@code %s} placeholders filled
		 * from the palette by {@link #BakeButtonCss} — order: background, text,
		 * border, hover background, pressed background, focus border.
		 */
		private static final String BUTTON_CSS_FORMAT = """
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
		 * Bake the button skin for the given variant as an inline data-URI
		 * stylesheet URL. The URL changes whenever the colors do, so JavaFX
		 * re-parses the new skin (and keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @param _Variant the button variant whose skin to bake
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeButtonCss(ColorPalette _Palette, EButtonVariant _Variant)
		{
			String __Css = String.format(BUTTON_CSS_FORMAT, (Object[]) _Variant.ResolveSkinColors(_Palette));
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The list-view skin baked by {@link #BakeListViewCss}: a flat palette
		 * background (cells, odd cells and the border ring all use the
		 * background colour, so even/odd stripes and the default border
		 * disappear) plus a minimalist scrollbar — transparent track, a thin
		 * rounded thumb whose colour comes from the palette's muted-text slot,
		 * and no increment/decrement arrows. The scrollbar rules mirror the
		 * standalone {@code .a-scroll-bar} skin (see {@link #SCROLL_BAR_CSS_FORMAT}),
		 * so a standalone scrollbar and the list's own bars look identical.
		 * <p>
		 * The container itself is frameless — no border or shadow frames the
		 * whole list (the default ring is suppressed by setting the ring
		 * colour to the palette background), so the skin's only border lives
		 * on the rows.
		 * <p>
		 * Each row (cell) carries that border as a hairline bottom separator
		 * in the palette's border colour, drawn within the cell's own bounds
		 * so the pinned row height is unaffected. Hovered rows fill with the
		 * palette's passive highlight; selected rows fill with the active
		 * highlight and flip to white text (same white-on-accent convention as
		 * the combo popup's selected row). The hover/selected rules mirror
		 * Modena's full {@code .virtual-flow} path — like the combo popup, the
		 * short {@code .a-list-view .list-cell:selected} form loses the cascade
		 * to Modena's more specific {@code :filled:selected} rules, so the
		 * palette values need the full path to win at equal specificity.
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette — order:
		 * list background (x3: cells, odd cells, border), row separator, hover
		 * cell, selected cell, thumb, hover thumb, pressed thumb. Hover/pressed
		 * shades are computed in CSS with {@code derive()} so the skin needs
		 * nothing but the palette hex values.
		 */
		private static final String LIST_VIEW_CSS_FORMAT = """
				.a-list-view {
				    -fx-control-inner-background: %s;
				    -fx-control-inner-background-alt: %s;
				    -fx-box-border: %s;
				}
				.a-list-view .list-cell {
				    -fx-border-color: %s;
				    -fx-border-width: 0 0 1 0;
				    -fx-border-insets: 0;
				}
				.a-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:hover {
				    -fx-background-color: %s;
				}
				.a-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:filled:selected {
				    -fx-background-color: %s;
				    -fx-text-fill: #ffffff;
				}
				.a-list-view .scroll-bar {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-padding: 0;
				}
				.a-list-view .scroll-bar:vertical {
				    -fx-pref-width: 8;
				}
				.a-list-view .scroll-bar:horizontal {
				    -fx-pref-height: 8;
				}
				.a-list-view .scroll-bar > .thumb {
				    -fx-background-color: %s;
				    -fx-background-insets: 1;
				    -fx-background-radius: 3;
				}
				.a-list-view .scroll-bar > .thumb:hover {
				    -fx-background-color: %s;
				}
				.a-list-view .scroll-bar > .thumb:pressed {
				    -fx-background-color: %s;
				}
				.a-list-view .scroll-bar > .increment-button,
				.a-list-view .scroll-bar > .decrement-button {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-padding: 0;
				    -fx-pref-width: 0;
				    -fx-pref-height: 0;
				}
				.a-list-view .scroll-bar > .increment-button > .increment-arrow,
				.a-list-view .scroll-bar > .decrement-button > .decrement-arrow {
				    -fx-background-color: transparent;
				    -fx-padding: 0;
				}
				""";

		/**
		 * The standalone scrollbar skin baked by {@link #BakeScrollBarCss} and
		 * applied to every themed {@link com.gitalpha.UI.Components.AScrollBar}:
		 * a minimalist bar matching the list-view skin's own scrollbars —
		 * transparent track, a thin rounded thumb from the palette's muted-text
		 * slot, and no increment/decrement arrows. Mirror of the
		 * {@code .a-list-view .scroll-bar} rules in {@link #LIST_VIEW_CSS_FORMAT},
		 * scoped to the {@code .a-scroll-bar} class because the standalone bar
		 * lives outside any list (e.g. the diff viewer's bottom pan bar) and
		 * carries this stylesheet on its own node.
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette — order:
		 * thumb, hover thumb, pressed thumb. Hover/pressed shades are computed
		 * in CSS with {@code derive()} so the skin needs nothing but the
		 * palette hex values.
		 */
		private static final String SCROLL_BAR_CSS_FORMAT = """
				.a-scroll-bar {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-padding: 0;
				}
				.a-scroll-bar:vertical {
				    -fx-pref-width: 8;
				}
				.a-scroll-bar:horizontal {
				    -fx-pref-height: 8;
				}
				.a-scroll-bar > .thumb {
				    -fx-background-color: %s;
				    -fx-background-insets: 1;
				    -fx-background-radius: 3;
				}
				.a-scroll-bar > .thumb:hover {
				    -fx-background-color: %s;
				}
				.a-scroll-bar > .thumb:pressed {
				    -fx-background-color: %s;
				}
				.a-scroll-bar > .increment-button,
				.a-scroll-bar > .decrement-button {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-padding: 0;
				    -fx-pref-width: 0;
				    -fx-pref-height: 0;
				}
				.a-scroll-bar > .increment-button > .increment-arrow,
				.a-scroll-bar > .decrement-button > .decrement-arrow {
				    -fx-background-color: transparent;
				    -fx-padding: 0;
				}
				""";

		/**
		 * Bake the list-view scrollbar skin as an inline data-URI stylesheet URL.
		 * The URL changes whenever the colors do, so JavaFX re-parses the new
		 * skin (and keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeListViewCss(ColorPalette _Palette)
		{
			String __Background = _Palette.GetBackgroundColor();
			String __Thumb = _Palette.GetMutedTextColor();
			String __Css = String.format(LIST_VIEW_CSS_FORMAT, __Background, __Background, __Background,
				_Palette.GetBorderColor(),
				_Palette.GetPassiveHighlightColor(), _Palette.GetActiveHighlightColor(),
				__Thumb, "derive(" + __Thumb + ", -15%)", "derive(" + __Thumb + ", -25%)");
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * Bake the standalone scrollbar skin as an inline data-URI stylesheet
		 * URL. The URL changes whenever the colors do, so JavaFX re-parses the
		 * new skin (and keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeScrollBarCss(ColorPalette _Palette)
		{
			String __Thumb = _Palette.GetMutedTextColor();
			String __Css = String.format(SCROLL_BAR_CSS_FORMAT,
				__Thumb, "derive(" + __Thumb + ", -15%)", "derive(" + __Thumb + ", -25%)");
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The minimalist check-box skin: a flat, single-layer box (no Modena
		 * gradient stack) with a subtle secondary fill and border that fills
		 * with the palette's active highlight when selected, a white check mark
		 * and an accent focus ring. The check mark is an intentional literal:
		 * white reads on both the light and dark accents (same convention as
		 * DANGER buttons' white text).
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette by
		 * {@link #BakeCheckBoxCss} — order: box background, box border, hover
		 * background (passive highlight), hover border, pressed background,
		 * selected background (active highlight), selected border, check mark,
		 * focus border.
		 */
		private static final String CHECK_BOX_CSS_FORMAT = """
				.a-check-box {
				    -fx-padding: 0;
				    -fx-cursor: hand;
				}
				.a-check-box > .box {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 3;
				    -fx-border-color: %s;
				    -fx-border-insets: 0;
				    -fx-border-radius: 3;
				    -fx-padding: 2;
				}
				.a-check-box:hover > .box {
				    -fx-background-color: %s;
				    -fx-border-color: %s;
				}
				.a-check-box:armed > .box {
				    -fx-background-color: %s;
				}
				.a-check-box:selected > .box {
				    -fx-background-color: %s;
				    -fx-border-color: %s;
				}
				.a-check-box:selected > .box > .mark {
				    -fx-background-color: %s;
				}
				.a-check-box:focused > .box {
				    -fx-border-color: %s;
				}
				""";

		/**
		 * Bake the check-box skin as an inline data-URI stylesheet URL. The URL
		 * changes whenever the colors do, so JavaFX re-parses the new skin (and
		 * keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeCheckBoxCss(ColorPalette _Palette)
		{
			String __Css = String.format(CHECK_BOX_CSS_FORMAT,
				_Palette.GetSecondaryColor(), _Palette.GetBorderColor(),
				_Palette.GetPassiveHighlightColor(), _Palette.GetPrimaryColor(),
				_Palette.GetBorderColor(),
				_Palette.GetActiveHighlightColor(), _Palette.GetPrimaryColor(),
				"#ffffff",
				_Palette.GetPrimaryColor());
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The combo-box skin: a flat single-layer fill (no Modena shadow/border
		 * stack) with a palette border that switches to the accent (primary) on
		 * focus — the same text-input skin family, with the value cell and the
		 * arrow button flattened to transparent so the control's own fill shows
		 * through. The arrow is a plain muted triangle (Modena's shape, recolored).
		 * <p>
		 * The drop-down popup is intentionally NOT styled here: it lives in its
		 * own scene that node-level stylesheets cannot reach, so its rules are
		 * baked into the scene base stylesheet instead (see
		 * {@link #COMBO_BOX_POPUP_CSS_FORMAT}).
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette by
		 * {@link #BakeComboBoxCss} — order: fill, border, text, arrow, focus border.
		 */
		private static final String COMBO_BOX_CSS_FORMAT = """
				.a-combo-box {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 4;
				    -fx-border-color: %s;
				    -fx-border-radius: 4;
				    -fx-border-insets: 0;
				    -fx-font-size: 12px;
				    -fx-cursor: hand;
				}
				.a-combo-box > .list-cell {
				    -fx-background-color: transparent;
				    -fx-text-fill: %s;
				    -fx-padding: 4 8 4 8;
				}
				.a-combo-box > .arrow-button {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-padding: 0 8 0 0;
				}
				.a-combo-box > .arrow-button > .arrow {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-padding: 4;
				    -fx-shape: "M 0 0 H 7 L 3.5 4 z";
				}
				.a-combo-box:focused {
				    -fx-border-color: %s;
				}
				.a-combo-box:disabled {
				    -fx-opacity: 0.4;
				}
				""";

		/**
		 * Bake the combo-box skin as an inline data-URI stylesheet URL. The URL
		 * changes whenever the colors do, so JavaFX re-parses the new skin (and
		 * keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeComboBoxCss(ColorPalette _Palette)
		{
			String __Css = String.format(COMBO_BOX_CSS_FORMAT,
				_Palette.GetSecondaryColor(), _Palette.GetBorderColor(),
				_Palette.GetTextColor(), _Palette.GetMutedTextColor(),
				_Palette.GetPrimaryColor());
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The message-box (dialog) skin baked by {@link #BakeDialogCss} and
		 * applied to a dialog pane by {@link ThemeManager#ApplyThemeToDialog}:
		 * palette background with a border hairline, a secondary header panel
		 * with the bold palette header text, palette content text, buttons
		 * matching the app's NORMAL themed buttons (the default button — e.g.
		 * OK — is filled with the accent/primary and white text) and a text
		 * field matching the app's text-input skin for input dialogs.
		 * Colors are inlined because the skin is attached to the pane node, not
		 * the scene, so the scene's {@code -gitalpha-*} lookups would not
		 * resolve (same convention as the button/text-field skins).
		 * <p>
		 * Button selectors mirror Modena's full
		 * {@code .dialog-pane > .button-bar > .container > .button} path so the
		 * palette values win the cascade at equal specificity.
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette by
		 * {@link #BakeDialogCss} — order: pane background, pane border, header
		 * background, header text, content text, the six NORMAL button colors
		 * (background, text, border, hover, pressed, focus border), default
		 * button background, default button border, default hover, text-field
		 * background, text-field border, text-field text, text-field prompt,
		 * text-field selection (active highlight), text-field focus border.
		 */
		private static final String DIALOG_CSS_FORMAT = """
				.dialog-pane {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-border-color: %s;
				    -fx-border-width: 1;
				    -fx-padding: 12;
				}
				.dialog-pane:header .header-panel {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-padding: 0 0 8 0;
				}
				.dialog-pane:header .header-panel .label {
				    -fx-text-fill: %s;
				    -fx-font-size: 15px;
				    -fx-font-weight: bold;
				}
				.dialog-pane > .content.label {
				    -fx-text-fill: %s;
				    -fx-font-size: 12px;
				}
				.dialog-pane > .button-bar > .container > .button {
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
				.dialog-pane > .button-bar > .container > .button:hover {
				    -fx-background-color: %s;
				}
				.dialog-pane > .button-bar > .container > .button:pressed {
				    -fx-background-color: %s;
				}
				.dialog-pane > .button-bar > .container > .button:focused {
				    -fx-border-color: %s;
				}
				.dialog-pane > .button-bar > .container > .button:default {
				    -fx-background-color: %s;
				    -fx-text-fill: #ffffff;
				    -fx-border-color: %s;
				}
				.dialog-pane > .button-bar > .container > .button:default:hover {
				    -fx-background-color: %s;
				}
				.dialog-pane .text-field {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 4;
				    -fx-border-color: %s;
				    -fx-border-radius: 4;
				    -fx-border-insets: 0;
				    -fx-text-fill: %s;
				    -fx-prompt-text-fill: %s;
				    -fx-highlight-fill: %s;
				    -fx-highlight-text-fill: #ffffff;
				    -fx-cursor: text;
				}
				.dialog-pane .text-field:focused {
				    -fx-border-color: %s;
				}
				""";

		/**
		 * Bake the message-box skin as an inline data-URI stylesheet URL. The
		 * URL changes whenever the colors do, so JavaFX re-parses the new skin
		 * (and keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeDialogCss(ColorPalette _Palette)
		{
			// Build the full argument list explicitly: an array passed to a
			// varargs method is only spread when it is the sole trailing
			// argument, so the NORMAL button colors are unfolded here instead
			// of being handed over as one array (which would starve the format
			// of placeholders).
			String[] __ButtonColors = EButtonVariant.NORMAL.ResolveSkinColors(_Palette);
			String __Primary = _Palette.GetPrimaryColor();
			Object[] __Args = new Object[] {
					_Palette.GetBackgroundColor(), _Palette.GetBorderColor(),
					_Palette.GetSecondaryColor(),
					_Palette.GetTextColor(), _Palette.GetTextColor(),
					__ButtonColors[0], __ButtonColors[1], __ButtonColors[2],
					__ButtonColors[3], __ButtonColors[4], __ButtonColors[5],
					__Primary, __Primary, "derive(" + __Primary + ", -10%)",
					_Palette.GetSecondaryColor(), _Palette.GetBorderColor(),
					_Palette.GetTextColor(), _Palette.GetMutedTextColor(),
					_Palette.GetActiveHighlightColor(), __Primary };
			String __Css = String.format(DIALOG_CSS_FORMAT, __Args);
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The combo-box popup skin, baked into the scene base stylesheet rather
		 * than the node skin: the drop-down popup renders in its own scene that
		 * node-level stylesheets cannot reach, but it inherits the owner scene's
		 * stylesheets, so these rules resolve their colors from the scene's
		 * {@code -gitalpha-*} variables. Matches the closed control's flat look
		 * — palette background and border around the list, palette text with a
		 * passive-highlight (hover) row and an active-highlight (selected) row
		 * with white text. The popup background is the palette background rather
		 * than the secondary fill so the passive-colored hover row stays visible
		 * against it. The cell selector mirrors Modena's full
		 * {@code .virtual-flow} path so the palette values win the cascade at
		 * equal specificity.
		 */
		private static final String COMBO_BOX_POPUP_CSS_FORMAT = """
				.combo-box-popup > .list-view {
				    -fx-background-color: -gitalpha-background;
				    -fx-background-insets: 0;
				    -fx-background-radius: 4;
				    -fx-border-color: -gitalpha-border;
				    -fx-border-width: 1;
				    -fx-border-radius: 4;
				}
				.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell {
				    -fx-background-color: transparent;
				    -fx-padding: 4 10 4 10;
				    -fx-text-fill: -gitalpha-text;
				}
				.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:hover {
				    -fx-background-color: -gitalpha-passive-highlight;
				}
				.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:selected {
				    -fx-background-color: -gitalpha-active-highlight;
				    -fx-text-fill: #ffffff;
				}
				""";

		/**
		 * The text-input skin shared by the text field and the text area: a flat
		 * single-layer fill (no Modena shadow/border stack) with a palette
		 * border that switches to the accent (primary) on focus, palette text,
		 * muted prompt text and an active-highlight selection with white text
		 * (same white-on-accent convention as the check mark). Text areas
		 * additionally flatten their internal scroll-pane/viewport/content to
		 * transparent so the control's own fill shows through.
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette by
		 * {@link #BakeTextInputCss} — order: fill, border, text, prompt text,
		 * selection fill, focus border.
		 */
		private static final String TEXT_INPUT_CSS_FORMAT = """
				.a-text-field,
				.a-text-area {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 4;
				    -fx-border-color: %s;
				    -fx-border-radius: 4;
				    -fx-border-insets: 0;
				    -fx-text-fill: %s;
				    -fx-prompt-text-fill: %s;
				    -fx-highlight-fill: %s;
				    -fx-highlight-text-fill: #ffffff;
				    -fx-cursor: text;
				}
				.a-text-field:focused,
				.a-text-area:focused {
				    -fx-border-color: %s;
				}
				.a-text-field:disabled,
				.a-text-area:disabled {
				    -fx-opacity: 0.4;
				}
				.a-text-area > .scroll-pane {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				    -fx-background-radius: 4;
				}
				.a-text-area > .scroll-pane > .viewport,
				.a-text-area > .scroll-pane > .viewport > .content {
				    -fx-background-color: transparent;
				    -fx-background-insets: 0;
				}
				""";

		/**
		 * Bake the text-input skin as an inline data-URI stylesheet URL. The URL
		 * changes whenever the colors do, so JavaFX re-parses the new skin (and
		 * keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeTextInputCss(ColorPalette _Palette)
		{
			String __Css = String.format(TEXT_INPUT_CSS_FORMAT,
				_Palette.GetSecondaryColor(), _Palette.GetBorderColor(),
				_Palette.GetTextColor(), _Palette.GetMutedTextColor(),
				_Palette.GetActiveHighlightColor(), _Palette.GetPrimaryColor());
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The flat tab-pane skin: the header strip uses the palette background;
		 * inactive tabs are derived darker and the active (selected) tab derived
		 * brighter, so the selected tab reads as the lit one in both themes.
		 * Labels follow the palette (muted for inactive, body text for active)
		 * and the close button is muted, accent on hover. The tab button is
		 * roomier than Modena's default (generous vertical and horizontal
		 * padding, so the label sits clear of the tab edges) and its label
		 * larger, both set in {@code em} so they scale with the base font.
		 * Selector chains mirror Modena's full paths so the palette values win
		 * the cascade at equal specificity.
		 * <p>
		 * A hairline runs across the top of the content canvas, separating the
		 * tab buttons from the content below.
		 * <p>
		 * Colors are {@code %s} placeholders filled from the palette by
		 * {@link #BakeTabPaneCss} — order: strip background, inactive tab,
		 * hover tab, active tab, inactive label, active label, close button,
		 * close hover, header/content separator. Shades are computed in CSS
		 * with {@code derive()} so the skin needs nothing but the palette hex
		 * values.
		 */
		private static final String TAB_PANE_CSS_FORMAT = """
				.a-tab-pane > .tab-header-area > .tab-header-background {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				}
				.a-tab-pane > .tab-header-area > .headers-region > .tab {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 0;
				    /* Roomier than Modena's default: generous em padding makes the tab button bigger and keeps the label clear of the tab edges. */
				    -fx-padding: 0.28em 1.0em 0.27em 1.0em;
				}
				.a-tab-pane > .tab-header-area > .headers-region > .tab:hover {
				    -fx-background-color: %s;
				}
				.a-tab-pane > .tab-header-area > .headers-region > .tab:selected {
				    -fx-background-color: %s;
				    -fx-background-insets: 0;
				    -fx-background-radius: 0;
				}
				.a-tab-pane > .tab-header-area > .headers-region > .tab > .tab-container > .tab-label {
				    -fx-text-fill: %s;
				    /* Larger than Modena's default so the label reads clearly at the bigger button size. */
				    -fx-font-size: 1.2em;
				}
				.a-tab-pane > .tab-header-area > .headers-region > .tab:selected > .tab-container > .tab-label,
				.a-tab-pane > .tab-header-area > .headers-region > .tab:selected:focus > .tab-container > .tab-label {
				    -fx-text-fill: %s;
				}
				.a-tab-pane .tab-close-button {
				    -fx-background-color: %s;
				}
				.a-tab-pane .tab-close-button:hover {
				    -fx-background-color: %s;
				}
				.a-tab-pane > .tab-content-area {
				    -fx-border-color: %s transparent transparent transparent;
				    -fx-border-width: 1 0 0 0;
				}
				""";

		/**
		 * Bake the tab-pane skin as an inline data-URI stylesheet URL. The URL
		 * changes whenever the colors do, so JavaFX re-parses the new skin (and
		 * keeps the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeTabPaneCss(ColorPalette _Palette)
		{
			String __Bg = _Palette.GetBackgroundColor();
			String __Css = String.format(TAB_PANE_CSS_FORMAT,
				__Bg,
				"derive(" + __Bg + ", -12%)",
				"derive(" + __Bg + ", -6%)",
				"derive(" + __Bg + ", +12%)",
				_Palette.GetMutedTextColor(),
				_Palette.GetTextColor(),
				_Palette.GetMutedTextColor(),
				_Palette.GetPrimaryColor(),
				_Palette.GetBorderColor());
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}

		/**
		 * The scene-level base stylesheet: a single {@code .root} rule that
		 * disables the default Modena focus ring app-wide. JavaFX focus colors
		 * are looked-up colors, so every control referencing
		 * {@code -fx-focus-color} / {@code -fx-faint-focus-color} resolves them
		 * up the scene graph to this root value — one rule covers all controls
		 * in every registered scene. Explicit {@code :focused} borders baked by
		 * the widget skins (buttons, text inputs, check boxes) are unaffected:
		 * they set {@code -fx-border-color} directly, not via the focus lookups.
		 */
		private static final String BASE_CSS_FORMAT = """
				.root {
				    -fx-focus-color: transparent;
				    -fx-faint-focus-color: transparent;
				}
				""";

		/**
		 * Bake the scene-level base stylesheet as an inline data-URI stylesheet
		 * URL: the focus-ring kill from {@link #BASE_CSS_FORMAT} plus the
		 * palette's {@code -gitalpha-*} CSS variables from
		 * {@link ColorPalette#GetCssOverrides()} (two {@code .root} rules that
		 * CSS merges) and the combo-box popup skin from
		 * {@link #COMBO_BOX_POPUP_CSS_FORMAT} — popups render in their own scene
		 * that node-level skins cannot reach, but inherit this scene stylesheet,
		 * so the popup rules are the only way to theme them. The URL changes
		 * whenever the colors do, so JavaFX re-parses the new skin (and keeps
		 * the old one cached until dropped).
		 *
		 * @param _Palette the palette to read colors from
		 * @return the data-URI stylesheet URL
		 */
		private static String BakeBaseCss(ColorPalette _Palette)
		{
			String __Css = BASE_CSS_FORMAT + "\n" + _Palette.GetCssOverrides() + COMBO_BOX_POPUP_CSS_FORMAT;
			return "data:text/css;base64," + Base64.getEncoder().encodeToString(__Css.getBytes(StandardCharsets.UTF_8));
		}
	}
}
