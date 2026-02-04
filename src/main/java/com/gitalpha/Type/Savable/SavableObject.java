package com.gitalpha.Type.Savable;

import com.gitalpha.Type.ISerializable;
import org.json.JSONObject;

public class SavableObject implements ISerializable
{
    @Override
    public JSONObject OnSerialize()
    {
        return null;
    }

    @Override
    public void OnDeserialize(JSONObject JSON)
    {

    }
}
