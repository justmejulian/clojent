(ns agent.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [agent.core :refer [exit-commands system-message agentic-turn]]))

;; --- exit-commands ---

(deftest exit-commands-set
  (testing "contains quit and exit"
    (is (contains? exit-commands "quit"))
    (is (contains? exit-commands "exit")))
  (testing "does not contain arbitrary strings"
    (is (not (contains? exit-commands "bye")))
    (is (not (contains? exit-commands "")))))

;; --- system-message ---

(deftest system-message-shape
  (testing "is a system role message with string content"
    (let [msg (system-message)]
      (is (= "system" (:role msg)))
      (is (string? (:content msg))))))

;; --- agentic-turn ---

(def ^:private final-answer-msg
  {:role "assistant" :content "42" :tool_calls []})

(def ^:private tool-call-msg
  {:role       "assistant"
   :content    nil
   :tool_calls [{:function {:name "get-current-datetime" :arguments {}}}]})

(defn- queued-call-fn
  "Returns a call-fn [msgs tools] that returns message maps from replies-vec in order."
  [replies-vec]
  (let [queue (atom replies-vec)]
    (fn [_msgs _tools]
      (let [reply (first @queue)]
        (swap! queue rest)
        reply))))

(deftest agentic-turn-final-answer-on-first-call
  (testing "returns answer string and appended history"
    (let [call-fn (fn [_msgs _tools] final-answer-msg)
          [answer history] (agentic-turn [] "What is 6 times 7?" call-fn)]
      (is (= "42" answer))
      (is (= 2 (count history)))
      (is (= "user"      (:role (first history))))
      (is (= "assistant" (:role (second history)))))))

(deftest agentic-turn-tool-call-then-final-answer
  (testing "dispatches tool, appends result, then returns final answer"
    (let [call-fn (queued-call-fn [tool-call-msg final-answer-msg])
          [answer history] (agentic-turn [] "What time is it?" call-fn)]
      (is (= "42" answer))
      ;; user → assistant(tool-call) → tool(result) → assistant(final-answer)
      (is (= 4 (count history)))
      (is (= "tool" (:role (nth history 2))))
      (is (= "get-current-datetime" (:tool_name (nth history 2)))))))

(deftest agentic-turn-unknown-tool-continues-loop
  (testing "unknown tool returns error string but loop continues to final answer"
    (let [bad-tool-msg {:role       "assistant"
                        :content    nil
                        :tool_calls [{:function {:name "no-such-tool" :arguments {}}}]}
          call-fn      (queued-call-fn [bad-tool-msg final-answer-msg])
          [answer history] (agentic-turn [] "Do something" call-fn)]
      (is (= "42" answer))
      (is (str/includes? (:content (nth history 2)) "Unknown tool:")))))
