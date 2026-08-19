package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.GitBranch;
import com.gitalpha.Theme.ThemeManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;

class BranchWidget extends BaseWidget
{
	private static final String ACTIVE_BRANCH_DOT = "\u25CF"; // "●"
	private static final String ACTIVE_BRANCH_STYLE = "-fx-font-weight: bold; -fx-text-fill: #2e7d32;";
	private static final int SPACING = 10;

	private final TreeView<String> LocalTreeView;
	private final TreeView<String> RemoteTreeView;

	public BranchWidget(GitDir _GitDirTarget, GitDirWidget _GitDirWidgetTarget)
	{
		super(_GitDirTarget, _GitDirWidgetTarget);

		// Create and configure the TreeViews for local and remote branches
		LocalTreeView = new TreeView<>();
		RemoteTreeView = new TreeView<>();
		// Cell factory renders the active branch with a dot, bold text and accent
		// color instead of a text marker; remote rows never show the active style.
		// The lambda parameter (__Ignored) is the TreeView itself, which the factory
		// does not need — only the local/remote flag decides the cell appearance.
		LocalTreeView.setCellFactory(__Ignored -> CreateBranchCell(false));
		RemoteTreeView.setCellFactory(__Ignored -> CreateBranchCell(true));

		// Update the branch list (populate trees)
		UpdateBranchList();

		// Set up click handlers
		SetupClickHandlers();

		// Layout: two vertical boxes side-by-side
		VBox localBox = new VBox(new Label("Local Branches"), LocalTreeView);
		VBox remoteBox = new VBox(new Label("Remote Branches"), RemoteTreeView);
		HBox h = new HBox(localBox, remoteBox);
		h.setSpacing(SPACING);
		// The trees must grow inside their VBoxes: the branch row height comes from
		// GitDirWidget's RowConstraints, and VBox only stretches
		// children that opt in via Vgrow.
		VBox.setVgrow(LocalTreeView, Priority.ALWAYS);
		VBox.setVgrow(RemoteTreeView, Priority.ALWAYS);
		// The two branch panes must stretch to fill the width the grid row
		// grants them — HBox only stretches children that opt in via Hgrow,
		// otherwise the trees would stay at their preferred width and leave
		// the rest of the branch row empty. Both panes opt in, so any extra
		// width is shared equally between local and remote.
		HBox.setHgrow(localBox, Priority.ALWAYS);
		HBox.setHgrow(remoteBox, Priority.ALWAYS);
		getChildren().add(h);
	}

	/**
	 * Wire up mouse interaction on both trees: double-click a leaf node to check
	 * out that branch, right-click to open the shared branch context menu.
	 */
	private void SetupClickHandlers()
	{
		// Context menu for branch operations
		ContextMenu contextMenu = new ContextMenu();

		MenuItem checkoutItem = new MenuItem("Checkout");
		checkoutItem.setOnAction(e -> CheckoutSelectedBranch());

		MenuItem createBranchItem = new MenuItem("Create New Branch...");
		createBranchItem.setOnAction(e -> CreateNewBranch());

		MenuItem deleteBranchItem = new MenuItem("Delete Branch");
		deleteBranchItem.setOnAction(e -> DeleteBranch());

		MenuItem pushBranchItem = new MenuItem("Push Branch");
		pushBranchItem.setOnAction(e -> PushBranch());

		MenuItem pullBranchItem = new MenuItem("Pull Branch");
		pullBranchItem.setOnAction(e -> PullBranch());

		contextMenu.getItems().addAll(checkoutItem, new SeparatorMenuItem(), createBranchItem, deleteBranchItem, new SeparatorMenuItem(), pushBranchItem, pullBranchItem);

		// Set up mouse click handlers
		// Local tree click handlers
		LocalTreeView.setOnMouseClicked(event ->
		{
			TreeItem<String> sel = LocalTreeView.getSelectionModel().getSelectedItem();
			if (sel != null)
			{
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2)
				{
					String full = BuildFullNameFromItem(sel);
					if (full != null)
						CheckoutBranchByName(full);
				}
				else if (event.getButton() == MouseButton.SECONDARY)
				{
					contextMenu.show(LocalTreeView, event.getScreenX(), event.getScreenY());
				}
			}
		});

