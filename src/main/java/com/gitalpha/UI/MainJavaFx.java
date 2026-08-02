package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaEngine;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application entry point (fat-jar main class). Restores the persisted
 * session and window bounds, enforces a minimum window size that matches the
 * project widget's layout floor, and persists bounds changes on shutdown.
 */
public class MainJavaFx extends Application
{
	/**
	 * Starts the application: load the session, build the main UI, apply the
	 * saved window bounds (clamped to the layout-floor minimum), then show.
	 */
	@Override
	public void start(Stage stage) throws Exception
	{
		AlphaEngine.Instance.LoadSession();
		if (AlphaUI.Instance == null)
			new AlphaUI();

		// Default size suits the layout floor: the left pane needs 140px branch
		// row + 240px minimum changes row + 300px commit form row + two 10px
		// gaps (mirroring BRANCH_ROW_MIN_HEIGHT, CHANGES_ROW_MIN_HEIGHT and
		// COMMIT_ROW_PREF_HEIGHT in GitDirWidget), plus the tab
		// header.
		Scene __Scene = new Scene(AlphaUI.Instance, 800, 720);
		stage.setTitle("Git Alpha");
		stage.setScene(__Scene);

		// The left pane is fixed at 500px and its rows never shrink below their
		// minimums, so clamp the window to 800x720: the height keeps the commit
		// form fully visible, the width keeps the diff viewer usable beside the
		// left column. Set before applying the saved bounds so a previously-saved
		// smaller window is restored at (at least) this size.
		stage.setMinWidth(800);
		stage.setMinHeight(720);

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

	/** Saves the session (open/recent repos + window bounds) on shutdown. */
	@Override
	public void stop() throws Exception
	{
		AlphaEngine.Instance.SaveSession();
		super.stop();
	}

	/** Application launcher — the shaded fat-jar entry point. */
	public static void main(String[] args)
	{
		launch(args);
	}
}
