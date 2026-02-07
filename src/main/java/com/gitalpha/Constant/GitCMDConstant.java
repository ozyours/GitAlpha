package com.gitalpha.Constant;

import java.util.List;

public class GitCMDConstant
{
    public static List<String> Changed_Added = List.of("diff", "HEAD", "--name-only", "--diff-filter=A");
    public static List<String> Changed_Modified = List.of("diff", "HEAD", "--name-only", "--diff-filter=M");
    public static List<String> Changed_Removed = List.of("diff", "HEAD", "--name-only", "--diff-filter=D");

    public static List<String> Branches = List.of("branch", "-a");
    public static List<String> Checkout = List.of("checkout");
    public static List<String> Diff_Head_File = List.of("diff", "HEAD");
}
