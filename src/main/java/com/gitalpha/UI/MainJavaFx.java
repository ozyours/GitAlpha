package com.gitalpha.UI;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Theme.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Screen;
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
		// No user-agent stylesheet override: the stock JavaFX Modena skin is used
		// and themed widgets (AButton and friends) carry their own inline skins
		// baked from the active ColorPalette by ThemeManager. The scene itself is
		// registered with ThemeManager, which swaps in the scene-level base
		// stylesheet (the .root focus-ring kill + palette CSS variables).

		AlphaEngine.Instance.LoadSession();
		if (AlphaUI.Instance == null)
			new AlphaUI();

		// Default size suits the layout floor: the left pane needs 140px branch
		// row + 240px minimum changes row + 300px commit form row + two 10px
		// gaps (mirroring BRANCH_ROW_MIN_HEIGHT, CHANGES_ROW_MIN_HEIGHT and
		// COMMIT_ROW_PREF_HEIGHT in GitDirWidget), plus the tab
		// header.
		Scene __Scene = new Scene(AlphaUI.Instance, 800, 780);
		ThemeManager.Instance.RegisterScene(__Scene);
		stage.setTitle("Git Alpha");
		stage.setScene(__Scene);

		// The left pane starts at 500px (persisted globally once resized) and
		// its rows never shrink below their minimums, so clamp the window to
		// 800x780: the height keeps the commit form fully visible (720px
		// layout floor + ~60px of top chrome: menu bar + quick command bar),
		// the width keeps the diff viewer usable
		// beside the left column. The height floor is clamped to the primary
		// screen's visual bounds so a small display (e.g. 1366x768 with a
		// taskbar) can still shrink the window to fit. Set before applying the
		// saved bounds so a previously-saved smaller window is restored at
		// (at least) this size.
		stage.setMinWidth(800);
		stage.setMinHeight(Math.min(780, Screen.getPrimary().getVisualBounds().getHeight()));

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

		// Closing the main window terminates the whole application. Without the
		// explicit exit the runtime would keep running as long as any stash
		// window (a separate stage) is still open; Platform.exit() closes every
		// window and then calls stop(), which saves the session.
		stage.setOnCloseRequest(__Event -> Platform.exit());

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
