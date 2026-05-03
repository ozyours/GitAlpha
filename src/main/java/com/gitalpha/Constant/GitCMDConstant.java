package com.gitalpha.Constant;

import java.util.List;

public class GitCMDConstant
{
    public static List<String> Changed_Unstaged_Added = List.of("diff", "--name-only", "--diff-filter=A");
    public static List<String> Changed_Unstaged_Modified = List.of("diff", "--name-only", "--diff-filter=M");
    public static List<String> Changed_Unstaged_Removed = List.of("diff", "--name-only", "--diff-filter=D");
    public static List<String> Changed_Unstaged_Untracked = List.of("ls-files", "--others", "--exclude-standard");

    public static List<String> Changed_Staged_Added = List.of("diff", "--cached", "--name-only", "--diff-filter=A");
    public static List<String> Changed_Staged_Modified = List.of("diff", "--cached", "--name-only", "--diff-filter=M");
    public static List<String> Changed_Staged_Removed = List.of("diff", "--cached", "--name-only", "--diff-filter=D");

    public static List<String> Branches = List.of("branch", "-a");
    public static List<String> Checkout = List.of("checkout");
    public static List<String> Diff_Unstaged_File = List.of("diff");
    public static List<String> Diff_Staged_File = List.of("diff", "--cached");
}
