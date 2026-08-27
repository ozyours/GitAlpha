package com.gitalpha.Type;

/**
 * Size variants for the tab-button skin ({@code TabButtonSkin}). Both
 * variants share the same colors and rule structure — only the face metrics
 * (padding, label font size, close-face gap) differ, so the two tab strips
 * can never drift apart in hue:
 * <ul>
 * <li>{@code NORMAL} — the full-size strip; used by the main project tabs
 * (modifiable {@code ATabWidget} in {@code AlphaUI}).</li>
 * <li>{@code SMALL} — a compact strip with reduced padding and label; used
 * for secondary navigation (the "Changes" / "History" sub-tabs in
 * {@code GitDirWidget}).</li>
 * </ul>
 */
public enum ETabButtonVariant
{
	/** Full-size faces: the default/main-tab metrics (unchanged legacy sizing) */
	NORMAL,
	/** Compact faces: smaller padding and label for secondary sub-tab strips */
	SMALL;
}
