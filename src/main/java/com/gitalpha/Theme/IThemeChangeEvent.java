package com.gitalpha.Theme;

/**
 * Theme-change notification: implement to be told when the active palette
 * changes (preset switch or user override) so Java-driven colors can be
 * re-applied. CSS-driven controls need no listener — the cascade handles them
 * when the theme stylesheet is re-applied.
 * <p>
 * Registered through {@link ThemeManager#AddIThemeChangeEvent}; fired on the
 * FX thread. Mirrors the {@code IRefreshGitDirEvent} pattern (WeakReference
 * list, pruned on dead references), so implementers never need to unsubscribe.
 */
public interface IThemeChangeEvent
{
	/**
	 * Called on the FX thread whenever the active palette is replaced.
	 * Re-read the colors this widget draws and re-apply them; keep it to
	 * color-only updates to avoid flicker during rapid palette tweaks.
	 *
	 * @param _Palette the newly active palette
	 */
	void Event(ColorPalette _Palette);
}
