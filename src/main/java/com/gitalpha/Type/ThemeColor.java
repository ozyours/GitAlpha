package com.gitalpha.Type;

import com.gitalpha.Theme.ColorPalette;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A themeable color value that is either a direct sRGB color or a derivation
 * of another named color. This replaces the palette's raw hex strings with a
 * typed model: colors are stored as sRGB floats (0-1), the hex string is
 * produced on demand by {@link #GetHex(Map)}, and every color carries an
 * optional brightness multiplier.
 * <p>
 * Two forms exist, chosen by {@link #IsDerived}:
 * <ul>
 * <li><b>Direct</b> ({@code IsDerived == false}) — an absolute color from its
 * own {@code R}/{@code G}/{@code B} floats.</li>
 * <li><b>Derived</b> ({@code IsDerived == true}) — computed from another
 * named color at resolve time: {@code source RGB x Brightness}.</li>
 * </ul>
 * The brightness multiplier applies uniformly to both forms (1.0 = as-is,
 * 0.5 = half, 0 = black), so a direct color also gets a darkness knob and a
 * chain of derived colors composes their multipliers.
 * <p>
 * {@link #Name} identifies this slot and {@link #SourceName} names the slot
 * this one derives from. Both are hard-coded at construction time and are
 * <b>never serialized</b> — the derive topology is frozen in code, so the
 * session file only stores the user-tunable values (the RGB floats, the
 * brightness multiplier and the {@link #IsDerived} flag). A derived slot keeps
 * its RGB floats in the payload so flipping {@code IsDerived} off yields a
 * usable direct color without a format change.
 */
public final class ThemeColor implements ISerializable
{
	// --- Serialized (user-tunable) fields ---
	private float R, G, B;
	private float Brightness;
	private boolean IsDerived;
	// --- Hard-coded fields (never serialized) ---
	private final String Name;
	private final String SourceName;

	private static final String R_KEY = "R";
	private static final String G_KEY = "G";
	private static final String B_KEY = "B";
	private static final String BRIGHTNESS_KEY = "Brightness";
	private static final String IS_DERIVED_KEY = "IsDerived";

	/**
	 * Create a direct color with full brightness (1.0).
	 *
	 * @param _Name the hard-coded slot name (the key other colors derive from)
	 * @param _R    the red channel, sRGB 0-1 (clamped)
	 * @param _G    the green channel, sRGB 0-1 (clamped)
	 * @param _B    the blue channel, sRGB 0-1 (clamped)
	 */
	public ThemeColor(String _Name, float _R, float _G, float _B)
	{
		Name = _Name;
		SourceName = "";
		IsDerived = false;
		Brightness = 1.0f;
		SetRGB(_R, _G, _B);
	}

	/**
	 * Create a direct color with an explicit brightness multiplier.
	 *
	 * @param _Name       the hard-coded slot name
	 * @param _R          the red channel, sRGB 0-1 (clamped)
	 * @param _G          the green channel, sRGB 0-1 (clamped)
	 * @param _B          the blue channel, sRGB 0-1 (clamped)
	 * @param _Brightness the brightness multiplier, 0-1 (clamped)
	 */
	public ThemeColor(String _Name, float _R, float _G, float _B, float _Brightness)
	{
		this(_Name, _R, _G, _B);
		SetBrightness(_Brightness);
	}

	/**
	 * Create a derived color: resolves to {@code source RGB x Brightness}.
	 * The RGB floats stay zero until the slot is flipped to a direct color.
	 * Rejects a null or blank source name — the target's existence is checked
	 * at resolve time.
	 *
	 * @param _Name       the hard-coded slot name
	 * @param _SourceName the hard-coded name of the slot to derive from
	 * @param _Brightness the brightness multiplier, 0-1 (clamped)
	 */
	public ThemeColor(String _Name, String _SourceName, float _Brightness)
	{
		if (_SourceName == null || _SourceName.isBlank())
			throw new IllegalArgumentException("Derived color '" + _Name + "' needs a source name");
		Name = _Name;
		SourceName = _SourceName;
		IsDerived = true;
		SetBrightness(_Brightness);
		R = G = B = 0.0f;
	}

	/**
	 * Create a direct color from a strict {@code #rrggbb} hex string.
	 * Malformed input is rejected (see {@link #ParseHexToFloats}).
	 *
	 * @param _Name the hard-coded slot name
	 * @param _Hex  the color as {@code #rrggbb}
	 * @return the direct color (full brightness)
	 */
	public static ThemeColor FromHex(String _Name, String _Hex)
	{
		float[] __RGB = ParseHexToFloats(_Hex);
		return new ThemeColor(_Name, __RGB[0], __RGB[1], __RGB[2]);
	}

	/**
	 * @return the hard-coded slot name (the key other colors derive from)
	 */
	public String GetName()
	{
		return Name;
	}

	/**
	 * @return the hard-coded source slot name, empty when this is a direct color
	 */
	public String GetSourceName()
	{
		return SourceName;
	}

	/**
	 * @return the red channel, sRGB 0-1
	 */
	public float GetR()
	{
		return R;
	}

	/**
	 * @return the green channel, sRGB 0-1
	 */
	public float GetG()
	{
		return G;
	}

	/**
	 * @return the blue channel, sRGB 0-1
	 */
	public float GetB()
	{
		return B;
	}

	/**
	 * @return the brightness multiplier, 0-1 (1.0 = as-is)
	 */
	public float GetBrightness()
	{
		return Brightness;
	}

	/**
	 * @return true when this color resolves from {@link #GetSourceName()}
	 */
	public boolean GetIsDerived()
	{
		return IsDerived;
	}

	/**
	 * Set the red channel, clamped to 0-1.
	 *
	 * @param _R the sRGB value to store
	 */
	public void SetR(float _R)
	{
		R = ClampUnit(_R);
	}

	/**
	 * Set the green channel, clamped to 0-1.
	 *
	 * @param _G the sRGB value to store
	 */
	public void SetG(float _G)
	{
		G = ClampUnit(_G);
	}

	/**
	 * Set the blue channel, clamped to 0-1.
	 *
	 * @param _B the sRGB value to store
	 */
	public void SetB(float _B)
	{
		B = ClampUnit(_B);
	}

	/**
	 * Set all three channels at once, each clamped to 0-1.
	 *
	 * @param _R the red sRGB value
	 * @param _G the green sRGB value
	 * @param _B the blue sRGB value
	 */
	public void SetRGB(float _R, float _G, float _B)
	{
		SetR(_R);
		SetG(_G);
		SetB(_B);
	}

	/**
	 * Set the brightness multiplier, clamped to 0-1.
	 *
	 * @param _Brightness the multiplier (1.0 = as-is, 0.5 = half, 0 = black)
	 */
	public void SetBrightness(float _Brightness)
	{
		Brightness = ClampUnit(_Brightness);
	}

	/**
	 * Toggle between the derived form and the direct form. The RGB floats are
	 * always kept in the payload, so flipping a derived slot off yields the
	 * stored direct color immediately.
	 *
	 * @param _IsDerived true to resolve from the source slot, false to use the own RGB
	 */
	public void SetIsDerived(boolean _IsDerived)
	{
		IsDerived = _IsDerived;
	}

	/**
	 * Create an independent copy with the same hard-coded names and the same
	 * tunable values. Used by {@link ColorPalette#CopyFrom} so two palettes
	 * never share color instances (mutating one would otherwise leak into the
	 * other).
	 *
	 * @return a new ThemeColor with identical state
	 */
	public ThemeColor Copy()
	{
		ThemeColor __Copy = IsDerived
				? new ThemeColor(Name, SourceName, Brightness)
				: new ThemeColor(Name, R, G, B, Brightness);
		// The derived constructor zeroes the RGB payload; restore it so the
		// copy keeps the dormant direct-form color (flipping IsDerived off on
		// the copy stays usable without a format change).
		__Copy.SetRGB(R, G, B);
		__Copy.SetIsDerived(IsDerived);
		return __Copy;
	}

	/**
	 * Set the direct RGB values from a strict {@code #rrggbb} hex string.
	 * Does not change the {@link #IsDerived} flag; the hex is the direct-form
	 * payload either way. Malformed input is rejected by
	 * {@link #ParseHexToFloats}.
	 *
	 * @param _Hex the color as {@code #rrggbb}
	 */
	public void SetHex(String _Hex)
	{
		float[] __RGB = ParseHexToFloats(_Hex);
		R = __RGB[0];
		G = __RGB[1];
		B = __RGB[2];
	}

	/**
	 * Resolve this color to its effective RGB: for a direct color the own
	 * channels multiplied by the brightness; for a derived color the source's
	 * resolved RGB multiplied by the brightness. Chains of derived colors
	 * compose (the source's own brightness is already inside its resolved RGB),
	 * and a visited set guards against derivation cycles.
	 *
	 * @param _Lookup the map of slot name to color used to follow {@link #SourceName}
	 * @return the resolved RGB (0-1 per channel)
	 */
	public RGB Resolve(Map<String, ThemeColor> _Lookup)
	{
		return Resolve(_Lookup, new HashSet<>());
	}

	/**
	 * Recursive core of {@link #Resolve(Map)}: follows the derivation chain
	 * through the lookup map. {@code _Visited} holds the names already on the
	 * current path so a derivation cycle throws instead of recursing forever.
	 *
	 * @param _Lookup  the map of slot name to color used to follow {@link #SourceName}
	 * @param _Visited the names on the current derivation path (cycle guard)
	 * @return the resolved RGB (0-1 per channel)
	 */
	private RGB Resolve(Map<String, ThemeColor> _Lookup, Set<String> _Visited)
	{
		if (!_Visited.add(Name))
			throw new IllegalArgumentException("Derivation cycle at color: " + Name);
		float __Red, __Green, __Blue;
		if (IsDerived)
		{
			ThemeColor __Source = _Lookup.get(SourceName);
			if (__Source == null)
				throw new IllegalArgumentException(
						"Unknown derive source '" + SourceName + "' for color '" + Name + "'");
			RGB __SourceRGB = __Source.Resolve(_Lookup, _Visited);
			__Red = __SourceRGB.Red() * Brightness;
			__Green = __SourceRGB.Green() * Brightness;
			__Blue = __SourceRGB.Blue() * Brightness;
		}
		else
		{
			__Red = R * Brightness;
			__Green = G * Brightness;
			__Blue = B * Brightness;
		}
		return new RGB(__Red, __Green, __Blue);
	}

	/**
	 * Resolve this color and render it as a strict {@code #rrggbb} hex string,
	 * rounding each resolved channel to the nearest 8-bit byte.
	 *
	 * @param _Lookup the map of slot name to color used to follow {@link #SourceName}
	 * @return the resolved color as {@code #rrggbb}
	 */
	public String GetHex(Map<String, ThemeColor> _Lookup)
	{
		RGB __Resolved = Resolve(_Lookup);
		int __R = ClampByte(Math.round(__Resolved.Red() * 255.0f));
		int __G = ClampByte(Math.round(__Resolved.Green() * 255.0f));
		int __B = ClampByte(Math.round(__Resolved.Blue() * 255.0f));
		return String.format("#%02x%02x%02x", __R, __G, __B);
	}

	/**
	 * Write the user-tunable payload to the session JSON: the RGB floats, the
	 * brightness multiplier and the derived flag. The slot names are hard-coded
	 * and intentionally absent — the derive topology is frozen in code.
	 *
	 * @return the session JSON with one entry per tunable field
	 */
	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __JSON = new JSONObject();
		__JSON.put(R_KEY, R);
		__JSON.put(G_KEY, G);
		__JSON.put(B_KEY, B);
		__JSON.put(BRIGHTNESS_KEY, Brightness);
		__JSON.put(IS_DERIVED_KEY, IsDerived);
		return __JSON;
	}

	/**
	 * Restore the tunable fields from the session JSON. Missing keys keep the
	 * current value and out-of-range values are clamped by the setters, so a
	 * partial or corrupt payload cannot abort the load.
	 *
	 * @param _JSON the session payload for this color
	 */
	@Override
	public void OnDeserialize(JSONObject _JSON)
	{
		if (_JSON.has(R_KEY))
			SetR(_JSON.optFloat(R_KEY, R));
		if (_JSON.has(G_KEY))
			SetG(_JSON.optFloat(G_KEY, G));
		if (_JSON.has(B_KEY))
			SetB(_JSON.optFloat(B_KEY, B));
		if (_JSON.has(BRIGHTNESS_KEY))
			SetBrightness(_JSON.optFloat(BRIGHTNESS_KEY, Brightness));
		if (_JSON.has(IS_DERIVED_KEY))
			SetIsDerived(_JSON.optBoolean(IS_DERIVED_KEY, IsDerived));
	}

	/**
	 * The resolved RGB channels as plain floats (0-1 each).
	 *
	 * @param Red   the resolved red channel
	 * @param Green the resolved green channel
	 * @param Blue  the resolved blue channel
	 */
	public static record RGB(float Red, float Green, float Blue) {}

	private static float ClampUnit(float _Value)
	{
		return Math.max(0.0f, Math.min(1.0f, _Value));
	}

	private static int ClampByte(int _Value)
	{
		return Math.max(0, Math.min(255, _Value));
	}

	/**
	 * Parse a strict {@code #rrggbb} hex color into sRGB floats. Strict about
	 * the format so a malformed color fails fast rather than silently producing
	 * broken CSS.
	 *
	 * @param _Hex color as {@code #rrggbb}
	 * @return the three channels as 0-1 floats
	 */
	private static float[] ParseHexToFloats(String _Hex)
	{
		if (_Hex == null || _Hex.length() != 7 || _Hex.charAt(0) != '#')
			throw new IllegalArgumentException("Color must be #rrggbb, got: " + _Hex);
		int __Packed;
		try
		{
			__Packed = Integer.parseInt(_Hex.substring(1), 16);
		}
		catch (NumberFormatException __BadHex)
		{
			throw new IllegalArgumentException("Color must be #rrggbb, got: " + _Hex);
		}
		return new float[] {
				((__Packed >> 16) & 0xFF) / 255.0f,
				((__Packed >> 8) & 0xFF) / 255.0f,
				(__Packed & 0xFF) / 255.0f };
	}
}