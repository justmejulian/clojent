# Talker notes — LLMs & Coding Agents workshop

## Block 1 — LLM fundamentals (60 min)

**1. What an LLM is**
"Next-token predictor over a vocabulary. Not a database, not a search engine. It generates the next most-likely token given everything before it. That's the whole thing."

Bigger vocab = fewer tokens/word, but needs a bigger model to house it. 20 lines Haskell > 20 lines JS in token count — rare words and less-common languages fragment more.

**2. Tokens — demo live:**
```bash
clojure -M -m token.demo "Clojure makes data explicit."

input: Clojure makes data explicit.
chars: 28
tokens: 7
ids: (34 72013 627 4787 1238 23141 13)
decoded: Clojure makes data explicit.
```

"Text goes in, token IDs come out, then you can decode back to the same text. The model never sees words, it sees numbers."

```bash
ollama run gemma4:e2b-mlx --verbose "Why is the sky blue?"
```

"The tokenizer demo shows the representation. `--verbose` shows where those token counts show up in a real model call."

"If you want to connect this back to Claude Code, run `/context`. Same idea: it surfaces how much context budget you are burning inside a real tool."

**2b. When does generation stop?**
No separate "done?" check. The EOS token (`<eos>`, `<|im_end|>`) is a normal vocab entry — probability spikes after a complete thought. Stopping *is* the next prediction, same computation as any other token. External stops (`max_tokens`, stop sequences, context limit) are app-level, not model-level. Exceeding `max_tokens` → abrupt mid-sentence cutoff.

**3. Context window**
"The model sees a fixed-size window of tokens and nothing else. No RAM, no disk, no history. Everything it 'knows' in this turn is in that window."

Lost-in-the-middle is true from tiny to frontier models — needle-in-haystack benchmarks show it clearly. Llama 4 Scout: 10M token window, poor middle recall. Callback to statelessness: the app decides what stays in context.

**4. Statelessness — the key idea. Demo:**

Curl — two completely separate HTTP requests:
```bash
# Request 1 — tell it your name
curl -s http://localhost:11434/api/chat \
  -d '{
    "model": "gemma4:e2b-mlx",
    "stream": false,
    "messages": [
      {"role": "user", "content": "My name is Julian."}
    ]
  }' | jq -r '.message.content'

# Request 2 — new request, messages array starts fresh
curl -s http://localhost:11434/api/chat \
  -d '{
    "model": "gemma4:e2b-mlx",
    "stream": false,
    "messages": [
      {"role": "user", "content": "What is my name?"}
    ]
  }' | jq -r '.message.content'
# → "I don't know your name..."
```

"It can't answer because there's no session. The only reason ChatGPT 'remembers' is that the app resends the whole conversation every time. The app owns memory, not the model. This is what we're building in Block 3."

**5. Prompting**
"System = persona and rules. User = the turn. Assistant = prior reply. Role names are just convention but models are trained on them hard." Structure steers structure.

> **An agent = a loop that resends state to a stateless model and executes what it asks for.**

---

## Block 2 — The runnable loop (40 min)

**Explain (10 min):**
"Before any AI: build something you can run, break, and fix. A REPL loop that reads a line, echoes it, and exits on 'quit'. Name the moving parts now so the LLM calls have a home later."

**Done when:** `clojure -M:run` echoes input and exits cleanly.

---

## Block 3 — Connect the LLM (25 min) 

**Explain (10 min):**
"Two things: (1) POST to Ollama `/api/chat` — hardcode model + URL for now. (2) The history payoff from Block 1: an accumulator. You append every user and assistant message, resend the whole vector every turn."

**Model-choice talk track before the code:**

"When you see `7B`, `14B`, `32B`, the `B` means billions of parameters. Parameters are the learned weights of the model: billions of numbers that encode patterns about language, code, and reasoning. More parameters usually means a smarter model, especially for coding and multi-step tasks, but also more memory and more compute per token."

"Important caveat: size is not everything. A newer 7B can beat an older larger model if the architecture and training are better. So parameter count is a useful first filter, not the whole story."

"How do we know whether a model fits? Rough rule: memory is weights plus KV cache plus overhead. The weight-only estimate is parameter count times bytes per parameter. FP16 is 2 bytes, INT8 is 1, 4-bit is about half a byte."

"So: 7B at 4-bit is roughly 3.5 GB of raw weights, usually more like 4 to 5 GB in practice. 32B at 4-bit is roughly 16 GB raw, and more like 16 to 22 GB once you include overhead and context. That is why larger local models are possible on Apple Silicon, but still tight."

"Quantization is what makes local LLMs practical at all. It is lossy compression for model weights. You take the original higher-precision numbers and store approximate versions in fewer bits. That shrinks memory a lot, with a small quality hit if the quantization is good. `Q4_K_M` is the usual sweet spot: much smaller, usually still good enough."

