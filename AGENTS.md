# GitAlpha — Agent Guide

## Build

```sh
mvn compile                    # compile
mvn verify                     # compile + package (shaded fat jar via maven-shade-plugin)
```

- Java 21, JavaFX 21, no test framework, no formatter/linter config
- Single-module Maven project; fat-jar main class: `com.gitalpha.UI.MainJavaFx`
- Only dependency beyond JavaFX: `org.json:json:20250517`
- JavaFX classifier `win` — **Windows-only build**
- JPMS: `src/main/java/module-info.java` declares module `com.gitalpha` (requires JavaFX + org.json; `opens` UI packages to `javafx.graphics`)
- No `src/test` tests exist (empty directory); Maven wrapper not present (use system `mvn`)

## Entrypoint

```
MainJavaFx.main() → launch() → start(Stage)
  ├─ AlphaEngine.Instance.LoadSession()        // ~/.gitalpha/session.json → open/recent repos + window bounds
  ├─ new AlphaUI()                             // sets static AlphaUI.Instance; StackPane holding a TabPane
  ├─ Scene(AlphaUI.Instance, 800, 600)         // stage title "Git Alpha"
  └─ stop(): AlphaEngine.Instance.SaveSession()
```

- `com.gitalpha.Main` has an **empty** `main()` — **not** the real entrypoint; use `MainJavaFx`
- Window position/size/maximized are persisted via stage property listeners (position skipped while maximized)
- Window focus in/out and tab selection fire `AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh(reason, gitDirOrNull)` — a `null` target refreshes **all** open project managers

## Architecture

```
AlphaEngine (singleton: AlphaEngine.Instance — never construct a second)
  ├─ AlphaSettings: GitPath ("git.exe"), RecentSize (8), TabMaxSize (150)
  ├─ GitDirContainer OpenGitDirList / RecentGitDirContainer RecentGitDirList (recent list capped + deduped)
  ├─ Session: ~/.gitalpha/session.json — SHA-256 hash skips redundant writes;
  │    stores open/recent repos + window bounds; invalid repos sanitized on load
  └─ Event lists (WeakReference, pruned on dead refs):
       IOpenGitDirEvent / ICloseGitDirEvent / IRefreshGitDirEvent   (com.gitalpha.Engine.GitDirContainer)

AlphaUI (StackPane) → TabPane (trailing "+" tab creates new tabs)
  ├─ GitDirTabButton (extends Tab, implements IObject) → ProjectBrowser (path field + recent repo list)
  └─ OpenProject(gitDir) → GitDirProjectManagerWidget (GridPane)     ← BaseWidget subclasses
       ├─ BranchWidget      local/remote branch TreeViews
       ├─ ChangesWidget     ListView<ChangeEntryWidget> (staged/unstaged)
       ├─ CommitWidget      empty placeholder
       └─ TextViewerWidget  diff viewer (intra-line LCS highlight)

GitDir (per repo, ISerializable) — data holder only
  ├─ GetChangedFiles() / GetBranches() / GetActiveBranch()
  ├─ RunCMD(...) sync git exec (→ RunCMDUtil) — used only by FileChange diff queries
  └─ GetOperator() → GitOperator (AutoCloseable)
```

## Git operations & refresh (GitOperator)

- **Every git mutation goes through the operator queue**: `GitDir.GetOperator().RunGitOp(cmd, policy, callback)`. Never run git from UI code directly.
- One **runner virtual thread** per GitDir drains a `BlockingQueue` FIFO; when the queue is empty it runs an **interruptible refresh** of GitDir state.
- `ERefreshPolicy` — the highest policy in a drained batch wins:
  - `NO_REFRESH` — run the command, do nothing else
  - `REFRESH_DATA_ONLY` — refresh GitDir state, no UI broadcast
  - `REFRESH_AND_UPDATE_UI` — refresh + save session + broadcast refresh event (user-visible ops)
- **Cancellation**: if a new operation arrives during a refresh, `RefreshCanceled` is set and the in-flight process is `destroyForcibly()`-killed; the refresh bails between sub-commands and a fresh one runs after the new operation.
- Refresh pipeline: `git branch -a` → staged changes → unstaged changes (+ untracked) → diff-merge (keeps existing `FileChange` objects so cached diffs survive).
- Callbacks (`IGitOperationCallback.OnCompleted(ok, error, gitDir)`) fire **on the runner thread** after the batch — wrap UI work in `Platform.runLater`. Callbacks always fire, even when the refresh fails or is skipped.
- UI rebuild entry point: `GitDirProjectManagerWidget.RefreshGitDirProjectManagerWidget()` → `GitDir.Refresh(callback)` → on the FX thread: `ChangesWidget.UpdateChanges()`, `BranchWidget.UpdateBranchList()`, `TextViewerWidget.RefreshCurrentFileChange()`.

