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

---

## Pending Plan

Features/plans that are missing and have not been discussed yet. Pick items in future sessions.

### Core git operations (missing)

- [ ] **Branch create / delete** — context-menu items are still TODO stubs (`CreateNewBranch` /
      `DeleteBranch`); checkout already works (double-click and context menu, with error alert)
- [ ] **Push / Pull** — context-menu items are TODO stubs (`PushBranch` / `PullBranch` are empty)
- [ ] **Fetch** — no fetch operation exists
- [ ] **Stash** — no stash support (create/apply/drop)
- [ ] **Restore points (stash-based)** — higher-level feature built on `git stash`: save current
      changes as a named restore point, later restore the working tree to a previous restore point;
      must not replace the normal stash flow — restore points remain visible/usable via the regular
      stash UI
- [ ] **Tags** — no tag listing/creation/deletion

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
- [ ] **Commit history / log view** — no `git log` visualization exists
- [ ] **Settings for refresh behavior** — auto-refresh frequency / toggle (currently refresh is
      event-driven only)

### App shell / UI

- [ ] **Top menu bar** — no `MenuBar` exists anywhere; `AlphaUI` is a `StackPane` whose only child is
      the `TabPane` (`AlphaUI.java:30-31`) and the scene root is `AlphaUI.Instance` directly
      (`MainJavaFx.java:31`). Add a `MenuBar` (e.g. File / Git / Settings / Help) above the tab pane —
      requires restructuring `AlphaUI`'s root to a `BorderPane` (menu on top, tabs in center) and
      rechecking the window floor. Natural home for the pending Settings UI item above
- [ ] **Quick command bar** — a button row under the top menu bar whose command set is assigned in
      settings. `AlphaSettings` currently holds only fixed entries (`GitPath`, `RecentSize`,
      `TabMaxSize`) and `ESettingEntryType` supports only String/Bool/Integer/Float — no user-defined
      command list exists. Add a settings-defined command list (each entry = display name + git args)
      and render it as quick-action buttons; execution goes through the existing
      `GitOperator.RunGitOp(cmd, policy, callback)` queue. Depends on the top menu bar + Settings UI
      items above
