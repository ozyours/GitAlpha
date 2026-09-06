package com.gitalpha.Engine;

import com.gitalpha.Engine.GitDirContainer.*;
import com.gitalpha.Function.GitDirFunction;
import com.gitalpha.Type.FileChange;
import com.gitalpha.Type.StashWindowState;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-wide engine singleton: owns the open/recent repository lists,
 * the shared session state (window bounds, Stash-window state, shared
 * left-pane width) and the weak event lists that notify the UI of repository
 * open/close and refresh. The session file (~/.gitalpha/session.json) is
 * written on
 * {@link #SaveSession()} and restored on {@link #LoadSession()}; a SHA-256
 * hash of the serialized root skips redundant writes.
 * <p>
 * Never construct a second instance — always use {@link #Instance}.
 */
public class AlphaEngine
{
	/**
	 * The single engine instance; the whole app reaches shared state through this.
	 */
	public static AlphaEngine Instance = new AlphaEngine();

	public AlphaEngine()
	{
		AddIOpenGitDirEvent(new IOpenGitDirEvent()
		{
			@Override
			public void Event(GitDir _GitDirTarget)
			{
				// RecentGitDirList
			}
		});
	}

	private final AlphaSettings Settings = new AlphaSettings(this);
	private final RecentGitDirContainer RecentGitDirList = new RecentGitDirContainer(this);
	private final GitDirContainer OpenGitDirList = new GitDirContainer(this);

	/**
	 * @return the app settings (git path, recent/tab size limits)
	 */
	public final AlphaSettings GetSettings()
	{
		return Settings;
	}

	/**
	 * @return the capped + deduped list of recently opened repositories
	 */
	public final RecentGitDirContainer GetRecentGitDirList()
	{
		return RecentGitDirList;
	}

	/**
	 * @return the list of currently open repositories
	 */
	public final GitDirContainer GetOpenGitDirList()
	{
		return OpenGitDirList;
	}

	/**
	 * Weak event listeners notified when a repository is opened (pruned on dead refs)
	 */
	private final List<WeakReference<IOpenGitDirEvent>> OpenGitDirEventList = new ArrayList<>();

	/**
	 * Weak event listeners notified when a repository is closed (pruned on dead refs)
	 */
	private final List<WeakReference<ICloseGitDirEvent>> CloseGitDirEventList = new ArrayList<>();
	/**
	 * Weak event listeners notified when a repository refreshes (pruned on dead refs)
	 */
	private final List<WeakReference<IRefreshGitDirEvent>> RefreshGitDirEventList = new ArrayList<>();
	/**
	 * Weak event listeners notified when existing FileChange entries had their
	 * scanned mtime updated during a refresh (pruned on dead refs)
	 */
	private final List<WeakReference<IScannedFilesUpdatedEvent>> ScannedFilesUpdatedEventList = new ArrayList<>();
	/**
	 * Session file location: ~/.gitalpha/session.json
	 */
	private final Path SessionFilePath = Path.of(System.getProperty("user.home"), ".gitalpha", "session.json");
	/**
	 * SHA-256 of the last written session root; equal hashes skip redundant writes
	 */
	private String LastSessionRootHash = "";

	/**
	 * Shared Stash-window state: one geometry/maximized/column set used by every
	 * repository's Stash window. It stays null until a stash window has been
	 * opened (and persisted) at least once; the last window to change it wins.
	 */
	private StashWindowState SharedStashWindowState = null;

	/**
	 * Session-file JSON key under which the shared Stash-window state is stored
	 */
	private static final String STASH_WINDOW_STATE_KEY = "StashWindowState";

	/**
	 * @return the shared Stash-window state, or null if no stash window has been persisted yet
	 */
	public StashWindowState GetStashWindowState()
	{
		return SharedStashWindowState;
	}

	/**
	 * @param _State the shared Stash-window state to persist (null is ignored)
	 */
	public void SetStashWindowState(StashWindowState _State)
	{
		if (_State == null)
			return;
		SharedStashWindowState = _State;
	}

	/**
	 * Shared left-pane width (px) of the project widget: one width used by
	 * every open repository's GitDirWidget, not per repository. The last
	 * widget to drag the divider wins.
	 */
	private double SharedLeftPaneWidth = 500;

	/**
	 * Session-file JSON key under which the shared left-pane width is stored
	 */
	private static final String LEFT_PANE_WIDTH_KEY = "LeftPaneWidth";

	/**
	 * @return the shared project-widget left-pane width in pixels (default 500)
	 */
	public double GetSharedLeftPaneWidth()
	{
		return SharedLeftPaneWidth;
	}

	/**
	 * @param _Width the shared project-widget left-pane width in pixels to persist
	 */
	public void SetSharedLeftPaneWidth(double _Width)
	{
		SharedLeftPaneWidth = _Width;
	}

	/**
	 * Persisted window bounds; -1,-1 means "use platform default position"
	 */
	private int WindowX = -1;
	private int WindowY = -1;
	private int WindowWidth = 800;
	private int WindowHeight = 600;
	private boolean WindowMaximized = false;

	/**
	 * Session-file JSON keys for the persisted main-window bounds
	 */
	private static final String WINDOW_X_KEY = "WindowX";
	private static final String WINDOW_Y_KEY = "WindowY";
	private static final String WINDOW_WIDTH_KEY = "WindowWidth";
	private static final String WINDOW_HEIGHT_KEY = "WindowHeight";
	private static final String WINDOW_MAXIMIZED_KEY = "WindowMaximized";

	public int GetWindowX()
	{
		return WindowX;
	}

	public int GetWindowY()
	{
		return WindowY;
	}

	public int GetWindowWidth()
	{
		return WindowWidth;
	}

	public int GetWindowHeight()
	{
		return WindowHeight;
	}

	public boolean GetWindowMaximized()
	{
		return WindowMaximized;
	}

	/**
	 * @param _X/_Y/_Width/_Height the main-window bounds to persist (X/Y -1 = platform default)
	 */
	public void SetWindowBounds(int _X, int _Y, int _Width, int _Height)
	{
		WindowX = _X;
		WindowY = _Y;
		WindowWidth = _Width;
		WindowHeight = _Height;
	}

	/**
	 * @param _Maximized whether the main window was last shown maximized
	 */
	public void SetWindowMaximized(boolean _Maximized)
	{
		WindowMaximized = _Maximized;
	}

	/**
	 * Opens a repository: validates the path, reuses an already-open GitDir if
	 * present, otherwise creates one, adds it to the open + recent lists and
	 * broadcasts the open event.
	 *
	 * @param _ProjectPath the repository path (any path inside the repo works)
	 * @return the GitDir for the repository, or null if the path is invalid
	 */
	public GitDir TryOpenGitDir(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return null;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		if (!GitDirFunction.CheckGitDirValidity(_GitPath))
			return null;

		var _ExistingIndex = FindOpenGitDirIndexByPath(_GitPath);
		if (_ExistingIndex >= 0)
			return OpenGitDirList.GetGitDirs().get(_ExistingIndex);

		var _Probe = new GitDir(_GitPath);
		OpenGitDirList.GetGitDirs().add(_Probe);
		RecentGitDirList.AddGitDir(_Probe);
		BroadcastIOpenGitDirEvent(_Probe);
		return _Probe;
	}

	/**
	 * Closes a repository: removes it from the open list, stops the
	 * filesystem watcher and broadcasts the close event. The watcher must
	 * be stopped before the broadcast so no stale callback reaches the UI
	 * after the tab is disposed. No-op if the path is not currently open.
	 *
	 * @param _ProjectPath the repository path to close
	 */
	public void TryCloseGitDir(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		var __Index = FindOpenGitDirIndexByPath(_GitPath);

		if (__Index >= 0)
		{
			var __GitDir = OpenGitDirList.GetGitDirs().remove(__Index);
			// Release OS watch handles before broadcasting the close event;
			// the watcher's debounced callback may still be pending on the
			// debounce timer — Stop cancels the timer first.
			__GitDir.StopWatching();
			BroadcastICloseGitDirEvent(__GitDir);
		}
	}

	/**
	 * @return an immutable snapshot of the currently open repositories
	 */
	public List<GitDir> GetOpenGitDirs()
	{
		return List.copyOf(OpenGitDirList.GetGitDirs());
	}

	/**
	 * Serializes the whole session (open/recent repos, window bounds, shared
	 * Stash-window state and shared left-pane width) to ~/.gitalpha/session.json.
	 * Skipped entirely when the serialized root hashes to the last written
	 * value, so repeated calls (focus in/out, tab switches) don't touch the
	 * disk unless something actually changed.
	 */
	public synchronized void SaveSession()
	{
		try
		{
			JSONObject __Root = new JSONObject();
			__Root.put("OpenGitDirList", OpenGitDirList.Serialize());
			__Root.put("RecentGitDirList", RecentGitDirList.Serialize());
			// Persist window bounds (only write position when explicitly set)
			if (WindowX >= 0)
				__Root.put(WINDOW_X_KEY, WindowX);
			if (WindowY >= 0)
				__Root.put(WINDOW_Y_KEY, WindowY);
			__Root.put(WINDOW_WIDTH_KEY, WindowWidth);
			__Root.put(WINDOW_HEIGHT_KEY, WindowHeight);
			__Root.put(WINDOW_MAXIMIZED_KEY, WindowMaximized);
			if (SharedStashWindowState != null)
				__Root.put(STASH_WINDOW_STATE_KEY, SharedStashWindowState.OnSerialize());
			// The left-pane width is a plain scalar, written directly (no wrapper object).
			__Root.put(LEFT_PANE_WIDTH_KEY, SharedLeftPaneWidth);
			String __RootString = __Root.toString(2);
			String __NewHash = ComputeSessionHash(__RootString);

			if (Objects.equals(LastSessionRootHash, __NewHash))
				return;

			Files.createDirectories(SessionFilePath.getParent());
			Files.writeString(SessionFilePath, __RootString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			LastSessionRootHash = __NewHash;
		}
		catch (IOException __Ex)
		{
			System.err.println("Error saving session: " + __Ex.getMessage());
		}
	}

	/**
	 * Restores the session from ~/.gitalpha/session.json: open/recent repos
	 * (invalid ones sanitized out), window bounds, the shared Stash-window
	 * state and the shared left-pane width. No-op when the file is missing or
	 * blank.
	 */
	public synchronized void LoadSession()
	{
		if (!Files.exists(SessionFilePath))
			return;

		try
		{
			String __Raw = Files.readString(SessionFilePath);
			if (__Raw == null || __Raw.isBlank())
				return;

			JSONObject __Root = new JSONObject(__Raw);
			LastSessionRootHash = ComputeSessionHash(__Root.toString(2));
			if (__Root.has("OpenGitDirList"))
			{
				OpenGitDirList.GetGitDirs().clear();
				OpenGitDirList.Deserialize(__Root.getJSONObject("OpenGitDirList"));
				SanitizeGitDirContainer(OpenGitDirList);
			}

			if (__Root.has("RecentGitDirList"))
			{
				RecentGitDirList.GetGitDirs().clear();
				RecentGitDirList.Deserialize(__Root.getJSONObject("RecentGitDirList"));
				SanitizeGitDirContainer(RecentGitDirList);
			}

			// Restore window bounds
			if (__Root.has(WINDOW_X_KEY))
				WindowX = __Root.getInt(WINDOW_X_KEY);
			if (__Root.has(WINDOW_Y_KEY))
				WindowY = __Root.getInt(WINDOW_Y_KEY);
			if (__Root.has(WINDOW_WIDTH_KEY))
				WindowWidth = __Root.getInt(WINDOW_WIDTH_KEY);
			if (__Root.has(WINDOW_HEIGHT_KEY))
				WindowHeight = __Root.getInt(WINDOW_HEIGHT_KEY);
			if (__Root.has(WINDOW_MAXIMIZED_KEY))
				WindowMaximized = __Root.getBoolean(WINDOW_MAXIMIZED_KEY);

			// Restore the shared Stash-window state (a single geometry set for all
			// repositories; the old per-repository map format is intentionally not
			// migrated — stale per-repo geometries are dropped).
			if (__Root.has(STASH_WINDOW_STATE_KEY))
			{
				SharedStashWindowState = new StashWindowState();
				SharedStashWindowState.OnDeserialize(__Root.getJSONObject(STASH_WINDOW_STATE_KEY));
			}

			// Restore the shared left-pane width (a single scalar for all
			// repositories, applied to every project widget).
			if (__Root.has(LEFT_PANE_WIDTH_KEY))
				SharedLeftPaneWidth = __Root.getDouble(LEFT_PANE_WIDTH_KEY);
		}
		catch (Exception __Ex)
		{
			System.err.println("Error loading session: " + __Ex.getMessage());
		}
	}

	/**
	 * Drops repositories whose path is null or no longer a valid git dir (stale session entries)
	 */
	private void SanitizeGitDirContainer(GitDirContainer _Container)
	{
		if (_Container == null)
			return;

		int __i = 0;
		while (__i < _Container.GetGitDirs().size())
		{
			GitDir __GitDir = _Container.GetGitDirs().get(__i);
			if (__GitDir == null || __GitDir.GetGitDirPath() == null)
			{
				_Container.GetGitDirs().remove(__i);
				continue;
			}

			Path __NormalizedPath = GitDirFunction.TryFixGitDirPath(__GitDir.GetGitDirPath());
			if (!GitDirFunction.CheckGitDirValidity(__NormalizedPath))
			{
				_Container.GetGitDirs().remove(__i);
				continue;
			}
			__i++;
		}
	}

	/**
	 * @return the index of the open GitDir matching the path, or -1 if not open
	 */
	private int FindOpenGitDirIndexByPath(Path _SearchPath)
	{
		if (_SearchPath == null)
			return -1;

		for (int i = 0; i < OpenGitDirList.GetGitDirs().size(); ++i)
		{
			var _Existing = OpenGitDirList.GetGitDirs().get(i);
			if (_Existing == null)
				continue;

			Path _ExistingPath = _Existing.GetGitDirPath();
			if (Objects.equals(_ExistingPath, _SearchPath))
				return i;
		}

		return -1;
	}

	/**
	 * Registers an open-event listener (held weakly; no unsubscribe required)
	 */
	public void AddIOpenGitDirEvent(IOpenGitDirEvent _Event)
	{
		OpenGitDirEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregisters an open-event listener (optional — dead references are pruned on broadcast)
	 */
	public void RemoveIOpenGitDirEvent(IOpenGitDirEvent _Event)
	{
		int i = 0;
		while (i < OpenGitDirEventList.size())
		{
			if (Objects.equals(OpenGitDirEventList.get(i).get(), _Event))
			{
				OpenGitDirEventList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Registers a close-event listener (held weakly; no unsubscribe required)
	 */
	public void AddICloseGitDirEvent(ICloseGitDirEvent _Event)
	{
		CloseGitDirEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregisters a close-event listener (optional — dead references are pruned on broadcast)
	 */
	public void RemoveICloseGitDirEvent(ICloseGitDirEvent _Event)
	{
		int i = 0;
		while (i < CloseGitDirEventList.size())
		{
			if (Objects.equals(CloseGitDirEventList.get(i).get(), _Event))
			{
				CloseGitDirEventList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Registers a refresh-event listener (held weakly; no unsubscribe required)
	 */
	public void AddIRefreshGitDirEvent(IRefreshGitDirEvent _Event)
	{
		RefreshGitDirEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregisters a refresh-event listener (optional — dead references are pruned on broadcast)
	 */
	public void RemoveIRefreshGitDirEvent(IRefreshGitDirEvent _Event)
	{
		int i = 0;
		while (i < RefreshGitDirEventList.size())
		{
			if (Objects.equals(RefreshGitDirEventList.get(i).get(), _Event))
			{
				RefreshGitDirEventList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Registers a scanned-files-updated listener (held weakly; no unsubscribe required)
	 */
	public void AddIScannedFilesUpdatedEvent(IScannedFilesUpdatedEvent _Event)
	{
		ScannedFilesUpdatedEventList.add(new WeakReference<>(_Event));
	}

	/**
	 * Unregisters a scanned-files-updated listener (optional — dead references are pruned on broadcast)
	 */
	public void RemoveIScannedFilesUpdatedEvent(IScannedFilesUpdatedEvent _Event)
	{
		int i = 0;
		while (i < ScannedFilesUpdatedEventList.size())
		{
			if (Objects.equals(ScannedFilesUpdatedEventList.get(i).get(), _Event))
			{
				ScannedFilesUpdatedEventList.remove(i);
				break;
			}
			i++;
		}
	}

	/**
	 * Saves the session and broadcasts a refresh event — the single entry point
	 * UI code calls when something user-visible changed (window focus in/out,
	 * tab selection).
	 *
	 * @param _Reason       why the refresh was triggered (for logging/debug)
	 * @param _GitDirTarget the repository to refresh; null refreshes all open ones
	 */
	public void AttemptSaveAndBroadcastRefresh(String _Reason, GitDir _GitDirTarget)
	{
		SaveSession();
		BroadcastIRefreshGitDirEvent(_GitDirTarget, _Reason);
	}

	/**
	 * Notifies every live open-event listener, pruning dead weak references inline
	 */
	private void BroadcastIOpenGitDirEvent(GitDir _GitDirTarget)
	{
		int i = 0;
		while (i < OpenGitDirEventList.size())
		{
			var e = OpenGitDirEventList.get(i);
			if (e.get() != null)
			{
				e.get().Event(_GitDirTarget);
				i++;
			}
			else
			{
				OpenGitDirEventList.remove(i);
			}
		}
	}

	/**
	 * SHA-256 of the session payload; lets SaveSession skip writes when nothing changed
	 */
	private String ComputeSessionHash(String _Payload)
	{
		try
		{
			MessageDigest __Digest = MessageDigest.getInstance("SHA-256");
			byte[] __Bytes = __Digest.digest(_Payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder __Hex = new StringBuilder(__Bytes.length * 2);
			for (byte __Byte : __Bytes)
			{
				__Hex.append(String.format("%02x", __Byte));
			}
			return __Hex.toString();
		}
		catch (NoSuchAlgorithmException __Ex)
		{
			throw new RuntimeException(__Ex);
		}
	}

	/**
	 * Notifies every live close-event listener, pruning dead weak references inline
	 */
	private void BroadcastICloseGitDirEvent(GitDir _GitDirTarget)
	{
		int i = 0;
		while (i < CloseGitDirEventList.size())
		{
			var e = CloseGitDirEventList.get(i);
			if (e.get() != null)
			{
				e.get().Event(_GitDirTarget);
				i++;
			}
			else
			{
				CloseGitDirEventList.remove(i);
			}
		}
	}

	/**
	 * Notifies every live refresh-event listener, pruning dead weak references inline
	 */
	private void BroadcastIRefreshGitDirEvent(GitDir _GitDirTarget, String _Reason)
	{
		int i = 0;
		while (i < RefreshGitDirEventList.size())
		{
			var e = RefreshGitDirEventList.get(i);
			if (e.get() != null)
			{
				e.get().Event(_GitDirTarget, _Reason);
				i++;
			}
			else
			{
				RefreshGitDirEventList.remove(i);
			}
		}
	}

	/**
	 * Notifies every live scanned-files-updated listener, pruning dead weak references inline
	 */
	public void BroadcastIScannedFilesUpdatedEvent(List<FileChange> _UpdatedFiles)
	{
		int i = 0;
		while (i < ScannedFilesUpdatedEventList.size())
		{
			var e = ScannedFilesUpdatedEventList.get(i);
			if (e.get() != null)
			{
				e.get().Event(_UpdatedFiles);
				i++;
			}
			else
			{
				ScannedFilesUpdatedEventList.remove(i);
			}
		}
	}
}

