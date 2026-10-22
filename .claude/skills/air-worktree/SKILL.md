---
name: air-worktree
description: "Run every coding task in an isolated git worktree managed by Air. Use BEFORE the first edit of any implementation, bug-fix, refactor or migration task: `air-worktree create --name <feature-slug>` makes a fresh worktree on a new branch named after the feature/task, `air-worktree join <name>` attaches to one the user or another agent already opened, `air-worktree rename` renames one. The Air Sessions panel then shows the worktree — changed files, commits, push/PR state — with every conversation working in it nested underneath. Also use when the user says 'work in a worktree', 'join worktree X', 'what's the status of my worktree', or asks to clean a worktree up."
---

# Air worktrees

Coding work in this workspace can happen in **Air worktrees**: one git worktree per task, created by the `air-worktree` tool so the Air plugin can track it and let more agents join it. The tool lives at `~/.air/bin/air-worktree` (honours `AIR_HOME`).

Whether a task MUST use one is the session's **isolation scope**, chosen by the user when the task starts (or the workspace default) and stated by the per-turn reminder: **Worktree isolation** → the rule below is mandatory and the edit gate denies main-checkout edits until you have a worktree; **Current branch** → stay in the checkout on its current branch, no worktree unless the user asks; **Agent decides** → use the rule below when isolation helps (a change that wants its own branch/PR, other agents in the same repository, a large or risky edit), otherwise work on the current branch.

It is a bash script that needs `git` and `jq`. On Windows run it from Git Bash (the shell Claude Code uses there) — `~/.air/bin/air-worktree …` works as written; Air downloads a `jq` into `~/.air/bin` when none is on PATH (or `winget install jqlang.jq`). Paths it prints are native (`C:/Users/…`), which Git Bash accepts as-is; quote a path that contains a space.

## The rule

1. **Before the first edit of a coding task**, run:

   ```bash
   ~/.air/bin/air-worktree create --name "<feature-slug>"
   ```

   **Name it after the feature or task you are about to work on** — short, descriptive, kebab-case (e.g. `login-rate-limit`, `fix-export-crash`, `sessions-panel-grouping`). Derive it from the user's request; do not ask them for a name and do not use placeholders like `task` or `work`. Free text is slugified and de-duplicated (`-2` suffix) automatically. The tool prints the final name, the absolute `path`, and the branch (`air/<name>`, cut from the current HEAD; `--base <ref>` picks another base). The user can rename it at any time (`air-worktree rename <name> <new-name>`, or from the Sessions panel) — sessions stay linked.

2. **Do everything inside the printed path.** `cd` there for shell commands (builds, tests, git), and use absolute paths under it for every file edit. The main checkout is not your working copy — under Worktree isolation, edits to it are denied until the session has a worktree.

3. **Joining instead of creating.** If the user names a worktree, or you are told another agent already opened one for this task, attach to it:

   ```bash
   ~/.air/bin/air-worktree join <name>
   ```

   It prints the same path + branch. Several agents can work in one worktree; coordinate through git (commit small, pull before pushing).

4. **Finishing.** Commit on the worktree's branch, push it, and open a PR as the user's workflow dictates. The Sessions panel reads the git state (uncommitted → committed → pushed → PR) straight from the worktree. Only remove a worktree when the user asks (`air-worktree remove <name>` — the branch is kept).

5. **Shared knowledge of the group.** Every conversation working in a worktree records its tasks, decisions, specifications and rationale into gnosis; the worktree name scopes them into one shared knowledge base (the same name across repositories is one group). Right after creating or joining, and before settling something a peer may already have decided, read it:

   ```bash
   gnosis knowledge worktree-tasks --name <name>                              # every task the group ran, oldest first, with its decisions
   gnosis knowledge task-kb --conv <c> --task <t>                             # one task's full knowledge (the row's `cmd`) — open it for every task touching your change
   gnosis knowledge search "<question>" --worktree <name> --mode hybrid       # a specific question across the group
   gnosis knowledge search "<terms>" --conv <c> --task <t> --mode lexical     # find one thing inside one task
   ```

   Read-only, no API key. `--mode lexical` matches exact identifiers, flags and file names; `hybrid` fuses that with the semantic search. Results are grouped by the conversation and task that recorded them, with runnable links to the rest of their knowledge.

No worktree is needed for pure questions, reading, research or planning. **One worktree per repository per session**: don't create a second one for follow-up work in the same repo — keep using the one you have (`air-worktree status <name>` shows what changed). When the task also needs changes in **another repository of the workspace** (a multi-root workspace, a sibling repo), create or join a worktree there too: `air-worktree create --name <same-feature-slug> --repo <path-to-that-repo>`. The conversation then belongs to every worktree it opened, and the Sessions panel nests it under each.

## Commands

| Command | What it does |
|---|---|
| `air-worktree create --name <feature-slug> [--base <ref>] [--json]` | New worktree + branch `air/<name>` named after the feature, registered for the panel |
| `air-worktree adopt [<path>] [--name <name>] [--base <ref>] [--json]` | Register a worktree another tool created (Cursor's `/worktree` / `agent --worktree`, Claude Code's EnterWorktree, `git worktree add`) — the one at `<path>`, else the cwd. Creates nothing; idempotent. Use it instead of `create` when the session already runs inside such a worktree |
| `air-worktree rename <name> <new-name> [--keep-branch]` | Rename (dir, registry, links, and the branch unless it is already pushed; an adopted worktree keeps its directory and branch) |
| `air-worktree join <name> [--json]` | Attach this session to an existing worktree; prints path + branch |
| `air-worktree list [--json]` | Worktrees registered for this repo |
| `air-worktree status <name> [--json]` | Modified/added/deleted/untracked counts, commits ahead of base, unpushed |
| `air-worktree path <name>` | Just the absolute path |
| `air-worktree remove <name>` | Remove the worktree checkout (branch kept) |
| `air-worktree doctor [--repo <path>]` | Check jq / HOME / hook scripts + registrations / registry and self-test the PostToolUse linker — run it when a conversation does not appear under its worktree |

Every `create`/`join` ends with an `AIR_WORKTREE_JSON {…}` line — leave it in the output; Air's hooks read it to link this conversation to the worktree.
