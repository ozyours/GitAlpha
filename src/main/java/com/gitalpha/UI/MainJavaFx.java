package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaEngine;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainJavaFx extends Application
{
	@Override
	public void start(Stage stage) throws Exception
	{
		AlphaEngine.Instance.LoadSession();
		if (AlphaUI.Instance == null)
			new AlphaUI();

		Scene __Scene = new Scene(AlphaUI.Instance, 800, 600);
		stage.setTitle("Git Alpha");
		stage.setScene(__Scene);

		// Apply saved window position/size
		AlphaEngine __Engine = AlphaEngine.Instance;
		if (__Engine.GetWindowX() >= 0)
			stage.setX(__Engine.GetWindowX());
		if (__Engine.GetWindowY() >= 0)
			stage.setY(__Engine.GetWindowY());
		stage.setWidth(__Engine.GetWindowWidth());
		stage.setHeight(__Engine.GetWindowHeight());
		stage.setMaximized(__Engine.GetWindowMaximized());

		// Persist window bounds when they change (skip while maximized — preserve restore size)
		stage.xProperty().addListener((obs, oldVal, newVal) ->
		{
			if (stage.isMaximized()) return;
			__Engine.SetWindowBounds(newVal.intValue(), (int) stage.getY(), (int) stage.getWidth(), (int) stage.getHeight());
		});
		stage.yProperty().addListener((obs, oldVal, newVal) ->
		{
			if (stage.isMaximized()) return;
			__Engine.SetWindowBounds((int) stage.getX(), newVal.intValue(), (int) stage.getWidth(), (int) stage.getHeight());
		});
		stage.widthProperty().addListener((obs, oldVal, newVal) ->
		{
			if (stage.isMaximized()) return;
			__Engine.SetWindowBounds((int) stage.getX(), (int) stage.getY(), newVal.intValue(), (int) stage.getHeight());
		});
		stage.heightProperty().addListener((obs, oldVal, newVal) ->
		{
			if (stage.isMaximized()) return;
			__Engine.SetWindowBounds((int) stage.getX(), (int) stage.getY(), (int) stage.getWidth(), newVal.intValue());
		});
		stage.maximizedProperty().addListener((obs, oldVal, newVal) ->
			__Engine.SetWindowMaximized(newVal));

		stage.focusedProperty().addListener((obs, oldValue, newValue) ->
		{
			if (Boolean.TRUE.equals(newValue))
			{
				AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("window-focus-in", null);
			}
			else
			{
				AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh("window-focus-out", null);
			}
		});
		stage.show();
	}

	@Override
	public void stop() throws Exception
	{
		AlphaEngine.Instance.SaveSession();
		super.stop();
	}

	public static void main(String[] args)
	{
		launch(args);
	}
}
