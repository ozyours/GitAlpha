package com.gitalpha.Engine.GitDirContainer;

import com.gitalpha.Engine.AlphaEngine;
import com.gitalpha.Engine.GitDir;
import com.gitalpha.Type.ISerializable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GitDirContainer implements ISerializable
{
    public GitDirContainer(AlphaEngine _AlphaEngineParent)
    {
        AlphaEngineParent = _AlphaEngineParent;
    }

    private final AlphaEngine AlphaEngineParent;
    private List<GitDir> GitDirs = new ArrayList<>();

    public AlphaEngine GetAlphaEngineParent()
    {
        return AlphaEngineParent;
    }

    public List<GitDir> GetGitDirs()
    {
        return GitDirs;
    }

    @Override
    public JSONObject OnSerialize()
    {
        var __JSON = new JSONObject();

        // Serialize GitDirs
        {
            JSONArray __Dirs = new JSONArray();
            for (var e : GitDirs)
            {
                __Dirs.put(e.Serialize());
            }
            __JSON.put("D", GitDirs);
        }

        return __JSON;
    }

    @Override
    public void OnDeserialize(JSONObject JSON)
    {

        // Deserialize GitDirs
        {
            JSONArray __Dirs = (JSONArray) JSON.get("D");
            for (var e : __Dirs)
            {
                var __d = new GitDir();
                __d.Deserialize((JSONObject) e);
                GitDirs.add(__d);
            }
        }
    }
}
