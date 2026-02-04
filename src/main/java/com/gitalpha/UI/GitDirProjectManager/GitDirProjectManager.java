package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.FileChanges;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.scene.layout.*;

public class GitDirProjectManager extends StackPane
{
	public GitDirProjectManager(GitDirTabButton _TabButton, GitDir _GitDir)
	{
		super();

		assert _TabButton != null;
		assert _GitDir != null;

		TabButton = _TabButton;
		GitDirTarget = _GitDir;

		// Create grid layout
		BranchWidgetInstance = new BranchWidget(GitDirTarget);
		ChangesWidgetInstance = new ChangesWidget(this, GitDirTarget);
		CommitWidgetInstance = new CommitWidget(GitDirTarget);
		TextViewerWidgetInstance = new TextViewerWidget(GitDirTarget); // TODO: Pass the selected FileChanges when implemented

		var __GridLayout = new GridPane();

		// Basic layout without constraints
		__GridLayout.add(BranchWidgetInstance, 0, 0);    // Top left
		__GridLayout.add(ChangesWidgetInstance, 0, 1);   // Middle left
		__GridLayout.add(CommitWidgetInstance, 0, 2);    // Bottom left
		__GridLayout.add(TextViewerWidgetInstance, 1, 0, 1, 3); // Right side, spanning 3 rows

		//		GridPane.setHgrow(BranchWidgetInstance, Priority.ALWAYS);
		//		GridPane.setVgrow(BranchWidgetInstance, Priority.ALWAYS);
		//		GridPane.setHgrow(ChangesWidgetInstance, Priority.ALWAYS);
		GridPane.setVgrow(ChangesWidgetInstance, Priority.ALWAYS);

		GridPane.setHgrow(TextViewerWidgetInstance, Priority.ALWAYS);
		GridPane.setVgrow(TextViewerWidgetInstance, Priority.ALWAYS);

		// Set spacing
		__GridLayout.setHgap(10);
		__GridLayout.setVgap(10);

		getChildren().add(__GridLayout);

		GitDirTarget.Refresh().thenRun(() ->
		{
			ChangesWidgetInstance.updateChanges();
		});
	}

	public final GitDirTabButton TabButton;
	public final GitDir GitDirTarget;

	private final BranchWidget BranchWidgetInstance;
	private final ChangesWidget ChangesWidgetInstance;
	private final CommitWidget CommitWidgetInstance;
	private final TextViewerWidget TextViewerWidgetInstance;

	public void ReadFileChanges(FileChanges _FileChanges)
	{
		TextViewerWidgetInstance.SetFileChanges(_FileChanges);
	}
}
