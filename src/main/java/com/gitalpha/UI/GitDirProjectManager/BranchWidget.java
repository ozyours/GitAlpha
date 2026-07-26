package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.Debug;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.GitBranch;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.util.concurrent.CompletableFuture;

class BranchWidget extends BaseWidget
{
	private static final double MIN_WIDTH = 200;
	private static final double MIN_HEIGHT = 300;
	private static final String ACTIVE_BRANCH_MARKER = "* ";

	private final TreeView<String> LocalTreeView;
	private final TreeView<String> RemoteTreeView;

	public BranchWidget(GitDir _GitDirTarget, GitDirProjectManagerWidget _GitDirProjectManagerWidgetTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerWidgetTarget);

		// Create and configure the TreeViews for local and remote branches
		LocalTreeView = new TreeView<>();
		RemoteTreeView = new TreeView<>();
		LocalTreeView.setMinSize(MIN_WIDTH / 2.0, MIN_HEIGHT);
		RemoteTreeView.setMinSize(MIN_WIDTH / 2.0, MIN_HEIGHT);

		// Update the branch list (populate trees)
		UpdateBranchList();

		// Set up click handlers
		SetupClickHandlers();

		// Layout: two vertical boxes side-by-side
		VBox localBox = new VBox(new Label("Local Branches"), LocalTreeView);
		VBox remoteBox = new VBox(new Label("Remote Branches"), RemoteTreeView);
		HBox h = new HBox(localBox, remoteBox);
		getChildren().add(h);
	}

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
					String full = BuildFullNameFromItem(sel, false);
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
					String full = BuildFullNameFromItem(sel, true);
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

	private void CheckoutSelectedBranch()
	{
		// Deprecated: use tree double-click handlers which call checkoutBranchByName
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
				InsertIntoTree(remoteRoot, ns, name, true);
			}
			else
			{
				InsertIntoTree(localRoot, ns, name, false);
			}
		}

		LocalTreeView.setShowRoot(false);
		RemoteTreeView.setShowRoot(false);
		LocalTreeView.setRoot(localRoot);
		RemoteTreeView.setRoot(remoteRoot);
	}

	private void InsertIntoTree(TreeItem<String> root, java.util.List<String> namespace, String name, boolean isRemote)
	{
		TreeItem<String> cur = root;
		for (String seg : namespace)
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

		// add leaf
		// mark active local branch
		String leafText = name;
		if (!isRemote && name.equals(GetGitDirTarget().GetActiveBranch()))
		{
			leafText = ACTIVE_BRANCH_MARKER + name;
		}
		TreeItem<String> leaf = new TreeItem<>(leafText);
		cur.getChildren().add(leaf);
	}

	// Build full branch name (namespace/name) from a selected TreeItem; returns null if selection is a namespace node
	private String BuildFullNameFromItem(TreeItem<String> item, boolean isRemote)
	{
		if (item == null)
			return null;
		if (!item.isLeaf())
			return null; // only leaf nodes represent branch names

		java.util.ArrayList<String> parts = new java.util.ArrayList<>();
		TreeItem<String> cur = item;
		// strip active marker if present
		String val = cur.getValue();
		if (val.startsWith(ACTIVE_BRANCH_MARKER))
			val = val.substring(ACTIVE_BRANCH_MARKER.length());
		parts.add(val);
		while (cur.getParent() != null && cur.getParent().getValue() != null && !cur.getParent().getValue().equals("local-root") && !cur.getParent().getValue().equals("remote-root"))
		{
			cur = cur.getParent();
			parts.add(0, cur.getValue());
		}

		// join namespace and name
		return String.join("/", parts);
	}

	private void CheckoutBranchByName(String fullName)
	{
		GetGitDirTarget().ChangeBranch(fullName).thenRun(() ->
		{
			AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("git-operation-completed", GetGitDirTarget());
		}).exceptionally((ex) ->
		{
			ex.printStackTrace();
			Platform.runLater(() ->
			{
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.setTitle("Checkout Failed");
				alert.setHeaderText("Failed to checkout branch");
				alert.setContentText(ex.getMessage());
				alert.showAndWait();
			});
			return null;
		});
	}
}
