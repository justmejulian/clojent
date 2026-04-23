(ns agent.core
  (:require [clj-http.client :as http]
            [jsonista.core :as json]
            [malli.core :as m]
            [malli.json-schema :as mjs])
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
                  :think    false
                  :format   "json"}
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

;; --- Structured output ---

(def classification-schema
  [:map
   [:intent     [:enum "question" "command" "chitchat"]]
   [:confidence [:and :double [:>= 0.0] [:<= 1.0]]]])

(defn schema->system-message
  "Converts a Malli schema to a system message instructing the model
   to reply with ONLY JSON matching that shape."
  [schema]
  (let [json-schema (json/write-value-as-string (mjs/transform schema))]
    {:role    "system"
     :content (str "You must reply with ONLY valid JSON matching this JSON Schema. "
                   "No prose, no markdown, no code fences. "
                   "Schema: " json-schema)}))

(defn parse-json
  "Attempts to parse a JSON string. Returns nil on failure."
  [s]
  (try
    (json/read-value s mapper)
    (catch Exception _ nil)))

(defn structured-call
  "Calls the LLM and returns a validated Clojure map matching schema.
   Retries up to max-retries times, feeding validation errors back to
   the model so it can self-correct."
  ([schema prompt] (structured-call schema prompt 3))
  ([schema prompt max-retries]
   (let [system-msg (schema->system-message schema)
         user-msg   {:role "user" :content prompt}]
     (loop [messages [system-msg user-msg]
            retries  max-retries]
       (let [reply   (call-llm messages)
             parsed  (parse-json reply)
             valid?  (and parsed (m/validate schema parsed))]
         (cond
           valid?
           parsed

           (zero? retries)
           (throw (ex-info "structured-call exhausted retries"
                           {:last-reply reply :schema schema}))

           :else
           (let [errors   (m/explain schema parsed)
                 feedback (str "That reply was invalid. "
                               "Errors: " (pr-str (:errors errors)) ". "
                               "Reply with ONLY valid JSON matching the schema.")]
             (recur (conj messages
                          {:role "assistant" :content reply}
                          {:role "user"      :content feedback})
                    (dec retries)))))))))

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
  ;; Conversational memory — still works.
  (let [h1 (process-input [] "My name is Tom.")
        h2 (process-input h1 "What's my name?")]
    (:content (last h2)))

  ;; Schema validation sanity check.
  (m/validate classification-schema {:intent "question" :confidence 0.9})   ; => true
  (m/validate classification-schema {:intent "nonsense" :confidence 0.9})   ; => false
  (m/validate classification-schema {:intent "question" :confidence 1.5})   ; => false

  ;; Happy path — well-formed structured response.
  (structured-call classification-schema "What time is it in Zurich?")
  ;; => {:intent "question" :confidence 0.95}

  ;; Tricky input — model should still classify correctly.
  (structured-call classification-schema "Turn off the lights.")
  ;; => {:intent "command" :confidence 0.9}

  ;; Inspect explain output on a bad map.
  (m/explain classification-schema {:intent "nonsense" :confidence 0.9}))
  ;; => {:schema [...] :value {...} :errors [{...}]}
