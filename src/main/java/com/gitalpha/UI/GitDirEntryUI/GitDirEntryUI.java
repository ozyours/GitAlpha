package com.gitalpha.UI.GitDirEntryUI;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.UI.AlphaUI;
import com.gitalpha.UI.GitDirTab.GitDirTabButton;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class GitDirEntryUI extends StackPane
{
    public GitDirEntryUI(GitDir _GitDir, GitDirTabButton _TabButton)
    {
        assert _GitDir != null;
        GitDirTarget = _GitDir;
        TabButton = _TabButton;

        BtnOpen = new Button("Open");
        BtnOpen.setOnMouseClicked(new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(MouseEvent mouseEvent)
            {
                if (AlphaUI.Instance != null)
                {
                    var __Existing = AlphaUI.Instance.TryGetOpenTabByPath(GitDirTarget.GetGitDirPath());
                    if (__Existing != null)
                    {
                        AlphaUI.Instance.SelectProjectTab(__Existing);
                        return;
                    }
                }

                // Guard before opening: TryOpenGitDir registers the repo in
                // the engine, so a null tab must not leave an orphan open dir.
                if (TabButton == null)
                    return;

                var __Target = AlphaEngine.Instance.TryOpenGitDir(GitDirTarget.GetGitDirPath());
                if (__Target != null)
                    TabButton.OpenProject(__Target);
            }
        });

        getChildren().add(
                new VBox(new Text(GitDirTarget.GetRepoRootPath().toString()),
                        BtnOpen
                ));
    }

    private GitDir GitDirTarget;
    private GitDirTabButton TabButton;
    private Button BtnOpen;
}
