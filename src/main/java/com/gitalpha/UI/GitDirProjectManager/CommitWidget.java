package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.ERefreshPolicy;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.ETextVariant;
import com.gitalpha.Theme.ThemeManager;
import com.gitalpha.UI.Components.AButton;
import com.gitalpha.UI.Components.AText;
import com.gitalpha.UI.Components.ATextArea;
import com.gitalpha.UI.Components.ATextField;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Commit form for one repository: a summary field, an optional description area,
 * and a Commit button that runs `git commit` through the GitOperator queue.
 */
public class CommitWidget extends BaseWidget
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	/** Single-line commit subject; Enter in this field also submits the commit.
	 *  Uses the themed {@link ATextField} control so it inherits the app-wide
	 *  input styling instead of the stock JavaFX look. */
	private final ATextField txb_CommitSummary;
	/** Optional multi-line commit body, passed as a second -m so git stores it as the commit body.
	 *  Uses the themed {@link ATextArea} control so it inherits the app-wide
	 *  input styling instead of the stock JavaFX look. */
	private final ATextArea txa_CommitDescription;
	/** Submits the commit; disabled while a commit operation is in flight.
	 *  Uses the themed {@link AButton} control so it inherits the app-wide
	 *  button styling instead of the stock JavaFX look. */
	private final AButton btn_Commit;

	public CommitWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// Single-line commit subject
		txb_CommitSummary = new ATextField();
		txb_CommitSummary.setPromptText("Commit summary");
		txb_CommitSummary.setOnAction(event -> CommitChanges());

		// Multi-line commit body
		txa_CommitDescription = new ATextArea();
		txa_CommitDescription.setPromptText("Commit description");
		txa_CommitDescription.setPrefRowCount(4);

		btn_Commit = new AButton("Commit");
		btn_Commit.setMaxWidth(Double.MAX_VALUE);
		btn_Commit.setOnAction(event -> CommitChanges());

		AText summaryLabel = new AText("Summary", ETextVariant.BOLD);
		AText descriptionLabel = new AText("Description", ETextVariant.BOLD);

		VBox layout = new VBox(summaryLabel, txb_CommitSummary, descriptionLabel, txa_CommitDescription, btn_Commit);
		layout.setSpacing(SPACING);
		layout.setPadding(new Insets(PADDING));
		// The description is the flexible element inside the form: it absorbs the
		// extra height the grid row grants (GitDirWidget sets the
		// commit row's pref height) while the summary field and commit button keep
		// their natural size.
		VBox.setVgrow(txa_CommitDescription, Priority.ALWAYS);

		getChildren().add(layout);
	}

	/**
	 * Queue a {@code git commit} through the GitOperator with REFRESH_AND_UPDATE_UI.
	 * Requires a non-blank summary; disables the form while the commit is in flight
	 * so neither the Commit button nor Enter can double-submit, and clears the form
	 * on success.
	 */
	private void CommitChanges()
	{
		String __Summary = txb_CommitSummary.getText().trim();
		if (__Summary.isEmpty())
		{
			Alert __Alert = new Alert(Alert.AlertType.WARNING);
			ThemeManager.Instance.ApplyThemeToDialog(__Alert);
			__Alert.setTitle("Commit");
			__Alert.setHeaderText("Commit summary is required");
			__Alert.setContentText("Enter a commit summary before committing.");
			__Alert.showAndWait();
			return;
		}

		String __Description = txa_CommitDescription.getText().trim();
		List<String> __Cmd = new ArrayList<>();
		__Cmd.add("commit");
		__Cmd.add("-m");
		__Cmd.add(__Summary);
		// A second -m flag makes git store the description as the commit body
		// (subject + body) rather than joining both texts onto one subject line.
		if (!__Description.isEmpty())
		{
			__Cmd.add("-m");
			__Cmd.add(__Description);
		}

		// Disable the whole form while the operation is in flight so neither the
		// Commit button nor the Enter key (TextField.setOnAction) can queue a second
		// commit with the same message.
		btn_Commit.setDisable(true);
		txb_CommitSummary.setDisable(true);
		txa_CommitDescription.setDisable(true);
		GetGitDirTarget().GetOperator().RunGitOp(__Cmd, ERefreshPolicy.REFRESH_AND_UPDATE_UI, (__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				btn_Commit.setDisable(false);
				txb_CommitSummary.setDisable(false);
				txa_CommitDescription.setDisable(false);
				if (!__Ok)
				{
					Alert __Alert = new Alert(Alert.AlertType.ERROR);
					ThemeManager.Instance.ApplyThemeToDialog(__Alert);
					__Alert.setTitle("Git Operation Failed");
					__Alert.setHeaderText("Failed to commit changes");
					__Alert.setContentText(__Err);
					__Alert.showAndWait();
				}
				else
				{
					// Clear the form so the next commit starts fresh; the
					// REFRESH_AND_UPDATE_UI refresh already updated the UI.
					txb_CommitSummary.clear();
					txa_CommitDescription.clear();
				}
			});
		});
	}
}
