---
name: gnosis-search
description: "Search the topics and recorded decisions of previous conversations in this project (`gnosis labs search-toc`). Use when the user refers to an earlier session, asks what was discussed or decided before, or wants prior work on a topic."
---
# Search Previous Conversations

**Skill:** Use `gnosis labs search-toc` to search topics and motifs (knowledge items) from previous conversations.

**Gnosis executable:** `C:\Users\jdesr\.local\bin\gnosis`

## What it does

`gnosis labs search-toc` performs semantic search across all persisted topics (chapter-like groupings of messages) and motifs (specific decisions, resources, summaries) extracted from your previous conversations. It finds the most relevant items based on your query.

## When to use it

Use this when you need to:
- Look up how something was done in a previous conversation
- Find a prior decision or design rationale
- Locate relevant context or examples from past work
- Search for resources, links, or references mentioned before

## How to use it

**Linux/macOS (bash):**
```bash
C:\Users\jdesr\.local\bin\gnosis labs search-toc <query> --project "C:\Users\jdesr\Projects\InspectField-Equipment Log" [options]
```

**Windows (PowerShell or cmd):**
```powershell
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc <query> --project "C:\Users\jdesr\Projects\InspectField-Equipment Log" [options]
```

### Basic usage

Search for topics and motifs related to your query:

**Linux/macOS (bash):**
```bash
C:\Users\jdesr\.local\bin\gnosis labs search-toc "authentication patterns" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
C:\Users\jdesr\.local\bin\gnosis labs search-toc "database optimization" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
C:\Users\jdesr\.local\bin\gnosis labs search-toc "error handling strategy" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
```

**Windows (PowerShell or cmd):**
```powershell
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "authentication patterns" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "database optimization" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "error handling strategy" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log"
```

### Options

- `--k N` (default: 10): Show top N results
- `--project NAME`: Filter by project (exact match against `conversations.project`)
- `--path FILE`: Filter by conversation file (exact match against `conversations.path`)
- `--format table|json` (default: table): Output format

### Examples

**Linux/macOS (bash):**
```bash
# Search across all conversations
C:\Users\jdesr\.local\bin\gnosis labs search-toc "how do we handle rate limiting"

# Show top 5 results
C:\Users\jdesr\.local\bin\gnosis labs search-toc "caching strategy" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log" --k 5

# Get structured output
C:\Users\jdesr\.local\bin\gnosis labs search-toc "testing patterns" --project "C:\Users\jdesr\Projects\InspectField-Equipment Log" --format json
```

**Windows (PowerShell or cmd):**
```powershell
# Search across all conversations
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "how do we handle rate limiting"

# Show top 5 results
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "caching strategy" --k 5

# Get structured output
"C:\Users\jdesr\.local\bin\gnosis" labs search-toc "testing patterns" --format json
```

## Output

Each result shows:
- **score**: Relevance score (0–1, higher = more relevant)
- **type**: `topic`, `motif`, `user_message`, etc.
- **title**: The content headline
- **conv**: Which conversation file it came from
- **loc**: Location details (task #, topic #, motif # with kind, message #)

## Notes
- Search is semantic (meaning-based), not keyword-based
- Use shorter queries (2–5 words) for better relevance
