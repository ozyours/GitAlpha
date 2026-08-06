package com.gitalpha.UI;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/**
 * Quick command bar: a row of placeholder quick-action buttons under the top
 * menu bar. The command set is planned to be settings-assigned (see
 * ROADMAP.md) — for now every button only reports that it is a placeholder.
 */
public class QuickCommandBar extends HBox
{
	private static final double SPACING = 8;

	public QuickCommandBar()
	{
		super();
		setSpacing(SPACING);
		getChildren().addAll(
				PlaceholderButton("Fetch"),
				PlaceholderButton("Pull"),
				PlaceholderButton("Push"),
				PlaceholderButton("Stash"),
				PlaceholderButton("Tags"));
	}

	/**
	 * Build a quick-action button that does nothing yet: the action only
	 * reports that the feature is a planned placeholder.
	 */
	private static Button PlaceholderButton(String _Text)
	{
		Button __Button = new Button(_Text);
		__Button.setOnAction(__Event -> PlaceholderNotice.ShowNotImplemented(_Text));
		return __Button;
	}
}
