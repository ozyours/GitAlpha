package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.IFileListEntry;

import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;

/**
 * History view for one open repository — the "History" sub-tab inside the
 * project widget. A vertical split: the top pane holds the planned commit
 * graph (DAG) — a placeholder scaffold until the {@code git log} + rendering
 * plumbing lands (see ROADMAP.md) — and the bottom pane holds a read-only
 * {@link ImmutableChangesWidget} that will list the selected commit's changed
 * files. The file diff itself is shown by the project widget's shared
 * {@link TextViewerWidget} on the right.
 */
public class TreeViewWidget extends BaseWidget
{
	/**
	 * Read-only file-change list for the selected commit; empty until the
	 * commit-graph selection is wired to populate it.
	 */
	private final ImmutableChangesWidget<IFileListEntry> ImmutableChangesWidgetInstance;

	/**
	 * Vertical split: commit graph on top, file-change list at the bottom.
	 * A SplitPane rather than a fixed layout so the divider between the two
	 * panes stays user-resizable as the graph grows.
	 */
	private final SplitPane SplitPaneInstance;

	/**
	 * Builds the history scaffold: a vertical split with the commit-graph
	 * placeholder above a read-only file list. The list reuses the
	 * {@link ImmutableChangesWidget} component shared with the stash window,
	 * so commit-file rows render and route selection exactly like stash files.
	 *
	 * @param _GitDirTarget       the repository this widget displays
	 * @param _GitDirWidgetTarget the hosting project widget
	 */
	public TreeViewWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// Top: the commit graph (placeholder for now); bottom: the file list
		// that will follow the graph selection. The diff viewer on the right of
		// the project widget shows the selected file's diff while this tab is
		// active.
		StackPane __GraphPlaceholder = CreatePlaceholder("Commit graph (DAG) - placeholder");
		ImmutableChangesWidgetInstance = new ImmutableChangesWidget<>();

		SplitPaneInstance = new SplitPane(__GraphPlaceholder, ImmutableChangesWidgetInstance);
		SplitPaneInstance.setOrientation(Orientation.VERTICAL);
		getChildren().add(SplitPaneInstance);
	}

	/**
	 * Build a centered, muted placeholder label on a shaded pane for the
	 * not-yet-implemented graph.
	 *
	 * @param _Text the placeholder message shown in the center of the pane
	 * @return the shaded pane carrying the label
	 */
	private static StackPane CreatePlaceholder(String _Text)
	{
		// Grey-on-grey styling is deliberate: it reads as inert scaffold rather
		// than live content. Wrapping keeps the message from clipping when the
		// pane is resized.
		Label __Label = new Label(_Text);
		__Label.setWrapText(true);
		__Label.setStyle("-fx-text-fill: #808080;");

		StackPane __Pane = new StackPane(__Label);
		__Pane.setStyle("-fx-background-color: #f4f4f4;");
		return __Pane;
	}
}
