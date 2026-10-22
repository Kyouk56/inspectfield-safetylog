---
name: gnosis-knowledge
description: "Step 1 of every task in this repository: before any grep, read or edit, run `gnosis knowledge search \"<the request>\"` to read what earlier sessions recorded about it. Also: recover design rationale, feature history, and code↔conversation context from prior Claude Code sessions in this workspace, using the `gnosis knowledge` CLI. Use this skill whenever the user asks 'why was X built this way', 'what did we decide about Y', 'history of Z', mentions a previous session or design decision, asks for the rationale behind some code, asks which files/tasks are relevant to a change, wants a safer edit by understanding original intent, or needs to find code or prior work by meaning rather than exact tokens. Also trigger on the bright-line rule: before editing a file whose subsystem an injected <air_related_context> block names, run the related-by-scope lookup first. Trigger even when the user doesn't name the tool — if cross-session context would make the answer better, this applies."
---
<!-- gnosis-knowledge prompt v12 -->

# gnosis-knowledge

`gnosis knowledge` is a hypermedia (HATEOAS) knowledge graph you drive from the shell (via Bash). It connects three things this workspace already tracks:

- **Code** — the call-graph methods a task edited.
- **Conversation tasks** — every prior Claude Code session is segmented into tasks; gnosis records what each task did, its messages, and the files it changed.
- **Knowledge items (KB)** — per task, gnosis extracts **topics** (semantic segments) and **motifs** (named decisions: `technical_design_decision`, `product_design_decision`, `context_resource`, `summary`).

Every command prints **one JSON envelope** to stdout:

```jsonc
{ "self": "<entity URI>", "data": { "explanation": "...", ... }, "links": [ ... ], "warnings": [ ... ] }
```

**Parsing the output — never `2>&1`, but never `2>/dev/null` either.** Only **stdout** is JSON (it starts at the first byte); **stderr** carries logs, model-load progress (`search` loads a MiniLM embedder) **and every backend failure**. A pipe only carries stdout, so `cmd | jq` is already safe — you don't need to redirect anything:

```bash
gnosis knowledge search "<query>" | jq '.data.results'   # stderr stays visible to you
```

Merging the streams (`2>&1 | jq`) corrupts the envelope (`Expecting value: line 1 column 1`), so don't. But **`2>/dev/null` is just as wrong**: stderr is the *only* channel that reports a broken backend, and gnosis exits **0** regardless — so discarding it turns a configuration failure into a silent, convincing "nothing found".

The load-bearing idea: each `links[]` entry is a **ready-to-run command**. You navigate the graph by reading `data`, picking the link whose `rel` matches your goal, and running its `cmd` string verbatim. **Follow links by `rel`; don't guess subcommands or flags.**

<when_to_use>

Use this skill whenever the user's question would benefit from prior-session context, even implicitly:

- **Bright-line trigger (mechanical, not a judgment call)** — before you edit a file whose subsystem is named in an injected `<air_related_context>` block (or that a related task surfaced here covers), run the `related-by-scope` command it points you to and read the recorded intent **first**. Skipping it risks silently contradicting a recorded decision or re-deriving context that already exists.
- **Rationale / history** — "why did we", "what was the reasoning", "previous decision", "we discussed this", "history of", "how did this evolve".
- **Feature-aware editing** — "modify the X feature", "extend the Y flow", "before I change", "what could break if I touch Z".
- **Locating relevant files / prior work** — "which files handle X", "where is Y", "what touches Z", "has anyone worked on this before".
- **Finding by meaning** — semantic intent, not exact tokens.

**Do not use** for: trivial edits where you already know the target file; pure-syntax questions; or when the user explicitly asked for a different tool.

</when_to_use>

## The hypermedia model

Stable `self` URIs:

- `conv://{id}` — a conversation
- `conv://{id}/task/{N}` — a task in a conversation
- `conv://{id}/task/{N}/topic/{i}` · `…/motif/{i}` — a KB item
- `code://file/{path}#L{n}` — a file location
- `code://method/{id}` — a call-graph method

Stable `rel` names on `links[]` — pick the one that matches your goal:

| `rel` | Edge / meaning |
|---|---|
| `files-modified` | task → code it changed (**Edge 1**) |
| `tasks-touching-method` | code → tasks that touched it (**Edge 2**) |
| `kb` | task → topics + motifs (**Edge 3**) |
| `code-mentioned` | KB item → code it refers to (**Edge 4**) |
| `related-by-scope` | task → prior tasks sharing its classified scope (structural) |
| `related-semantic` | task/text → similar tasks (embeddings) |
| `task` / `tasks` / `messages` | open the parent, listing, or raw messages |

## Subcommand cheat-sheet

`<conv>` is a conversation `#` id or absolute path; `<task>` is its per-conversation task number. Pass the query as a positional argument to `search`; the rest take `--conv`/`--task` flags.

