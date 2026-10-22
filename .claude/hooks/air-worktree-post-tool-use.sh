#!/usr/bin/env bash
# Air / worktrees — PostToolUse hook (matcher: Bash).
# When the agent ran `air-worktree create|join`, parse the tool's
# `AIR_WORKTREE_JSON {…}` output line and
#   1. record the worktree in the per-session state file — ONE ENTRY PER REPO:
#      a conversation that spans several repositories in the workspace holds a
#      worktree in each (`.worktrees[]`, keyed by repoRoot; a new worktree for
#      the same repo replaces the old entry). The legacy top-level name/path/
#      branch mirror the most recent entry so older readers keep working. The
#      UserPromptSubmit reminder + the PreToolUse gate read this file.
#   2. write a conversation↔worktree link record the Air Sessions panel reads:
#      ${AIR_HOME:-~/.air}/worktrees/<repoKey>/links/<name>/<session_id>.json
#      { sessionId, transcriptPath, role: creator|joined, at, cwd }
# `air-worktree remove <name>` drops that entry from the session state.
# LOUD, NOT SILENT (2026-10-10): a create/join whose output reached this hook
# WITHOUT its marker (piped through head/tail, summarised by a subagent) is
# handled from the link the tool wrote itself (PreToolUse handoff →
# `claim_pending_link`): the session state is rebuilt from that worktree's
# registry record. When no link exists either, the agent is told in
# `additionalContext` to run a bare `join` — the panel's "No conversation
# linked yet" must never be the first sign.
# Never blocks; exits 0 on anything unexpected.
set -euo pipefail

# Windows: a bash spawned without HOME (a non-login Git Bash inherits only the
# Windows environment) would put the registry under /.air; USERPROFILE is the
# home Windows itself knows.
HOME="${HOME:-${USERPROFILE:-}}"
AIR_HOME="${AIR_HOME:-$HOME/.air}"
PATH="$PATH:$AIR_HOME/bin"   # a jq Air provisioned for machines without one
command -v jq >/dev/null 2>&1 || exit 0
INPUT="$(cat)"
SID="$(jq -r '.session_id // empty' <<<"$INPUT" 2>/dev/null || true)"
TOOL="$(jq -r '.tool_name // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ -z "$SID" || "$TOOL" != "Bash" ]] && exit 0

CMD="$(jq -r '.tool_input.command // empty' <<<"$INPUT" 2>/dev/null || true)"
[[ "$CMD" == *air-worktree* ]] || exit 0

STATE="${TMPDIR:-/tmp}/air-worktree-hook-${SID}.json"
umask 077

