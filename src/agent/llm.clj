(ns agent.llm
  (:require [clj-http.client :as http]
            [jsonista.core :as json]))

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

(defn call-llm-with-tools
  "Sends messages + tools to Ollama. Returns the raw assistant message map:
     {:role \"assistant\"
      :content nil-or-string   ; nil when model fires a tool call
      :tool_calls [{:function {:name \"...\" :arguments {...}}}]}
   No :format \"json\" — that suppresses tool_calls in the response."
  [messages tools]
  (let [body     {:model    model-name
                  :messages messages
                  :tools    tools
                  :stream   false
                  :think    false}
        response (http/post ollama-url
                            {:headers {"Content-Type" "application/json"}
                             :body    (json/write-value-as-string body)})
        parsed   (json/read-value (:body response) mapper)]
    (get-in parsed [:message])))

(comment
  (call-llm [{:role "user" :content "Say hi in 3 words."}])

  (call-llm-with-tools
   [{:role "user" :content "What time is it?"}]
   [{:type "function"
     :function {:name        "get-current-datetime"
                :description "Returns the current date and time."
                :parameters  {:type "object" :properties {} :required []}}}]))
