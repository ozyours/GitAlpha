package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.FileChanges;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TextViewerWidget extends BaseWidget
{
	private static final Font MONO_FONT = Font.font("Consolas", 13);

	private static final String ADDED_BG = "#e6ffec";
	private static final String ADDED_BAR = "#2da44e";
	private static final String REMOVED_BG = "#ffebe9";
	private static final String REMOVED_BAR = "#cf222e";

	private final ScrollPane scrollPane;
	private final VBox contentContainer;
	private FileChanges FileChangesTarget = null;

	public TextViewerWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		contentContainer = new VBox();
		scrollPane = new ScrollPane(contentContainer);
		scrollPane.setFitToWidth(true);
		getChildren().add(scrollPane);
	}

	public void SetFileChanges(FileChanges _FileChangesTarget)
	{
		FileChangesTarget = _FileChangesTarget;
		contentContainer.getChildren().clear();

		if (FileChangesTarget == null)
			return;

		Text loadingText = new Text("Loading...");
		loadingText.setFont(MONO_FONT);
		contentContainer.getChildren().add(loadingText);

		FileChangesTarget.getDiffLines()
			.thenAccept(diffLines ->
				Platform.runLater(() ->
				{
					if (FileChangesTarget != _FileChangesTarget)
						return;
					renderDiffLines(diffLines);
				})
			)
			.exceptionally(ex ->
			{
				Platform.runLater(() ->
				{
					if (FileChangesTarget == _FileChangesTarget)
					{
						contentContainer.getChildren().clear();
						Text errorText = new Text("Error: " + ex.getCause().getMessage());
						errorText.setFont(MONO_FONT);
						contentContainer.getChildren().add(errorText);
					}
				});
				return null;
			});
	}

	private void renderDiffLines(List<FileChanges.LineChange> diffLines)
	{
		contentContainer.getChildren().clear();

		if (diffLines.isEmpty())
			return;

		int maxNum = 0;
		for (var e : diffLines)
		{
			if (e.oldLineNumber() != null)
				maxNum = Math.max(maxNum, e.oldLineNumber());
			if (e.newLineNumber() != null)
				maxNum = Math.max(maxNum, e.newLineNumber());
		}
		int numWidth = Math.max(1, String.valueOf(maxNum).length());
		String numFormat = "%" + numWidth + "d";
		String emptyNum = " ".repeat(numWidth);

		for (var line : diffLines)
		{
			HBox row = new HBox();
			row.setMaxWidth(Double.MAX_VALUE);

			boolean added = line.prefix() == '+';
			boolean removed = line.prefix() == '-';

			Pane bar = new Pane();
			bar.setMinWidth(4);
			bar.setPrefWidth(4);

			if (added)
			{
				row.setStyle("-fx-background-color: " + ADDED_BG + ";");
				bar.setStyle("-fx-background-color: " + ADDED_BAR + ";");
			}
			else if (removed)
			{
				row.setStyle("-fx-background-color: " + REMOVED_BG + ";");
				bar.setStyle("-fx-background-color: " + REMOVED_BAR + ";");
			}
			else
			{
				bar.setStyle("-fx-background-color: transparent;");
			}

			String oldStr = line.oldLineNumber() == null ? emptyNum : String.format(numFormat, line.oldLineNumber());
			String newStr = line.newLineNumber() == null ? emptyNum : String.format(numFormat, line.newLineNumber());

			Text oldNum = new Text(" " + oldStr);
			oldNum.setFont(MONO_FONT);
			oldNum.setFill(Color.GRAY);

			Text newNum = new Text(" " + newStr);
			newNum.setFont(MONO_FONT);
			newNum.setFill(Color.GRAY);

			Text prefixText = new Text(" " + line.prefix());
			prefixText.setFont(MONO_FONT);
			if (added)
				prefixText.setFill(Color.rgb(45, 164, 78));
			else if (removed)
				prefixText.setFill(Color.rgb(207, 34, 46));
			else
				prefixText.setFill(Color.GRAY);

			Text content = new Text(" " + line.text());
			content.setFont(MONO_FONT);

			row.getChildren().addAll(bar, oldNum, newNum, prefixText, content);
			contentContainer.getChildren().add(row);
		}
	}
}
