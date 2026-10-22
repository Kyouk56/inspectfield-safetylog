---
name: gnosis-research
context: fork
agent: gnosis-explorer
argument-hint: query
description: "Research and understand an unfamiliar codebase using this workspace's cross-session memory: the recorded history behind the code (who touched it, and why). Use to gather context before a change, find implementations, or trace how something evolved."
---

You need to gather all context for task $ARGUMENTS thoroughly — both the *code* and the *history behind it*. Prefer the `gnosis-explorer` subagent to run this in an isolated context and hand back a terse report.

## When to use
- Understanding an unfamiliar codebase or subsystem.
- Finding where/how something is implemented.
- Identifying related code before making a change.
- Recovering *why* code is the way it is (prior decisions, past tasks).

## Tools
`gnosis knowledge` is the front door for the history: recorded decisions, past tasks and the code each one touched. Semantic code search (jbcontext) is turned off in this workspace, so `search-code` / `kb-code` are not available — locate code through the tasks that touched it, then exact grep and Read. Only stdout is JSON, and a pipe carries only stdout, so `cmd | jq` needs no redirect: never `2>&1` (corrupts the envelope) and never `2>/dev/null` (hides failures). Keep stderr visible — a search that returns `count: 0` with exit `0` means either a genuine miss *or* a failed backend, and only a `[knowledge:…]` line on stderr tells you which. Never report an empty result as "no prior context" without checking.

```bash
gnosis knowledge search "<intent>"                             # decisions / history / prior tasks
gnosis knowledge task-code --conv <c> --task <t>              # the code a past task changed
gnosis knowledge code-tasks --file <F> --line <L>              # the tasks that touched a location
```

Query by meaning, not keywords: "function that validates user email and returns bool", not "validateEmail user".

## Research workflow (single-shot, then navigate)
1. **Bootstrap** with ONE `gnosis knowledge search "<intent>"`; follow a hit's `files-modified` link (`task-code`) to the code. With no recorded task, one exact `grep -rn` for the most specific identifier.
2. **Read** a returned file locally (inspect the relevant region, not the whole file) before any local grep/find.
3. **Follow the history edges** on the hits rather than guessing subcommands — each result carries runnable `links[].cmd`:
   - a hit's `tasks-touching-method` → `gnosis knowledge code-tasks --file <F> --line <L>` (who changed it),
   - then that task's `kb` → `gnosis knowledge task-kb --conv <c> --task <t>` (the recorded *why*).
4. **Narrow** only if the first search was off — at most once. Don't fire a second broad search.

## Report
Return the relevant `file:line` regions with short descriptions, plus any recorded decision you found, cited by its stable `conv://…` URI. Separate "confirmed in code" from "history says". If gnosis has nothing relevant, say so plainly — but if a lookup *failed* (a `[knowledge:…]` line on stderr), report that instead: the research is incomplete, not conclusive.
