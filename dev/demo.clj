(ns demo
  "Workshop demo script — one comment block per slide, in presentation order.
   Terminal demos marked with (terminal). Everything else: start a REPL
   (clojure -M:nrepl), connect editor, eval form by form.
   Needs Ollama running with the model pulled."
  (:require [agent.core :as core]
            [agent.llm :as llm]
            [agent.tools :as tools]))

;; ---------------------------------------------------------------------------
;; Block 1 — LLM fundamentals (terminal)
;; ---------------------------------------------------------------------------

;; Tokenizer demo:
;;   clojure -M -m token.demo "Clojure makes data explicit."
;;   ollama run gemma4:e2b-mlx --verbose "Why is the sky blue?"

;; Statelessness demo — two separate requests, no shared state:
;;   curl -s http://localhost:11434/api/chat -d '{"model":"gemma4:e2b-mlx","stream":false, "messages":[{"role":"user","content":"My name is Julian."}]}' | jq -r '.message.content'

;;   curl -s http://localhost:11434/api/chat -d '{"model":"gemma4:e2b-mlx","stream":false, "messages":[{"role":"user","content":"What is my name?"}]}' | jq -r '.message.content'

;; ---------------------------------------------------------------------------
;; Block 3 — Connect the LLM
;; ---------------------------------------------------------------------------

;; Ollama API (terminal):
;;   curl localhost:11434/api/generate -d '{"model":"gemma4:e2b-mlx","prompt":"Why is the sky blue?","stream":false}'

(comment
  ;; First real model response.
  (llm/call-llm [{:role "user" :content "Say hi in 3 words."}]))

;; ---------------------------------------------------------------------------
;; Block 4 — History
;; ---------------------------------------------------------------------------

(comment
  ;; Stateless: a fresh request knows nothing.
  (llm/call-llm [{:role "user" :content "My Name is Julian"}])
  (llm/call-llm [{:role "user" :content "What's my name?"}])

  ;; History as accumulator: resend everything → it "remembers".
  (let [h1 [(core/system-message)
            {:role "user" :content "My name is Julian."}]
        a1 (llm/call-llm h1)
        h2 (conj h1
                 {:role "assistant" :content a1}
                 {:role "user" :content "What's my name?"})]
    (llm/call-llm h2))

  ;; Full chat loop: history accumulates across every turn.
  ;; Type 'quit' or hit Ctrl-D to exit.
  (core/chat-loop))

;; ---------------------------------------------------------------------------
;; Block 5 — Tool calling, the agent loop
;; ---------------------------------------------------------------------------

(comment
  ;; Tools work without any model.
  (tools/run "get-current-datetime" {})
  (tools/run "bash" {:command "ls"})

  ;; What the registry looks like on the wire.
  (tools/tools-for-api)

  ;; The agent is born: the model that couldn't tell the date now can.
  (let [[answer _] (core/process-input [(core/system-message)]
                                       "What time is it?")]
    answer)

  ;; The moment. Watch the [calling tool] lines.
  (let [[answer _] (core/process-input [(core/system-message)]
                                       "Add my name (Julian) to the readme in this folder.")]
    answer))
