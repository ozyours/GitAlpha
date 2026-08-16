package com.gitalpha.Theme;

import com.gitalpha.Type.ISerializable;
import org.json.JSONObject;

/**
 * User-customized palette: a {@link ColorPalette} whose colors the user has
 * overridden, persisted in the session file like the other state objects.
 * <p>
 * Serialization stores the full color set (not just the overrides) so the
 * restored palette is self-contained and does not depend on which base theme
 * it was derived from. A stored palette that predates the custom-theme feature
 * or is missing a key keeps the field default, so a corrupted session file
 * cannot abort the load.
 */
public class CustomColorPalette extends ColorPalette implements ISerializable
{
	private static final String PRIMARY_KEY = "Primary";
	private static final String SECONDARY_KEY = "Secondary";
	private static final String TEXT_KEY = "Text";
	private static final String MUTED_TEXT_KEY = "MutedText";
	private static final String ACTIVE_HIGHLIGHT_KEY = "ActiveHighlight";
	private static final String PASSIVE_HIGHLIGHT_KEY = "PassiveHighlight";
	/** Legacy pre-split key: sessions saved before the highlight split stored the hover color here */
	private static final String HIGHLIGHT_KEY = "Highlight";
	private static final String BORDER_KEY = "Border";
	private static final String BACKGROUND_KEY = "Background";
	private static final String ADDED_KEY = "Added";
	private static final String REMOVED_KEY = "Removed";
	private static final String MODIFIED_KEY = "Modified";

	/**
	 * Create a custom palette seeded from the given base theme; the user then
	 * overrides individual colors on top of it.
	 *
	 * @param _BaseTheme the theme to copy colors from (falls back to Light when null)
	 */
	public CustomColorPalette(ColorPalette _BaseTheme)
	{
		CopyFrom(_BaseTheme != null ? _BaseTheme : new LightTheme());
	}

	/**
	 * Create a custom palette seeded from the Light theme (default when no base
	 * is known, e.g. a fresh session without stored customization).
	 */
	public CustomColorPalette()
	{
		CopyFrom(new LightTheme());
	}

	/**
	 * Write the full color set (not just the user's overrides) so a restored
	 * palette is self-contained and independent of the base theme it was seeded
	 * from (see the class doc).
	 *
	 * @return the session JSON with one entry per color key
	 */
	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __JSON = new JSONObject();
		__JSON.put(PRIMARY_KEY, GetPrimaryColor());
		__JSON.put(SECONDARY_KEY, GetSecondaryColor());
		__JSON.put(TEXT_KEY, GetTextColor());
		__JSON.put(MUTED_TEXT_KEY, GetMutedTextColor());
		__JSON.put(ACTIVE_HIGHLIGHT_KEY, GetActiveHighlightColor());
		__JSON.put(PASSIVE_HIGHLIGHT_KEY, GetPassiveHighlightColor());
		__JSON.put(BORDER_KEY, GetBorderColor());
		__JSON.put(BACKGROUND_KEY, GetBackgroundColor());
		__JSON.put(ADDED_KEY, GetAddedColor());
		__JSON.put(REMOVED_KEY, GetRemovedColor());
		__JSON.put(MODIFIED_KEY, GetModifiedColor());
		return __JSON;
	}

	/**
	 * Restore the palette from the session JSON. Session files written before
	 * the custom-theme feature existed (or a partial/corrupt payload) lack some
	 * keys — the field default then stands rather than failing the load.
	 * Sessions saved before the highlight split stored the hover color under
	 * the legacy {@code Highlight} key; when the new passive key is absent that
	 * value is migrated into the passive slot rather than dropped. A
	 * present-but-malformed hex value is rejected by {@link #SafeHex} and also
	 * keeps the current value, so a corrupted session file cannot abort the
	 * load or break later rendering.
	 *
	 * @param _JSON the session payload for this palette
	 */
	@Override
	public void OnDeserialize(JSONObject _JSON)
	{
		if (_JSON.has(PRIMARY_KEY))
			SetPrimaryColor(SafeHex(_JSON.getString(PRIMARY_KEY), GetPrimaryColor()));
		if (_JSON.has(SECONDARY_KEY))
			SetSecondaryColor(SafeHex(_JSON.getString(SECONDARY_KEY), GetSecondaryColor()));
		if (_JSON.has(TEXT_KEY))
			SetTextColor(SafeHex(_JSON.getString(TEXT_KEY), GetTextColor()));
		if (_JSON.has(MUTED_TEXT_KEY))
			SetMutedTextColor(SafeHex(_JSON.getString(MUTED_TEXT_KEY), GetMutedTextColor()));
		if (_JSON.has(ACTIVE_HIGHLIGHT_KEY))
			SetActiveHighlightColor(SafeHex(_JSON.getString(ACTIVE_HIGHLIGHT_KEY), GetActiveHighlightColor()));
		// Passive highlight: prefer the new key; sessions saved before the
		// highlight split stored the hover color under the legacy "Highlight"
		// key, so migrate that value rather than dropping it.
		if (_JSON.has(PASSIVE_HIGHLIGHT_KEY))
			SetPassiveHighlightColor(SafeHex(_JSON.getString(PASSIVE_HIGHLIGHT_KEY), GetPassiveHighlightColor()));
		else if (_JSON.has(HIGHLIGHT_KEY))
			SetPassiveHighlightColor(SafeHex(_JSON.getString(HIGHLIGHT_KEY), GetPassiveHighlightColor()));
		if (_JSON.has(BORDER_KEY))
			SetBorderColor(SafeHex(_JSON.getString(BORDER_KEY), GetBorderColor()));
		if (_JSON.has(BACKGROUND_KEY))
			SetBackgroundColor(SafeHex(_JSON.getString(BACKGROUND_KEY), GetBackgroundColor()));
		if (_JSON.has(ADDED_KEY))
			SetAddedColor(SafeHex(_JSON.getString(ADDED_KEY), GetAddedColor()));
		if (_JSON.has(REMOVED_KEY))
			SetRemovedColor(SafeHex(_JSON.getString(REMOVED_KEY), GetRemovedColor()));
		if (_JSON.has(MODIFIED_KEY))
			SetModifiedColor(SafeHex(_JSON.getString(MODIFIED_KEY), GetModifiedColor()));
	}

	/**
	 * Validate a hex color read from the session file, falling back to the
	 * current field value when the payload is malformed.
	 *
	 * @param _Value    the raw hex string from the session file
	 * @param _Fallback the value to keep when _Value is not a valid {@code #rrggbb}
	 * @return _Value if valid, otherwise _Fallback
	 */
	private static String SafeHex(String _Value, String _Fallback)
	{
		try
		{
			ColorPalette.ParseHex(_Value);
			return _Value;
		}
		catch (IllegalArgumentException __BadHex)
		{
			return _Fallback;
		}
	}
}
