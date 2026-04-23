# Clojent

A minimal Clojure AI agent, built to learn Clojure and how LLM coding agents work.

> This project was heavily AI-assisted — built with Claude as a pair-programming tool to explore and understand Clojure idioms and agent architecture. The code and implementation guide reflect that learning process.

Runs locally via [Ollama](https://ollama.com/) — no API keys required.

## How it works

The agent runs a tool loop for each user turn:

1. Build a conversation with a system prompt describing available tools and response format
2. Call the LLM — must reply with JSON (`tool-call` or `final-answer`)
3. If `tool-call`: execute the tool, append the result, go to 2
4. If `final-answer`: print the answer and wait for the next user input

Structured outputs use [Malli](https://github.com/metosin/malli) schemas. The schema is converted to JSON Schema and injected into the system prompt. If the reply fails validation, the error is fed back to the model and it retries (up to 3 times).

## Architecture

| Namespace          | Responsibility                                               |
|--------------------|--------------------------------------------------------------|
| `agent.core`       | Chat loop, agentic turn logic, entry point                   |
| `agent.llm`        | HTTP client for Ollama                                       |
| `agent.tools`      | Tool registry (`get-current-datetime`, `bash`)               |
| `agent.schemas`    | Malli schemas for LLM responses                              |
| `agent.structured` | JSON parsing, schema→system-prompt, structured call with retries |

## Implementation guide

The project was built incrementally. Each step below corresponds to one or more commits, so you can check out any point and see a working slice of the system.

### Step 1 — Echo CLI (`4309686`)

Scaffold the project: `deps.edn`, `.gitignore`, and a `core.clj` whose `-main` reads a line, prints it back, and loops. No deps beyond Clojure itself. The goal is a runnable REPL loop before touching anything AI-related.

### Step 2 — Named functions (`da47826`)

Extract the monolithic `-main` body into small, named functions: `exit-command?`, `process-input`, `prompt`, and `chat-loop`. Behavior is identical — the refactor just makes each concern independently evaluable in the REPL. Good habit before adding complexity.

### Step 3 — nREPL alias (`742d340`)

Add a `:nrepl` alias to `deps.edn` so `clojure -M:nrepl` starts an nREPL server. From here, every further step is developed interactively through the REPL rather than restart-driven.

### Step 4 — Ollama HTTP client (`39cda41`)

Add `hato` (HTTP) and `cheshire` (JSON) to `deps.edn`. Replace the echo body with a POST to `http://localhost:11434/api/chat`. The model name and endpoint are hardcoded. The agent now gives real LLM responses.

### Step 5 — Conversation history (`cf00575`)

Pass the growing message vector into `chat-loop` as an accumulator. Each user turn appends a `{:role "user"}` message; each assistant reply appends a `{:role "assistant"}` message. The model now has context across turns.

### Step 6 — Structured JSON output (`1f12475`)

Add [Malli](https://github.com/metosin/malli) to `deps.edn`. Define a schema for the expected reply shape, convert it to JSON Schema, and inject it into a system prompt. Parse the model's reply as JSON and validate it. If validation fails, append the error as a user message and retry — up to 3 times. This is the core technique that makes the agent reliable enough to act on LLM output programmatically.

### Step 7 — Extract modules (`fcbd537`)

`core.clj` has grown large. Split it into focused namespaces:

- `agent.llm` — HTTP call to Ollama
- `agent.schemas` — Malli schema definitions
- `agent.structured` — JSON parsing, schema→prompt, structured call with retries
- `agent.core` — chat loop and entry point (now thin)

### Step 8 — Tests (`ed4c896`)

Add `clojure.test` tests for the three extracted modules. Tests cover schema validation, JSON parsing edge cases, and the retry logic in `structured`. Running `clojure -M:test` gives a fast feedback loop without needing Ollama.

### Step 9 — Tool calling (`208a3fc`, `59eb07c`)

Extend the Malli schema to allow two reply shapes: `tool-call` (a tool name + arguments) and `final-answer` (a plain string). Add `agent.tools` with a registry map and a `dispatch` function. Change `agent.core` to loop until the model emits `final-answer`: on each `tool-call` it executes the tool, appends the result as a user message, and calls the LLM again. The first tool registered is `get-current-datetime`.

### Step 10 — Bash tool (`702feac`)

Add a `bash` tool to the registry that runs an arbitrary shell command via `clojure.java.shell/sh` and returns stdout (or stderr on failure). The model can now read files, run tests, and inspect the environment — completing the agent loop.

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) + JDK 21
- [Ollama](https://ollama.com/) running locally with `qwen3:8b` pulled

```
brew install clojure/tools/clojure
brew install openjdk@21
brew install ollama

ollama pull qwen3:8b
```

## Run

```
clojure -M:run
```

Type `quit` or `exit` to stop. The agent has access to the current time and can run bash commands.

## Test

```
clojure -M:test
```

## REPL

Start an nREPL server (connects with Calva, CIDER, or any nREPL client):

```
clojure -M:nrepl
```

Each namespace has a `(comment ...)` block at the bottom with ready-to-run examples for exploring that module interactively.
