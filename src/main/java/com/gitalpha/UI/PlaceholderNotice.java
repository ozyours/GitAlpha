package com.gitalpha.UI;

import javafx.scene.control.Alert;

/**
 * Shared "not implemented yet" notice for placeholder UI entries (top menu
 * bar, quick command bar). Keeps the placeholder copy and alert construction
 * in one place so the two bars cannot drift.
 */
public final class PlaceholderNotice
{
	// Utility class — never instantiated; callers use the static ShowNotImplemented entry point.
	private PlaceholderNotice()
	{
	}

	/**
	 * Show an informational alert stating that the given feature is still a
	 * planned placeholder (see ROADMAP.md).
	 */
	public static void ShowNotImplemented(String _Feature)
	{
		Alert __Alert = new Alert(Alert.AlertType.INFORMATION);
		__Alert.setTitle("Not implemented yet");
		__Alert.setHeaderText(null);
		__Alert.setContentText("'" + _Feature + "' is a placeholder entry (see ROADMAP.md for planned work).");
		__Alert.showAndWait();
	}
}
