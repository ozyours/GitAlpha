package com.gitalpha.UI.GitDirProjectManager;

import com.gitalpha.Type.GitBranch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shared namespace-tree machinery for the branch views: builds the flat
 * {@link BranchListRow} list (headers + branch leaves) from a set of
 * branches, and owns the collapse-set operations (toggle / collapse-all).
 * Both {@link BranchPickerPopover} (local + remote sections in one list) and
 * {@link BranchManagerWidget} (one column per side) call into it, so the
 * trie semantics — hierarchical per-segment headers, namespaces pinned above
 * sibling branches, filter/collapse interaction — stay identical everywhere.
 * <p>
 * The state lives in the caller: rows are a pure function of
 * (entries, filter, collapse set), and the caller rebuilds its observable
 * list after any mutation. Rendering constants are shared because both cell
 * factories indent and draw arrows the same way.
 */
final class BranchTree
{
	/** Left indent (px) applied per tree depth level for headers and branch leaves */
	static final double TREE_INDENT = 14;
	/** Unicode right-pointing triangle shown on collapsed namespace headers */
	static final String ARROW_COLLAPSED = "\u25B8"; // "▸"
	/** Unicode down-pointing triangle shown on expanded namespace headers */
	static final String ARROW_EXPANDED = "\u25BE"; // "▾"

	private BranchTree()
	{
	}

	/**
	 * One node of the namespace trie built for a section: a path segment,
	 * its full path from the section root (the collapse key), child nodes
	 * (sorted alphabetically via {@link TreeMap}) and the branch leaves that
	 * terminate at this node.
	 */
	private static final class NamespaceNode
	{
		/** This level's path segment ("" for the root pseudo-node) */
		final String Segment;
		/** Full path from the root ("" for the root pseudo-node) */
		final String FullPath;
		/** Child nodes keyed by segment; TreeMap iteration = alphabetical */
		final Map<String, NamespaceNode> Children = new TreeMap<>();
		/** Branches whose namespace path ends exactly at this node */
		final List<GitBranch> Leaves = new ArrayList<>();

		/**
		 * Create a node for one path segment.
		 *
		 * @param _Segment   this level's segment ("" for the root pseudo-node)
		 * @param _FullPath the path from the trie root (the collapse key)
		 */
		NamespaceNode(String _Segment, String _FullPath)
		{
			Segment = _Segment;
			FullPath = _FullPath;
		}
	}

	// ------------------------------------------------------------------
	//  Row building
	// ------------------------------------------------------------------

	/**
	 * Build the hierarchical namespace-grouped rows for one group of
	 * branches: trie construction, then a depth-first emission walk.
	 * Grouping rules:
	 * <ul>
	 *   <li>Branches are grouped into a hierarchical namespace trie: every
	 *       path segment becomes its own header, so {@code a/b/c} nests
	 *       {@code a} → {@code b} → leaf {@code c} instead of one flat
	 *       {@code a/b} header.</li>
	 *   <li>Root-level branches (empty namespace) appear after the
	 *       namespace groups, in input order, without a header.</li>
	 *   <li>At every node: child groups first (alphabetical), then that
	 *       node's own leaves (input order) — namespaces are pinned above
	 *       the branches sharing their parent.</li>
	 *   <li>A header is shown when its full path passes the filter or any
	 *       descendant does. When the header's own path matches, the whole
	 *       subtree is shown; otherwise only matching descendants appear.</li>
	 *   <li>Collapse state hides a header's entire subtree but keeps the
	 *       header visible — but only when no filter is active (filtering
	 *       overrides collapse so matches are never hidden).</li>
	 * </ul>
	 *
	 * @param _Entries    the branches to group (one side: local or remote)
	 * @param _FilterText raw filter text (null/blank = no filter; trimmed
	 *                    and lower-cased internally)
	 * @param _Collapsed  the collapse keys (full namespace paths) currently
	 *                    collapsed — caller-owned, only read here
	 * @return the flat row list (headers + branch rows); never null
	 */
	static List<BranchListRow> BuildRows(List<GitBranch> _Entries, String _FilterText, Set<String> _Collapsed)
	{
		// Normalize the filter once; all comparisons below use __FilterLower.
		String __Filter = _FilterText == null ? "" : _FilterText.trim();
		boolean __HasFilter = !__Filter.isEmpty();
		String __FilterLower = __HasFilter ? __Filter.toLowerCase() : "";

		// ── Build the trie ──
		// The root pseudo-node collects root-level branches (no namespace);
		// every path segment becomes a nested node. GitBranch already carries
		// its segments, so the walk iterates Namespace() directly — no
		// join-then-split round trip.
		NamespaceNode __Root = new NamespaceNode("", "");
		for (GitBranch __Entry : _Entries)
		{
			if (__Entry.Namespace().isEmpty())
			{
				__Root.Leaves.add(__Entry);
				continue;
			}
			NamespaceNode __Cursor = __Root;
			StringBuilder __Path = new StringBuilder();
			for (String __Segment : __Entry.Namespace())
			{
				if (__Path.length() > 0)
					__Path.append('/');
				__Path.append(__Segment);
				final String __Seg = __Segment;
				final String __FullPath = __Path.toString();
				__Cursor = __Cursor.Children.computeIfAbsent(__Seg,
						__K -> new NamespaceNode(__Seg, __FullPath));
			}
			__Cursor.Leaves.add(__Entry);
		}

		// ── Emit depth-first ──
		List<BranchListRow> __Rows = new ArrayList<>();
		EmitChildren(__Root, 0, false, __HasFilter, __FilterLower, _Collapsed, __Rows);
		return __Rows;
	}