		// Remote tree click handlers
		RemoteTreeView.setOnMouseClicked(event ->
		{
			TreeItem<String> sel = RemoteTreeView.getSelectionModel().getSelectedItem();
			if (sel != null)
			{
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2)
				{
					String full = BuildFullNameFromItem(sel);
					if (full != null)
						CheckoutBranchByName(full);
				}
				else if (event.getButton() == MouseButton.SECONDARY)
				{
					contextMenu.show(RemoteTreeView, event.getScreenX(), event.getScreenY());
				}
			}
		});
	}

	/**
	 * Check out the branch selected in either tree (local takes precedence).
	 * Context-menu action; checks out the selected leaf's full branch path.
	 */
	private void CheckoutSelectedBranch()
	{
		TreeItem<String> sel = LocalTreeView.getSelectionModel().getSelectedItem();
		boolean local = sel != null;
		if (!local)
		{
			sel = RemoteTreeView.getSelectionModel().getSelectedItem();
			if (sel == null)
				return;
		}

		String full = BuildFullNameFromItem(sel);
		if (full != null)
			CheckoutBranchByName(full);
	}

	private void CreateNewBranch()
	{
		// TODO: Show dialog to create new branch
	}

	private void DeleteBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement delete branch logic using GitDirTarget
		//		}
	}

	private void PushBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement push branch logic using GitDirTarget
		//		}
	}

	private void PullBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement pull branch logic using GitDirTarget
		//		}
	}

	/**
	 * Rebuild both branch trees from the current GitDir branch list.
	 * Re-roots each tree and repopulates; the rebuild is deferred to the JavaFX
	 * thread, so this may be called from a refresh callback.
	 */
	public void UpdateBranchList()
	{
		Debug.Log(Debug.BranchesCategory, "Updating branch list");
		Platform.runLater(() ->
		{
			LocalTreeView.setRoot(null);
			RemoteTreeView.setRoot(null);
			PopulateBranchTrees();
		});
	}

	/**
	 * Cell factory for the branch trees. The active LOCAL branch is rendered with
	 * a dot graphic, bold green text and a tooltip so it stands out without a text
	 * marker; remote rows never show the active style.
	 * The active check compares the FULL branch path (rebuilt from the tree
	 * hierarchy) so branches sharing a leaf name (e.g. "feature/foo" vs
	 * "hotfix/foo") are not both marked active.
	 *
	 * @param _IsRemote true for the remote tree (suppresses the active style)
	 */
	private TreeCell<String> CreateBranchCell(boolean _IsRemote)
	{
		return new TreeCell<>()
		{
			@Override
			protected void updateItem(String _BranchName, boolean _Empty)
			{
				super.updateItem(_BranchName, _Empty);
				if (_Empty || _BranchName == null)
				{
					setText(null);
					setGraphic(null);
					setTooltip(null);
					setStyle("");
					return;
				}

				setText(_BranchName);
				// Compare the full branch path (rebuilt from the tree hierarchy) so
				// branches sharing a leaf name (e.g. "feature/foo" vs "hotfix/foo")
				// are not both marked active.
				String __FullName = BuildFullNameFromItem(getTreeItem());
				boolean __IsActive = !_IsRemote && __FullName != null && __FullName.equals(GetGitDirTarget().GetActiveBranch());
				if (__IsActive)
				{
					setGraphic(new Label(ACTIVE_BRANCH_DOT));
					setStyle(ACTIVE_BRANCH_STYLE);
					setTooltip(new Tooltip("Currently checked out"));
				}
				else
				{
					setGraphic(null);
					setStyle("");
					setTooltip(null);
				}
			}
		};
	}

	// Core population logic (must be called on JavaFX thread)
	private void PopulateBranchTrees()
	{
		// Build separate trees for local and remote branches
		TreeItem<String> localRoot = new TreeItem<>("local-root");
		TreeItem<String> remoteRoot = new TreeItem<>("remote-root");
		localRoot.setExpanded(true);
		remoteRoot.setExpanded(true);

		for (GitBranch branch : GetGitDirTarget().GetBranches())
		{
			String name = branch.Name();
			java.util.List<String> ns = branch.Namespace();
			if (branch.Remote())
			{
				InsertIntoTree(remoteRoot, ns, name);
			}
			else
			{
				InsertIntoTree(localRoot, ns, name);
			}
		}

		LocalTreeView.setShowRoot(false);
		RemoteTreeView.setShowRoot(false);
		LocalTreeView.setRoot(localRoot);
		RemoteTreeView.setRoot(remoteRoot);
	}

	/**
	 * Insert a branch into a tree, creating namespace hierarchy nodes as needed
	 * (each is expanded on creation so nested branches are visible immediately).
	 *
	 * @param _Root      the tree root to insert under
	 * @param _Namespace branch namespace segments (empty for a top-level branch)
	 * @param _Name      leaf branch name
	 */
	private void InsertIntoTree(TreeItem<String> _Root, java.util.List<String> _Namespace, String _Name)
	{
		TreeItem<String> cur = _Root;
		for (String seg : _Namespace)
		{
			TreeItem<String> child = null;
			for (TreeItem<String> c : cur.getChildren())
			{
				if (c.getValue().equals(seg))
				{
					child = c;
					break;
				}
			}
			if (child == null)
			{
				child = new TreeItem<>(seg);
				child.setExpanded(true);
				cur.getChildren().add(child);
			}
			cur = child;
		}

		// add leaf with the clean branch name; active-branch styling is applied
		// by the cell factory, not baked into the string
		TreeItem<String> leaf = new TreeItem<>(_Name);
		cur.getChildren().add(leaf);
	}

	/**
	 * Rebuild the full branch path (namespace/name) from a tree item by walking
	 * up to the hidden "local-root"/"remote-root" sentinel. Only leaf nodes are
	 * branch names; namespace nodes cannot be checked out.
	 *
	 * @param _Item the tree item to resolve (a leaf = a branch)
	 * @return the full branch path, or null if _Item is null or not a leaf
	 */
	private String BuildFullNameFromItem(TreeItem<String> _Item)
	{
		if (_Item == null)
			return null;
		if (!_Item.isLeaf())
			return null; // only leaf nodes represent branch names

		java.util.ArrayList<String> parts = new java.util.ArrayList<>();
		TreeItem<String> cur = _Item;
		// branch names are stored clean, so there is no marker to strip
		parts.add(cur.getValue());
		while (cur.getParent() != null && cur.getParent().getValue() != null && !cur.getParent().getValue().equals("local-root") && !cur.getParent().getValue().equals("remote-root"))
		{
			cur = cur.getParent();
			parts.add(0, cur.getValue());
		}

		// join namespace and name
		return String.join("/", parts);
	}

	/**
	 * Check out a branch by its full path through the GitOperator queue and show
	 * an error dialog on failure (the refresh/broadcast after a successful
	 * checkout is handled by the operator).
	 */
	private void CheckoutBranchByName(String fullName)
	{
		GetGitDirTarget().ChangeBranch(fullName, (__Ok, __Err, __Dir) ->
		{
			if (!__Ok)
			{
				Platform.runLater(() ->
				{
					Alert alert = new Alert(Alert.AlertType.ERROR);
					ThemeManager.Instance.ApplyThemeToDialog(alert);
					alert.setTitle("Checkout Failed");
					alert.setHeaderText("Failed to checkout branch");
					alert.setContentText(__Err);
					alert.showAndWait();
				});
			}
		});
	}
}
