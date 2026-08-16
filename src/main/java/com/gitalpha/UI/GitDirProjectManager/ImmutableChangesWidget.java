package com.gitalpha.UI.GitDirProjectManager;
import com.gitalpha.Type.EFileChangeStatus;
import com.gitalpha.Type.IFileListEntry;
import com.gitalpha.UI.Components.AListView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
* Read-only file-change list: each row shows a bracketed single-letter
	 * status ({@code [A]/[D]/[M]}) followed by the file path, and selection is
	 * reported through a callback so the owner decides what to display
	 * (working-tree diffs vs raw stash/commit diffs differ). Extracted from the
	 * stash window; reused by the history view for commit files. Entries only
	 * need to implement {@link IFileListEntry}.
 *
 * @param <T> entry type; must expose a path + status via {@link IFileListEntry}
 */
public class ImmutableChangesWidget<T extends IFileListEntry> extends StackPane
{
	/** Selection callback fired on the FX thread for every selection change (null when cleared) */
	private Consumer<T> SelectionHandler = null;

	/** The list view; its cell factory renders the status letter + path for each entry */
	private final AListView<T> ListViewInstance;
	/** Backing list, replaced wholesale by {@link #SetEntries} / {@link #Clear} */
	private final ObservableList<T> Items = FXCollections.observableArrayList();

	/**
	 * Creates an empty read-only change list. Populate it via
	 * {@link #SetEntries} and route selections to the owner through
	 * {@link #SetSelectionHandler}.
	 */
	public ImmutableChangesWidget()
	{
		ListViewInstance = new AListView<>(Items);
		ListViewInstance.setCellFactory(__List -> new FileListCell<T>());
		// Selection is reported through the handler; the owner routes it to the
		// diff viewer (the diff format depends on the entry source).
		ListViewInstance.getSelectionModel().selectedItemProperty().addListener((__Obs, __Old, __New) ->
		{
			if (SelectionHandler != null)
				SelectionHandler.accept(__New);
		});
		getChildren().add(ListViewInstance);
	}

	/**
	 * Set the selection callback; may be null to stop routing. Fired with the
	 * newly selected entry, or null when the selection is cleared.
	 */
	public void SetSelectionHandler(Consumer<T> _Handler)
	{
		SelectionHandler = _Handler;
	}

	/** Replace the displayed entries (e.g. after a stash/commit selection change). */
	public void SetEntries(List<? extends T> _Entries)
	{
		if (_Entries == null)
		{
			Items.clear();
			return;
		}
		Items.setAll(_Entries);
	}

	/** Clear the list (no entries to show). */
	public void Clear()
	{
		Items.clear();
	}

	/** @return the currently selected entry, or null */
	public T GetSelectedEntry()
	{
		return ListViewInstance.getSelectionModel().getSelectedItem();
	}

	/**
	 * Recycled list cell rendering a bracketed single-letter status code
	 * ({@code [A]/[D]/[M]}) followed by the file path (moved from
	 * StashWidget.StashFileCell). Cells are reused by VirtualFlow, so
	 * {@code updateItem} resets the text and graphic on every call before
	 * building the row for the new entry.
	 */
	private static class FileListCell<T extends IFileListEntry> extends ListCell<T>
	{
		@Override
		protected void updateItem(T _Item, boolean _Empty)
		{
			super.updateItem(_Item, _Empty);
			if (_Empty || _Item == null)
			{
				setText(null);
				setGraphic(null);
				return;
			}

			// Compact bracketed codes; colours mirror the working-tree
			// changes list (green = added, red = removed, orange = modified)
			// so both lists read consistently.
			String __StatusStr = switch (_Item.GetStatus())
			{
				case Added -> "[A]";
				case Removed -> "[D]";
				case Modified -> "[M]";
			};
			Text __Status = new Text(__StatusStr + "  ");
			__Status.setFill(switch (_Item.GetStatus())
			{
				case Added -> Color.GREEN;
				case Removed -> Color.RED;
				case Modified -> Color.ORANGE;
			});
			Text __Path = new Text(_Item.GetPath());
			setGraphic(new HBox(4, __Status, __Path));
			setText(null);
		}
	}
}
