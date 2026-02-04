package com.gitalpha.Engine.GitDirContainer.OpenGitDirContainer;

@Deprecated
public record OpenGitDirResponse(EOpenGitDirResponseStatus Status, int GitDirIndex)
{
    public static OpenGitDirResponse FAILED()
    {
        return new OpenGitDirResponse(EOpenGitDirResponseStatus.Failed, -1);
    }

    public static OpenGitDirResponse REDIRECT(int Index)
    {
        return new OpenGitDirResponse(EOpenGitDirResponseStatus.Redirect, Index);
    }

    public static OpenGitDirResponse NEW(int Index)
    {
        return new OpenGitDirResponse(EOpenGitDirResponseStatus.New, Index);
    }
}
