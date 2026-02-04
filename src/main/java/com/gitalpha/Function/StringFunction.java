package com.gitalpha.Function;

public final class StringFunction
{
    public StringFunction()
    {
    }

    public static String FixCMDString(String _String)
    {
        return _String.trim().replace("\r", "").replace("\n", "").replace("/", "\\");
    }
}