**How it actually shrinks:** FP32 can represent ~4 billion distinct values. 4-bit can represent **16**. For each block of weights: find `min`/`max`, map the range onto `[0–15]`, store each weight as its 4-bit index. Reconstruct on inference: `value ≈ index × scale + min`. Quality loss = rounding — usually fine, worse for outlier weights.

```
original: [0.12, -0.87, 0.43, ...]   ← FP32, 4 bytes each
quantized: [9, 1, 11, ...]            ← 4-bit index, 0.5 bytes each
+ scale = 0.078, min = -0.87          ← stored once per block (fp16)
```

Why `× 0.6` and not `× 0.5`: pure 4-bit would be 0.5 bytes/param. But Q4_K_M isn't purely 4 bits — it stores quantization scales and min-values per block at fp16 precision. That overhead pushes the effective size to ~4.8 bits/param on average. `4.8 ÷ 8 ≈ 0.6 bytes/param`.

"So the tradeoff is simple: bigger models are usually more capable, quantization makes them fit, and context length quietly eats memory through the KV cache."

**Model slides talk track:**
MoE (`30B-A3B`): 3B active params per token but the whole 30B must fit in RAM — speed win, not a memory win. Tool-trained models for agents: Qwen3-Coder, Granite 4, Gemma 3+, Llama 3.3. Workshop uses a tiny model — fast to demo but weak at multi-step tool use.

Start REPL:
```bash
clojure -M:nrepl
```

**Demo:**

```clojure
;; First real model response
(require '[agent.llm :as llm])
(llm/call-llm [{:role "user" :content "My name is Julian"}])
(llm/call-llm [{:role "user" :content "What is my name?"}])

(llm/call-llm [{:role "user" :content "My name is Julian"}, {:role "assistant" :content "Hi Julian"}, {:role "user" :content "What is my name?"}])
```

## Block 4 — History (30 min)

```clojure
;; History as accumulator: resend everything → it "remembers"
(let [h1 [{:role "system" :content "You are a helpful assistant."}
          {:role "user" :content "My name is Julian."}]
      a1 (llm/call-llm h1)
      h2 (conj h1
               {:role "assistant" :content a1}
               {:role "user" :content "What's my name?"})]
  (llm/call-llm h2))
```

**They build — 30 min.** Wire `call-llm` into the loop, then add history accumulator. At 25 min, nudge anyone stuck on HTTP errors toward the checkpoint.

**Done when:** "My name is X" → "What's my name?" works — and they explain *why* (history resent).

**Checkpoint:** `git checkout 37cecf0 -- src/agent/core.clj` (LLM code lives in core.clj at this commit — no llm.clj yet)

> **Caveat:** Ollama caches prompt prefixes. A request sharing a prefix with the previous
> one may report only the *newly evaluated* tokens, so the numbers can look smaller than
> "whole history resent" implies. If that happens: vary the start of the prompt, or turn
> it into a teaching moment — the history *is* resent, the server just skips re-computing
> the cached prefix.

---

## Block 5 — Tool calling, the agent loop (70 min — build: 40 min)

**Explain on whiteboard (10 min):**
```
call LLM → tool_calls present? → execute each, append {:role "tool" ...} → recur
                 ↓ no tool_calls
           return plain text → print, wait for next user input
```
"The loop recurs until the model returns plain text. Adding tools doesn't change the loop at all — just the registry. One guard: cap the rounds (`max-tool-iterations`) — a model that keeps firing tool calls would otherwise loop forever."

**Demo (start with datetime — verify end-to-end before anything complex):**

```clojure
(require '[agent.tools :as tools]
         '[agent.core :as core])

;; Tools work without any model
(tools/run "get-current-datetime" {})
(tools/run "bash" {:command "ls"})

;; What the registry looks like on the wire
(tools/tools-for-api)
```

Demo what ollama returns when we pass it tools.

```
curl -s http://localhost:11434/api/chat \
  -d '{
    "model": "gemma4:e2b-mlx",
    "stream": false,
    "messages": [{"role": "user", "content": "What time is it?"}],
    "tools": [{
      "type": "function",
      "function": {
        "name": "get-current-datetime",
        "description": "Returns the current date and time.",
        "parameters": {"type": "object", "properties": {}, "required": []}
      }
    }]
  }' | jq '.message'
```

**They build — 40 min.** Start with `get-current-datetime` only — verify end-to-end before adding bash. At 35 min, nudge anyone not seeing tool calls toward the checkpoint.

**Done when:** "What time is it?" makes the model call the tool and answer with the real time.

> **Side note — if asked about validation:** we trust the LLM to pass correct args.
> If you want guarantees, validate the args map against the tool's `:parameters :required`
> before dispatch — check all required keys are present. Return an error string on failure;
> it feeds back as a tool result and the model self-corrects. Malli or a plain `contains?`
> check both work.

**Checkpoint:** `git checkout 448f4e9 -- src/agent/core.clj src/agent/llm.clj src/agent/tools.clj`
(includes the file tools a block early — harmless, the loop is identical)
