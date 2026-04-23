(ns agent.llm-test
  (:require [clojure.test :refer [deftest is]]
            [agent.llm :refer [call-llm]]))

(deftest ^:integration call-llm-returns-string
  (let [reply (call-llm [{:role "user" :content "Reply with the word ok."}])]
    (is (string? reply))
    (is (pos? (count reply)))))
