package com.gitalpha.Engine;

import com.gitalpha.Type.ISerializable;
import com.gitalpha.Type.ESettingEntryType;
import org.json.JSONObject;

public class AlphaSettingEntry implements ISerializable
{
    AlphaSettingEntry(ESettingEntryType Type, String Name, String DisplayName, Object DefaultValue)
    {
        this.Type = Type;
        this.Name = Name;
        this.DisplayName = DisplayName;
        this.DefaultValue = DefaultValue;
        this.Value = DefaultValue;
    }

    private final ESettingEntryType Type;
    private final String Name;
    private final String DisplayName;
    private final Object DefaultValue;
    private Object Value;

    public ESettingEntryType GetType()
    {
        return Type;
    }

    public String GetName()
    {
        return Name;
    }

    public String GetDisplayName()
    {
        return DisplayName;
    }

    public String GetDefaultValue_AsString()
    {
        return (String) DefaultValue;
    }

    public Integer GetDefaultValue_AsInteger()
    {
        return (Integer) DefaultValue;
    }

    public Float GetDefaultValue_AsFloat()
    {
        return (Float) DefaultValue;
    }

    public String GetValue_AsString()
    {
        return (String) Value;
    }

    public Integer GetValue_AsInteger()
    {
        return (Integer) Value;
    }

    public Float GetValue_AsFloat()
    {
        return (Float) Value;
    }

    @Override
    public JSONObject OnSerialize()
    {
        var __JSON = new JSONObject();
        __JSON.put("V", Value);
        return __JSON;
    }

    @Override
    public void OnDeserialize(JSONObject JSON)
    {
        Value = JSON.get("V");
    }
}
