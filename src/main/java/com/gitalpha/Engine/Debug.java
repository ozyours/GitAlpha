package com.gitalpha.Engine;

public final class Debug
{
    public static final String GeneralCategory = "General";
    public static final String BranchesCategory = "Branches";
    public static final String ChangesCategory = "Changes";

    public static boolean General = Boolean.getBoolean("gitalpha.debug.general");
    public static boolean Branches = Boolean.getBoolean("gitalpha.debug.branches");
    public static boolean Changes = Boolean.getBoolean("gitalpha.debug.changes");

    public static void Log(String _Category, String _Message)
    {
        if (IsCategoryEnabled(_Category))
            System.out.println(_Message);
    }

    public static void Log(String _Category, String _Format, Object... _Args)
    {
        if (IsCategoryEnabled(_Category))
            System.out.printf(_Format, _Args);
    }

    private static boolean IsCategoryEnabled(String _Category)
    {
        return switch (_Category)
        {
            case GeneralCategory -> General;
            case BranchesCategory -> Branches;
            case ChangesCategory -> Changes;
            default -> false;
        };
    }
}
