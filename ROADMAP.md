# GitAlpha — Roadmap

This file tracks the project plan. It is the place to record decisions before, during, and after
they are implemented.

- **Committed Plan** — plans that were discussed and committed (implemented). New work lands here
  once it is committed to `master`.
- **Pending Plan** — features/plans that are missing and not yet discussed; candidates for future
  discussion sessions.

---

## Committed Plan

Items that were discussed and have been committed to `master`.

- [x] **Session persistence** — save/restore open + recent repositories and window bounds to
      `~/.gitalpha/session.json`, SHA-256 hash skips redundant writes, invalid repos sanitized on
      load (`c96e7b7`)
- [x] **Refresh event bus** — `IOpenGitDirEvent` / `ICloseGitDirEvent` / `IRefreshGitDirEvent`
      (WeakReference, pruned) + optimized session saves (`cb6252b`)
- [x] **Staged/unstaged split & stage toggling** — tracked per scope, checkbox stage/unstage in the
      changes list (`1da39b5`)
- [x] **Project open flow & recent projects UI** — tab lifecycle cleanup, hardened path handling
      (`9292453`, `b87b63f`)
- [x] **Lazy diff loading** — `FileChange` promoted to a class, diff parsed lazily and cached by
      file mtime; untracked files synthesize a full-add diff (`1e6addf`)
- [x] **Intra-line word-level diff highlighting** — LCS token diff computed off the FX thread via
      `CompletableFuture`, stale responses dropped, diff-merge refresh keeps cached diffs alive
      (`4d5977b`, `08257dc`)
- [x] **Naming conventions refactor** — `_Param` / `__Local` / PascalCase methods / `I`-prefix
      interfaces / `E`-prefix enums across the codebase (`296d41f`)
- [x] **Widget rename + Debug logging** — `GitDirProjectManager` → `...Widget`, `Debug` class with
      per-category system-property gates (`70ea74d`)
- [x] **GitOperator** — single FIFO queue + runner virtual thread per repo, interruptible refresh
      (`RefreshCanceled` + `destroyForcibly()`), `ERefreshPolicy` batch semantics, deprecated
      GitDir async/refresh code removed (`bdcfe20`)
- [x] **AGENTS.md tracked + rewritten** — un-ignored from `.gitignore`, documents architecture,
      threading model, naming conventions, diff viewer, and known gotchas (`bdcfe20`)
- [x] **Selection-driven diff viewer & refresh-stable changes list** — the diff viewer follows the
      ListView selection (`selectedItemProperty`) instead of click handlers; header clicks keep their
      highlight; persistent header widgets; `ChangesWidget.UpdateChanges()` updates items in place
      (`removeIf` + move-capable ordered insertion, per-section path sort) so the selection and diff
      survive refreshes; fixed left pane width (`LEFT_PANE_WIDTH = 500`); ROADMAP.md added
      (`ea9cfe3`)
- [x] **Commit widget** — working commit form (summary `TextField` + description `TextArea` +
      `Commit` button) submits `git commit` via the operator queue with `REFRESH_AND_UPDATE_UI`,
      disables the form while in flight to prevent double-submit, clears on success, error alert on
      failure (`c998ac7`)
- [x] **Left-pane layout sizing** — `GitDirWidget` is the layout authority
      (`LEFT_PANE_WIDTH = 500`); branch row pinned `140`, changes row `min 240 + vgrow ALWAYS` (the
      only growable row), commit row fixed pref height `300` (sticks to the bottom); stage min size
      800×720 set before saved-bounds restore in `MainJavaFx` (`c998ac7`)
- [x] **Active-branch rendering** — `BranchWidget` cell factory shows the active local branch with a
      dot, bold green text and a tooltip; `ActiveBranch` stores the full branch path (leaf-name
      collision fix); remote classification by `remotes/` prefix (not slash count); detached-HEAD
      lines skipped; checkout error alert + context-menu Checkout wired (`c998ac7`)
