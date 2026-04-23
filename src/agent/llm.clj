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

(comment
  (call-llm [{:role "user" :content "Say hi in 3 words."}]))
