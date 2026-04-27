(ns agent.core
  (:require [agent.llm :as llm]
            [agent.tools :as tools])
  (:gen-class))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn system-message
  "Builds the system message that sets the assistant's persona."
  []
  {:role    "system"
   :content "You are a helpful assistant with access to tools. Use them whenever they would help answer the user's question."})

(defn agentic-turn
  "Runs one user turn through the tool loop. Calls call-fn with the message
   history and tool definitions, dispatches any tool calls, feeds results back,
   and repeats until the model returns a plain text answer.
   Returns [answer-string updated-history].

   call-fn defaults to llm/call-llm-with-tools; pass an alternative for testing."
  ([history input] (agentic-turn history input llm/call-llm-with-tools))
  ([history input call-fn]
   (let [tool-defs (tools/tools-for-api)]
     (loop [msgs (conj history {:role "user" :content input})]
       (println "[calling llm]")
       (let [assistant-msg (call-fn msgs tool-defs)
             msgs'         (conj msgs assistant-msg)]
         (if-let [tool-calls (seq (:tool_calls assistant-msg))]
           (let [msgs'' (reduce
                          (fn [acc tc]
                            (let [fn-name (:name (:function tc))
                                  args    (:arguments (:function tc))
                                  _       (println "[calling tool]" fn-name)
                                  result  (try
                                            (tools/run fn-name args)
                                            (catch Exception e
                                              (str "Tool error: " (.getMessage e))))]
                              (conj acc {:role "tool" :tool_name fn-name :content result})))
                          msgs'
                          tool-calls)]
             (recur msgs''))
           [(or (:content assistant-msg) "") msgs']))))))

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