	/**
	 * Depth-first emission of one trie node's children: its child groups
	 * first (alphabetically — namespaces are pinned above the node's own
	 * branches), then its branch leaves (input order). Each child header is
	 * emitted at {@code _Depth} and its own children recurse at
	 * {@code _Depth + 1}, so indentation grows one level per path segment.
	 *
	 * @param _Node        the trie node whose children are emitted
	 * @param _Depth       tree level of this call's rows (0 = group top)
	 * @param _ForceShow   true when this node's or an ancestor's full path
	 *                      matched the filter (show the whole subtree
	 *                      regardless of per-leaf matches)
	 * @param _HasFilter   true when a non-empty filter is active
	 * @param _FilterLower the lower-cased filter text ("" when no filter)
	 * @param _Collapsed   the caller's collapse keys (read only)
	 * @param _Rows        the row list being built
	 */
	private static void EmitChildren(NamespaceNode _Node, int _Depth, boolean _ForceShow,
			boolean _HasFilter, String _FilterLower, Set<String> _Collapsed, List<BranchListRow> _Rows)
	{
		// Child groups first, alphabetical (TreeMap order): nested namespaces
		// sit above this node's own branches so the tree reads top-down.
		for (NamespaceNode __Child : _Node.Children.values())
		{
			// The child's own path matching forces its whole subtree visible.
			boolean __ChildForce = _ForceShow
					|| (_HasFilter && __Child.FullPath.toLowerCase().contains(_FilterLower));
			// Hide the group entirely when nothing beneath it matches.
			if (_HasFilter && !__ChildForce && !SubtreeHasMatch(__Child, _FilterLower))
				continue;

			_Rows.add(BranchListRow.Namespace(__Child.Segment, __Child.FullPath, _Depth));

			// Collapse hides the whole subtree — but only without a filter.
			boolean __Collapsed = !_HasFilter && _Collapsed.contains(__Child.FullPath);
			if (__Collapsed)
				continue;

			EmitChildren(__Child, _Depth + 1, __ChildForce, _HasFilter, _FilterLower, _Collapsed, _Rows);
		}

		// This node's own branch leaves last: force-shown with the subtree,
		// or individually matched against the filter.
		for (GitBranch __Leaf : _Node.Leaves)
		{
			if (!_HasFilter || _ForceShow || __Leaf.Matches(_FilterLower))
				_Rows.add(BranchListRow.Branch(__Leaf, _Depth));
		}
	}

	/**
	 * @param _Node        the trie node to test
	 * @param _FilterLower the lower-cased filter text
	 * @return true when any branch leaf anywhere in the node's subtree
	 *         matches the filter
	 */
	private static boolean SubtreeHasMatch(NamespaceNode _Node, String _FilterLower)
	{
		for (GitBranch __Leaf : _Node.Leaves)
		{
			if (__Leaf.Matches(_FilterLower))
				return true;
		}
		for (NamespaceNode __Child : _Node.Children.values())
		{
			if (SubtreeHasMatch(__Child, _FilterLower))
				return true;
		}
		return false;
	}

	// ------------------------------------------------------------------
	//  Collapse-set operations (caller rebuilds afterwards)
	// ------------------------------------------------------------------

	/**
	 * Toggle the collapse state of one namespace header. Key by the header's
	 * <em>full</em> path, not its displayed segment, so identical segment
	 * names under different parents keep separate collapse state.
	 *
	 * @param _Collapsed the caller's collapse set (mutated)
	 * @param _Namespace the full namespace path acting as the collapse key
	 */
	static void ToggleNamespace(Set<String> _Collapsed, String _Namespace)
	{
		if (!_Collapsed.remove(_Namespace))
			_Collapsed.add(_Namespace);
	}

	/**
	 * Collapse every namespace recursively: marks every namespace path
	 * present in {@code _Entries} as collapsed. Only the headers remain
	 * visible — each with its collapsed arrow — and root-level branches (no
	 * namespace) stay listed, since they have no header to hide behind.
	 * Stale keys from earlier branch sets are kept; they are simply never
	 * looked up again by {@link #BuildRows}.
	 *
	 * @param _Entries  the branches whose namespaces should collapse
	 * @param _Collapsed the caller's collapse set (mutated)
	 */
	static void CollapseAll(List<GitBranch> _Entries, Set<String> _Collapsed)
	{
		for (GitBranch __Entry : _Entries)
		{
			if (__Entry.Namespace().isEmpty())
				continue;
			// Mark every prefix: "a/b/c" collapses "a", "a/b" and "a/b/c".
			String __PathSoFar = "";
			for (String __Segment : __Entry.Namespace())
			{
				__PathSoFar = __PathSoFar.isEmpty() ? __Segment : __PathSoFar + "/" + __Segment;
				_Collapsed.add(__PathSoFar);
			}
		}
	}
}