### Threading model

| Thread | Role |
|---|---|
| JavaFX Application Thread | All UI; the only reader of GitDir state (always after a refresh completes) |
| GitOperator runner (virtual) | Executes git commands + refresh; **only writer** of GitDir state |
| ForkJoinPool (`CompletableFuture`) | `FileChange.GetDiffLines()` + `TextViewerWidget` LCS preparation (pure data) |

`GetChangedFiles()` / `GetBranches()` return the **live lists**. They are safe because UI rebuilds are ordered after writes (callbacks/broadcast), not because of any lock — don't iterate them from other threads.

## Naming Conventions

| Pattern | Example | Scope |
|---|---|---|
| `_Param` | `_FilePath`, `_Scope` | Method parameters |
| `__Local` | `__Res`, `__GridLayout` | Local variables |
| `m_Prefixed` | Not used — all fields are plain | — |
| PascalCase methods | `GetRepoRootPath()`, `UpdateBranchList()` | Everywhere, including private methods (C#-style) |
| Interface prefix `I` | `IObject`, `ISerializable` | Interface type names |
| Interface variable: omit `I` | `IFramework` interface → `Framework` variable | Variables holding an interface reference |
| Enum prefix `E` | `EFileChangeStatus`, `ESettingEntryType` | Enum type names |
| Enum variable: omit `E` | `EFileChangeStatus` enum → `FileChangeStatus` variable | Variables holding an enum value |
| Widget fields: `type_Name` (small caps type, PascalCase name) | `btn_Button`, `vbox_Space`, `txb_ProjectPath`, `txt_ProjectBrowser` | JavaFX widget fields (Button, VBox, TextField, Text, etc.) |

These are not optional — match them when adding or editing methods.

## Diff Viewer (TextViewerWidget)

- **Intra-line word-level highlighting** via LCS token diff (tokenize → DP table → backtrack → styled segments)
- LCS computation runs **off** the JavaFX thread via `CompletableFuture.thenAcceptAsync()`; only node creation happens on the FX thread in a single `Platform.runLater` flush
- Stale responses (user switched to another file) are dropped by comparing the captured target
- "Loading..." is centered in the viewport via a `StackPane` bound to `ScrollPane.viewportBoundsProperty()`
- `FileChange.GetDiffLines()` runs a sync `git diff` through `GitDir.RunCMD` on the ForkJoinPool and caches the parsed diff by file mtime; untracked files synthesize a full-add `@@ -0,0 +1,n @@` diff
- File list click handling: clicking "Staged"/"Unstaged" headers or empty space clears the diff view and **removes the list selection** (`clearSelection()`)

## Key Gotchas

- `FileChange.LineChange` is an inner record — reference as `FileChange.LineChange`, not a separate import
- `ChangesWidget` uses `ChangeEntryWidget` (extends `HBox`, implements `IObject`) as `ListView` items — not `ListCell`. Headers and file entries use the same class with `IsHeader` flag
- `GitCMDConstant` holds raw `List<String>` git command fragments — treat them as mutable (they're `List.of()` which is immutable in practice, but the `List` type allows accidental mutation); `GitOperator` defensively copies commands
- Git exe resolution is inconsistent: `GitOperator.RunCMD` uses `GetValue_AsString()` but `RunCMDUtil.RunCMD` uses `GetDefaultValue_AsString()` — a user-configured git path is silently ignored for diff commands (known issue)
- `BranchWidget` local/remote TreeViews use `setOnMouseClicked` (not `setOnAction`) for double-click checkout and right-click context menu
- `ChangesWidget.ToggleStagedState` disables the checkbox, then runs `add`/`reset` via `RunGitOp(..., REFRESH_AND_UPDATE_UI, callback)`; on failure it re-enables, reverts the selection, and shows an error alert. The post-operation broadcast is handled by the operator, not the caller
- `Debug` logging is off by default; enable via system properties `-Dgitalpha.debug.general|branches|changes=true`
