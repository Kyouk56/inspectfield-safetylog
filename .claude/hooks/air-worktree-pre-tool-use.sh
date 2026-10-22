#!/usr/bin/env bash
# Air / worktrees — PreToolUse hook (matcher: Bash|Edit|Write|MultiEdit|NotebookEdit).
# TWO jobs. (1) Bash — the SESSION HANDOFF, never a gate: an `air-worktree
# create|join` command gets a pending record ($AIR_HOME/pending-links/<key>/
# <session_id>.json, key = sha1_12 of the raw name typed) that the tool claims
# to write the conversation↔worktree link itself — the link no longer depends
# on the AIR_WORKTREE_JSON line surviving the agent's pipeline (`… | head -5`
# cut it twice on 2026-10-10). Any scope, no output, exit 0.
# (2) Edit tools — the worktree gate: a session may edit a file in a repository's MAIN checkout
# only once it holds an Air worktree FOR THAT REPOSITORY (a conversation that
# spans several repos in the workspace needs one per repo). The agent is told
# exactly what to run. Fails OPEN whenever it can't be sure the rule applies:
#   • the air-worktree tool isn't installed (nothing the agent could comply with)
#   • the target is outside the workspace root (another folder we don't know)
#   • the target isn't inside a git checkout at all
#   • the workspace root is itself an Air worktree (agent launched inside one)
#   • jq is missing, or the payload has no file path
#   • AIR_WORKTREE_GATE=off
# Agent-wiring files (.claude/, CLAUDE.md, .mcp.json) are exempt — configuring
# the agent isn't a coding task.
# The gate only applies to a session whose ISOLATION SCOPE is `worktree`: the
# session's own record ($AIR_HOME/isolation/<session_id>.json, written by the
# Air chat when the task started) decides, else the default this hook was
# installed with (--default-scope worktree|branch|agent; --enforce = the legacy
# spelling of --default-scope worktree). `branch` and `agent` sessions are
# never gated.
# Windows (Git Bash): the harness hands NATIVE paths (C:\Users\…, C:/Users/…,
# cwd and file_path alike) while cd + `pwd -P` yield POSIX ones (/c/Users/…)
# and the filesystem ignores case — so every comparison below runs on canonical
# `pwd -P` spellings, case-folded there. Without this the gate mistook every
# absolute Windows path for a relative one and silently failed open.
set -euo pipefail

[[ "${AIR_WORKTREE_GATE:-on}" == "off" ]] && exit 0
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
case "$(uname -s 2>/dev/null)" in MINGW*|MSYS*|CYGWIN*) IS_WIN=1 ;; *) IS_WIN=0 ;; esac
PATH="$PATH:$AIR_HOME/bin"   # a jq Air provisioned for machines without one
command -v jq >/dev/null 2>&1 || exit 0

