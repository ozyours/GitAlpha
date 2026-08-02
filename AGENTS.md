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
  ├─ Scene(AlphaUI.Instance, 800, 720)         // stage title "Git Alpha"; min window 800×720 enforced
  └─ stop(): AlphaEngine.Instance.SaveSession()
```

- `com.gitalpha.Main` has an **empty** `main()` — **not** the real entrypoint; use `MainJavaFx`
- Window position/size/maximized are persisted via stage property listeners (position skipped while maximized)
- Window focus in/out and tab selection fire `AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh(reason, gitDirOrNull)` — a `null` target refreshes **all** open project managers
- Stage min size (800×720) is set **before** the saved-bounds restore so a previously-saved smaller window is clamped to the layout floor

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
  └─ OpenProject(gitDir) → GitDirWidget (GridPane)     ← BaseWidget subclasses
       ├─ BranchWidget      local/remote branch TreeViews (active branch: dot + bold green + tooltip)
       ├─ ChangesWidget     ListView<ChangeEntryWidget> (staged/unstaged)
       ├─ CommitWidget      summary TextField + description TextArea + commit Button
       └─ TextViewerWidget  diff viewer (intra-line LCS highlight)

GitDir (per repo, ISerializable) — data holder only
  ├─ GetChangedFiles() / GetBranches() / GetActiveBranch()   // ActiveBranch is volatile (full path, not leaf)
  ├─ RunCMD(...) sync git exec (→ RunCMDUtil) — used only by FileChange diff queries
  └─ GetOperator() → GitOperator (AutoCloseable)
```

### Left-pane layout (GitDirWidget is the layout authority)

- `GitDirWidget` owns all left-pane sizing as `RowConstraints`/`ColumnConstraints` — per-widget size constants were removed (sizing is **not** set inside `BranchWidget`/`ChangesWidget`/`CommitWidget`)
- `LEFT_PANE_WIDTH = 500` (fixed); diff viewer fills the rest
- Row policy: branch row pinned `min == max = 140` (never grows); changes row `min 240 + vgrow ALWAYS` (the **only** growable row); commit row fixed pref height `COMMIT_ROW_PREF_HEIGHT = 300` (no vgrow) so it sticks to the bottom
- Resizing the window only stretches the changes row; branch + commit heights are fixed

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
- UI rebuild entry point: `GitDirWidget.RefreshGitDirWidget()` → `GitDir.Refresh(callback)` → on the FX thread: `ChangesWidget.UpdateChanges()` + `BranchWidget.UpdateBranchList()` (the diff viewer is driven by the file list selection, so it needs no explicit refresh)

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

