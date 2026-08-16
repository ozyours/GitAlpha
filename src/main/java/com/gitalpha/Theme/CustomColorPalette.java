package com.gitalpha.Theme;

import com.gitalpha.Theme.Themes.LightTheme;
import com.gitalpha.Type.ISerializable;
import com.gitalpha.Type.ThemeColor;
import org.json.JSONObject;

/**
 * User-customized palette: a {@link ColorPalette} whose colors the user has
 * overridden, persisted in the session file like the other state objects.
 * <p>
 * Serialization stores the full color set (not just the overrides) so the
 * restored palette is self-contained and does not depend on which base theme
 * it was derived from. Each slot is stored as its {@link ThemeColor} payload
 * (RGB floats + brightness + derived flag); the hard-coded slot names never
 * serialize. A stored palette that predates the custom-theme feature or is
 * missing a key keeps the field default, so a corrupted session file cannot
 * abort the load.
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
	 * from (see the class doc). Each value is the slot's {@link ThemeColor}
	 * payload; the hard-coded slot names are intentionally absent.
	 *
	 * @return the session JSON with one entry per color key
	 */
	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __JSON = new JSONObject();
		__JSON.put(PRIMARY_KEY, GetPrimaryColor().OnSerialize());
		__JSON.put(SECONDARY_KEY, GetSecondaryColor().OnSerialize());
		__JSON.put(TEXT_KEY, GetTextColor().OnSerialize());
		__JSON.put(MUTED_TEXT_KEY, GetMutedTextColor().OnSerialize());
		__JSON.put(ACTIVE_HIGHLIGHT_KEY, GetActiveHighlightColor().OnSerialize());
		__JSON.put(PASSIVE_HIGHLIGHT_KEY, GetPassiveHighlightColor().OnSerialize());
		__JSON.put(BORDER_KEY, GetBorderColor().OnSerialize());
		__JSON.put(BACKGROUND_KEY, GetBackgroundColor().OnSerialize());
		__JSON.put(ADDED_KEY, GetAddedColor().OnSerialize());
		__JSON.put(REMOVED_KEY, GetRemovedColor().OnSerialize());
		__JSON.put(MODIFIED_KEY, GetModifiedColor().OnSerialize());
		return __JSON;
	}

	/**
	 * Restore the palette from the session JSON. Session files written before
	 * the custom-theme feature existed (or a partial/corrupt payload) lack some
	 * keys — the field default then stands rather than failing the load.
	 * Sessions saved before the highlight split stored the hover color under
	 * the legacy {@code Highlight} key; when the new passive key is absent that
	 * value is migrated into the passive slot rather than dropped. Each value
	 * may be either a legacy {@code #rrggbb} hex string or the newer
	 * {@link ThemeColor} JSON payload — {@link #ReadColor} type-sniffs and
	 * falls back to the current value on malformed input, so a corrupted
	 * session file cannot abort the load or break later rendering.
	 *
	 * @param _JSON the session payload for this palette
	 */
	@Override
	public void OnDeserialize(JSONObject _JSON)
	{
		if (_JSON.has(PRIMARY_KEY))
			SetPrimaryColor(ReadColor(_JSON.get(PRIMARY_KEY), GetPrimaryColor()));
		if (_JSON.has(SECONDARY_KEY))
			SetSecondaryColor(ReadColor(_JSON.get(SECONDARY_KEY), GetSecondaryColor()));
		if (_JSON.has(TEXT_KEY))
			SetTextColor(ReadColor(_JSON.get(TEXT_KEY), GetTextColor()));
		if (_JSON.has(MUTED_TEXT_KEY))
			SetMutedTextColor(ReadColor(_JSON.get(MUTED_TEXT_KEY), GetMutedTextColor()));
		if (_JSON.has(ACTIVE_HIGHLIGHT_KEY))
			SetActiveHighlightColor(ReadColor(_JSON.get(ACTIVE_HIGHLIGHT_KEY), GetActiveHighlightColor()));
		// Passive highlight: prefer the new key; sessions saved before the
		// highlight split stored the hover color under the legacy "Highlight"
		// key, so migrate that value rather than dropping it.
		if (_JSON.has(PASSIVE_HIGHLIGHT_KEY))
			SetPassiveHighlightColor(ReadColor(_JSON.get(PASSIVE_HIGHLIGHT_KEY), GetPassiveHighlightColor()));
		else if (_JSON.has(HIGHLIGHT_KEY))
			SetPassiveHighlightColor(ReadColor(_JSON.get(HIGHLIGHT_KEY), GetPassiveHighlightColor()));
		if (_JSON.has(BORDER_KEY))
			SetBorderColor(ReadColor(_JSON.get(BORDER_KEY), GetBorderColor()));
		if (_JSON.has(BACKGROUND_KEY))
			SetBackgroundColor(ReadColor(_JSON.get(BACKGROUND_KEY), GetBackgroundColor()));
		if (_JSON.has(ADDED_KEY))
			SetAddedColor(ReadColor(_JSON.get(ADDED_KEY), GetAddedColor()));
		if (_JSON.has(REMOVED_KEY))
			SetRemovedColor(ReadColor(_JSON.get(REMOVED_KEY), GetRemovedColor()));
		if (_JSON.has(MODIFIED_KEY))
			SetModifiedColor(ReadColor(_JSON.get(MODIFIED_KEY), GetModifiedColor()));
	}

	/**
	 * Read one color slot from the session payload, accepting both the legacy
	 * {@code #rrggbb} hex-string form and the newer {@link ThemeColor} JSON
	 * payload. The fallback's hard-coded name is preserved in both cases (the
	 * legacy form is parsed into a direct color with that name at full
	 * brightness — legacy files predate the brightness multiplier, so any
	 * fallback brightness is intentionally dropped; the JSON form is applied
	 * onto a copy of the fallback). Malformed input keeps the fallback, so a
	 * corrupted session file cannot abort the load.
	 *
	 * @param _Value    the raw payload from the session file
	 * @param _Fallback the current slot value to keep when _Value is unusable
	 * @return the restored color
	 */
	private static ThemeColor ReadColor(Object _Value, ThemeColor _Fallback)
	{
		if (_Value instanceof String __Hex)
		{
			try
			{
				return ThemeColor.FromHex(_Fallback.GetName(), __Hex);
			}
			catch (IllegalArgumentException __BadHex)
			{
				return _Fallback;
			}
		}
		if (_Value instanceof JSONObject __JSON)
		{
			ThemeColor __Color = _Fallback.Copy();
			__Color.OnDeserialize(__JSON);
			return __Color;
		}
		return _Fallback;
	}
}