is_abs() { case "$1" in /*|[A-Za-z]:/*|[A-Za-z]:\\*|\\\\*) return 0 ;; *) return 1 ;; esac; }
fold() { if [[ "$IS_WIN" == "1" ]]; then printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; else printf '%s' "$1"; fi; }
# $1 is $2 itself or lies under it (both canonical).
under() { case "$(fold "$1")/" in "$(fold "$2")"/*) return 0 ;; *) return 1 ;; esac; }
same_dir() { [[ "$(fold "$1")" == "$(fold "$2")" ]]; }
# $1 relative to dir $2 (both canonical), or $1 itself when not under it.
rel_to() {
  if [[ "$(fold "$1")" == "$(fold "$2")"/* ]]; then printf '%s' "${1:$(( ${#2} + 1 ))}"; else printf '%s' "$1"; fi
}
# One shell word for the command the agent is told to run (a home like
# /c/Users/Assaf Chen has a space in it).
sh_word() { case "$1" in *[[:space:]]*) printf '"%s"' "$1" ;; *) printf '%s' "$1" ;; esac; }

INPUT="$(cat)"
SID="$(jq -r '.session_id // empty' <<<"$INPUT" 2>/dev/null || true)"
TOOL="$(jq -r '.tool_name // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ -z "$SID" || -z "$TOOL" ]] && exit 0

sha1_12() {
  if command -v shasum >/dev/null 2>&1; then printf '%s' "$1" | shasum -a 1 | cut -c1-12
  elif command -v sha1sum >/dev/null 2>&1; then printf '%s' "$1" | sha1sum | cut -c1-12
  else printf 'anonymous'; fi
}
# The raw worktree names the command's OWN `air-worktree create|join`
# invocations name, one per line: the join positional, else `--name X` /
# `--name=X` (quotes removed the way the shell removes them); an empty line for
# a nameless create. Must agree with the tool's `pending_key`. Only a segment
# (split on ; & | and newlines) whose COMMAND WORD is air-worktree counts — a
# later `docker run --name db` filed the handoff under "db" and the tool never
# found it. Same parser as the PostToolUse hook's command_names.
handoff_names() {
  local seg tok verb i n
  local -a toks
  while IFS= read -r seg; do
    # Tokenise honouring quotes, with no expansion; a quote the split cut in
    # half (a string literal holding ; or |) makes xargs fail → no tokens.
    toks=()
    while IFS= read -r tok; do toks+=("$tok"); done < <(printf '%s' "$seg" | xargs -n1 printf '%s\n' 2>/dev/null || true)
    i=0
    while [[ $i -lt ${#toks[@]} ]] && [[ "${toks[i]}" == [A-Za-z_]*=* || "${toks[i]}" == "(" || "${toks[i]}" == "{" ]]; do i=$((i + 1)); done
    [[ $i -lt ${#toks[@]} && "${toks[i]##*/}" == "air-worktree" ]] || continue
    verb=""; n=""; i=$((i + 1))
    while [[ $i -lt ${#toks[@]} ]]; do
      tok="${toks[i]}"
      case "$tok" in
        --name=*) [[ "$verb" == "create" || "$verb" == "adopt" ]] && n="${tok#--name=}" ;;
        --name) [[ "$verb" == "create" || "$verb" == "adopt" ]] && n="${toks[i + 1]:-}"; i=$((i + 1)) ;;
        --repo|--base) i=$((i + 1)) ;;
        -*) ;;
        *)
          if [[ -z "$verb" ]]; then verb="$tok"
          elif [[ "$verb" == "join" && -z "$n" ]]; then n="$tok"
          fi ;;
      esac
      i=$((i + 1))
    done
    # A nameless create/adopt prints an empty line (= any name); adopt's positional is a path.
    if [[ "$verb" == "create" || "$verb" == "adopt" || ( "$verb" == "join" && -n "$n" ) ]]; then printf '%s\n' "$n"; fi
  done < <(printf '%s\n' "$1" | tr ';|&' '\n\n\n')
  return 0
}
if [[ "$TOOL" == "Bash" ]]; then
  CMD="$(jq -r '.tool_input.command // empty' <<<"$INPUT" 2>/dev/null || true)"
  [[ "$CMD" == *air-worktree* ]] || exit 0
  case "$CMD" in *" create"*|*" join "*|*" join"$'\n'*|*" adopt"*) ;; *) exit 0 ;; esac
  TRANSCRIPT="$(jq -r '.transcript_path // empty' <<<"$INPUT" 2>/dev/null || true)"
  CWD="$(jq -r '.cwd // empty' <<<"$INPUT" 2>/dev/null || true)"
  umask 077
  while IFS= read -r raw; do
    if [[ -n "$raw" ]]; then key="$(sha1_12 "$raw")"; else key="anonymous"; fi
    dir="$AIR_HOME/pending-links/$key"
    mkdir -p "$dir" 2>/dev/null || exit 0
    jq -n --arg sid "$SID" --arg t "$TRANSCRIPT" --arg cwd "$CWD" --arg cmd "$CMD" --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      '{sessionId:$sid, transcriptPath:(if $t=="" then null else $t end), cwd:(if $cwd=="" then null else $cwd end), command:$cmd, at:$at, version:1}' \
      >"$dir/$SID.json.tmp" 2>/dev/null && mv "$dir/$SID.json.tmp" "$dir/$SID.json"
  done < <(handoff_names "$CMD" | awk '!seen[$0]++')
  exit 0