| Command | Use |
|---|---|
| `gnosis knowledge search "<query>"` | Semantic search over KB items **and raw messages**, grouped by (conv, task). **First call for "what did we decide / discuss" questions.** Add `--worktree <name>` for a worktree group, `--conv <conv> [--task <task>]` for one conversation/task, `--mode lexical` for exact terms (identifiers, flags, file names, error strings) or `--mode hybrid` for both. |
| `gnosis knowledge worktree-tasks --name <worktree> [--query "<keywords>"]` | **The tasks a worktree group already ran**, oldest first: headline, agreed design, recorded decisions, and the `cmd` that fetches each task's whole knowledge. **First call after joining or creating an Air worktree.** |
| `gnosis knowledge conversations [--limit N]` | List analyzed conversations (entry point; prefer search). |
| `gnosis knowledge tasks --conv <conv>` | Tasks in a conversation. |
| `gnosis knowledge task --conv <conv> --task <task>` | One task's detail + links to all edges. |
| `gnosis knowledge task-kb --conv <conv> --task <task>` | **Edge 3.** Persisted topics + motifs (the "why"). |
| `gnosis knowledge task-code --conv <conv> --task <task>` | **Edge 1.** Code locations/methods the task changed. |
| `gnosis knowledge code-tasks --file <F> [--line L]` | **Edge 2.** Tasks that modified a file/method. |
| `gnosis knowledge kb-code --conv <conv> --task <task> (--topic i \| --motif i)` | **Edge 4.** Code a topic/motif refers to. |
| `gnosis knowledge related-by-scope (--conv <conv> --task <task> \| --scope S)` | Prior work on the same classified scope — deterministic, **key-free**. Backs `<air_related_context>`. |

## Single-Shot Policy (don't over-search)

When the relevant subsystem is still unknown, bootstrap with **one** semantic search, then navigate by links and direct file reads:

- No relevant file open yet → start with one `search`.
- Make the first query specific — name the feature, class, method, flag, or behavior.
- After the first hit, **follow a link** (run its `cmd`) or open the returned file locally. Don't fire a second broad search; inspect what the first one gave you.
- Re-search only with a narrower query (`--path <dir-of-best-hit>`) if the first was genuinely off.

## Reading results well

- Always read `data.explanation` — it tells you what the returned entity is.
- `warnings[]` are non-fatal and actionable (`no-toc` on task-kb → run `gnosis labs toc` to build KB; `methods-unnamed` on task-code → file:line anchors are still accurate even when the method name is empty).
- **`search` hits mix persisted KB items with raw conversation messages.** A `kb_hits[]` entry with `topic_index` *and* `motif_index` both `null` is a **raw message** — nothing to navigate to; read its text directly. Only hits with a non-null `topic_index`/`motif_index` are navigable KB items.

## Working inside an Air worktree group

An Air worktree **group** is one feature: every agent that created, joined or edited a checkout of the same worktree name (in any repository) records its tasks and decisions into one shared record. When you start work in a worktree (the `air-worktree create|join` output or an injected reminder names it), recover what your peers already did **before** you settle a design:

1. `gnosis knowledge worktree-tasks --name <worktree>` — the group's task history. Read each row's `headline`, `agreed_design` and `decisions`; a decision there is something a peer settled, so don't silently contradict it.
2. The rows carry decision **headlines** only. For every task whose decisions touch your change, run that row's `cmd` (`gnosis knowledge task-kb --conv <c> --task <t>`) for its whole knowledge — or look up one thing inside it with `gnosis knowledge search "<terms>" --conv <c> --task <t> --mode lexical`.
3. For a specific question across the whole group: `gnosis knowledge search "<question>" --worktree <worktree> --mode hybrid` (meaning **and** exact terms — use `--mode lexical` when you are looking for an identifier, flag, config key or file name).

If what you are about to do contradicts a recorded decision, say so and follow the decision unless the user asks otherwise; cite it by its `self` URI.

## Worked navigation patterns

**"What did we decide about <topic>?"** (decision lookup)
1. `gnosis knowledge search "<topic>"` → ranked (conv, task) hits with their matching KB items.
2. Follow the top hit's `kb` link for the full topics/motifs, or `task` for detail.

**Before editing a file an `<air_related_context>` block named** (the bright-line rule)
1. Run the `related-by-scope` command the block hands you.
2. For each prior task it returns, follow `kb` (intent) and `files-modified` (what they changed) before making your edit.

## Citing back to the user

Every response's `self` URI is stable. When you reference a past task or KB item, cite it like:

> Based on `conv://31/task/2/topic/2` — the original fix moved mypy errors out of the build path because they were blocking unrelated CI runs.

That gives the user a clickable deep-link into the conversation viewer.
