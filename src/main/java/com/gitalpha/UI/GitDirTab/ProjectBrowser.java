package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Function.GitDirFunction;
import com.gitalpha.UI.AlphaUI;
import com.gitalpha.UI.IObject;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.nio.file.InvalidPathException;

public class ProjectBrowser extends StackPane implements IObject
{
	public ProjectBrowser(Object _Parent, GitDirTabButton _TabButton, AlphaUI _AlphaUI)
	{
		Parent = _Parent;
		AlphaUIInstance = _AlphaUI;

		var __txt_ProjectBrowser = new Text("Project Browser");
		var __txb_ProjectPath = new TextField();
		var __btn_OpenProject = new Button("Open");
		__btn_OpenProject.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent mouseEvent)
			{
				String __RawInput = __txb_ProjectPath.getText();
				if (__RawInput == null || __RawInput.isBlank())
				{
					ShowInvalidPathWarning("Project path is empty.");
					return;
				}

				Path __SelectedPath;
				try
				{
					__SelectedPath = Path.of(__RawInput.trim());
				}
				catch (InvalidPathException __Ex)
				{
					ShowInvalidPathWarning("Project path is not valid.");
					return;
				}
				Path __GitPath = GitDirFunction.TryFixGitDirPath(__SelectedPath);

				if (AlphaUIInstance != null)
				{
					GitDirTabButton ExistingTab = AlphaUIInstance.TryGetOpenTabByPath(__GitPath);
					if (ExistingTab != null && ExistingTab.getTabPane() != null)
					{
						ExistingTab.getTabPane().getSelectionModel().select(ExistingTab);
						return;
					}
				}

				GitDir __TargetGitDir = AlphaEngine.Instance.TryOpenGitDir(__SelectedPath);

				if (__TargetGitDir != null)
				{
					_TabButton.OpenProject(__TargetGitDir);
				}
				else
				{
					ShowInvalidPathWarning("Project path is not a valid git repository: " + __GitPath);
				}
			}
		});

		getChildren().add(new VBox(__txt_ProjectBrowser, __txb_ProjectPath, __btn_OpenProject));
	}

	private Object Parent;
	private final AlphaUI AlphaUIInstance;

	@Override
	public Object GetParent()
	{
		return Parent;
	}

	private void ShowInvalidPathWarning(String _Message)
	{
		Alert __Alert = new Alert(Alert.AlertType.WARNING);
		__Alert.setTitle("Invalid Project Path");
		__Alert.setHeaderText("Unable to open project");
		__Alert.setContentText(_Message);
		__Alert.showAndWait();
	}
}
