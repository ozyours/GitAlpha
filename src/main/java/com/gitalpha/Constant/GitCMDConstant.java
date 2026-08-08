package com.gitalpha.Constant;

import java.util.List;

/**
 * Central registry of raw git command fragments. Constants are
 * {@code List<String>} command prefixes — callers append the concrete
 * arguments (refs, paths, messages) before execution. Backed by
 * {@code List.of()} (immutable in practice), but {@code GitOperator}
 * defensively copies commands before running them.
 */
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

    public static List<String> Stash_List = List.of("stash", "list");
    public static List<String> Stash_Push = List.of("stash", "push");
    public static List<String> Stash_Pop = List.of("stash", "pop");
    public static List<String> Stash_Drop = List.of("stash", "drop");
    public static List<String> Stash_Apply = List.of("stash", "apply");
    public static List<String> Stash_Show_NameStatus = List.of("stash", "show", "--name-status");
    // `git stash show -p` accepts no pathspec, so per-file diffs diff the stash
    // against its parent commit instead: `git diff <stash>^ <stash> -- <path>`.
    // The two revisions and the pathspec are appended dynamically by the caller.
    public static List<String> Stash_Diff_File = List.of("diff");
    // Commit-preserving stash rename (git has no native stash rename). The
    // caller runs these in order, appending per-command arguments:
    //   Stash_RevParse        resolve <stash> to its commit hash
    //   Stash_RevList_Parents print that commit followed by its parents on one
    //                         line (-n 1); the first token is the commit itself,
    //                         the rest are the parents (a stash commit can have
    //                         up to three: HEAD, index state, untracked)
    //   Stash_CommitTree      create a new commit reusing the same tree and
    //                         parents with the new message (caller appends
    //                         <hash>^{tree}, -p <parent> per parent, -m <msg>)
    //   Stash_Store           register the new commit as a stash (becomes stash@{0})
    // The sequence finishes with a Stash_Drop of the original, which the store
    // shifted to stash@{N+1}.
    public static List<String> Stash_RevParse = List.of("rev-parse");
    public static List<String> Stash_RevList_Parents = List.of("rev-list", "--parents", "-n", "1");
    public static List<String> Stash_CommitTree = List.of("commit-tree");
    public static List<String> Stash_Store = List.of("stash", "store");
}