- **Virtualized rendering (performance)** — the diff is a `ListView<PreparedRow>` with a `DiffRowCell` cell factory; `VirtualFlow` recycles cells so only visible rows are materialized. Rows are plain-data `PreparedRow` records (prefix, old/new line numbers, text, intra-line segments) built off-thread; a huge diff renders instantly because node creation is O(visible rows), not O(diff lines). Row height is pinned via `ListView.setFixedCellSize(ComputeFixedCellSize())` — same JDK VirtualFlow scroll-range workaround as `ChangesWidget` (JDK-8296871 / JDK-8301375 / JDK-8328167)
- **Horizontal scrolling (pan-content)** — the `ListView` itself is never wider than the pane (so its vertical scrollbar always stays at the right edge of the visible viewport). Wide rows are panned by a bottom horizontal `ScrollBar` (`DiffScrollBar`) whose value drives `PanOffset`; every visible cell binds its row graphic's `translateX` to `PanOffset.negate()` (`DiffRowCell`), so only the content slides inside the clipped cell. `SetDiffRows` publishes the widest-row width to `DiffContentWidth`; `UpdateScrollRange()` derives the pan range/bar visibility (`contentWidth - listWidth`) and is re-run on pane resize via a width listener. Wheel tilt pans via a `ScrollEvent` filter when the bar is shown. The list sits in a `VBox` with the bar below; `OverlayPane` layers above both
- **Intra-line word-level highlighting** via LCS token diff (tokenize → DP table → backtrack → styled segments)
- LCS computation runs **off** the JavaFX thread via `CompletableFuture.thenAcceptAsync()` (pairing + LCS in `PrepareDiffRows`); the FX thread only publishes the plain-data rows to the list (`SetDiffRows`), and actual JavaFX nodes are created lazily by the cell factory when rows scroll into view
- Stale responses (user switched to another file) are dropped by comparing the captured target
- **Overlay states** — loading, guard messages, large-file prompt, and error messages are shown in an `OverlayPane` (a `StackPane` layered above the `ListView` that is toggled managed/visible); "Loading..." is a centered `Text`
- **File load guards** — before a diff is computed, `FileChange.CheckLoadGuard()` checks the on-disk file: content sniffing (NUL byte in the first 8000 bytes) marks non-text files `BINARY`, and files above `LARGE_FILE_THRESHOLD_BYTES` (1 MB) are marked `LARGE_FILE`. Guards apply to both the untracked full-add path and the `git diff` path (when the file still exists); a removed file has no content to guard. `GetDiffLines()` returns a `DiffLoadResult` (lines + `EFileLoadGuard`); `BINARY` files are never loaded, `LARGE_FILE` files are not auto-loaded — `TextViewerWidget` shows a centered prompt with a "Load file" button that calls `GetDiffLinesForce()` (explicit user action bypasses only the large-file guard). A cached diff (matching mtime) is returned regardless of guards, so a force-loaded large file renders instantly on reselect
- `FileChange.GetDiffLines()` runs a sync `git diff` through `GitDir.RunCMD` on the ForkJoinPool and caches the parsed diff by file mtime; untracked files synthesize a full-add `@@ -0,0 +1,n @@` diff
- File list behavior is **selection-driven**: the diff viewer follows the ListView highlight (`selectedItemProperty`) — selecting a file entry shows its diff, while a "Staged"/"Unstaged" header or an empty selection empties the viewer. Headers carry no `FileChange`, so selecting one only highlights it; there are no mouse-click handlers on the list (not even for empty space)
- `ChangesWidget.UpdateChanges()` updates the items list **in place** (diff-merge by path/scope/status, `removeIf` + move-capable ordered insertion) instead of clearing/repopulating: surviving entries keep their widget instance and are only ever relocated, never rebuilt, so the current selection — and therefore the diff viewer — survives a refresh untouched. Header widgets (`StagedHeader`/`UnstagedHeader`) are persistent instances so a highlighted header keeps its highlight too; each section is re-sorted by file path every refresh (paths normalized to `/` separators to approximate git's ordering)

## Key Gotchas

- `FileChange.LineChange` is an inner record — reference as `FileChange.LineChange`, not a separate import
- `ChangesWidget` uses `ChangeEntryWidget` (extends `HBox`, implements `IObject`) as `ListView` items — not `ListCell`. Headers and file entries use the same class with `IsHeader` flag
- `ChangesWidget` pins `ListView.setFixedCellSize()` to a **measured** row height (see `ComputeFixedCellSize`): without it, JavaFX 17–21 VirtualFlow under-computes the scroll range from estimated cell sizes for long lists, so the last entries can't be scrolled into view (keyboard selection reaches them, the view doesn't render them) — JDK-8296871 / JDK-8301375 / JDK-8328167
- `GitCMDConstant` holds raw `List<String>` git command fragments — treat them as mutable (they're `List.of()` which is immutable in practice, but the `List` type allows accidental mutation); `GitOperator` defensively copies commands
- Git exe resolution is inconsistent: `GitOperator.RunCMD` uses `GetValue_AsString()` but `RunCMDUtil.RunCMD` uses `GetDefaultValue_AsString()` — a user-configured git path is silently ignored for diff commands (known issue)
- `BranchWidget` local/remote TreeViews use `setOnMouseClicked` (not `setOnAction`) for double-click checkout and right-click context menu
- Branch parsing (`GitOperator.ParseBranchesOutput`): remote vs local is decided by the `remotes/` prefix (not slash count — local branches like `feature/foo` contain slashes too); `ActiveBranch` stores the **full path** (`feature/foo`, not the leaf `foo`) so shared leaf names don't collide in the active-branch cell factory; detached-HEAD lines (`(HEAD detached at ...)`) are skipped
- `BranchWidget` active-branch styling is done in the `TreeCell` factory (dot + bold green + tooltip) — never bake markers into the tree strings; cells are reused, so the empty branch must reset `setText/setGraphic/setTooltip/setStyle`
- `TextViewerWidget.DiffRowCell` is a recycled `ListCell` — `updateItem` must reset every property (`setText(null)`, `setGraphic(null)`, `setStyle("")`) and re-derive the row graphic from the `PreparedRow`; the inline `-fx-background-color` on the cell overrides the `:selected`/`:hover` styles so green/red rows keep their colour
- `ChangesWidget.ToggleStagedState` disables the checkbox, then runs `add`/`reset` via `RunGitOp(..., REFRESH_AND_UPDATE_UI, callback)`; on failure it re-enables, reverts the selection, and shows an error alert. The post-operation broadcast is handled by the operator, not the caller
- `CommitWidget` disables the whole form while `git commit` runs so Enter (TextField action) can't double-submit; on success the form is cleared (the refresh already updated the UI)
- `Debug` logging is off by default; enable via system properties `-Dgitalpha.debug.general|branches|changes=true`

See `ROADMAP.md` for planned work.