- [x] **File load guards** — `FileChange.GetDiffLines()` returns a `DiffLoadResult` with an
      `EFileLoadGuard`: content sniffing (NUL byte in the first 8000 bytes) marks non-text files
      `BINARY` (never loaded); files above `LARGE_FILE_THRESHOLD_BYTES` (1 MB) are `LARGE_FILE` (not
      auto-loaded) and `TextViewerWidget` shows a centered "Load file" button that calls
      `GetDiffLinesForce()`; a cached diff (matching mtime) is returned regardless of guards
      (`c998ac7`)
- [x] **Virtualized diff viewer** — `TextViewerWidget` replaced the ScrollPane + VBox (one HBox per
      row, froze on large files) with a virtualized `ListView<PreparedRow>` + `DiffRowCell` cell
      factory + `setFixedCellSize` so node creation is O(visible rows); a bottom horizontal
      `ScrollBar` pans wide rows (`DiffContentWidth` / `PanOffset`); loading/guard/prompt/error
      states moved to a layered `OverlayPane`; stale-response guards kept; `SetDiffRows` calls
      `refresh()` so same-count diffs rebuild visible cells (`c998ac7`)
- [x] **Top menu bar + quick command bar placeholders** — `AlphaUI` root restructured from `StackPane`
      to `BorderPane`; placeholder `TopMenuBar` (File / Git / Settings / Help) + `QuickCommandBar`
      button row above the tab pane, sharing a `PlaceholderNotice`; window floor raised to 800×780
      (screen-clamped min height); Java compiler source/target bumped to 26 (`56f0d4b`)
- [x] **Misspelled class rename** — `UI/GitDirEntryUI/GirDirEntryUI.java` renamed to `GitDirEntryUI`
      (class, constructor, filename) with the `GitDirContainerUI` reference updated (`56f0d4b`)
- [x] **Stash feature** — `StashWidget` (separate `Stage`, one per repo deduped in `TopMenuBar`) with
      three-column layout: stash list, file-change list, virtualized diff viewer; bottom actions
      (Rename/Pop/Drop/Apply/Save/Close) + `EStashMode` selector + Auto Restore checkbox + drop
      confirmation; `GitStashOperator` facade queues mutations through the `GitOperator` runner and
      runs reads (list/files/diff) synchronously off the FX thread; `StashEntry` data class parsed
      from `git stash list` (`8e85400`)
