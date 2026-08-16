package com.gitalpha.UI;

import com.gitalpha.UI.Components.AButton;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
		// The bar carries the top chrome's vertical spacing itself: a top
		// margin separates it from the menu bar above, and a bottom margin
		// spaces the chrome from the tab pane below (the chrome VBox has no
		// padding of its own).
		VBox.setMargin(this, new Insets(8));
		getChildren().addAll(PlaceholderButton("Fetch"), PlaceholderButton("Pull"), PlaceholderButton("Push"), PlaceholderButton("Stash"), PlaceholderButton("Tags"));
	}

	/**
	 * Build a quick-action button that does nothing yet: the action only
	 * reports that the feature is a planned placeholder. Uses the fully
	 * customized {@link AButton} template as a live skin experiment.
	 */
	private static Button PlaceholderButton(String _Text)
	{
		AButton __Button = new AButton(_Text);
		__Button.setOnAction(__Event -> PlaceholderNotice.ShowNotImplemented(_Text));
		return __Button;
	}
}
