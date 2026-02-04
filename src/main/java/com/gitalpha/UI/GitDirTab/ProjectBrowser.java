package com.gitalpha.UI.GitDirTab;

import com.gitalpha.Function.GitDirFunction;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.nio.file.Path;

public class ProjectBrowser extends StackPane
{
    public ProjectBrowser(GitDirTabButton _TabButton)
    {
        setStyle("-fx-border-color: red;");

        var __txt_ProjectBrowser = new Text("Project Browser");
        var __txb_ProjectPath = new TextField();
        var __btn_OpenProject = new Button("Open");
        __btn_OpenProject.setOnMouseClicked(new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(MouseEvent mouseEvent)
            {
                Path __SelectedPath = null;
                try
                {
                    __SelectedPath = Path.of(__txb_ProjectPath.getText());

                }
                catch (Exception e)
                {
                }

                if (__SelectedPath != null)
                {
                    __SelectedPath = GitDirFunction.TryFixGitDirPath(__SelectedPath);
                    if (GitDirFunction.CheckGitDirValidity(__SelectedPath))
                    {
                        _TabButton.OpenProject(__SelectedPath);
                        return;
                    }
                }

                System.err.println("Error opening the git dir.");
            }
        });

        getChildren().add(new VBox(__txt_ProjectBrowser, __txb_ProjectPath, __btn_OpenProject));
    }
}
