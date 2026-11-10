package com.gitalpha.Engine;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link WatchService}-based {@link GitWatcher}: registers every subdirectory of
 * a repository's working tree and reports a change when any of them signals
 * file activity. This is the event-driven mechanism — the OS notifies, so the
 * callback fires shortly after a change with no scan in between.
 * <p>
 * <b>Split with {@link GitWatcher}:</b> the watched {@link GitDir}, the change
 * callback and the trailing-edge debounce (timer plus pending notification) are
 * inherited state, not this class's. What this subclass owns is the
 * {@link WatchService}, the {@link #RegisteredDirs} bookkeeping and the watch
 * thread. No root path is cached either — it is resolved through
 * {@link #GetWatchedRoot()} at {@link #Start()} time.
 * <p>
 * <b>Event handling:</b>
 * <ul>
 *   <li>Folder created  → registered for watching (bookkeeping only, no refresh)</li>
 *   <li>Folder deleted  → unregistered + all child keys cancelled and removed</li>
 *   <li>File created/modified/deleted → debounced refresh callback</li>
 * </ul>
 * <p>
 * The {@code .git} directory and its contents are always skipped to avoid noise
 * from internal git operations. The watch loop runs on a daemon virtual thread
 * so it never blocks the JavaFX thread or prevents JVM shutdown; the debounce
 * timer is the base class's shared one, so the change callback fires on that
 * class's daemon rather than on the watch thread.
 * <p>
 * Usage: construct with a {@link GitDir} and change callback, call {@link #Start()}
 * to begin watching, and {@link #Stop()} to release resources. The base class
 * holds the target and callback, so the constructor here does nothing but chain
 * to it. {@link GitDir} no longer binds this mechanism: {@link GitDir#OnOpen()}
 * creates a {@link GW_GitPoll} instead, and this class remains the event-driven
 * alternative, still switchable back in one line there (it is the only place that
 * names the concrete mechanism).
 * <p>
 * Watching is on by default: {@link #WATCHER_ENABLED} is an opt-out switch, not
 * an opt-in kill switch. It stays switchable because the OS directory handles
 * behind {@link WatchService} keep a watched folder in Windows delete-pending
 * state while it is registered, so a project folder cannot be renamed or
 * deleted until its watcher stops — which is exactly why the default binding is
 * the polling mechanism {@link GW_GitPoll} rather than this class.
 */
public class GW_WatchService extends GitWatcher
{
	/** Git's own state directory; everything below it is filtered out of watch events */
	private static final String GIT_DIR_NAME = ".git";

	/**
	 * Opt-out switch for the filesystem watcher — watching runs unless the JVM
	 * was started with {@code -Dgitalpha.watcher.enabled=false}. The switch
	 * exists because {@link WatchService} registration is not free: the JDK opens
	 * an OS directory handle per registered directory (see
	 * {@code sun.nio.fs.WindowsWatchService}), and a watched folder sits in
	 * delete-pending while that handle is open, so it cannot be renamed or
	 * removed until the watcher stops.
	 * <p>
	 * Read once at class initialization ({@code static final}), so the property
	 * must be on the command line; setting it later has no effect. With watching
	 * off no directory handles are opened, and the app still refreshes on focus
	 * changes, tab switches and every git mutation — only changes made by other
	 * programs go unnoticed until such a refresh.
	 */
	private static final boolean WATCHER_ENABLED =
		Boolean.parseBoolean(System.getProperty("gitalpha.watcher.enabled", "true"));

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/** OS-level watch service; null until a successful {@link #Start()} creates it */
	private volatile WatchService WatchServiceInstance;

	/**
	 * Every registered directory mapped to its {@link WatchKey}, used to cancel
	 * individual keys on directory deletion and to find child keys for cascading
	 * cleanup.
	 * <p>
	 * Not strictly single-threaded: {@link #Start()} seeds the root entry and
	 * {@link #Stop()} clears the map from the caller's thread. A plain
	 * {@link HashMap} is still acceptable because those are point mutations on a
	 * map that is only iterated by the watch loop, and {@link #Stop()} is
	 * best-effort teardown — the subsequent watch-service close releases any OS
	 * handle left behind.
	 */
	private final Map<Path, WatchKey> RegisteredDirs = new HashMap<>();

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	/**
	 * Create a stopped watcher. Both arguments are handed straight to
	 * {@link GitWatcher} — no OS resources are acquired here, and watching only
	 * starts on {@link #Start()}, not at all when the
	 * {@code -Dgitalpha.watcher.enabled=false} opt-out is in effect.
	 *
	 * @param _GitDirTarget the repository to watch; may be path-less at this point
	 *                      because the root is resolved at {@link #Start()} time
	 * @param _OnChange     callback fired when file changes are detected; it is
	 *                      debounced by the base class's shared timer, so it runs
	 *                      on that daemon thread and must marshal to the UI itself
	 */
	public GW_WatchService(GitDir _GitDirTarget, Runnable _OnChange)
	{
		super(_GitDirTarget, _OnChange);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Start watching the directory tree. Resolves the root path from the
	 * {@link GitDir} at call time through {@link #GetWatchedRoot()}, so
	 * session-restored repositories whose path is set after construction are
	 * handled correctly.
	 * <p>
	 * Registers the root directory and opens the base class's debounce timer
	 * synchronously, then registers the remaining subdirectories and enters the
	 * watch loop on a daemon virtual thread — the recursive walk of a large
	 * repository therefore never blocks the calling thread, and the loop blocks on
	 * {@link WatchService#take()} on that same thread. The timer is opened before
	 * the thread starts because every event, including those produced by the
	 * initial walk, schedules through it.
	 * <p>
	 * No-op if the root path does not exist or is not a directory, or if the
	 * {@code -Dgitalpha.watcher.enabled=false} opt-out is in effect (watching is
	 * otherwise on by default). A failed registration is logged to stderr and
	 * leaves the watcher idle instead of throwing.
	 * <p>
	 * Not idempotent: each call creates a fresh {@link WatchService} and a new
	 * watch loop, replacing the fields without closing the previous service, so
	 * call it once per watcher (a second {@link #Start()} after {@link #Stop()} is
	 * the supported re-arm).
	 */
	public void Start()
	{
		// Opt-out switch: no WatchService, no directory keys, no debounce thread —
		// and therefore no OS handle of ours on the repository.
		if (!WATCHER_ENABLED)
			return;

		Path __RootPath = GetWatchedRoot();
		if (__RootPath == null || !Files.isDirectory(__RootPath))
			return;

		try
		{
			WatchServiceInstance = FileSystems.getDefault().newWatchService();
			WatchKey __RootKey = __RootPath.register(WatchServiceInstance,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY);
			RegisteredDirs.put(__RootPath, __RootKey);

			// Opened before the loop starts: every event schedules through it.
			OpenDebounceTimer();

			// Recursive registration + watch loop run on a single daemon virtual thread.
			// RegisterRecursive walks the tree (potentially slow for large repos) but
			// never touches the calling thread.
			Thread.startVirtualThread(() ->
			{
				RegisterRecursive(__RootPath);
				WatchLoop();
			});
		}
		catch (IOException __Ex)
		{
			System.err.println("GW_WatchService: failed to start for " + __RootPath + ": " + __Ex.getMessage());
		}
	}

	/**
	 * Stop watching and release all OS resources. Safe to call multiple times
	 * and from any thread, including on a watcher that never started. The
	 * inherited debounce timer is closed first, so a queued notification cannot
	 * fire after the stop, and the registry is cleared, so {@link #IsRunning()}
	 * reports false at once. Individual keys are not cancelled one by one: closing
	 * the {@link WatchService} releases all of them, and that close runs on a
	 * background thread because on Windows it tears down one notification handle
	 * per directory and can be slow for large trees — the blocked
	 * {@link WatchService#take()} then ends the watch loop.
	 */
	public void Stop()
	{
		CloseDebounceTimer();
		RegisteredDirs.clear();

		// Close the WatchService off the calling thread — on Windows this
		// calls FindCloseChangeNotification for every registered directory,
		// which can be slow for large trees.
		WatchService __Service = WatchServiceInstance;
		if (__Service != null)
		{
			Thread.startVirtualThread(() ->
			{
				try { __Service.close(); }
				catch (IOException __Ex) { /* best-effort cleanup */ }
			});
		}
	}

	/**
	 * Report whether watching is live. Derived from the watcher's own state
	 * rather than a flag, so a watcher that never started (including one opted
	 * out of via {@code -Dgitalpha.watcher.enabled=false}) and one whose last
	 * watched directory has disappeared both report false without an explicit
	 * {@link #Stop()}.
	 * <p>
	 * Reads the registry without locking while the watch thread mutates it, so
	 * treat the answer as the state at the moment of the call.
	 *
	 * @return true while a watch service exists and at least one directory is registered
	 */
	public boolean IsRunning()
	{
		return WatchServiceInstance != null && !RegisteredDirs.isEmpty();
	}

	// -------------------------------------------------------------------------
	// Watch loop
	// -------------------------------------------------------------------------

	/**
	 * Blocking loop that processes {@link WatchKey} signals. Dispatches events
	 * to folder bookkeeping (register/unregister) or file-change refresh
	 * (debounced callback). Exits when the watch service is closed, when the
	 * thread is interrupted, or after a key invalidated by its directory's
	 * deletion leaves no registered directories behind.
	 * <p>
	 * Runs on the daemon virtual thread started by {@link #Start()}; the registry
	 * is only iterated here. A refresh request is handed off with
	 * {@link #ScheduleChange()}, so the change callback lands on the base class's
	 * debounce daemon and never on this thread.
	 */
	private void WatchLoop()
	{
		try
		{
			while (true)
			{
				WatchKey __Key;
				try
				{
					__Key = WatchServiceInstance.take();
				}
				catch (InterruptedException __Ex)
				{
					Thread.currentThread().interrupt();
					return;
				}
				catch (ClosedWatchServiceException __Ex)
				{
					return;
				}

				for (WatchEvent<?> __Event : __Key.pollEvents())
				{
					WatchEvent.Kind<?> __Kind = __Event.kind();
					if (__Kind == StandardWatchEventKinds.OVERFLOW)
						continue;

					@SuppressWarnings("unchecked")
					WatchEvent<Path> __PathEvent = (WatchEvent<Path>) __Event;
					Path __FileName = __PathEvent.context();

					// Ignore changes inside .git (staging, ref updates, etc.)
					if (__FileName != null && __FileName.toString().equals(GIT_DIR_NAME))
						continue;

					Path __ParentDir = (Path) __Key.watchable();
					Path __Resolved = __ParentDir.resolve(__FileName);

					if (__Kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(__Resolved))
					{
						// Folder added → register only, no refresh
						RegisterDirectory(__Resolved);
					}
					else if (__Kind == StandardWatchEventKinds.ENTRY_DELETE && RegisteredDirs.containsKey(__Resolved))
					{
						// Folder removed → cancel this key + all children, remove from map
						UnregisterDirectory(__Resolved);
					}
					else
					{
						// File created / modified / deleted → trigger refresh
						ScheduleChange();
					}
				}

				boolean __Valid = __Key.reset();
				if (!__Valid)
				{
					// This key's directory was deleted or moved.
					// Remove from map and clean up any children.
					Path __DeletedPath = null;
					for (var __Entry : RegisteredDirs.entrySet())
					{
						if (__Entry.getValue() == __Key)
						{
							__DeletedPath = __Entry.getKey();
							break;
						}
					}
					if (__DeletedPath != null)
						UnregisterDirectory(__DeletedPath);

					// If no directories remain, stop watching.
					if (RegisteredDirs.isEmpty())
						return;
				}
			}
		}
		catch (Exception __Ex)
		{
			__Ex.printStackTrace();
		}
	}

	// -------------------------------------------------------------------------
	// Directory registration
	// -------------------------------------------------------------------------

	/**
	 * Register a single directory with the watch service and track it in
	 * {@link #RegisteredDirs}. Each directory gets its own {@link WatchKey}
	 * that monitors CREATE, DELETE, and MODIFY events.
	 * Duplicate registrations are skipped, so the existing key is kept.
	 * <p>
	 * Called from the watch thread only (the initial walk and the
	 * {@code ENTRY_CREATE} handler). Registration is best-effort: a directory
	 * deleted between discovery and registration is ignored rather than reported,
	 * so a delete racing the walk cannot fail watching setup.
	 *
	 * @param _Dir existing directory to watch; a path deleted mid-walk is ignored
	 */
	private void RegisterDirectory(Path _Dir)
	{
		if (RegisteredDirs.containsKey(_Dir))
			return;

		try
		{
			WatchKey __Key = _Dir.register(WatchServiceInstance,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY);
			RegisteredDirs.put(_Dir, __Key);
		}
		catch (IOException __Ex)
		{
			// Directory may have been deleted between discovery and registration.
		}
	}

	/**
	 * Unregister a directory and all its children from the watch service.
	 * Cancels each {@link WatchKey} so the watch service can release the
	 * underlying OS notification handles, and removes the entries from
	 * {@link #RegisteredDirs}. Children are found by prefix matching against
	 * the deleted path — {@link Path#startsWith} compares whole name segments, so
	 * a sibling such as {@code src2} is not swept up by the removal of {@code src}.
	 * <p>
	 * Runs on the watch thread; the child paths are collected before removal so
	 * the iteration does not modify the map it reads.
	 *
	 * @param _DirPath the directory that was deleted
	 */
	private void UnregisterDirectory(Path _DirPath)
	{
		// Cancel the key for the deleted directory itself.
		WatchKey __Key = RegisteredDirs.remove(_DirPath);
		if (__Key != null)
			__Key.cancel();

		// Cancel keys for any child directories (cascading deletion).
		// Collect first to avoid ConcurrentModificationException.
		var __Children = RegisteredDirs.keySet().stream()
			.filter(__P -> __P.startsWith(_DirPath))
			.toList();

		for (var __Child : __Children)
		{
			WatchKey __ChildKey = RegisteredDirs.remove(__Child);
			if (__ChildKey != null)
				__ChildKey.cancel();
		}
	}

	// -------------------------------------------------------------------------
	// Recursive directory registration
	// -------------------------------------------------------------------------

	/**
	 * Walk the directory tree from the given root and register every subdirectory
	 * that is not inside {@code .git}. Directories created after this walk
	 * completes are picked up by the {@code ENTRY_CREATE} handler in the
	 * watch loop.
	 * <p>
	 * Also on the watch thread, immediately before {@link #WatchLoop()}. The walk
	 * is best-effort: if it fails partway the root key is already registered, so
	 * watching continues and only the unvisited subtrees stay unwatched.
	 *
	 * @param _Root repository working-tree root, already registered by {@link #Start()}
	 */
	private void RegisterRecursive(Path _Root)
	{
		try (var __Walker = Files.walk(_Root))
		{
			__Walker.filter(__P -> Files.isDirectory(__P) && !__P.equals(_Root))
				.filter(__P -> !IsInsideGitDir(__P, _Root))
				.forEach(this::RegisterDirectory);
		}
		catch (IOException __Ex)
		{
			// Best-effort: some directories may have been deleted during the walk.
		}
	}

	/**
	 * Decide whether a path sits inside a {@code .git} directory. Matching whole
	 * path segments (not a substring) is what keeps names such as
	 * {@code .gitignore} inside the watch set.
	 *
	 * @param _Path     path to test; expected to be below {@code _RepoRoot}
	 * @param _RepoRoot repository working-tree root, used to relativize the path
	 * @return true if any segment of the relative path is {@code .git}
	 */
	private static boolean IsInsideGitDir(Path _Path, Path _RepoRoot)
	{
		Path __Relative = _RepoRoot.relativize(_Path);
		for (Path __Segment : __Relative)
		{
			if (__Segment.toString().equals(GIT_DIR_NAME))
				return true;
		}
		return false;
	}
}
