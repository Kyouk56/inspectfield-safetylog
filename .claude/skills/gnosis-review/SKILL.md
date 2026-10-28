---
name: gnosis-review
description: "Review code changes with full context: semantic search for impact via `gnosis knowledge search-code` (wraps jbcontext) PLUS the recorded history of each changed file (which past tasks touched it and the decisions behind them). Use before committing, reviewing a PR/branch, or gauging the blast radius of a change within this repo."
---

Review changes using this workspace's cross-session memory — so a change is checked not just against the current code but against the recorded decisions behind it.

## When to use
- Before committing, to understand what you're about to commit.
- Reviewing a PR or branch.
- Understanding the impact of a change on the rest of the repo.

## Review workflow

### 1. See the changes
```bash
git status
git diff                 # unstaged
git diff --staged        # staged
git diff main...HEAD     # everything on this branch
```

### 2. Understand each changed file's context (code + history)
For each changed file (keep stderr visible — never `2>&1`, never `2>/dev/null`):
```bash
# similar patterns / callers, to gauge impact (semantic, via jbcontext)
gnosis knowledge search-code "calls to <changed function> to understand impact"
gnosis knowledge search-code "<pattern that was changed>" --path test   # related tests

# WHO changed this file before, and WHY (history the code alone can't give you)
gnosis knowledge code-tasks --file <changed/file>            # Edge 2: tasks that touched it
gnosis knowledge related-by-scope --scope "<subsystem>"      # prior work on the same scope
# follow a returned task's `kb` link → gnosis knowledge task-kb --conv <c> --task <t> (the recorded decision)
```

> A `count: 0` result exits `0` and looks identical whether nothing matched or a backend is unreachable — the difference only appears on stderr as a `[knowledge:…]` line. That matters doubly here: an empty history lookup you treat as "no prior decisions" is how a review silently green-lights a change that contradicts one. If a lookup failed, fix the tool or say the review is missing history — don't pass it off as clean. A stderr line ending in `this environment looks sandboxed (JetBrains daemon socket is blocked)` is Claude Code's Bash sandbox blocking jbcontext's auth socket: name the sandbox as the cause and the fix (re-run `gnosis instrument`, which writes `sandbox.excludedCommands` for `gnosis` / `jbcontext` / `embark-call-graph`, or turn the sandbox off) rather than calling the index unavailable.

### 3. Review checklist
- [ ] The change is consistent with similar patterns already in the repo.
- [ ] All callers of anything modified still work.
- [ ] Related tests exist and cover the change.
- [ ] It doesn't reintroduce a previously-fixed bug (check `code-tasks` history).
- [ ] **It doesn't silently contradict a recorded decision** — if a prior task's motif explains why the code was this way, reconcile or call it out. Cite the `conv://…` URI.

## Report
Summarize impact (callers/tests affected), flag any change that conflicts with a recorded decision (with its `conv://` citation), and separate confirmed issues from things worth a second look.
