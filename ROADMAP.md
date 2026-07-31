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

---

## Pending Plan

Features/plans that are missing and have not been discussed yet. Pick items in future sessions.

### Core git operations (missing)

- [ ] **Commit widget** — `CommitWidget` is an empty placeholder; add a Summary text field + a
      Description text area (summary bar on top, description body below), placed at the bottom of the
      left pane (shrink `BranchWidget` height to fit); submit runs `git commit` via the operator queue
      and triggers a `REFRESH_AND_UPDATE_UI` refresh
- [ ] **Branch create / delete / checkout** — create/delete context-menu items are TODO stubs;
      double-click checkout works but has no error alert on failure
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

- [ ] **File load guards** — `FileChange.GetDiffLines()` reads any file unconditionally (and
      `Files.readString` for untracked ones); before loading, check the file: large files are not
      auto-loaded by default — the `TextViewerWidget` shows a button to load explicitly; non-text
      files (detected by **content, not extension** — e.g. NUL bytes in a `.dll`) are never loaded
- [ ] **Diff enhancements** — e.g. side-by-side mode, syntax highlighting, word-wrap toggle
- [ ] **Commit history / log view** — no `git log` visualization exists
- [ ] **Settings for refresh behavior** — auto-refresh frequency / toggle (currently refresh is
      event-driven only)
