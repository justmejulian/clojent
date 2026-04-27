(ns agent.llm-test
  (:require [clojure.test :refer [deftest is]]
            [agent.llm :refer [call-llm call-llm-with-tools]]))

(deftest ^:integration call-llm-returns-string
  (let [reply (call-llm [{:role "user" :content "Reply with the word ok."}])]
    (is (string? reply))
    (is (pos? (count reply)))))

(deftest ^:integration call-llm-with-tools-returns-message-map
  (let [tools  [{:type     "function"
                 :function {:name        "get-current-datetime"
                            :description "Returns the current date and time."
                            :parameters  {:type "object" :properties {} :required []}}}]
        result (call-llm-with-tools [{:role "user" :content "What time is it?"}] tools)]
    (is (map? result))
    (is (= "assistant" (:role result)))))
