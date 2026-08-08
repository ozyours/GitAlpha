package com.gitalpha.Engine;

import com.gitalpha.Engine.GitDirContainer.*;
import com.gitalpha.Function.GitDirFunction;
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

public class AlphaEngine
{
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

	public final AlphaSettings GetSettings()
	{
		return Settings;
	}

	public final RecentGitDirContainer GetRecentGitDirList()
	{
		return RecentGitDirList;
	}

	public final GitDirContainer GetOpenGitDirList()
	{
		return OpenGitDirList;
	}

	private final List<WeakReference<IOpenGitDirEvent>> OpenGitDirEventList = new ArrayList<>();

	private final List<WeakReference<ICloseGitDirEvent>> CloseGitDirEventList = new ArrayList<>();
	private final List<WeakReference<IRefreshGitDirEvent>> RefreshGitDirEventList = new ArrayList<>();
	private final Path SessionFilePath = Path.of(System.getProperty("user.home"), ".gitalpha", "session.json");
	private String LastSessionRootHash = "";

	/**
	 * Shared Stash-window state: one geometry/maximized/column set used by every
	 * repository's Stash window. It stays null until a stash window has been
	 * opened (and persisted) at least once; the last window to change it wins.
	 */
	private StashWindowState SharedStashWindowState = null;

	private static final String STASH_WINDOW_STATE_KEY = "StashWindowState";

	public StashWindowState GetStashWindowState()
	{
		return SharedStashWindowState;
	}

	public void SetStashWindowState(StashWindowState _State)
	{
		if (_State == null)
			return;
		SharedStashWindowState = _State;
	}

	/** Persisted window bounds; -1,-1 means "use platform default position" */
	private int WindowX = -1;
	private int WindowY = -1;
	private int WindowWidth = 800;
	private int WindowHeight = 600;
	private boolean WindowMaximized = false;

	private static final String WINDOW_X_KEY = "WindowX";
	private static final String WINDOW_Y_KEY = "WindowY";
	private static final String WINDOW_WIDTH_KEY = "WindowWidth";
	private static final String WINDOW_HEIGHT_KEY = "WindowHeight";
	private static final String WINDOW_MAXIMIZED_KEY = "WindowMaximized";

	public int GetWindowX() { return WindowX; }
	public int GetWindowY() { return WindowY; }
	public int GetWindowWidth() { return WindowWidth; }
	public int GetWindowHeight() { return WindowHeight; }
	public boolean GetWindowMaximized() { return WindowMaximized; }

	public void SetWindowBounds(int _X, int _Y, int _Width, int _Height)
	{
		WindowX = _X;
		WindowY = _Y;
		WindowWidth = _Width;
		WindowHeight = _Height;
	}

	public void SetWindowMaximized(boolean _Maximized)
	{
		WindowMaximized = _Maximized;
	}

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

	public void TryCloseGitDir(Path _ProjectPath)
	{
		if (_ProjectPath == null)
			return;

		Path _GitPath = GitDirFunction.TryFixGitDirPath(_ProjectPath);
		var __Index = FindOpenGitDirIndexByPath(_GitPath);

		if (__Index >= 0)
		{
			var __GitDir = OpenGitDirList.GetGitDirs().remove(__Index);
			BroadcastICloseGitDirEvent(__GitDir);
		}
	}

	public List<GitDir> GetOpenGitDirs()
	{
		return List.copyOf(OpenGitDirList.GetGitDirs());
	}

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
		}
		catch (Exception __Ex)
		{
			System.err.println("Error loading session: " + __Ex.getMessage());
		}
	}

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

	public void AddIOpenGitDirEvent(IOpenGitDirEvent _Event)
	{
		OpenGitDirEventList.add(new WeakReference<>(_Event));
	}

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

	public void AddICloseGitDirEvent(ICloseGitDirEvent _Event)
	{
		CloseGitDirEventList.add(new WeakReference<>(_Event));
	}

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

	public void AddIRefreshGitDirEvent(IRefreshGitDirEvent _Event)
	{
		RefreshGitDirEventList.add(new WeakReference<>(_Event));
	}

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

	public void AttemptSaveAndBroadcastRefresh(String _Reason, GitDir _GitDirTarget)
	{
		SaveSession();
		BroadcastIRefreshGitDirEvent(_GitDirTarget, _Reason);
	}

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
}