fi
case "$TOOL" in Edit|Write|MultiEdit|NotebookEdit) ;; *) exit 0 ;; esac

# Only a worktree-isolation session is gated. A gate installed before scopes
# existed carries no argument — and was only ever registered when enforcing —
# so no argument means the default is `worktree`.
SCOPE=""
SCOPE_FILE="$AIR_HOME/isolation/${SID}.json"
# NOT OUR SESSION, NOT OUR BUSINESS (user decision 2026-10-13): an Air chat writes
# this record before its first prompt (the picked scope, else the workspace
# default). A session with no record — Claude Code in a terminal, a headless run,
# another tool's agent — is never gated, whatever the hooks were installed with.
# Inside Air sessions the installed default still applies to a record with an
# unknown scope.
[[ -f "$SCOPE_FILE" ]] || exit 0
if [[ -f "$SCOPE_FILE" ]]; then
  SCOPE="$(jq -r '.scope // empty' "$SCOPE_FILE" 2>/dev/null || true)"
fi
case "$SCOPE" in worktree|branch|agent) ;; *) SCOPE="${DEFAULT_SCOPE:-worktree}" ;; esac
[[ "$SCOPE" == "worktree" ]] || exit 0

WT_TOOL="$AIR_HOME/bin/air-worktree"
# -f, not -x: NTFS has no execute bit (the installer's chmod is a no-op there).
[[ -f "$WT_TOOL" ]] || exit 0

CWD="$(jq -r '.cwd // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ "$IS_WIN" == "1" ]] && CWD="${CWD//\\//}"
ROOT="${CLAUDE_PROJECT_DIR:-$CWD}"
[[ -n "$ROOT" ]] || exit 0
ROOT="$(cd "$ROOT" 2>/dev/null && pwd -P)" || exit 0

# Workspace root inside the Air worktrees home → the agent is already in a
# worktree by construction.
WT_HOME_REAL="$(cd "$AIR_HOME/worktrees" 2>/dev/null && pwd -P || printf '%s' "$AIR_HOME/worktrees")"
if under "$ROOT" "$WT_HOME_REAL"; then exit 0; fi

FILE="$(jq -r '.tool_input.file_path // .tool_input.path // .tool_input.notebook_path // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ -n "$FILE" ]] || exit 0
[[ "$IS_WIN" == "1" ]] && FILE="${FILE//\\//}"
case "$FILE" in
  "~"|"~/"*) FILE="$HOME${FILE#\~}" ;;
esac
is_abs "$FILE" || FILE="${CWD:-$ROOT}/$FILE"
DIR="$(cd "$(dirname "$FILE")" 2>/dev/null && pwd -P)" || exit 0
FILE="$DIR/$(basename "$FILE")"

