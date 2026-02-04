package com.gitalpha.UI;

import com.gitalpha.Engine.GitDirContainer.GitDirContainer;
import com.gitalpha.UI.GitDirEntryUI.GirDirEntryUI;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;

public class GitDirContainerUI extends StackPane
{
    public GitDirContainerUI(GitDirContainer _Container)
    {
        assert _Container != null;
        Container = _Container;

        GridPaneInstance = new TilePane();
        for (var e : Container.GetGitDirs())
        {
            GridPaneInstance.getChildren().add(new GirDirEntryUI(e));
        }
    }

    private GitDirContainer Container;
    private TilePane GridPaneInstance;
}
