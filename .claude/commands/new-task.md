---
description: Spin the described work off into its own tracked task
---

[air-new-task] Spin this off into its own tracked task: $ARGUMENTS

As the LAST thing in your reply, emit one `air-new-task` envelope (the exact
format is in your air capabilities): the goal as a one-line attribute, and the
full handoff context as the body. Write the context richly — the new agent has
none of this conversation's history, so spell out the relevant files with line
numbers, what you've found, and where to begin. Decide whether the task
belongs in this conversation's Air worktree: leave the envelope as-is to have
the task inherit it (same feature/branch), or add `worktree="separate"` to the
opening tag when the work is unrelated to what this worktree is for, so the
task starts outside it and picks its own. Choose the task's model by adding
`model="<model>"` to the opening tag with one of the
models your air capabilities list for this agent; if the user named a model
for the task, use it. Without the attribute the task runs on the agent's
default, often this chat's own model, so always set it, picking by cost: a
cheaper model for mechanical or well-scoped work, a mid-tier model for
ordinary work, and the most expensive / most capable model ONLY for highly
complex work (hard design, subtle cross-cutting bugs). When you need the task's results back, add
`result-schema="…"` to the opening tag saying what you need and in what shape
(a sentence or two, no double quotes); the task then reports to you with a
`send_message_to_agent` tool, and its results arrive as an
`<air-agent-message>` turn. Omit it when you need nothing back. Emit nothing
after it.
