package com.gitalpha.Engine;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches a directory tree for filesystem changes using {@link WatchService}.
 * Designed to monitor a {@link GitDir}'s working-tree root — registers every
 * subdirectory recursively, tracks all registrations in a map, and cleans up
 * properly when directories are deleted.
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
 * so it never blocks the JavaFX thread or prevents JVM shutdown.
 * <p>
 * Usage: construct with a {@link GitDir} and change callback, call {@link #Start()}
 * to begin watching, and {@link #Stop()} to release resources.
 */
public class GitWatcher
{
	private static final String GIT_DIR_NAME = ".git";

	/**
	 * Debounce interval: filesystem events within this window are coalesced
	 * into a single callback. Prevents overwhelming downstream consumers
	 * during rapid changes (e.g. checkout, rebase).
	 */
	private static final long DEBOUNCE_MILLIS = 500;

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/** The repository whose working tree is being watched */
	private final GitDir GitDirTarget;

	/** Callback fired (on the debounce timer thread) when file changes are detected */
	private final Runnable OnChange;

	/** OS-level watch service; null until {@link #Start()} is called */
	private volatile WatchService WatchServiceInstance;

	/**
	 * Every registered directory mapped to its {@link WatchKey}.
	 * Used to cancel individual keys on directory deletion and to find
	 * child keys for cascading cleanup. Only accessed from the watch-loop
	 * thread (single-threaded), so a plain {@link HashMap} suffices.
	 */
	private final Map<Path, WatchKey> RegisteredDirs = new HashMap<>();

	/** Single-threaded timer for debouncing rapid filesystem events */
	private final ScheduledExecutorService DebounceTimer =
		Executors.newSingleThreadScheduledExecutor(__R ->
		{
			Thread __T = new Thread(__R, "GitWatcher-debounce");
			__T.setDaemon(true);
			return __T;
		});

	/** Handle to the currently pending debounced callback; non-null while a debounce window is active */
	private volatile ScheduledFuture<?> PendingDebounce;

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	/**
	 * @param _GitDirTarget the repository to watch (root path resolved at {@link #Start()} time)
	 * @param _OnChange     callback fired when file changes are detected (debounced)
	 */
	public GitWatcher(GitDir _GitDirTarget, Runnable _OnChange)
	{
		GitDirTarget = _GitDirTarget;
		OnChange = _OnChange;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * @return the {@link GitDir} this watcher is monitoring
	 */
	public GitDir GetGitDirTarget()
	{
		return GitDirTarget;
	}

	/**
	 * Start watching the directory tree. Resolves the root path from the
	 * {@link GitDir} at call time (not construction time), so session-restored
	 * repositories whose path is set after construction are handled correctly.
	 * <p>
	 * Registers the root directory immediately, then recursively registers all
	 * existing subdirectories on a daemon virtual thread so the calling thread
	 * is never blocked. The watch loop blocks on {@link WatchService#take()}
	 * on that same thread.
	 * <p>
	 * No-op if the root path does not exist or is not a directory.
	 */
	public void Start()
	{
		Path __RootPath = GitDirTarget.GetRepoRootPath();
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
			System.err.println("GitWatcher: failed to start for " + __RootPath + ": " + __Ex.getMessage());
		}
	}

	/**
	 * Stop watching and release all OS resources. Safe to call multiple times
	 * and from any thread. The debounce timer is shut down immediately;
	 * the {@link WatchService} close runs on a background thread to avoid
	 * blocking the caller while the OS tears down notification handles.
	 */
	public void Stop()
	{
		DebounceTimer.shutdownNow();
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
	 * @return true if this watcher has been started and not yet stopped
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
	 * (debounced callback). Exits when the watch service is closed, all keys
	 * are cancelled, or the thread is interrupted.
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
						ScheduleDebouncedCallback();
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
	 * Duplicate registrations are skipped.
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
	 * Cancels each {@link WatchKey} (releasing the OS handle) and removes
	 * the entries from {@link #RegisteredDirs}. Children are found by
	 * prefix matching against the deleted path.
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
	 * @return true if the given path is inside a {@code .git} directory
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

	// -------------------------------------------------------------------------
	// Debounced callback
	// -------------------------------------------------------------------------

	/**
	 * Schedule or reschedule a debounced callback. Each new filesystem event
	 * resets the timer so rapid bursts produce a single invocation of
	 * {@link #OnChange}.
	 */
	private void ScheduleDebouncedCallback()
	{
		ScheduledFuture<?> __Old = PendingDebounce;
		if (__Old != null)
			__Old.cancel(false);

		PendingDebounce = DebounceTimer.schedule(OnChange, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
	}
}
