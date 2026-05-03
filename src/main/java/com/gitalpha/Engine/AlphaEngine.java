package com.gitalpha.Engine;

import com.gitalpha.Function.GitDirFunction;
import com.gitalpha.Engine.GitDirContainer.CloseGitDirEventNew;
import com.gitalpha.Engine.GitDirContainer.GitDirContainer;
import com.gitalpha.Engine.GitDirContainer.OpenGitDirEventNew;
import com.gitalpha.Engine.GitDirContainer.RecentGitDirContainer;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlphaEngine
{
	public static AlphaEngine Instance = new AlphaEngine();

	public AlphaEngine()
	{
		AddOpenGitDirEvent(new OpenGitDirEventNew()
		{
			@Override
			public void Event(GitDir _GitDirTarget)
			{
				// RecentGitDirList
			}
		});
	}

	private AlphaSettings Settings = new AlphaSettings(this);
	private RecentGitDirContainer RecentGitDirList = new RecentGitDirContainer(this);
	private GitDirContainer OpenGitDirList = new GitDirContainer(this);

	public AlphaSettings GetSettings()
	{
		return Settings;
	}

	public RecentGitDirContainer GetRecentGitDirList()
	{
		return RecentGitDirList;
	}

	// public OpenGitDirContainer GetOpenGitDirList()
	// {
	//     return OpenGitDirList;
	// }

	private final List<WeakReference<OpenGitDirEventNew>> OpenGitDirEventList = new ArrayList<>();

	private final List<WeakReference<CloseGitDirEventNew>> CloseGitDirEventList = new ArrayList<>();
	private final Path SessionFilePath = Path.of(System.getProperty("user.home"), ".gitalpha", "session.json");

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

			Files.createDirectories(SessionFilePath.getParent());
			Files.writeString(
					SessionFilePath,
					__Root.toString(2),
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
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

	public void AddOpenGitDirEvent(OpenGitDirEventNew _Event)
	{
		OpenGitDirEventList.add(new WeakReference<>(_Event));
	}

	public void RemoveOpenGitDirEvent(OpenGitDirEventNew _Event)
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

	public void AddCloseGitDirEvent(CloseGitDirEventNew _Event)
	{
		CloseGitDirEventList.add(new WeakReference<>(_Event));
	}

	public void RemoveCloseGitDirEvent(CloseGitDirEventNew _Event)
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
}

