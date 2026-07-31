package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.IRefreshGitDirEvent;
import com.gitalpha.Type.FileChange;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.application.Platform;
import javafx.scene.layout.*;

import java.util.Objects;

public class GitDirProjectManagerWidget extends StackPane
{
	public GitDirProjectManagerWidget(GitDirTabButton _TabButton, GitDir _GitDir)
	{
		super();

		assert _TabButton != null;
		assert _GitDir != null;

		TabButton = _TabButton;
		GitDirTarget = _GitDir;

		// Create grid layout
		BranchWidgetInstance = new BranchWidget(GitDirTarget, this);
		ChangesWidgetInstance = new ChangesWidget(GitDirTarget, this);
		CommitWidgetInstance = new CommitWidget(GitDirTarget, this);
		TextViewerWidgetInstance = new TextViewerWidget(GitDirTarget, this); // TODO: Pass the selected FileChange when implemented

		var __GridLayout = new GridPane();

		// Left pane (branches + changes + commit) keeps a fixed width;
		// the diff viewer (column 1) takes all remaining space.
		var __LeftColumn = new ColumnConstraints();
		__LeftColumn.setMinWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setPrefWidth(LEFT_PANE_WIDTH);
		__LeftColumn.setMaxWidth(LEFT_PANE_WIDTH);
		__GridLayout.getColumnConstraints().add(__LeftColumn);

		__GridLayout.add(BranchWidgetInstance, 0, 0);    // Top left
		__GridLayout.add(ChangesWidgetInstance, 0, 1);   // Middle left
		__GridLayout.add(CommitWidgetInstance, 0, 2);    // Bottom left
		__GridLayout.add(TextViewerWidgetInstance, 1, 0, 1, 3); // Right side, spanning 3 rows

		// Fill the fixed-width column so the widgets stretch to its full width
		GridPane.setHgrow(BranchWidgetInstance, Priority.ALWAYS);
		GridPane.setHgrow(ChangesWidgetInstance, Priority.ALWAYS);
		GridPane.setHgrow(CommitWidgetInstance, Priority.ALWAYS);

		GridPane.setVgrow(ChangesWidgetInstance, Priority.ALWAYS);

		GridPane.setHgrow(TextViewerWidgetInstance, Priority.ALWAYS);
		GridPane.setVgrow(TextViewerWidgetInstance, Priority.ALWAYS);

		// Set spacing
		__GridLayout.setHgap(10);
		__GridLayout.setVgap(10);

		getChildren().add(__GridLayout);

		RefreshGitDirEventListener = (_GitDirTarget, _Reason) ->
		{
			if (_GitDirTarget == null || Objects.equals(_GitDirTarget.GetGitDirPath(), GitDirTarget.GetGitDirPath()))
			{
				RefreshGitDirProjectManagerWidget();
			}
		};
		AlphaEngine.Instance.AddIRefreshGitDirEvent(RefreshGitDirEventListener);

		RefreshGitDirProjectManagerWidget();
	}

	/** Fixed width (px) of the left pane containing branches + file changes */
	private static final double LEFT_PANE_WIDTH = 500;

	public final GitDirTabButton TabButton;
	public final GitDir GitDirTarget;

	private final BranchWidget BranchWidgetInstance;
	private final ChangesWidget ChangesWidgetInstance;
	private final CommitWidget CommitWidgetInstance;
	private final TextViewerWidget TextViewerWidgetInstance;
	private final IRefreshGitDirEvent RefreshGitDirEventListener;
	private boolean Disposed = false;

	public void ReadFileChange(FileChange _FileChange)
	{
		TextViewerWidgetInstance.SetFileChange(_FileChange);
	}

	public void RefreshGitDirProjectManagerWidget()
	{
		if (Disposed)
			return;

		GitDirTarget.Refresh((__Ok, __Err, __Dir) ->
		{
			Platform.runLater(() ->
			{
				if (Disposed)
					return;

				ChangesWidgetInstance.UpdateChanges();
				BranchWidgetInstance.UpdateBranchList();
			});
		});
	}

	public void Dispose()
	{
		if (Disposed)
			return;

		Disposed = true;
		AlphaEngine.Instance.RemoveIRefreshGitDirEvent(RefreshGitDirEventListener);
	}
}
