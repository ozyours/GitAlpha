package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.FileChanges;
import com.gitalpha.UI.INode;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

class ChangesEntry extends HBox implements INode
{
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ChangesWidget ChangesWidget;
	private final FileChanges FileChangesTarget;
	private final CheckBox commitCheckBox;

	public ChangesEntry(ChangesWidget _ChangesWidget, FileChanges _FileChangesTarget)
	{
		ChangesWidget = _ChangesWidget;
		FileChangesTarget = _FileChangesTarget;

		// Configure the HBox
		setSpacing(SPACING);
		setPadding(new Insets(PADDING));

		// Create checkbox for selecting files to commit
		commitCheckBox = new CheckBox();

		// Create text showing file status and path
		Text statusText = createStatusText(FileChangesTarget._Status());
		Text pathText = new Text(FileChangesTarget._FilePath().getFileName().toString());

		// Add components to the entry
		getChildren().addAll(commitCheckBox, statusText, pathText);

		setOnMouseClicked(mouseEvent ->
		{
			ChangesWidget.GetGitDirProjectManagerTarget().ReadFileChanges(FileChangesTarget);
		});
	}

	public boolean isSelected()
	{
		return commitCheckBox.isSelected();
	}

	public FileChanges getFileChanges()
	{
		return FileChangesTarget;
	}

	private Text createStatusText(EFileChangeStatus status)
	{
		Text statusText = new Text();
		statusText.setText(switch (status)
		{
			case Added -> "[Added] ";
			case Modified -> "[Modified] ";
			case Removed -> "[Removed] ";
		});

		statusText.setFill(switch (status)
		{
			case Added -> Color.GREEN;
			case Modified -> Color.ORANGE;
			case Removed -> Color.RED;
		});

		return statusText;
	}

	@Override
	public Node GetParent()
	{
		return ChangesWidget;
	}
}

public class ChangesWidget extends BaseWidget
{
	private static final double MIN_WIDTH = 400;
	private static final double MIN_HEIGHT = 300;
	private static final int SPACING = 10;
	private static final int PADDING = 5;

	private final ListView<ChangesEntry> changesListView;

	public ChangesWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		// Create and configure ListView
		changesListView = new ListView<>();
		changesListView.setMinSize(MIN_WIDTH, MIN_HEIGHT);

		// Add ListView to the StackPane
		getChildren().add(changesListView);
	}

	public void updateChanges()
	{
		changesListView.getItems().clear();

		for (FileChanges change : GetGitDirTarget().GetChangedFiles())
		{
			// Create and add entry to the list
			changesListView.getItems().add(new ChangesEntry(this, change));
		}
	}

	/**
	 * Get all selected file changes
	 *
	 * @return List of FileChanges that are selected for commit
	 */
	public List<FileChanges> getSelectedChanges()
	{
		List<FileChanges> selectedChanges = new ArrayList<>();
		for (ChangesEntry entry : changesListView.getItems())
		{
			if (entry.isSelected())
			{
				selectedChanges.add(entry.getFileChanges());
			}
		}
		return selectedChanges;
	}
}
