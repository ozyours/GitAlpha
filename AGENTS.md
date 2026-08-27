# GitAlpha — Agent Guide

## Build

```sh
mvn compile                    # compile
mvn verify                     # compile + package (shaded fat jar via maven-shade-plugin)
```

- Java 26, JavaFX 21, no test framework, no formatter/linter config
- Single-module Maven project; fat-jar main class: `com.gitalpha.UI.MainJavaFx`
- Only dependency beyond JavaFX: `org.json:json:20250517`
- JavaFX classifier `win` — **Windows-only build**
- JPMS: `src/main/java/module-info.java` declares module `com.gitalpha` (requires JavaFX + org.json; `opens` UI packages to `javafx.graphics`)
- No `src/test` tests exist (empty directory); Maven wrapper not present (use system `mvn`)

## Entrypoint

```
MainJavaFx.main() → launch() → start(Stage)
  ├─ AlphaEngine.Instance.LoadSession()        // ~/.gitalpha/session.json → open/recent repos + window bounds
  ├─ new AlphaUI()                             // sets static AlphaUI.Instance; BorderPane: menu bar + quick bar on top, TabPane centered
  ├─ Scene(AlphaUI.Instance, 800, 780)         // stage title "Git Alpha"; min window 800×780 enforced
  └─ stop(): AlphaEngine.Instance.SaveSession()
```

