package com.gitalpha.UI.GitDirEntryUI;

import com.gitalpha.Engine.GitDirContainer.GitDirContainer;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;

public class GitDirContainerUI extends StackPane
{
    public GitDirContainerUI(GitDirContainer _Container, GitDirTabButton _TabButton)
    {
        assert _Container != null;
        Container = _Container;
        TabButton = _TabButton;

        GridPaneInstance = new TilePane();
        GridPaneInstance.setHgap(8);
        GridPaneInstance.setVgap(8);
        for (var e : Container.GetGitDirs())
        {
            var entry = new GitDirEntryUI(e, TabButton);
            GridPaneInstance.getChildren().add(entry);
        }
        getChildren().add(GridPaneInstance);
    }

    private final GitDirContainer Container;
    private final GitDirTabButton TabButton;
    private final TilePane GridPaneInstance;
}
