(ns agent.core
  (:require [agent.llm :as llm])
  (:gen-class))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn process-input
  "Appends the user input to history, calls the LLM, and returns the
   updated history with the assistant's reply appended."
  [history input]
  (let [history'  (conj history {:role "user" :content input})  ; + user message
        reply     (llm/call-llm history')
        history'' (conj history' {:role "assistant" :content reply})]  ; + assistant reply
    history''))

;; --- I/O shell ---

(defn prompt
  "Prints the input prompt and flushes stdout."
  []
  (print "> ")
  (flush))

(defn chat-loop
  "Runs the read-eval-print loop until the user exits or EOF."
  []
  (loop [history []]
    (prompt)
    (let [input (read-line)]
      (cond
        (nil? input)          (println "\nGoodbye!")
        (exit-commands input) (println "Goodbye!")
        :else (let [history' (process-input history input)]
                (println (:content (last history')))
                (recur history'))))))

;; --- Entry point ---

(defn -main
  "Entry point. Greets the user and starts the chat loop."
  [& _args]
  (println "Agent ready. Type 'quit' to exit.")
  (chat-loop))

;; --- REPL scratch ---

(comment
  ;; Conversational memory.
  (let [h1 (process-input [] "My name is Tom.")
        h2 (process-input h1 "What's my name?")]
    (:content (last h2))))