- `com.gitalpha.Main` has an **empty** `main()` — **not** the real entrypoint; use `MainJavaFx`
- Window position/size/maximized are persisted via stage property listeners (position skipped while maximized)
- Window focus in/out and tab selection fire `AlphaEngine.Instance.AttemptSaveAndBroadcastRefresh(reason, gitDirOrNull)` — a `null` target refreshes **all** open project managers
- Stage min size (800×780 = 720px layout floor + ~60px top chrome, height floor clamped to the primary screen's visual bounds for small displays) is set **before** the saved-bounds restore so a previously-saved smaller window is clamped to the layout floor

## Architecture

```
AlphaEngine (singleton: AlphaEngine.Instance — never construct a second)
  ├─ AlphaSettings: GitPath ("git.exe"), RecentSize (8), TabMaxSize (150)
  ├─ GitDirContainer OpenGitDirList / RecentGitDirContainer RecentGitDirList (recent list capped + deduped)
  ├─ Session: ~/.gitalpha/session.json — SHA-256 hash skips redundant writes;
  │    stores open/recent repos + window bounds + shared StashWindowState + shared
  │    left-pane width (plain scalar "LeftPaneWidth"); invalid repos sanitized on load
  └─ Event lists (WeakReference, pruned on dead refs):
       IOpenGitDirEvent / ICloseGitDirEvent / IRefreshGitDirEvent   (com.gitalpha.Engine.GitDirContainer)

AlphaUI (BorderPane) → top: TopMenuBar + QuickCommandBar (placeholder entries except Git → Stash…, see ROADMAP.md; shared "not implemented" notice: PlaceholderNotice); center: ATabWidget (modifiable: "+" affix creates tabs, per-tab × close faces, drag-to-reorder)
  ├─ GitDirTabButton (plain IObject controller, no javafx Tab) → stable root StackPane hosting ProjectBrowser (path field + recent repo list); AlphaUI maps root → tab (TabsByRoot) so lookups survive reorder
  └─ OpenProject(gitDir) → GitDirWidget (StackPane) — content is an outer split pane:
     │   left = sub-tab panel (user-resizable; width persisted globally as a plain
     │          scalar in the session file via AlphaEngine):
      │       header = non-modifiable ATabWidget ("Changes" / "History"; faces are its
      │                private TabButton HBox component: title Label + close ATabButton
      │                (compound .a-tab-button.a-tab-close flat rules),
      │                :selected/:pressed pseudo-classes + TabButtonSkin
      │                (SMALL variant; main project tabs use NORMAL),
     │       content = StackPane swap (working-tree GridPane / TreeViewWidget placeholder);
     │   right = ONE shared TextViewerWidget diff viewer for both tabs
     │   the commit row is pinned (min == pref) so the changes row stays the only grower
     ├─ BranchWidget      local/remote branch TreeViews (active branch: dot + bold green + tooltip)
     ├─ ChangesWidget     ListView<ChangeEntryWidget> (staged/unstaged)
     ├─ CommitWidget      summary TextField + description TextArea + commit Button
     └─ TextViewerWidget  diff viewer (intra-line LCS highlight)

StashWidget (separate Stage, launched from Git → Stash…; one per repo, deduped in TopMenuBar) → three-column SplitPane
  ├─ Left:   ListView<StashEntry> (stash list parsed from git stash list)
  ├─ Centre: ImmutableChangesWidget<StashFile> (shared read-only file list for selected stash)
  ├─ Right:  TextViewerWidget diff viewer (raw unified diff via SetRawDiffText — per-file diffs use
  │          `git diff <stash>^ <stash> -- <path>` because `git stash show -p` accepts no pathspec)
  └─ Bottom: action buttons (Rename/Pop/Drop/Apply/Save/Close) + EStashMode selector + Auto Restore checkbox
  └─ GitStashOperator (Engine): stash facade — mutations (push/pop/drop/apply) queue through the
     GitOperator runner; rename runs as ONE queued IGitTask (commit-tree → stash store → drop
     stash@{N+1}, rollback on drop failure — NOT drop+push, which would stash the working tree);
     reads (list/files/diff) run synchronously via GitDir.RunCMD off the FX thread
  └─ StashEntry (Type package): data class with Index, Branch, Description, StashFile list
  └─ StashWindowState (Type package): ONE shared state for all repos (last window to change it wins),
     persisted in the session file ("StashWindowState" key) with windowed bounds/maximized/column
     sizes + Auto Restore preference + Save mode (EStashMode); bounds are only stored while the window is windowed (a
     maximized close must not clobber the windowed restore size); restore is screen-clamped
  └─ Stale async loads are dropped via per-selection version counters (StashSelectionVersion /
     FileSelectionVersion) compared inside Platform.runLater completions
  └─ Main-window close calls Platform.exit() so open stash windows don't keep the app alive

GitDir (per repo, ISerializable) — data holder only
  ├─ GetChangedFiles() / GetBranches() / GetActiveBranch()   // ActiveBranch is volatile (full path, not leaf)
  ├─ RunCMD(...) sync git exec (→ RunCMDUtil) — used only by FileChange diff queries
  └─ GetOperator() → GitOperator (AutoCloseable)
```

### Left-pane layout (GitDirWidget is the layout authority)

- `GitDirWidget` owns all left-pane sizing as `RowConstraints` — per-widget size constants were removed (sizing is **not** set inside `BranchWidget`/`ChangesWidget`/`CommitWidget`)
- The outer split is an `ASplitPane` (themed divider = the vertical border): the left sub-tab pane is **user-resizable** and its pixel width is persisted **globally** in the session file via a plain `AlphaEngine` scalar (ONE width for every repository, key `"LeftPaneWidth"`, default 500 — no wrapper object, same pattern as the window-bounds keys). The divider position is a fraction, so the saved pixels are applied as `width / paneWidth` only after the pane is laid out (setting it before sizing lets `SplitPaneSkin` redistribute the divider — JDK-8092863); dragging the divider writes `fraction × paneWidth` back to the shared scalar. Floor widths: left pane `LEFT_PANE_MIN_WIDTH = 250`, diff viewer `RIGHT_PANE_MIN_WIDTH = 200`
- Row policy: branch row pinned `min == max = 140` (never grows); changes row `min 240 + vgrow ALWAYS` (the **only** growable row); commit row pinned `min == pref == COMMIT_ROW_PREF_HEIGHT = 240` (no vgrow) so it sticks to the bottom and never compresses when the sub-tab header takes vertical space
- Resizing the window only stretches the changes row; branch + commit heights are fixed

## Theming (Theme package + UI/Components)

- **Two-tier application** — `ThemeManager.Instance` (singleton, mirrors `AlphaEngine.Instance`) owns the active `ColorPalette`:
  - *Scene tier*: `RegisterScene(Scene)` attaches the scene base stylesheet — the `.root` focus-ring kill + window backdrop (`-gitalpha-background-2`) + the palette's `-gitalpha-*` CSS variables (`GetCssOverrides`) — re-applied on every palette change
  - *Widget tier*: themed controls bake their own inline data-URI skins from the palette and re-bake on palette switches via `IThemeChangeEvent` (WeakReference list, pruned on broadcast, fired on the FX thread)
  - `SetActivePalette(palette)` replaces the palette, re-applies to all registered scenes, then broadcasts; `ApplyThemeToDialog(dialog)` themes a dialog pane (registers its scene + bakes the dialog skin)
- **Palette** — `ColorPalette` (abstract): 12 base colors as `ThemeColor` values (sRGB float RGB + brightness multiplier, direct or derived form; see the ThemeColor bullet below); diff shades (`GetAddedBackground()` etc.) are derived via `MixToward(base, background, t)` so the same base tints correctly in light and dark. Two background slots: `BackgroundColor` (content/panel level) and `Background2Color` (window-level backdrop — the scene `.root` background is `-gitalpha-background-2`). The concrete themes `LightTheme`/`DarkTheme` live in the `Theme/Themes` subpackage and populate the slots with sRGB float literals; `CustomColorPalette` (ISerializable, stays in the Theme package) persists the full set in the session file with legacy `Highlight`-key migration and legacy hex-string/ThemeColor-JSON type-sniffing (`ReadColor`)
- **Highlight split** — active highlight = *selected* states (list/combo selection, checked fill, text selection), passive highlight = *hover* states (buttons, checkbox, combo popup, list hover); primary (accent) stays for focus rings, the dialog default OK button and `ETextVariant.ACCENT`
- **Components** (`UI/Components`) — `AButton`, `ACheckBox`, `AComboBox`, `AListView`, `AScrollBar`, `ASplitPane`, `AText`, `ATextField`, `ATextArea`, `ATopMenuBar`: thin subclasses of the JavaFX controls that add a style class, apply the skin from `ThemeManager` and implement `IThemeChangeEvent` to re-bake on palette switches. `EButtonVariant` (NORMAL/DANGER/GHOST, Type package) maps the button skin's six color slots to palette colors; `ETextVariant` (Type package) resolves complete inline CSS strings for `AText`. Composite tab panel: `ATabWidget(boolean _Modifiable, ETabButtonVariant _Variant)` (adds a `"+"` affix firing `INewTabRequestEvent`, per-tab `×` close face firing `ITabCloseEvent`, and live drag-to-reorder when modifiable; handlers resolve indexes via `indexOf` at invocation so closures survive reorders; the variant picks the baked face metrics — main project tabs `NORMAL`, sub-tabs `SMALL`). `ATabButton` (the themed tab/close button) attaches no stylesheet itself — it is styled by its host widget's cascading `TabButtonSkin` sheet, so × faces inherit their strip's variant
- **Skin baking** — abstract `ThemeSkin` base (in the `Theme/Skin` subpackage): each element is one subclass (`ButtonSkin`, `ListViewSkin`, `ScrollBarSkin`, `CheckBoxSkin`, `ComboBoxSkin`, `DialogSkin`, `TextInputSkin`, `MenuBarSkin`, `SplitPaneSkin`, `TabButtonSkin`) defining its CSS format (`GetCssFormat`) + palette argument resolution (`GetColorArguments`); the base's concrete `Bake(palette)` fills the format and encodes a `data:text/css;base64,...` URL — the URL changes when colors change so JavaFX re-parses. `BaseSkin` is the exception (scene-level composition of the focus-ring kill + palette variables + combo-box and context-menu popups, overrides `Bake`). Node-level skins inline the hex values (scene variables don't resolve on node stylesheets); only scene-level CSS (the popups) uses `-gitalpha-*` lookups
- **ThemeColor** (`com.gitalpha.Type`, alongside the variant enums `EButtonVariant`/`ETextVariant`) — typed color model replacing raw hex: sRGB float RGB + brightness multiplier (0-1), direct or derived form (`IsDerived` + hard-coded `SourceName`), `Resolve(Map)` follows derivation chains with a cycle guard, `GetHex(Map)` renders `#rrggbb`; `ISerializable` stores only R/G/B/Brightness/IsDerived (names are code-side, never serialized)

