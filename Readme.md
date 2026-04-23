# Clojent

A minimal Clojure AI agent, built to learn Clojure and how LLM coding agents work.

🦜 🦜 🪨

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

Building an agent breaks down into four concerns, each of which can be built and validated in isolation. This repo was built in that order — you can check out any commit to see a working slice.

### 1. Get a runnable loop first

Before touching anything AI-related, build the outer shell: a REPL loop that reads input and prints output. Keep it to a single file with no external deps.

The point is to have something you can run, break, and fix cheaply. It also forces you to name the moving parts early — `exit-command?`, `chat-loop`, etc. — so that when you start adding LLM calls there's already a clear place for them to go. See `4309686` (init) → `da47826` (named functions).

Set up an nREPL alias at this stage so every subsequent step can be developed interactively (`742d340`).

### 2. Connect to the LLM

Add an HTTP client and POST to the model API. The only thing this step adds is real responses — keep it minimal. Hardcode the model name and endpoint; there's no benefit in parameterizing them yet.

Conversation history is a simple accumulator: each user turn appends `{:role "user" :content ...}`, each reply appends `{:role "assistant" :content ...}`, and the whole vector is sent with every request. Without this the model has no context between turns. See `39cda41` → `cf00575`.

### 3. Make output structured and reliable

This is the step that turns an LLM into something you can program against. The model must reply with JSON matching a known schema. The technique:

1. Define the expected shape as a [Malli](https://github.com/metosin/malli) schema
2. Convert it to JSON Schema and inject it into the system prompt
3. Parse the reply and validate it against the schema
4. If validation fails, append the error as a user message and retry (up to 3 times)

The retry loop is load-bearing. Models occasionally produce malformed JSON or miss required fields — feeding the error back and letting the model self-correct is simpler and more reliable than any post-processing heuristic. See `1f12475`.

Once the schema logic grows beyond a few functions, split it into its own namespace to keep `core.clj` readable (`fcbd537`). Add tests for the schema validation and retry path at this point — they run without Ollama and give a fast feedback loop (`ed4c896`).

### 4. Add tool calling

Extend the schema to allow two reply shapes: `tool-call` (tool name + arguments) or `final-answer` (plain string). The agent loop becomes:

```
call LLM → tool-call? → execute, append result, repeat
                ↓ final-answer
           print and wait for next input
```

Start with one trivial tool (e.g. `get-current-datetime`) to verify the loop works end-to-end before adding anything complex (`208a3fc`). Add a `bash` tool next — it lets the model run arbitrary shell commands, which is powerful enough to cover most tasks without building bespoke tools for each one (`702feac`).

Tools are just functions in a registry map. Adding a new tool means writing a function and registering it; the schema and loop don't change.

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) + JDK 21
- [Ollama](https://ollama.com/) running locally with a model that supports tool calling

The model must support tool calling — not all do. See [ollama.com/search?c=tools](https://ollama.com/search?c=tools) for compatible models. This repo uses [`qwen3`](https://ollama.com/library/qwen3).

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
