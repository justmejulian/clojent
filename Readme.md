# Clojent

A minimal Clojure AI agent, built to learn Clojure and how LLM coding agents work.

🦜 🦜 🪨

> This project was heavily AI-assisted — built with Claude as a pair-programming tool to explore and understand Clojure idioms and agent architecture. The code and implementation guide reflect that learning process.

Runs locally via [Ollama](https://ollama.com/) — no API keys required.

Slides: [`docs/slides/slides.md`](docs/slides/slides.md)

## How it works

The agent uses native Ollama tool calling. For each user turn:

1. Send the conversation history + tool definitions (JSON Schema) to the LLM
2. If the response has `tool_calls`: execute each tool, append `{:role "tool" :content result}`, repeat from 2
3. If the response is plain text: print and wait for the next user input

## Architecture

| Namespace          | Responsibility                                               |
|--------------------|--------------------------------------------------------------|
| `agent.core`       | Chat loop, agentic turn logic, entry point                   |
| `agent.llm`        | HTTP client for Ollama                                       |
| `agent.tools`      | Tool registry (`get-current-datetime`, `bash`)               |
| `agent.structured` | JSON parsing, schema→system-prompt, structured call with retries |

## Implementation guide

> Inspired by [How to Build an Agent](https://ampcode.com/notes/how-to-build-an-agent) by Thorsten Ball.

Building an agent breaks down into four concerns, each of which can be built and validated in isolation. This repo was built in that order — you can check out any commit to see a working slice.

### 1. Get a runnable loop first

Before touching anything AI-related, build the outer shell: a REPL loop that reads input and prints output. Keep it to a single file with no external deps.

The point is to have something you can run, break, and fix cheaply. It also forces you to name the moving parts early — `exit-command?`, `chat-loop`, etc. — so that when you start adding LLM calls there's already a clear place for them to go. See `4309686` (init) → `da47826` (named functions).

Set up an nREPL alias at this stage so every subsequent step can be developed interactively (`742d340`).

### 2. Connect to the LLM

Add an HTTP client and POST to the model API. The only thing this step adds is real responses — keep it minimal. Hardcode the model name and endpoint; there's no benefit in parameterizing them yet.

Conversation history is a simple accumulator: each user turn appends `{:role "user" :content ...}`, each reply appends `{:role "assistant" :content ...}`, and the whole vector is sent with every request. Without this the model has no context between turns. See `39cda41` → `cf00575`.

### 3. Add tool calling

Use the model API's native tool calling. Pass tool definitions (name, description, JSON Schema for inputs) in the `tools` request parameter. The model replies with a `tool_calls` array instead of content when it wants to use a tool.

The agent loop:

```
call LLM → tool_calls present? → execute each, append {:role "tool" :content result}, repeat
                    ↓ plain text
           print and wait for next input
```

Start with one trivial tool (e.g. `get-current-datetime`) to verify the loop works end-to-end before adding anything complex (`208a3fc`). Add a `bash` tool next — it lets the model run arbitrary shell commands, which is powerful enough to cover most tasks without building bespoke tools for each one (`702feac`).

Each tool in the registry has a `:name`, `:description`, `:parameters` (JSON Schema), and `:fn`. The loop dispatches by name; adding a tool means writing a function and registering it — the loop doesn't change.

See also: [Ollama tool calling docs](https://docs.ollama.com/capabilities/tool-calling).

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) + JDK 21
- [Ollama](https://ollama.com/) running locally with a model that supports tool calling

The model must support tool calling — not all do. See [ollama.com/search?c=tools](https://ollama.com/search?c=tools) for compatible models. This repo uses `gemma4:e2b-mlx`.

```
brew install clojure/tools/clojure
brew install openjdk@21
brew install ollama

ollama pull gemma4:e2b-mlx
```

## Run

```
clojure -M:run
```

Type `quit` or `exit` to stop. The agent has access to the current time and can run bash commands.

```
❯ clojure -M:run
Agent ready. Type 'quit' to exit.
> My name is Julian
[calling llm]
Hello, Julian!
> add my name to the readme in this folder
[calling llm]
[calling tool] bash
[calling llm]
[calling tool] edit-file
[calling llm]
Your name has been added to the README.md file.
>
```

## Tokenizer Demo

Runnable tokenizer demo used in the workshop slides:

```bash
clojure -M -m token.demo "Clojure makes data explicit."
```

Example output:

```text
input: Clojure makes data explicit.
chars: 28
tokens: 7
ids: (34 72013 627 4787 1238 23141 13)
decoded: Clojure makes data explicit.
```

This is useful for showing that models operate on token IDs, not raw text.

## Test

```
clojure -M:test
```

## REPL

Start an nREPL server (connects with Calva, CIDER, or any nREPL client):

```
clj -M:nrepl
```

### Usage example

Load `agent.core` and call the pure entry point directly:

```clojure
(require '[agent.core :as core] :reload)

(core/process-input
 [{:role "system" :content "You are terse."}
  {:role "user" :content "My name is Julian"}
  {:role "assistant" :content "Got it, Julian."}]
 "What is my name?")
```

Each namespace also has a `(comment ...)` block at the bottom with ready-to-run examples for exploring that module interactively.
