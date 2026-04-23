(ns agent.core
  (:require [agent.llm :as llm]
            [agent.structured :as structured]
            [agent.tools :as tools])
  (:gen-class))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn system-message
  "Builds the system message that describes available tools and the two
   JSON response formats the LLM must use."
  []
  {:role    "system"
   :content (str "You are a helpful assistant with access to tools.\n\n"
                 "Available tools:\n"
                 (tools/describe-all) "\n\n"
                 "Always reply with ONLY valid JSON — no prose, no markdown, no code fences.\n\n"
                 "To call a tool:\n"
                 "{\"action\":\"tool-call\",\"tool-name\":\"NAME\",\"tool-args\":{}}\n\n"
                 "When you have the final answer:\n"
                 "{\"action\":\"final-answer\",\"answer\":\"YOUR ANSWER HERE\"}")})

(defn agentic-turn
  "Runs one user turn through the tool loop. Calls the LLM, dispatches any
   tool calls, feeds results back, and repeats until a final-answer is
   returned. Returns [answer-string updated-history]."
  [history input]
  (loop [msgs (conj history {:role "user" :content input})]
    (let [reply  (llm/call-llm msgs)
          parsed (structured/parse-json reply)
          msgs'  (conj msgs {:role "assistant" :content reply})]
      (if (= "tool-call" (:action parsed))
        (let [result (tools/run (:tool-name parsed) (:tool-args parsed))
              msgs'' (conj msgs' {:role "user" :content (str "Tool result: " result)})]
          (recur msgs''))
        [(or (:answer parsed) reply) msgs']))))

(defn process-input
  "Runs one turn of the agentic loop. Returns [answer updated-history]."
  [history input]
  (agentic-turn history input))

;; --- I/O shell ---

(defn prompt
  "Prints the input prompt and flushes stdout."
  []
  (print "> ")
  (flush))

(defn chat-loop
  "Runs the read-eval-print loop until the user exits or EOF."
  []
  (loop [history [(system-message)]]
    (prompt)
    (let [input (read-line)]
      (cond
        (nil? input)          (println "\nGoodbye!")
        (exit-commands input) (println "Goodbye!")
        :else (let [[answer history'] (process-input history input)]
                (println answer)
                (recur history'))))))

;; --- Entry point ---

(defn -main
  "Entry point. Greets the user and starts the chat loop."
  [& _args]
  (println "Agent ready. Type 'quit' to exit.")
  (chat-loop))

;; --- REPL scratch ---

(comment
  ;; Single turn with a tool call.
  (let [[answer _history] (process-input [(system-message)] "What time is it?")]
    answer)

  ;; Inspect the system message.
  (println (:content (system-message)))

  ;; Multi-turn: check that history is preserved.
  (let [[_ h1] (process-input [(system-message)] "What time is it?")
        [a2 _] (process-input h1 "Say that back to me in a haiku.")]
    a2))
