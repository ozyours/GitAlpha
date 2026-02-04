package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Engine.GitDir;
import javafx.scene.layout.StackPane;

public class CommitWidget extends StackPane
{
    private GitDir GitDirTarget;

    public CommitWidget(GitDir _GitDirTarget)
    {
        GitDirTarget = _GitDirTarget;
    }
}
