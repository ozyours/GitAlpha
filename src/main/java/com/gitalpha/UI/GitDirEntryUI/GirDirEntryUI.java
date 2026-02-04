package com.gitalpha.UI.GitDirEntryUI;

import com.gitalpha.Engine.GitDir;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.lang.ref.WeakReference;

public class GirDirEntryUI extends StackPane
{
    public GirDirEntryUI(GitDir _GitDir)
    {
        assert _GitDir != null;
        GitDirTarget = _GitDir;

        btn_Open = new Button("Open");
        btn_Open.setOnMouseClicked(new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(MouseEvent mouseEvent)
            {
                if (OpenClickEvent.get() != null)
                    OpenClickEvent.get().OpenClick(GitDirTarget);
            }
        });

        getChildren().add(
                new VBox(new Text(GitDirTarget.GetGitDirParentPath().toString()),
                        btn_Open
                ));
    }

    private GitDir GitDirTarget;
    private Button btn_Open;
    private WeakReference<GitDirOpenClick> OpenClickEvent;

    public void SetOpenEvent(GitDirOpenClick _Event)
    {
        OpenClickEvent = new WeakReference<>(_Event);
    }
}
