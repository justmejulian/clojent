(ns agent.core
  (:gen-class))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn process-input
  "Returns the agent's reply for a given user input string."
  [input]
  (str "You said: " input))

;; --- I/O shell ---

(defn prompt
  "Prints the input prompt and flushes stdout."
  []
  (print "> ")
  (flush))

(defn chat-loop
  "Runs the read-eval-print loop until the user exits or EOF."
  []
  (loop []
    (prompt)
    (let [input (read-line)]
      (cond
        (nil? input)          (println "\nGoodbye!")
        (exit-commands input) (println "Goodbye!")
        :else (do
                (println (process-input input))
                (recur))))))

;; --- Entry point ---

(defn -main
  "Entry point. Greets the user and starts the chat loop."
  [& _args]
  (println "Echo agent ready. Type 'quit' to exit.")
  (chat-loop))

;; --- REPL scratch ---

(comment
  (process-input "hi")
  (exit-commands "quit")
  (exit-commands "nope"))
