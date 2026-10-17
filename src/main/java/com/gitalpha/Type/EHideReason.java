package com.gitalpha.Type;

/**
 * Why a popup hid: the reason handed to the hide lifecycle callback of
 * {@link com.gitalpha.UI.Components.APopup}. It distinguishes a hide the app
 * asked for ({@link #PROGRAMMATIC}) from one the platform performed on the
 * user's behalf ({@link #DISMISSED} — an outside click or the Escape key), so
 * a host can react differently per route (e.g. only clear a selection when the
 * user dismissed the popup, never when the app closed it).
 */
public enum EHideReason
{
	/** The host called {@code Hide()} explicitly. */
	PROGRAMMATIC,
	/** The platform dismissed the popup: an outside click or the Escape key. */
	DISMISSED;
}
