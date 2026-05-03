package com.gitalpha.Engine;

import com.gitalpha.Engine.GitDirContainer.*;
import com.gitalpha.Function.GitDirFunction;
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
		AddOpenGitDirEvent(new OpenGitDirEvent()
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

	private final List<WeakReference<OpenGitDirEvent>> OpenGitDirEventList = new ArrayList<>();

	private final List<WeakReference<CloseGitDirEvent>> CloseGitDirEventList = new ArrayList<>();
	private final List<WeakReference<RefreshGitDirEvent>> RefreshGitDirEventList = new ArrayList<>();
	private final Path SessionFilePath = Path.of(System.getProperty("user.home"), ".gitalpha", "session.json");
	private String LastSessionRootHash = "";

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
		BroadcastOpenGitDirEvent(_Probe);
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
			BroadcastCloseGitDirEvent(__GitDir);
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

	public void AddOpenGitDirEvent(OpenGitDirEvent _Event)
	{
		OpenGitDirEventList.add(new WeakReference<>(_Event));
	}

	public void RemoveOpenGitDirEvent(OpenGitDirEvent _Event)
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

	public void AddCloseGitDirEvent(CloseGitDirEvent _Event)
	{
		CloseGitDirEventList.add(new WeakReference<>(_Event));
	}

	public void RemoveCloseGitDirEvent(CloseGitDirEvent _Event)
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

	public void AddRefreshGitDirEvent(RefreshGitDirEvent _Event)
	{
		RefreshGitDirEventList.add(new WeakReference<>(_Event));
	}

	public void RemoveRefreshGitDirEvent(RefreshGitDirEvent _Event)
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
		BroadcastRefreshGitDirEvent(_GitDirTarget, _Reason);
	}

	private void BroadcastOpenGitDirEvent(GitDir _GitDirTarget)
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

	private void BroadcastCloseGitDirEvent(GitDir _GitDirTarget)
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

	private void BroadcastRefreshGitDirEvent(GitDir _GitDirTarget, String _Reason)
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

