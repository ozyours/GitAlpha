package com.gitalpha.Engine;

import com.gitalpha.Type.ISerializable;
import com.gitalpha.Type.ESettingEntryType;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlphaSettings implements ISerializable
{
    public static AlphaSettings Get()
    {
        return AlphaEngine.Instance.GetSettings();
    }

    public AlphaSettings(AlphaEngine _AlphaEngineParent)
    {
        AlphaEngineParent = _AlphaEngineParent;

        Settings = new ArrayList<>();
        Settings.add(new AlphaSettingEntry(ESettingEntryType.String, GitPathName, "Git Path", "git.exe"));
        Settings.add(new AlphaSettingEntry(ESettingEntryType.Integer, RecentSizeName, "Recent Files Count", 8));
        Settings.add(new AlphaSettingEntry(ESettingEntryType.Integer, TabMaxSize, "Tab Max Size", 150));
    }

    private final AlphaEngine AlphaEngineParent;
    private final List<AlphaSettingEntry> Settings;

    public static final String GitPathName = "GitPath";
    public static final String RecentSizeName = "RecentSize";
    public static final String TabMaxSize = "TabMaxSize";

    public AlphaEngine GetAlphaEngineParent()
    {
        return AlphaEngineParent;
    }

    // Get the whole settings
    public List<AlphaSettingEntry> GetSettings()
    {
        return Settings;
    }

    // Get setting entry by name
    public AlphaSettingEntry GetSettingEntry(String Name)
    {
        for (var e : Settings)
        {
            if (Objects.equals(e.GetName(), Name))
                return e;
        }
        return null;
    }

    @Override
    public JSONObject OnSerialize()
    {
        var __JSON = new JSONObject();

        for (var e : Settings)
        {
            __JSON.put(e.GetName(), e.Serialize());
        }

        return __JSON;
    }

    @Override
    public void OnDeserialize(JSONObject JSON)
    {
        for (var e : Settings)
        {
            e.Deserialize((JSONObject) JSON.get(e.GetName()));
        }
    }
}