# Outside the workspace root → not ours to gate. (Repos elsewhere on disk are
# covered when they are opened as the workspace.)
under "$FILE" "$ROOT" || exit 0
# Inside the Air worktrees home (e.g. a symlinked layout) → fine.
if under "$FILE" "$WT_HOME_REAL"; then exit 0; fi
# Agent-wiring files are exempt (relative to the repo the file is in, below,
# and to the workspace root).
REL="$(rel_to "$FILE" "$ROOT")"
case "$REL" in
  .claude/*|CLAUDE.md|CLAUDE.local.md|.mcp.json|.air/*) exit 0 ;;
esac

# The repository the file belongs to (a multi-root workspace may hold several).
FILE_REPO="$(git -C "$DIR" rev-parse --show-toplevel 2>/dev/null || true)"
[[ -n "$FILE_REPO" ]] || exit 0
FILE_REPO="$(cd "$FILE_REPO" && pwd -P)"
REPO_REL="$(rel_to "$FILE" "$FILE_REPO")"
case "$REPO_REL" in
  .claude/*|CLAUDE.md|CLAUDE.local.md|.mcp.json|.air/*) exit 0 ;;
esac
# A file inside a linked worktree of that repo (git-common-dir ≠ .git of toplevel)
# is already isolated work — allow.
COMMON="$(git -C "$DIR" rev-parse --git-common-dir 2>/dev/null || true)"
GITDIR="$(git -C "$DIR" rev-parse --git-dir 2>/dev/null || true)"
if [[ -n "$COMMON" && -n "$GITDIR" ]]; then
  COMMON_ABS="$(cd "$DIR" && cd "$COMMON" 2>/dev/null && pwd -P || true)"
  GITDIR_ABS="$(cd "$DIR" && cd "$GITDIR" 2>/dev/null && pwd -P || true)"
  if [[ -n "$COMMON_ABS" && -n "$GITDIR_ABS" ]] && ! same_dir "$COMMON_ABS" "$GITDIR_ABS"; then exit 0; fi
fi

# Does the session hold a worktree for THIS repository?
STATE="${TMPDIR:-/tmp}/air-worktree-hook-${SID}.json"
HAVE=""
if [[ -f "$STATE" ]]; then
  HAVE="$(jq -r '
    (if (.worktrees | type) == "array" then .worktrees elif (.name // "") != "" then [{name:.name, path:.path, repoRoot:(.repoRoot // "")}] else [] end)
    | map(select((.repoRoot // "") != "")) | map(.repoRoot + "\t" + .name) | .[]' "$STATE" 2>/dev/null || true)"
  # Legacy v1 state without repoRoot: keep the old "any worktree unlocks" rule.
  if [[ -z "$HAVE" ]] && [[ -n "$(jq -r '.name // empty' "$STATE" 2>/dev/null || true)" ]] && [[ "$(jq -r '(.worktrees // []) | length' "$STATE" 2>/dev/null || echo 0)" == "0" ]]; then
    exit 0
  fi
fi
OTHERS=""
while IFS=$'\t' read -r root name; do
  [[ -n "$root" ]] || continue
  root_real="$(cd "$root" 2>/dev/null && pwd -P || printf '%s' "$root")"
  if same_dir "$root_real" "$FILE_REPO"; then
    exit 0
  fi
  OTHERS="${OTHERS:+$OTHERS, }$name ($(basename "$root"))"
done <<<"$HAVE"

TOOL_W="$(sh_word "$WT_TOOL")"
REPO_W="$(sh_word "$FILE_REPO")"
if [[ -n "$OTHERS" ]]; then
  REASON="Air worktree gate: this session's worktree(s) — $OTHERS — belong to other repositories; \`$REPO_REL\` is in \`$FILE_REPO\`, which has none yet. Create one there first: \`$TOOL_W create --name <feature-slug> --repo $REPO_W\` (or \`join <name>\` if one exists), then make this edit at the same relative path INSIDE the worktree path it prints."
else
  REASON="Air worktree gate: this session has no worktree yet, so editing \`$REPO_REL\` in the main checkout of \`$FILE_REPO\` is denied. Start the coding task properly: run \`$TOOL_W create --name <feature-slug>\` (a short kebab-case name for the feature/task you are working on; add \`--repo $REPO_W\` when your cwd is another repo), then make this edit at the same relative path INSIDE the worktree path it prints (cd there for shell commands). To attach to an existing worktree the user named, run \`$TOOL_W join <name>\` instead."
fi

jq -n --arg reason "$REASON" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: $reason
  }
}'
