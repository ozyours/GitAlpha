package com.gitalpha.Engine.GitDirContainer.OpenGitDirContainer;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Engine.GitDirContainer.GitDirContainer;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Contain git dir that currently opened
@Deprecated
public class OpenGitDirContainer extends GitDirContainer
{
    public OpenGitDirContainer(AlphaEngine _AlphaEngineParent)
    {
        super(_AlphaEngineParent);
    }

    private List<WeakReference<OpenGitDirEvent>> OpenGitDirEventList = new ArrayList<>();
    private List<WeakReference<CloseGitDirEvent>> CloseGitDirEventList = new ArrayList<>();

    private boolean CheckGitDirValidity(File _GitDir)
    {
        if (_GitDir == null || !_GitDir.exists() || !_GitDir.isDirectory())
            return false;

        File head = new File(_GitDir, "HEAD");
        File config = new File(_GitDir, "config");
        File objects = new File(_GitDir, "objects");
        File refs = new File(_GitDir, "refs");

        return head.isFile() && config.isFile() && objects.isDirectory() && refs.isDirectory();
    }

    private void BroadcastOpenGitDirEvent(OpenGitDirResponse _Response)
    {
        int i = 0;
        while (i < OpenGitDirEventList.size())
        {
            var e = OpenGitDirEventList.get(i);
            if (e.get() != null)
            {
                e.get().Event(_Response);
                i++;
            }
            else
            {
                OpenGitDirEventList.remove(i);
            }
        }
    }

    private void BroadcastCloseGitDirEvent(boolean _Response)
    {
        int i = 0;
        while (i < CloseGitDirEventList.size())
        {
            var e = CloseGitDirEventList.get(i);
            if (e.get() != null)
            {
                e.get().Event(_Response);
                i++;
            }
            else
            {
                CloseGitDirEventList.remove(i);
            }
        }
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

    /**
     * Open a git dir
     *
     * @param _GitPath The .git path
     * @return The open response
     */
    public OpenGitDirResponse OpenGitDir(Path _GitPath)
    {
        if (_GitPath == null)
        {
            var __response = OpenGitDirResponse.FAILED();
            BroadcastOpenGitDirEvent(__response);
            return __response;
        }

        if (!CheckGitDirValidity(_GitPath.toFile()))
        {
            var __response = OpenGitDirResponse.FAILED();
            BroadcastOpenGitDirEvent(__response);
            return __response;
        }

        for (int i = 0; i < GetGitDirs().size(); i++)
        {
            var e = GetGitDirs().get(i);
            if (Objects.equals(e.GetGitPath(), _GitPath))
            {
                var __response = OpenGitDirResponse.REDIRECT(i);
                BroadcastOpenGitDirEvent(__response);
                return __response;
            }
        }

        GetGitDirs().add(new GitDir(_GitPath));
        var __response = OpenGitDirResponse.NEW(GetGitDirs().size() - 1);
        BroadcastOpenGitDirEvent(__response);
        return __response;
    }

    /**
     * Close a git dir
     *
     * @param _GitPath The git path
     * @return True if successfully closed
     */
    public boolean CloseGitDir(Path _GitPath)
    {
        for (int i = 0; i < GetGitDirs().size(); i++)
        {
            var e = GetGitDirs().get(i);
            if (Objects.equals(e.GetGitPath(), _GitPath))
            {
                GetGitDirs().remove(i);
                BroadcastCloseGitDirEvent(true);
                return true;
            }
        }

        BroadcastCloseGitDirEvent(false);
        return false;
    }

    /**
     * Close a git dir
     *
     * @param _Index The index of the git dir to be closed
     * @return True if successfully closed
     */
    public boolean CloseGitDir(int _Index)
    {
        if (_Index < GetGitDirs().size())
        {
            GetGitDirs().remove(_Index);
            BroadcastCloseGitDirEvent(true);
            return true;
        }

        BroadcastCloseGitDirEvent(false);
        return false;
    }
}
