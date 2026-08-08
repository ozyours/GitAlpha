package com.gitalpha.Type;

import org.json.JSONObject;

/**
 * Persisted window state for a Stash window, stored once in the session file
 * and shared by every repository: window position/size, maximized flag, the
 * three-column SplitPane divider positions, and the Auto Restore preference.
 * Bounds are only mutated while
 * the window is windowed, so the fields always hold the last *windowed*
 * geometry — a maximized window's on-screen bounds are never persisted
 * ({@code -1} position means "use the platform default"). The windowed size is
 * what a restored window un-maximizes back to.
 */
public class StashWindowState implements ISerializable
{
	private static final String X_KEY = "X";
	private static final String Y_KEY = "Y";
	private static final String WIDTH_KEY = "Width";
	private static final String HEIGHT_KEY = "Height";
	private static final String MAXIMIZED_KEY = "Maximized";
	private static final String COLUMN1_KEY = "Column1";
	private static final String COLUMN2_KEY = "Column2";
	private static final String AUTO_RESTORE_KEY = "AutoRestore";

	/** Last windowed X; -1 means "use platform default position" */
	private int X = -1;
	/** Last windowed Y; -1 means "use platform default position" */
	private int Y = -1;
	/** Last windowed width (matches the widget's initial scene size) */
	private int Width = 1000;
	/** Last windowed height (matches the widget's initial scene size) */
	private int Height = 600;
	/** Whether the window was last shown maximized */
	private boolean Maximized = false;
	/** First SplitPane divider position (left pane / total width); matches the widget's initial position */
	private double Column1 = 0.25;
	/** Second SplitPane divider position (centre pane boundary); matches the widget's initial position */
	private double Column2 = 0.60;
	/** Whether the Save operation auto-restores the stashed changes afterwards; default off */
	private boolean AutoRestore = false;

	public int GetX() { return X; }
	public int GetY() { return Y; }
	public int GetWidth() { return Width; }
	public int GetHeight() { return Height; }
	public boolean GetMaximized() { return Maximized; }
	public double GetColumn1() { return Column1; }
	public double GetColumn2() { return Column2; }

	public void SetWindowBounds(int _X, int _Y, int _Width, int _Height)
	{
		X = _X;
		Y = _Y;
		Width = _Width;
		Height = _Height;
	}

	public void SetMaximized(boolean _Maximized) { Maximized = _Maximized; }
	public void SetColumns(double _Column1, double _Column2)
	{
		Column1 = _Column1;
		Column2 = _Column2;
	}

	public boolean GetAutoRestore() { return AutoRestore; }
	public void SetAutoRestore(boolean _AutoRestore) { AutoRestore = _AutoRestore; }

	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __JSON = new JSONObject();
		// Position/size always carry the last *windowed* geometry: the widget
		// only mutates the bounds while the window is windowed, so a maximized
		// window never overwrites them. Writing them even when Maximized is set
		// lets a restored window keep its windowed size when un-maximized.
		// AutoRestore is a plain preference (no geometry rules) and is written as-is.
		__JSON.put(X_KEY, X);
		__JSON.put(Y_KEY, Y);
		__JSON.put(WIDTH_KEY, Width);
		__JSON.put(HEIGHT_KEY, Height);
		__JSON.put(MAXIMIZED_KEY, Maximized);
		__JSON.put(COLUMN1_KEY, Column1);
		__JSON.put(COLUMN2_KEY, Column2);
		__JSON.put(AUTO_RESTORE_KEY, AutoRestore);
		return __JSON;
	}

	@Override
	public void OnDeserialize(JSONObject JSON)
	{
		if (JSON.has(X_KEY)) X = JSON.getInt(X_KEY);
		if (JSON.has(Y_KEY)) Y = JSON.getInt(Y_KEY);
		if (JSON.has(WIDTH_KEY)) Width = JSON.getInt(WIDTH_KEY);
		if (JSON.has(HEIGHT_KEY)) Height = JSON.getInt(HEIGHT_KEY);
		if (JSON.has(MAXIMIZED_KEY)) Maximized = JSON.getBoolean(MAXIMIZED_KEY);
		if (JSON.has(COLUMN1_KEY)) Column1 = JSON.getDouble(COLUMN1_KEY);
		if (JSON.has(COLUMN2_KEY)) Column2 = JSON.getDouble(COLUMN2_KEY);
		// Session files written before the preference existed lack the key; the
		// field default (false) then stands.
		if (JSON.has(AUTO_RESTORE_KEY)) AutoRestore = JSON.getBoolean(AUTO_RESTORE_KEY);
	}
}
