package com.gitalpha.UI;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainJavaFx extends Application
{
    @Override
    public void start(Stage stage) throws Exception
    {
        Scene __Scene = new Scene(new AlphaUI(), 800, 600);
        stage.setTitle("Git Alpha");
        stage.setScene(__Scene);
        stage.show();
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
