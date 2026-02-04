package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaSettingEntry;
import com.gitalpha.Engine.AlphaSettings;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class AlphaUI extends StackPane
{
	public AlphaUI()
	{
		super();

		var __TabPane = new TabPane();
		getChildren().add(__TabPane);

		__TabPane.getTabs().add(new GitDirTabButton(null));
		__TabPane.setTabMaxWidth(AlphaSettings.Get().GetSettingEntry(AlphaSettings.TabMaxSize).GetDefaultValue_AsInteger());

		// Create a special "+" tab
		Tab addTab = new Tab("+");
		addTab.setClosable(false);
		// Add "+" tab to tabpane
		__TabPane.getTabs().add(addTab);
		// Handle add-tab click
		__TabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
		{
			if (newTab == addTab)
			{
				Tab newUserTab = NewTab(null);
				__TabPane.getTabs().add(__TabPane.getTabs().size() - 1, newUserTab); // insert before "+"
				__TabPane.getSelectionModel().select(newUserTab); // switch to new tab
			}
		});

	}

	private GitDirTabButton NewTab(GitDir _GitDir)
	{
		return new GitDirTabButton(_GitDir);
	}
}
