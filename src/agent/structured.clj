(ns agent.structured
  (:require [agent.llm :as llm]
            [jsonista.core :as json]
            [malli.core :as m]
            [malli.json-schema :as mjs]))

(defn parse-json
  "Attempts to parse a JSON string. Returns nil on failure."
  [s]
  (try
    (json/read-value s llm/mapper)
    (catch Exception _ nil)))

(defn schema->system-message
  "Converts a Malli schema to a system message instructing the model
   to reply with ONLY JSON matching that shape."
  [schema]
  (let [json-schema (json/write-value-as-string (mjs/transform schema))]
    {:role    "system"
     :content (str "You must reply with ONLY valid JSON matching this JSON Schema. "
                   "No prose, no markdown, no code fences. "
                   "Schema: " json-schema)}))

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
       (let [reply   (llm/call-llm messages)
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

(comment
  (require '[agent.schemas :as schemas])

  ;; Happy path.
  (structured-call schemas/classification-schema "What time is it in Zurich?")
  ;; => {:intent "question" :confidence 0.95}

  (structured-call schemas/classification-schema "Turn off the lights.")
  ;; => {:intent "command" :confidence 0.9}

  ;; Inspect the system message that gets sent.
  (schema->system-message schemas/classification-schema))
