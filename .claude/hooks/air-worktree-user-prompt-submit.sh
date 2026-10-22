#!/usr/bin/env bash
# Air / worktrees — UserPromptSubmit hook.
# Tells the agent this session's ISOLATION SCOPE and what it means:
#   worktree  every coding task runs in an Air worktree; main-checkout edits are
#             denied by the PreToolUse gate until the session has one
#   branch    work in the checkout on its current branch; no worktree unless asked
#   agent     worktrees available, the agent decides; nothing is blocked
# The scope is the session's own record (written by the Air chat when the task
# started: $AIR_HOME/isolation/<session_id>.json), else the default this hook
# was installed with. When the session already created/joined worktrees (state
# written by the PostToolUse hook — one per repository the conversation works
# in), the reminder lists them so the agent keeps working inside them.
#   --default-scope worktree|branch|agent   the workspace default
#   --enforce                               legacy alias of --default-scope worktree
set -euo pipefail

DEFAULT_SCOPE=""
prev=""
for a in "$@"; do
  if [[ "$prev" == "--default-scope" ]]; then DEFAULT_SCOPE="$a"; fi
  [[ "$a" == "--enforce" ]] && DEFAULT_SCOPE="worktree"
  prev="$a"
done

# Windows: a bash spawned without HOME (a non-login Git Bash inherits only the
# Windows environment) would put the registry under /.air; USERPROFILE is the
# home Windows itself knows.
HOME="${HOME:-${USERPROFILE:-}}"
AIR_HOME="${AIR_HOME:-$HOME/.air}"
PATH="$PATH:$AIR_HOME/bin"   # a jq Air provisioned for machines without one
command -v jq >/dev/null 2>&1 || exit 0

INPUT="$(cat)"
SID="$(jq -r '.session_id // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ -z "$SID" ]] && exit 0

# Spelled as one shell word: a Windows home such as /c/Users/Assaf Chen has a
# space in it, and the agent copies this command verbatim.
TOOL="$AIR_HOME/bin/air-worktree"
case "$TOOL" in *[[:space:]]*) TOOL="\"$TOOL\"" ;; esac
STATE="${TMPDIR:-/tmp}/air-worktree-hook-${SID}.json"

# The session's isolation scope: its own record, else the installed default,
# else (a pre-scope install without arguments) the agent decides.
SCOPE=""
SCOPE_FILE="$AIR_HOME/isolation/${SID}.json"
if [[ -f "$SCOPE_FILE" ]]; then
  SCOPE="$(jq -r '.scope // empty' "$SCOPE_FILE" 2>/dev/null || true)"
  case "$SCOPE" in worktree|branch|agent) ;; *) SCOPE="$DEFAULT_SCOPE" ;; esac
elif [[ ! -f "$STATE" ]]; then
  # NOT OUR SESSION, NOT OUR BUSINESS (user decision 2026-10-13): no Air record and
  # no worktree of its own → say nothing. (A recordless session that DID create or
  # join a worktree on request still gets the list below — that is help, not a rule.)
  exit 0
fi
case "$SCOPE" in worktree|branch|agent) ;; *) SCOPE="agent" ;; esac

LIST=""
if [[ -f "$STATE" ]]; then
  LIST="$(jq -r '
    (if (.worktrees | type) == "array" then .worktrees elif (.name // "") != "" then [{name:.name, path:.path, branch:(.branch // ""), repoRoot:(.repoRoot // "")}] else [] end)
    | map("`" + .name + "`" + (if (.branch // "") != "" then " (branch `" + .branch + "`)" else "" end) + " at `" + .path + "`" + (if (.repoRoot // "") != "" then " — repo " + .repoRoot else "" end))
    | join("; ")' "$STATE" 2>/dev/null || true)"
fi

# The group's shared knowledge: gnosis scopes `knowledge search` to every
# conversation affiliated with the worktree name (all repositories of the set).
KNOWLEDGE=""
if [[ -f "$STATE" ]]; then
  PRIMARY="$(jq -r 'if (.worktrees | type) == "array" then (.worktrees[0].name // "") else (.name // "") end' "$STATE" 2>/dev/null || true)"
  if [[ -n "$PRIMARY" ]]; then
    KNOWLEDGE=" Shared knowledge of this worktree group (tasks and decisions recorded by every conversation working in it, any repository): \`gnosis knowledge worktree-tasks --name $PRIMARY\` lists what the group already did (open every task that touches your change with its row's \`cmd\`); \`gnosis knowledge search \"<question>\" --worktree $PRIMARY --mode hybrid\` answers a specific question — check them before settling something a peer may already have decided."
  fi
fi

if [[ -n "$LIST" ]]; then
  CONTEXT="Air worktrees for this session: $LIST. Keep ALL edits and shell commands inside those paths — cd there for commands, use absolute paths under them for file edits; the main checkouts are not your working copy. If the task needs changes in ANOTHER repository of this workspace, create (or join) a worktree there too: \`$TOOL create --name <feature-slug> --repo <that repo's path>\` — one worktree per repo per session, never a second one in the same repo. \`$TOOL status <name>\` shows what changed.$KNOWLEDGE"
else
  case "$SCOPE" in
    worktree)
      CONTEXT="Isolation scope for this session: WORKTREE ISOLATION. Before you start ANY coding task (editing, adding or deleting source files), create an isolated git worktree with \`$TOOL create --name <feature-slug>\` — name it after the feature/task from the user's request (short kebab-case, e.g. \`login-rate-limit\`; never a placeholder, don't ask the user) — and do every edit + command inside the path it prints. If the user names an existing worktree, attach to it with \`$TOOL join <name>\` instead. A task that spans several repositories in the workspace gets one worktree per repo (\`--repo <path>\` targets another repo). Pure questions, reading and research need no worktree. Edits to files in a main checkout are DENIED until the session has a worktree for that repository."
      ;;
    branch)
      CONTEXT="Isolation scope for this session: CURRENT BRANCH. Work directly in the workspace checkout on the branch that is checked out — do NOT create or join an Air worktree unless the user explicitly asks for one (then \`$TOOL create --name <feature-slug>\` / \`$TOOL join <name>\` are available). Edits are not blocked."
      ;;
    *)
      CONTEXT="Isolation scope for this session: AGENT DECIDES. Air worktrees are available — \`$TOOL create --name <feature-slug>\` makes an isolated checkout on its own branch (name it after the feature/task, short kebab-case), \`$TOOL join <name>\` attaches to one the user or another agent opened; do every edit + command inside the path it prints. Use one when the task benefits from isolation: a change that wants its own branch/PR, other agents working in the same repository, a large or risky edit, or the user asking for one. Otherwise work on the current branch. Edits are never blocked. Pure questions, reading and research need no worktree."
      ;;
  esac
fi

jq -n --arg ctx "$CONTEXT" '{
  hookSpecificOutput: {
    hookEventName: "UserPromptSubmit",
    additionalContext: $ctx
  }
}'
