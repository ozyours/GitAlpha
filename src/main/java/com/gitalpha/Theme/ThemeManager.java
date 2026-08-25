package com.gitalpha.Theme;

import com.gitalpha.Theme.Skin.BaseSkin;
import com.gitalpha.Theme.Skin.ButtonSkin;
import com.gitalpha.Theme.Skin.CheckBoxSkin;
import com.gitalpha.Theme.Skin.ComboBoxSkin;
import com.gitalpha.Theme.Skin.DialogSkin;
import com.gitalpha.Theme.Skin.ListViewSkin;
import com.gitalpha.Theme.Skin.MenuBarSkin;
import com.gitalpha.Theme.Skin.ScrollBarSkin;
import com.gitalpha.Theme.Skin.SplitPaneSkin;
import com.gitalpha.Theme.Skin.SubTabButtonSkin;
import com.gitalpha.Theme.Skin.TabPaneSkin;
import com.gitalpha.Theme.Skin.TextInputSkin;
import com.gitalpha.Theme.Skin.ThemeSkin;
import com.gitalpha.Theme.Themes.LightTheme;
import com.gitalpha.Type.EButtonVariant;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
	 * authoring to the per-element {@link ThemeSkin} subclasses so
	 * ThemeManager stays a thin coordinator.
	 *
	 * @param _Variant the button variant whose skin to bake
	 * @return the stylesheet URL for the baked skin
	 */
	public List<String> GetButtonStylesheets(EButtonVariant _Variant)
	{
		return List.of(new ButtonSkin(_Variant).Bake(ActivePalette));
	}

	/**
	 * Stylesheet for a themed tab button
	 * ({@link com.gitalpha.UI.Components.ATabButton}), baked from the active
	 * palette as an inline data-URI stylesheet: full flat tab skin — square
	 * corners, generous {@code em} padding, larger label — on a Background2
	 * header strip, with tab faces derived from the panel background (inactive
	 * derive -12%, hover derive -6%, selected derive +12%), muted vs text
	 * labels, a hairline above the content, focus ring and pressed shade. See
	 * {@link ThemeSkin} ({@link SubTabButtonSkin}).
	 *
	 * @return the stylesheet URL for the baked tab-button skin
	 */
	public List<String> GetSubTabButtonStylesheets()
	{
		return List.of(new SubTabButtonSkin().Bake(ActivePalette));
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
		return List.of(new ListViewSkin().Bake(ActivePalette));
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
		return List.of(new ScrollBarSkin().Bake(ActivePalette));
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
		return List.of(new CheckBoxSkin().Bake(ActivePalette));
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
		return List.of(new ComboBoxSkin().Bake(ActivePalette));
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
		return List.of(new TextInputSkin().Bake(ActivePalette));
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
		return List.of(new TextInputSkin().Bake(ActivePalette));
	}

	/**
	 * Stylesheet for a themed tab pane ({@link com.gitalpha.UI.Components.ATabPane}),
	 * baked from the active palette as an inline data-URI stylesheet: flat tab
	 * header with the active tab brighter and the inactive tabs darker than
	 * the palette background. See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked tab-pane skin
	 * @deprecated Only consumer is the deprecated {@code ATabPane}; use
	 *             {@link #GetSubTabButtonStylesheets()} (via
	 *             {@link com.gitalpha.UI.Components.ATabWidget}) for themed
	 *             tab strips.
	 */
	// The entire chain (this method + ATabPane + TabPaneSkin) is deprecated
	// together and removed together — the bake below is intentional.
	@Deprecated(forRemoval = true)
	@SuppressWarnings("deprecation")
	public List<String> GetTabPaneStylesheets()
	{
		return List.of(new TabPaneSkin().Bake(ActivePalette));
	}

	/**
	 * Stylesheet for a themed menu bar ({@link com.gitalpha.UI.Components.ATopMenuBar}),
	 * baked from the active palette as an inline data-URI stylesheet: flat
	 * secondary fill with hover (passive) and open (active) menu buttons.
	 * See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked menu-bar skin
	 */
	public List<String> GetMenuBarStylesheets()
	{
		return List.of(new MenuBarSkin().Bake(ActivePalette));
	}

	/**
	 * Stylesheet for a themed split pane ({@link com.gitalpha.UI.Components.ASplitPane}),
	 * baked from the active palette as an inline data-URI stylesheet:
	 * transparent background with a thin palette-border divider that widens to
	 * the passive highlight on hover (the visible draggable border between the
	 * panes). See {@link ThemeSkin}.
	 *
	 * @return the stylesheet URL for the baked split-pane skin
	 */
	public List<String> GetSplitPaneStylesheets()
	{
		return List.of(new SplitPaneSkin().Bake(ActivePalette));
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
		__Sheets.add(new DialogSkin().Bake(ActivePalette));
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
	 * {@link BaseSkin} as an inline data-URI URL and
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
		String __BaseUrl = new BaseSkin().Bake(ActivePalette);
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

}
