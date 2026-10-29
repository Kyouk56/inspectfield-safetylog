<!-- BEGIN AIR worktrees (managed by Air — do not edit) -->

## Worktrees and isolation scope

Every session has an **isolation scope** — chosen by the user when the task starts, otherwise the workspace default (currently **Worktree isolation**). The per-turn reminder names the one that applies to this session; follow it:

- **Worktree isolation** — every coding task (implementing, fixing, refactoring, migrating) runs in its own git worktree created by Air's tool at `C:/Users/jdesr/.air/bin/air-worktree`. Before the first edit, run `C:/Users/jdesr/.air/bin/air-worktree create --name <feature-slug>` — **name it after the feature or task** (short kebab-case derived from the request, e.g. `login-rate-limit`; never a placeholder, never ask the user for one); the tool slugifies + de-duplicates it and prints the absolute path + branch (`air/<name>`). The user may rename it later (`rename`); sessions stay linked. Do all edits and shell commands inside that path (`cd` there for commands, absolute paths under it for edits) — the main checkout is not your working copy. To work in a worktree the user or another agent already opened, run `C:/Users/jdesr/.air/bin/air-worktree join <name>` instead. Pure questions, reading and research need no worktree. One worktree per REPOSITORY per session — reuse it for follow-ups; when the task also touches another repository in the workspace, create or join a worktree there too (`--repo <that repo's path>`). **Enforced by a hook:** under this scope, edits to files in the main checkout are denied until the session has created or joined a worktree.
- **Current branch** — work directly in the workspace checkout on the branch that is checked out. Do not create or join a worktree unless the user explicitly asks for one.
- **Agent decides** — the tool above is available; use a worktree when the task benefits from isolation (a change that wants its own branch/PR, other agents working in the same repository, a large or risky edit), otherwise work on the current branch. Edits are never blocked.

Every conversation working in a worktree records its tasks and decisions into one shared knowledge base scoped by the worktree name (the same name across repositories is one group). After creating or joining one, read what the group already did before settling something a peer may already have decided: `gnosis knowledge worktree-tasks --name <name>` lists each task it ran with its decision headlines — open the full knowledge of every task that touches your change with that row's `cmd`; `gnosis knowledge search "<question>" --worktree <name> --mode hybrid` answers a specific question (`--mode lexical` for an exact identifier, flag or file name).

- **Already in a worktree another tool made?** If this session runs inside a git worktree that Cursor (`/worktree`, `agent --worktree`), Claude Code (EnterWorktree) or `git worktree add` created, do not create a second one: run `C:/Users/jdesr/.air/bin/air-worktree adopt` there once to register it with Air, then work in it as the task's worktree.

See the `/air-worktree` skill for the full command list (`create`, `join`, `adopt`, `rename`, `list`, `status`, `path`, `remove`).

<!-- END AIR worktrees -->

<!-- BEGIN gnosis-knowledge -->
<!-- gnosis-knowledge prompt v12 -->
## Codebase knowledge (gnosis)

Earlier agent sessions in this repository recorded WHY the code is the way it is: design
decisions, rejected approaches, user requirements, known pitfalls. None of that is visible
in the code or the git log. `gnosis knowledge` (read-only, ~1s, JSON on stdout) reads it.

**Step 1 of every task in this repository, before any grep, read, edit or subagent:** run one
lookup phrased as the task itself:

    C:\Users\jdesr\.local\bin\gnosis knowledge search "<the request, in your own words>"

This holds even when the request names the exact identifier or file: the lookup is for
the why, which grep cannot find. Run it as one plain command and keep stderr visible (no
`2>/dev/null`): a broken backend reports only there, and the exit code is 0 either way.

If a recorded decision covers what you are about to change, follow it, or tell the user
when the request contradicts it. An empty result is fine; carry on. After step 1, grep
stays the right tool for exact identifiers.

- Workspace setup (creating or joining a worktree) may come first; step 1 follows it.
- In an Air worktree (you created or joined one, or a reminder names it), other agents on
  the same feature recorded their work under its name. Step 1 there is the group's history:
  `C:\Users\jdesr\.local\bin\gnosis knowledge worktree-tasks --name <worktree>` lists the tasks they ran with the
  headlines of what each decided. Headlines are summaries: before you edit, run the row's `cmd`
  for every task whose decisions touch your change, to read the full record (names, values,
  reasons). Then search the group:
  `C:\Users\jdesr\.local\bin\gnosis knowledge search "<the request>" --worktree <worktree> --mode hybrid`.
- Exploring through a subagent? Use the `gnosis-explorer` subagent, which runs these
  lookups itself. Any other subagent, including your tool's built-in code-search agent
  (Explore, general-purpose, search_code_subagent), does not read this file: run step 1
  yourself before you delegate, or put it in the subagent's prompt.

- `C:\Users\jdesr\.local\bin\gnosis knowledge search-code "<query>"` — semantic code search; use FIRST to find where code lives.
- `C:\Users\jdesr\.local\bin\gnosis knowledge search-flows "<query>"` — flow-first semantic search over whole behaviour flows.
- `C:\Users\jdesr\.local\bin\gnosis knowledge flows-show <file:line>` — the flows a location participates in (impact radius).
- `C:\Users\jdesr\.local\bin\gnosis knowledge search "<query>"` — recorded decisions and prior tasks matching the query (the step-1 lookup); `--mode lexical` for exact identifiers, flags and file names.
<!-- END gnosis-knowledge -->
