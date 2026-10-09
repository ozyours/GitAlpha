package com.gitalpha.Type;

import java.util.List;

/**
 * A parsed git branch (local or remote): the leaf name, its namespace
 * segments, the remote flag, the precomputed full path — the joined
 * {@code namespace/name} string (e.g. {@code feature/foo}, or
 * {@code origin/feature/foo} for remotes, since a remote's name is its first
 * namespace segment) — and the upstream link of a local branch. The full path
 * is a component rather than a derived method because it is consumed on every
 * keystroke and rebuild (cell rendering, active-branch comparison, activation
 * targeting and filter matching), while the picker's trie build and
 * collapse-all walk read {@link #Namespace()} segment by segment instead of
 * re-splitting the path; the 3- and 4-arg convenience constructors derive the
 * path so callers never build it by hand and it can never disagree with
 * {@link #Name()} / {@link #Namespace()}.
 *
 * @param Name      the leaf branch name (last path segment, e.g. {@code foo})
 * @param Namespace the namespace segments from root to leaf (empty for
 *                  root-level branches; the remote name leads for remotes)
 * @param Remote    true when the branch lives on a remote
 * @param FullPath  the full path (e.g. {@code feature/foo}); prefer the
 *                  convenience constructors, which compute it
 * @param Upstream  the linked remote-tracking branch full path (e.g.
 *                  {@code origin/main}); empty when the local branch has no
 *                  upstream configured — remote branches never carry one
 */
public record GitBranch(String Name, List<String> Namespace, boolean Remote, String FullPath, String Upstream)
{
	/**
	 * Convenience constructor that derives {@link #FullPath()} from the name
	 * and namespace and leaves the branch unlinked — the normal construction
	 * path for branches without upstream data; an empty namespace yields the
	 * bare leaf name.
	 *
	 * @param Name      the leaf branch name
	 * @param Namespace the namespace segments (empty for root-level branches)
	 * @param Remote    true when the branch lives on a remote
	 */
	public GitBranch(String Name, List<String> Namespace, boolean Remote)
	{
		this(Name, Namespace, Remote,
				Namespace.isEmpty() ? Name : String.join("/", Namespace) + "/" + Name,
				"");
	}

	/**
	 * Convenience constructor that derives {@link #FullPath()} and carries an
	 * upstream link — the construction path when the upstream query produced
	 * one ({@code git for-each-ref} merge); the link may be empty to mean
	 * "explicitly unlinked".
	 *
	 * @param Name      the leaf branch name
	 * @param Namespace the namespace segments (empty for root-level branches)
	 * @param Remote    true when the branch lives on a remote
	 * @param Upstream  the linked remote-tracking branch full path ("" = none)
	 */
	public GitBranch(String Name, List<String> Namespace, boolean Remote, String Upstream)
	{
		this(Name, Namespace, Remote,
				Namespace.isEmpty() ? Name : String.join("/", Namespace) + "/" + Name,
				Upstream);
	}

	/**
	 * Join the namespace segments back into a path — the
	 * {@link #FullPath()} prefix before the {@link #Name()}, empty at root
	 * level. Prefer iterating {@link #Namespace()} directly when walking
	 * segment by segment (as the picker's trie build does), so the path is
	 * never re-split.
	 *
	 * @return the namespace path (e.g. {@code feature} or
	 *         {@code origin/feature}), or empty string for root-level branches
	 */
	public String NamespacePath()
	{
		return String.join("/", Namespace);
	}

	/**
	 * Case-insensitive substring match against the full path (namespace
	 * included), used by the shared branch filter of both views — the picker
	 * popover and the branch manager — so filtering on a namespace segment
	 * matches every branch beneath it.
	 *
	 * @param _Filter the filter text (null or blank = match everything)
	 * @return true when the branch matches the filter
	 */
	public boolean Matches(String _Filter)
	{
		return _Filter == null || _Filter.isBlank() || FullPath.toLowerCase().contains(_Filter.toLowerCase());
	}

	/**
	 * @return true when this local branch has an upstream link configured
	 *         (remote branches never do)
	 */
	public boolean HasUpstream()
	{
		return !Upstream.isEmpty();
	}

	@Override
	public String toString()
	{
		return String.format("(Branch: %s, Namespace: %s, Remote: %b, Upstream: %s)",
				Name, Namespace, Remote, Upstream.isEmpty() ? "-" : Upstream);
	}
}
