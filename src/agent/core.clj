(ns agent.core
  (:gen-class))

(defn -main
  "Simple echo loop. Reads a line from stdin and prints it back.
   Type 'quit' or 'exit' to leave."
  [& _args]
  (println "Echo agent ready. Type 'quit' to exit.")
  (loop []
    (print "> ")
    (flush)
    (let [input (read-line)]
      (cond
        (nil? input)                     (println "\nGoodbye!")
        (contains? #{"quit" "exit"} input) (println "Goodbye!")
        :else (do
                (println "You said:" input)
                (recur))))))
