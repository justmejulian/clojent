(ns agent.core
  (:require [clj-http.client :as http]
            [jsonista.core :as json])
  (:gen-class))

;; --- LLM ---

(def ollama-url "http://localhost:11434/api/chat")
(def model-name "qwen3:8b")

(def mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defn call-llm
  "Sends a conversation (vector of message maps) to Ollama and returns
   the assistant's reply as a string."
  [messages]
  (let [body     {:model    model-name
                  :messages messages
                  :stream   false
                  :think    false}
        response (http/post ollama-url
                            {:headers {"Content-Type" "application/json"}
                             :body    (json/write-value-as-string body)})
        parsed   (json/read-value (:body response) mapper)]
    (get-in parsed [:message :content])))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn process-input
  "Appends the user input to history, calls the LLM, and returns the
   updated history with the assistant's reply appended."
  [history input]
  (let [history'  (conj history {:role "user" :content input})  ; + user message
        reply     (call-llm history')
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
  ;; Single turn — still works.
  (call-llm [{:role "user" :content "Say hi in 3 words."}])

  ;; Build history across two turns and verify memory.
  (def h1 (process-input [] "My name is Tom."))
  ;; => [{:role "user" :content "My name is Tom."}
  ;;     {:role "assistant" :content "..."}]

  (def h2 (process-input h1 "What's my name?"))
  ;; The model should answer "Tom."
  (:content (last h2)))