- [x] **Stash window polish** — ONE shared `StashWindowState` for all repos (last window to change
      it wins) persisted in the session file under `"StashWindowState"`: windowed bounds/maximized/
      column sizes + Auto Restore preference (bounds only stored while windowed so a maximized close
      can't clobber the windowed restore size; restore is screen-clamped); the diff viewer is the
      shared virtualized `TextViewerWidget` (`SetRawDiffText` renders a raw unified diff via the
      shared `FileChange.ParseDiff`); per-file diffs use `git diff <stash>^ <stash> -- <path>`
      because `git stash show -p` accepts no pathspec; **commit-preserving rename** runs as ONE
      queued `IGitTask` (`commit-tree` → `stash store` → `stash drop stash@{N+1}`, rollback on drop
      failure) instead of the destructive drop+push; stale async loads dropped via per-selection
      version counters; main-window close calls `Platform.exit()` so stash windows don't keep the
      app alive (`8e85400`)
- [x] **Multi-select & bulk checkbox toggle** — `ChangesListView` enables `SelectionMode.MULTIPLE`;
      the diff viewer follows the focused (last-clicked) item via `selectedItemProperty`; with
      several rows selected, toggling any one checkbox checks/unchecks all selected rows and
      stages/unstages them in one operator-queue batch (`RunGitOp(..., REFRESH_AND_UPDATE_UI, ...)`)
      with `:(literal)` pathspec magic so glob characters in filenames match literally; all synced
      checkboxes are disabled in flight and re-enabled/reverted on completion (`8e85400`)
- [x] **History tab scaffold & shared file-change list** — `GitDirWidget` content becomes an outer
      two-column grid: a fixed 500px left sub-`TabPane` with non-closable "Changes" / "History"
      tabs; the single right-column `TextViewerWidget` now serves both tabs. `TreeViewWidget` is
      the History-tab scaffold (commit-graph DAG placeholder over a read-only file list, empty
      until `git log` parsing lands). `ImmutableChangesWidget` extracted from the stash window as a
      generic read-only list over the new `IFileListEntry` contract (`StashEntry.StashFile`
      implements it); the stash Save mode (`EStashMode`) is now persisted in `StashWindowState`
      (`5bb7c87`)
- [x] **Palette-driven theming** — `Theme` package (`ColorPalette` with 11 slots incl. the
      active/passive highlight split, `LightTheme`/`DarkTheme`, session-persisted
      `CustomColorPalette` with legacy `Highlight`-key migration, `ThemeManager` baking inline
      data-URI skins + scene base CSS with `-gitalpha-*` variables, `EButtonVariant`/`ETextVariant`,
      `IThemeChangeEvent`) + `Components` package (`AButton`, `ACheckBox`, `AComboBox`, `AListView`,
      `AScrollBar`, `ATabPane`, `AText`, `ATextField`, `ATextArea`) re-baking on palette switches;
      widgets migrated, all dialogs themed via `ApplyThemeToDialog`, both scenes registered via
      `RegisterScene`, compact `[A]/[M]/[R]` + `[A]/[D]/[M]` status codes, list-cell hover/selected
      rules mirroring Modena's virtual-flow path (`071a8d4`)
- [x] **Palette migration to ThemeColor** — `ColorPalette` slots become `ThemeColor` values (sRGB
      float RGB + brightness, direct/derived forms with hard-coded slot names, cycle-guarded
      `Resolve(Map)`, `GetHex(Map)` rendering `#rrggbb`, `ISerializable` storing only
      R/G/B/Brightness/IsDerived); `CustomColorPalette` type-sniffs legacy hex strings vs ThemeColor
      JSON on load (`ReadColor`); `ThemeManager` bake sites read `GetHex()`; `ParseHex` moved into
      `ThemeColor` (`ParseHexToFloats`); `EButtonVariant` pressed slots map to palette colors
      (`MixToward` stayed in `ColorPalette`, now taking `ThemeColor` args); per-element skins
      extracted into `Theme/Skin` (`ThemeSkin` base + `ButtonSkin`/`ListViewSkin`/`ScrollBarSkin`/
      `CheckBoxSkin`/`ComboBoxSkin`/`DialogSkin`/`TextInputSkin`/`TabPaneSkin`/`BaseSkin`),
      `LightTheme`/`DarkTheme` moved to `Theme/Themes`, `EButtonVariant`/`ETextVariant` moved to the
      `Type` package (`318fc7d`)
- [x] **Resizable left pane with persisted width** — the fixed 500px left column becomes a themed
      `ASplitPane` with a user-draggable divider; the pixel width is stored globally as one
      `"LeftPaneWidth"` scalar in the session file via `AlphaEngine` (default 500) and re-applied on
      every pane resize so window maximize/restore keeps the left pane stable; persistence is gated
      on actual divider drags so resize redistribution (JDK-8092863) can't clobber the saved width;
      floor widths `LEFT_PANE_MIN_WIDTH = 250` / `RIGHT_PANE_MIN_WIDTH = 200`; theming adds the
      `Background2` window-backdrop palette slot, `MenuBarSkin`/`SplitPaneSkin` and
      `ATopMenuBar`/`ASplitPane` components, and migrates `TopMenuBar`/`StashWidget` to themed
      variants; `ChangesWidget` re-enables batch-disabled checkboxes on success/failure;
      `BranchWidget` panes stretch to fill the branch row (`6bb0a30`)
- [x] **Inner sub-tab pane → tab buttons + stack swap** — `GitDirWidget` inner `ATabPane`
      (two fixed non-closable tabs) replaced with a header of `ATabButton`s (flat tab skin via
      `SubTabButtonSkin`, custom `:selected` pseudo-class, no focus ring) above a `StackPane`
      content swap; `AButton.ApplySkin` becomes `protected` so subclasses extend the skin set;
      `ThemeManager.GetSubTabButtonStylesheets()` added; `ASubTabPane`/`ISubTabSelectionEvent`
      composite widget extracted with owned selection model, single cascading stylesheet and
      weak-referenced listeners, ready for later integration; `AGENTS.md` updated (`16caf1b`)
- [x] **Outer project TabPane → ATabWidget migration** — both tab strips off JavaFX
      `TabPane`/`Tab`: the outer project pane in `AlphaUI` becomes a modifiable
      `ATabWidget` ("+" affix fires `INewTabRequestEvent`, per-tab × close faces fire
      `ITabCloseEvent`); `GitDirTabButton` becomes a plain controller owning a stable
      root `StackPane` mapped via `TabsByRoot` so lookups survive reorders;
      `GitDirWidget`'s hand-wired `ATabButton` header + `StackPane` swap and its manual
      `TabSkinListener` replaced by a non-modifiable `ATabWidget`;
      `ATabPane`/`ASubTabPane`/`TabPaneSkin`/`ThemeManager.GetTabPaneStylesheets()`
      deprecated for removal; `SubTabButtonSkin` gains flat `.a-tab-close` close-face
      rules (`2773f06`)
- [x] **Draggable tab reorder** — `ATabWidget` supports live drag-to-reorder in
      modifiable mode; event handlers resolve indexes via `indexOf` at invocation so
      closures survive reorders (`2773f06`)

### Implemented, awaiting commit

- [ ] **Deprecated tab-pane chain removed** — `ATabPane`, `ASubTabPane`, `TabPaneSkin`
      and `ThemeManager.GetTabPaneStylesheets()` deleted (the whole `forRemoval`
      chain from the migration above); dangling javadoc references cleaned in
      `SubTabButtonSkin`, `ATabWidget`, `ISubTabSelectionEvent`; `AGENTS.md` updated
- [ ] **Tab skin rename + size variants** — `SubTabButtonSkin` renamed to `TabButtonSkin`
      (`GetSubTabButtonStylesheets()` → `GetTabButtonStylesheets(variant)`); new
      `ETabButtonVariant` (`NORMAL`/`SMALL`, Type package) picks the baked face
      metrics via a token-resolved CSS template (colors shared, only padding/
      font-size/close gap differ): main project tabs stay `NORMAL`, the
      "Changes"/"History" sub-tabs switch to `SMALL`; `ATabWidget` gains a variant
      constructor parameter and bakes its strip's sheet at that variant;
      `ATabButton` no longer attaches a stylesheet of its own (a node-level sheet
      would outrank the ancestor cascade and force main-tab metrics onto × faces
      in small strips)

---

## Pending Plan

Features/plans that are missing and have not been discussed yet. Pick items in future sessions.

### Core git operations (missing)

- [ ] **Branch create / delete** — context-menu items are still TODO stubs (`CreateNewBranch` /
      `DeleteBranch`); checkout already works (double-click and context menu, with error alert)
- [ ] **Push / Pull** — context-menu items are TODO stubs (`PushBranch` / `PullBranch` are empty)
- [ ] **Fetch** — no fetch operation exists
- [ ] **Restore points (stash-based)** — higher-level feature built on `git stash`: save current
      changes as a named restore point, later restore the working tree to a previous restore point;
      must not replace the normal stash flow — restore points remain visible/usable via the regular
      stash UI
- [ ] **Tags** — no tag listing/creation/deletion

### Branch widget

- [ ] **Dropdown local + menu remote branches** — `BranchWidget` renders two side-by-side `TreeView`s
      (`BranchWidget.java:27-28,51-53`) inside the pinned 140px branch row; planned redesign: local
      branches become a dropdown (`ComboBox`) with the active branch as current value (full-path
      comparison via `GetActiveBranch()`, `BranchWidget.java:231-232`) and checkout on selection
      (`ChangeBranch` through the operator queue, `:349`); remote branches move into a compact menu
      (`MenuButton`/`ComboBox`) instead of a second tree. Done = checkout semantics preserved,
      namespaced branches (`feature/foo`) still resolvable, row height may shrink below 140px —
      re-check `GitDirWidget` RowConstraints so the freed space feeds the changes list
- [ ] **Link local ↔ remote branch (set upstream)** — no upstream concept exists; context-menu items
      `CreateNewBranch` / `DeleteBranch` / `PushBranch` / `PullBranch` are TODO stubs
      (`BranchWidget.java:149-182`). Done = a branch-setting menu linking a local branch to a remote
      via `git branch --set-upstream-to=<remote>/<branch>` through
      `GitOperator.RunGitOp(..., REFRESH_AND_UPDATE_UI, callback)`; the local dropdown shows the
      configured upstream (e.g. `main → origin/main`)

### Known issues

- [ ] **Git exe resolution inconsistency** — `GitOperator.RunCMD` uses `GetValue_AsString()` but
      `RunCMDUtil.RunCMD` uses `GetDefaultValue_AsString()`; a user-configured git path is silently
      ignored for diff commands
- [ ] **Settings UI** — `AlphaSettings` exists (GitPath, RecentSize, TabMaxSize) but there is no UI
      to edit it

### Engineering hygiene

- [ ] **Tests** — no test framework, no `src/test` tests exist
- [ ] **Maven wrapper** — not present; CI/fresh clones rely on system `mvn`
- [ ] **Formatter/linter config** — none; consider adding one to enforce the naming conventions
- [ ] **Cross-platform build** — JavaFX classifier `win` makes the build Windows-only

### Diff viewer / UX

- [ ] **Diff enhancements** — e.g. side-by-side mode, syntax highlighting, word-wrap toggle
- [ ] **Jupyter notebook diff view** — `GitDirWidget` hard-wires one `TextViewerWidget` for every
      file (`GitDirWidget.java:42,76,128`), so a `.ipynb` renders as an unreadable unified diff of
      JSON; notebooks embedding base64 image output can also trip the 1 MB `LARGE_FILE` guard
      (`FileChange.java:25`). Planned: a `FileViewerWidget` dispatcher replacing the direct
      `new TextViewerWidget(...)` that picks by file type — a new `JupyterNotebookViewerWidget` for
      `.ipynb` (parse the old/new JSON blobs with the bundled `org.json`, render a cell-by-cell
      diff: code/markdown cells, outputs, execution counts) or the existing `TextViewerWidget` for
      text files. Done = the selection-driven `SetFileChange` flow, load guards, and the
      refresh-stable diff survive the dispatch
- [ ] **Commit history / log view** — the "History" sub-tab scaffold exists (`TreeViewWidget`:
      commit-graph DAG placeholder + `ImmutableChangesWidget` file list, committed in `5bb7c87`)
      but there is still no `git log` parsing or DAG rendering; the scaffold's file list stays
      empty until a commit selection is wired to populate it, and the shared right-column diff
      viewer will show the selected commit's file diffs
- [ ] **Settings for refresh behavior** — auto-refresh frequency / toggle (currently refresh is
      event-driven only)

### App shell / UI

- [ ] **Menu/quick-bar functionality** — `TopMenuBar` and `QuickCommandBar` exist with placeholder
      entries only (committed as placeholders in `56f0d4b`; Git → Stash… is real since `8e85400`):
      every other entry shows a "not implemented" notice. Done = real File (open/quit), Git
      (fetch/pull/push/branch/tags), Settings and Help (about) entries wired through the
      `GitOperator.RunGitOp(cmd, policy, callback)` queue
- [ ] **Command abstraction + quick command bar** — decided design: `abstract class BaseCommand`
      (metadata `GetId()` / `GetDisplayName()` / `GetDescription()` + `abstract void
      Execute(CommandContext)`), one subclass per command overriding `Execute` — git commands run
      through `GitOperator.RunGitOp(args, policy, callback)`, program commands (e.g. refresh) call
      app methods. Discovery: one-time reflection scan of the command package, cached in a
      `CommandCatalog`, no-arg constructors required. Persistence: stable `GetId()` string (never
      the class name) saved in `AlphaSettings`, resolved via the catalog on load — survives class
      renames, no `Class.forName`. `CommandContext` passes the project + UI hooks; `ERefreshPolicy`
      is per command. The `QuickCommandBar` is currently a fixed row of placeholder buttons; the
      same model also serves the `TopMenuBar` entries. Depends on the Settings UI item below

### Project widget state

- [ ] **Deleted project handling** — an open tab whose repo path is deleted/moved while open
      silently renders empty lists: `AlphaEngine.SanitizeGitDirContainer` removes invalid repos
      only at session load (`AlphaEngine.java:242-265`), and the `GitDirWidget` refresh callback
      ignores `__Ok`/`__Err` (`GitDirWidget.java:141-151`) even though
      `IGitOperationCallback.OnCompleted` carries the error. Done = on refresh failure with an
      invalid path the tab shows a "project deleted" state with a button to locate the new path
      (re-point the `GitDir` via a `DirectoryChooser`) and a button to close the tab
- [ ] **Busy indicator** — no UI feedback exists while the `GitOperator` is working: queue/runner
      state is internal (`GitOperator.java:49` exposes only `RefreshCanceled`; no `IsBusy` /
      `IsWorking` property anywhere), and `GitDirWidget` fires `GitDirTarget.Refresh(...)` without
      any start/stop indication (`GitDirWidget.java:141-151`). Done = `GitOperator` exposes a
      working state (queue non-empty or refresh in flight) and `GitDirWidget` shows an indicator
      (spinner / status strip in the left pane) driven on the FX thread from op start and
      `OnCompleted`

### Project browser & repo list

- [ ] **ProjectBrowser improvements** — path entry is a bare `TextField` + "Open" button with no folder
      picker and no Enter-to-submit (no `setOnAction` on the field, `ProjectBrowser.java:30-36`); the
      recent list is a `TilePane` built once in the constructor that never updates when the recent
      container changes and overflows without a `ScrollPane` (`GitDirContainerUI.java:16-23`). Done =
      `DirectoryChooser` browse button, Enter submits, live-updating scrollable recent list with an
      empty state
- [ ] **GitDir entry UI/UX** — `GitDirEntryUI` shows only the raw repo-root path and an "Open" button
      (`GitDirEntryUI.java:44-47`); no repo name, no hover/click affordance, no remove-from-recent or
      context menu, and the entry list never rebuilds on container changes. Done = row with repo name
      + path, click-to-open, context menu (remove/rename), live rebuild
- [ ] **Custom categorical GitDir** — no category concept exists: `GitDir.OnSerialize()` persists only
      the path (`GitDir.java`), `GitDirContainer` holds a flat `List<GitDir>` with no grouping
      (`GitDirContainer.java:20`), and the session stores only `{"D": [...]}`. Done = user-assignable
      per-repo category persisted in `~/.gitalpha/session.json`, grouped/filterable rendering in the
      browser and recent list
- [ ] **UX polish** — invalid-path feedback uses blocking `Alert.showAndWait()`
      (`ProjectBrowser.java:103-110`); no `DirectoryChooser`/`FileChooser` or folder drag-and-drop
      exists anywhere in the codebase; Open buttons use `setOnMouseClicked` (fires on any mouse
      button) instead of `setOnAction` (`ProjectBrowser.java:36`, `GitDirEntryUI.java:23`). Done =
      inline validation, browse/drag-drop, standardized button actions

### Changes list & staging

- [ ] **Section select-all checkboxes** — the "Staged"/"Unstaged" header rows are built by the header
      `ChangeEntryWidget(String)` constructor, which creates only an **invisible** checkbox
      (`CommitCheckBox.setVisible(false)`/`setManaged(false)`, `ChangesWidget.java:99-101`); no
      visible check-all control exists per section. Done = a real checkbox in each header
      (`ChangesWidget.java:163-164`) that checks/unchecks all entries in its section and batches the
      `git add`/`git reset` through the operator queue

### Theming

- [ ] **Pressed-state feedback** — the `AButton` pressed slot maps to the border color (nearly
      invisible in light theme) and the dialog default OK button has no `:default:pressed` rule
      (`:default:hover` overrides `:pressed`), so clicks show little feedback. Done = pressed
      derived from the passive/hover color (e.g. `derive(passive, -12%)` or a `ThemeColor` derived
      slot) plus a `:default:pressed` rule in the dialog skin
