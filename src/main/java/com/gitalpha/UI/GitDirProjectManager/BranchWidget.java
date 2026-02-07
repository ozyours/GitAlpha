package com.gitalpha.UI.GitDirProjectManager;

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

	private final TreeView<String> localTreeView;
	private final TreeView<String> remoteTreeView;

	public BranchWidget(GitDir _GitDirTarget, GitDirProjectManager _GitDirProjectManagerTarget)
	{
		super(_GitDirTarget, _GitDirProjectManagerTarget);

		// Create and configure the TreeViews for local and remote branches
		localTreeView = new TreeView<>();
		remoteTreeView = new TreeView<>();
		localTreeView.setMinSize(MIN_WIDTH / 2.0, MIN_HEIGHT);
		remoteTreeView.setMinSize(MIN_WIDTH / 2.0, MIN_HEIGHT);

		// Update the branch list (populate trees)
		updateBranchList();

		// Set up click handlers
		setupClickHandlers();

		// Layout: two vertical boxes side-by-side
		VBox localBox = new VBox(new Label("Local Branches"), localTreeView);
		VBox remoteBox = new VBox(new Label("Remote Branches"), remoteTreeView);
		HBox h = new HBox(localBox, remoteBox);
		getChildren().add(h);
	}

	private void setupClickHandlers()
	{
		// Context menu for branch operations
		ContextMenu contextMenu = new ContextMenu();

		MenuItem checkoutItem = new MenuItem("Checkout");
		checkoutItem.setOnAction(e -> checkoutSelectedBranch());

		MenuItem createBranchItem = new MenuItem("Create New Branch...");
		createBranchItem.setOnAction(e -> createNewBranch());

		MenuItem deleteBranchItem = new MenuItem("Delete Branch");
		deleteBranchItem.setOnAction(e -> deleteBranch());

		MenuItem pushBranchItem = new MenuItem("Push Branch");
		pushBranchItem.setOnAction(e -> pushBranch());

		MenuItem pullBranchItem = new MenuItem("Pull Branch");
		pullBranchItem.setOnAction(e -> pullBranch());

		contextMenu.getItems().addAll(checkoutItem, new SeparatorMenuItem(), createBranchItem, deleteBranchItem, new SeparatorMenuItem(), pushBranchItem, pullBranchItem);

		// Set up mouse click handlers
		// Local tree click handlers
		localTreeView.setOnMouseClicked(event ->
		{
			TreeItem<String> sel = localTreeView.getSelectionModel().getSelectedItem();
			if (sel != null)
			{
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2)
				{
					String full = buildFullNameFromItem(sel, false);
					if (full != null)
						checkoutBranchByName(full);
				}
				else if (event.getButton() == MouseButton.SECONDARY)
				{
					contextMenu.show(localTreeView, event.getScreenX(), event.getScreenY());
				}
			}
		});

		// Remote tree click handlers
		remoteTreeView.setOnMouseClicked(event ->
		{
			TreeItem<String> sel = remoteTreeView.getSelectionModel().getSelectedItem();
			if (sel != null)
			{
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2)
				{
					String full = buildFullNameFromItem(sel, true);
					if (full != null)
						checkoutBranchByName(full);
				}
				else if (event.getButton() == MouseButton.SECONDARY)
				{
					contextMenu.show(remoteTreeView, event.getScreenX(), event.getScreenY());
				}
			}
		});
	}

	private void checkoutSelectedBranch()
	{
		// Deprecated: use tree double-click handlers which call checkoutBranchByName
	}

	private void createNewBranch()
	{
		// TODO: Show dialog to create new branch
	}

	private void deleteBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement delete branch logic using GitDirTarget
		//		}
	}

	private void pushBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement push branch logic using GitDirTarget
		//		}
	}

	private void pullBranch()
	{
		//		String selectedItem = branchListView.getSelectionModel().getSelectedItem();
		//		if (selectedItem != null)
		//		{
		//			String branchName = selectedItem.replaceAll("^\\s*\\*?\\s*", "").replaceAll("\\s*\\(remote\\)$", "");
		//			// TODO: Implement pull branch logic using GitDirTarget
		//		}
	}

	public void updateBranchList()
	{
		System.out.println("Updating branch list");
		Platform.runLater(() ->
		{
			localTreeView.setRoot(null);
			remoteTreeView.setRoot(null);
			populateBranchTrees();
		});
	}

	// Core population logic (must be called on JavaFX thread)
	private void populateBranchTrees()
	{
		// Build separate trees for local and remote branches
		TreeItem<String> localRoot = new TreeItem<>("local-root");
		TreeItem<String> remoteRoot = new TreeItem<>("remote-root");
		localRoot.setExpanded(true);
		remoteRoot.setExpanded(true);

		for (GitBranch branch : GetGitDirTarget().GetBranches())
		{
			String name = branch._Name();
			java.util.List<String> ns = branch._Namespace();
			if (branch._Remote())
			{
				insertIntoTree(remoteRoot, ns, name, true);
			}
			else
			{
				insertIntoTree(localRoot, ns, name, false);
			}
		}

		localTreeView.setShowRoot(false);
		remoteTreeView.setShowRoot(false);
		localTreeView.setRoot(localRoot);
		remoteTreeView.setRoot(remoteRoot);
	}

	private void insertIntoTree(TreeItem<String> root, java.util.List<String> namespace, String name, boolean isRemote)
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
	private String buildFullNameFromItem(TreeItem<String> item, boolean isRemote)
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

	private void checkoutBranchByName(String fullName)
	{
		GetGitDirTarget().ChangeBranch(fullName).thenRun(() ->
		{
			GetGitDirProjectManagerTarget().RefreshGitDirProjectManager();
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