# The tool's stdout, whatever shape the harness hands it in: a plain string, an
# object with stdout/output/content, or a JSON-encoded string of either.
OUT="$(jq -r '.tool_response
  | (if type == "object" then (.stdout // .output // .content // "") else . end)
  | if type == "string" then . else tojson end' <<<"$INPUT" 2>/dev/null || true)"
case "$OUT" in
  \{*|\"*)
    DECODED="$(jq -r 'if type == "object" then (.stdout // .output // tojson) elif type == "string" then . else tojson end' <<<"$OUT" 2>/dev/null || true)"
    [[ -n "$DECODED" ]] && OUT="$DECODED"
    ;;
esac

# A single Bash call may run the tool several times (`create` in one repo,
# `cd ../other && create` in another; a `remove` then a `create`), so process
# EVERY marker line the tool printed, in order, instead of guessing one verb.
# The role for a create/join line: `create` (or `adopt`) only in the command →
# creator, `join` only → joined, mixed/unknown → creator when the record was
# created in the last few minutes (the create just happened), else joined.
# HAS_CREATE / HAS_JOIN are set below from `command_verbs` — the SAME tokeniser
# the PreToolUse handoff uses, so only a segment whose command word is
# air-worktree counts. The regex this replaced matched the words anywhere in
# the command text: 2026-10-12 a `grep -nE "create|join" …/air-worktree` and an
# `echo "… air-worktree create …"` each produced the "NOT linked — run join"
# instruction, and an agent that believed it would have run a pointless join.
HAS_CREATE=0; HAS_JOIN=0

# Current state as a v2 document: {"worktrees":[…]} (migrating a v1 single record).
load_state() {
  if [[ -f "$STATE" ]]; then
    jq 'if (.worktrees | type) == "array" then .
        elif (.name // "") != "" then {worktrees: [{name:.name, path:.path, branch:(.branch // ""), repoKey:(.repoKey // ""), repoRoot:(.repoRoot // ""), role:(.role // "joined")}]}
        else {worktrees: []} end' "$STATE" 2>/dev/null || printf '{"worktrees":[]}'
  else
    printf '{"worktrees":[]}'
  fi
}
# Write v2 + legacy mirror of the most recent entry.
save_state() {
  jq '. as $s | ($s.worktrees | if length > 0 then .[-1] else {} end) as $last
      | $s + {name: ($last.name // ""), path: ($last.path // ""), branch: ($last.branch // ""), repoKey: ($last.repoKey // ""), repoRoot: ($last.repoRoot // ""), role: ($last.role // "")}' \
    >"$STATE.tmp" 2>/dev/null && mv "$STATE.tmp" "$STATE"
}

TRANSCRIPT="$(jq -r '.transcript_path // empty' <<<"$INPUT" 2>/dev/null || true)"
CWD="$(jq -r '.cwd // empty' <<<"$INPUT" 2>/dev/null || true)"
NOW_EPOCH="$(date -u +%s)"
TOUCHED=0
# GNU stat first: on Linux `stat -f` means FILESYSTEM status and treats `%m` as a
# file name, printing a multi-line "File: …" block before it fails, which the
# arithmetic below then read as a variable name under `set -u`. BSD `stat -c` is
# an illegal option and prints nothing to stdout. Anything non-numeric reads as 0.
file_mtime() {
  local m
  m="$(stat -c %Y "$1" 2>/dev/null)" || m="$(stat -f %m "$1" 2>/dev/null)" || m=0
  [[ "$m" =~ ^[0-9]+$ ]] || m=0
  printf '%s' "$m"
}
# The raw worktree names the command's OWN air-worktree invocations name (join
# positional / create `--name X`), one per line, for narrowing the link lookup
# below; empty when none can be read. Only a segment (split on ; & | and
# newlines) whose COMMAND WORD is air-worktree counts: a `docker run --name db`
# after the create, or "join" inside a commit message, is not this tool's
# argument — a wrong name here filtered the real link out and told the agent
# to join a worktree that does not exist.
# Every verb of the command's OWN air-worktree invocations, one per line
# (create / join / adopt / …); nothing for a command that only mentions them.
command_verbs() {
  local seg tok verb i
  local -a toks
  while IFS= read -r seg; do
    toks=()
    while IFS= read -r tok; do toks+=("$tok"); done < <(printf '%s' "$seg" | xargs -n1 printf '%s\n' 2>/dev/null || true)
    i=0
    while [[ $i -lt ${#toks[@]} ]] && [[ "${toks[i]}" == [A-Za-z_]*=* || "${toks[i]}" == "(" || "${toks[i]}" == "{" ]]; do i=$((i + 1)); done
    [[ $i -lt ${#toks[@]} && "${toks[i]##*/}" == "air-worktree" ]] || continue
    verb=""; i=$((i + 1))
    while [[ $i -lt ${#toks[@]} ]]; do
      tok="${toks[i]}"
      case "$tok" in
        --name|--repo|--base) i=$((i + 1)) ;;
        -*) ;;
        *) [[ -z "$verb" ]] && verb="$tok" ;;
      esac
      i=$((i + 1))
    done
    [[ -n "$verb" ]] && printf '%s\n' "$verb"
  done < <(printf '%s\n' "$1" | tr ';|&' '\n\n\n')
  return 0
}
command_names() {
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
VERBS="$(command_verbs "$CMD")"
case $'\n'"$VERBS"$'\n' in *$'\ncreate\n'*|*$'\nadopt\n'*) HAS_CREATE=1 ;; esac
case $'\n'"$VERBS"$'\n' in *$'\njoin\n'*) HAS_JOIN=1 ;; esac
# Is NAME (a link dir, possibly de-duplicated `<want>-N`) one the command named?
wanted() {
  local w
  while IFS= read -r w; do
    [[ -n "$w" ]] || continue
    case "$1" in "$w"|"$w"-[0-9]*) return 0 ;; esac
  done <<<"$WANT"
  return 1
}

handle_removed() {
  local line="$1" removed rkey
  removed="${line%% *}"; rkey=""
  [[ "$line" == *" "* ]] && rkey="${line#* }"
  removed="${removed%%[;&|]*}"
  [[ -n "$removed" && -f "$STATE" ]] || return 0
  load_state | jq --arg n "$removed" --arg k "$rkey" '.worktrees |= map(select(.name != $n or ($k != "" and (.repoKey // "") != $k)))' | save_state || true
  TOUCHED=1
}

handle_record() {
  local json="$1" name wpath branch key root created role age
  jq -e . <<<"$json" >/dev/null 2>&1 || return 0
  name="$(jq -r '.name // empty' <<<"$json")"
  wpath="$(jq -r '.path // empty' <<<"$json")"
  branch="$(jq -r '.branch // empty' <<<"$json")"
  key="$(jq -r '.repoKey // empty' <<<"$json")"
  root="$(jq -r '.repoRoot // empty' <<<"$json")"
  created="$(jq -r '.createdAt // empty' <<<"$json")"
  [[ -n "$name" && -n "$wpath" && -n "$key" ]] || return 0
  if [[ "$HAS_CREATE" == "1" && "$HAS_JOIN" != "1" ]]; then role=creator
  elif [[ "$HAS_JOIN" == "1" && "$HAS_CREATE" != "1" ]]; then role=joined
  else
    role=joined
    if [[ -n "$created" ]]; then
      age=$(( NOW_EPOCH - $(date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$created" +%s 2>/dev/null || date -u -d "$created" +%s 2>/dev/null || echo 0) ))
      [[ "$age" -ge 0 && "$age" -lt 300 ]] && role=creator
    fi
  fi
  # Upsert: one worktree per repo (same repoKey replaces), appended last so the
  # legacy mirror = most recent.
  load_state | jq --arg name "$name" --arg path "$wpath" --arg branch "$branch" --arg key "$key" --arg root "$root" --arg role "$role" \
    '.worktrees |= (map(select(.repoKey != $key)) + [{name:$name, path:$path, branch:$branch, repoKey:$key, repoRoot:$root, role:$role}])' \
    | save_state || true
  TOUCHED=1
  local link_dir="$AIR_HOME/worktrees/$key/links/$name" link
  mkdir -p "$link_dir" 2>/dev/null || return 0
  link="$link_dir/$SID.json"
  # A session that joins its own creation stays "creator".
  if [[ -f "$link" && "$(jq -r '.role // empty' "$link" 2>/dev/null || true)" == "creator" ]]; then
    role=creator
  fi
  jq -n --arg sid "$SID" --arg t "$TRANSCRIPT" --arg role "$role" --arg cwd "$CWD" \
    --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{sessionId:$sid, transcriptPath:(if $t=="" then null else $t end), role:$role, cwd:(if $cwd=="" then null else $cwd end), at:$at, version:1}' \
    >"$link.tmp" 2>/dev/null && mv "$link.tmp" "$link"
}

# Walk the output line by line so removals and creates interleave correctly.
while IFS= read -r line; do
  case "$line" in
    "AIR_WORKTREE_REMOVED "*) handle_removed "${line#AIR_WORKTREE_REMOVED }" ;;
    "AIR_WORKTREE_JSON "*) handle_record "${line#AIR_WORKTREE_JSON }" ;;
    *AIR_WORKTREE_JSON*)
      # JSON-encoded stdout leaves escaped quotes; unescape the object once.
      obj="$(printf '%s\n' "$line" | sed -n 's/.*AIR_WORKTREE_JSON \({[^}]*}\).*/\1/p' | head -n1 | sed 's/\\"/"/g')"
      [[ -n "$obj" ]] && handle_record "$obj"
      ;;
  esac
done <<<"$OUT"

# A create/join with NO marker in the output: the tool may still have linked the
# session itself — rebuild the state from that link's registry record (a link
# written in the last 10 minutes, under the named worktree when the command
# names one). Nothing linked → say so to the agent, now.
if [[ "$TOUCHED" == "0" && ( "$HAS_CREATE" == "1" || "$HAS_JOIN" == "1" ) ]]; then
  WANT="$(command_names "$CMD")"
  # A nameless create among them could have made any name: no narrowing.
  [[ $'\n'"$WANT"$'\n' == *$'\n'$'\n'* ]] && WANT=""
  FOUND=0
  for link in "$AIR_HOME"/worktrees/*/links/*/"$SID.json"; do
    [[ -f "$link" ]] || continue
    age=$(( NOW_EPOCH - $(file_mtime "$link") ))
    [[ "$age" -ge 0 && "$age" -lt 600 ]] || continue
    name="$(basename "$(dirname "$link")")"
    if [[ -n "$WANT" ]] && ! wanted "$name"; then continue; fi
    key="$(basename "$(dirname "$(dirname "$(dirname "$link")")")")"
    meta="$AIR_HOME/worktrees/$key/meta/$name.json"
    [[ -f "$meta" ]] || continue
    handle_record "$(jq -c . "$meta" 2>/dev/null || true)"
    FOUND=1
  done
  if [[ "$FOUND" == "0" ]]; then
    WANT="${WANT%%$'\n'*}"   # the advice names one worktree: the first invocation's
    TOOL_PATH="$AIR_HOME/bin/air-worktree"
    case "$TOOL_PATH" in *[[:space:]]*) TOOL_PATH="\"$TOOL_PATH\"" ;; esac
    CTX="air-worktree: this create/join reached the Air linker WITHOUT its AIR_WORKTREE_JSON line (the output was piped through head/tail or otherwise filtered) and no link for this session was written, so this conversation is NOT linked to the worktree — the Air panel will show it as 'No conversation linked yet' and its knowledge will not be recorded under the worktree. Fix it now: run \`$TOOL_PATH join ${WANT:-<name>}\` on its own, with NOTHING after it (no \`| head\`, no \`| tail\`), and keep working inside the path it prints."
    jq -n --arg ctx "$CTX" '{hookSpecificOutput:{hookEventName:"PostToolUse", additionalContext:$ctx}}'
  fi
fi

# No worktree left → the session is back to "none" (the gate re-arms).
if [[ "$TOUCHED" == "1" && -f "$STATE" && "$(jq -r '(.worktrees // []) | length' "$STATE" 2>/dev/null || echo 0)" == "0" ]]; then
  rm -f "$STATE"
fi
exit 0
