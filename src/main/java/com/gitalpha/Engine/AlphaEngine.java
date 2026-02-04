package com.gitalpha.Engine;

import com.gitalpha.Engine.GitDirContainer.CloseGitDirEventNew;
import com.gitalpha.Engine.GitDirContainer.OpenGitDirContainer.CloseGitDirEvent;
import com.gitalpha.Engine.GitDirContainer.OpenGitDirEventNew;
import com.gitalpha.Engine.GitDirContainer.RecentGitDirContainer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlphaEngine
{
    public static AlphaEngine Instance = new AlphaEngine();

    private AlphaSettings Settings = new AlphaSettings(this);
    private RecentGitDirContainer RecentGitDirList = new RecentGitDirContainer(this);
//    private OpenGitDirContainer OpenGitDirList = new OpenGitDirContainer(this);

    public AlphaSettings GetSettings()
    {
        return Settings;
    }

    public RecentGitDirContainer GetRecentGitDirList()
    {
        return RecentGitDirList;
    }

//    public OpenGitDirContainer GetOpenGitDirList()
//    {
//        return OpenGitDirList;
//    }

    private List<WeakReference<OpenGitDirEventNew>> OpenGitDirEventList = new ArrayList<>();

    private List<WeakReference<CloseGitDirEventNew>> CloseGitDirEventList = new ArrayList<>();

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

    public void BroadcastOpenGitDirEvent(GitDir _GitDirTarget)
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
//                e.get().Event(_Response);
                i++;
            }
            else
            {
                CloseGitDirEventList.remove(i);
            }
        }
    }
}