## Git operations & refresh (GitOperator)

- **Every git mutation goes through the operator queue**: `GitDir.GetOperator().RunGitOp(cmd, policy, callback)`. Never run git from UI code directly.
- Multi-step sequences that must not interleave with other operations (e.g. the stash rename's five commands) are queued as **one `IGitTask`** via `GitOperator.QueueGitTask(task, policy, callback)` — the task runs to completion on the runner thread before anything else in the queue.
- One **runner virtual thread** per GitDir drains a `BlockingQueue` FIFO; when the queue is empty it runs an **interruptible refresh** of GitDir state.
- `ERefreshPolicy` — the highest policy in a drained batch wins:
  - `NO_REFRESH` — run the command, do nothing else
  - `REFRESH_DATA_ONLY` — refresh GitDir state, no UI broadcast
  - `REFRESH_AND_UPDATE_UI` — refresh + save session + broadcast refresh event (user-visible ops)
- **Cancellation**: if a new operation arrives during a refresh, `RefreshCanceled` is set and the in-flight process is `destroyForcibly()`-killed; the refresh bails between sub-commands and a fresh one runs after the new operation.
- Refresh pipeline: `git branch -a` → `git status --porcelain` (single call parses staged, unstaged, untracked, and renames in one pass) → diff-merge (keeps existing `FileChange` objects so cached diffs survive).
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
- File list behavior is **selection-driven**: the diff viewer follows the ListView highlight (`selectedItemProperty`). The list runs in `SelectionMode.MULTIPLE` — the diff viewer shows the focused (last-clicked) item, while a "Staged"/"Unstaged" header or an empty selection empties the viewer. Headers carry no `FileChange`, so selecting one only highlights it; there are no mouse-click handlers on the list (not even for empty space)
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
- `ChangesWidget.ToggleStagedState` batches: when the toggled row is part of a `MULTIPLE` selection it gathers every selected non-header row, syncs all their checkboxes, and runs ONE `add`/`reset` via `RunGitOp(..., REFRESH_AND_UPDATE_UI, callback)`; paths are prefixed with `:(literal)` so glob characters in filenames match literally. On completion all synced checkboxes are re-enabled; on failure they are reverted and an error alert is shown. The post-operation broadcast is handled by the operator, not the caller
- `CommitWidget` disables the whole form while `git commit` runs so Enter (TextField action) can't double-submit; on success the form is cleared (the refresh already updated the UI)
- `Debug` logging is off by default; enable via system properties `-Dgitalpha.debug.general|branches|changes=true`
- `ThemeManager` skins are baked as inline `data:text/css;base64` URLs on the node — node-level stylesheets **cannot** resolve the scene's `-gitalpha-*` variables, so node skins inline hex values; only scene-level CSS (the combo popup) uses the variables
- Modena specificity: `.a-list-view .list-cell:selected` and `.combo-box-popup .list-cell:selected` lose to Modena's `:filled:selected` — hover/selected rules must mirror the full `.virtual-flow` path to win at equal specificity
- Dialog default button: the dialog skin has no `:default:pressed` rule and `:default:hover` (higher specificity) overrides `:pressed` during a click, so the OK button shows no distinct pressed color
- `ThemeColor` slot names (`Name`/`SourceName`) are hard-coded and never serialized — only R/G/B/Brightness/IsDerived round-trip; `Resolve(Map)` throws on unknown sources and derivation cycles

See `ROADMAP.md` for planned work.
