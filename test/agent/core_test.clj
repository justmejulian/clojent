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

(deftest system-message-content
  (testing "mentions registered tools"
    (let [content (:content (system-message))]
      (is (str/includes? content "get-current-datetime"))
      (is (str/includes? content "bash"))))
  (testing "includes JSON response format instructions"
    (let [content (:content (system-message))]
      (is (str/includes? content "JSON"))
      (is (str/includes? content "tool-call"))
      (is (str/includes? content "final-answer")))))

;; --- agentic-turn ---

(def ^:private final-answer-reply
  "{\"action\":\"final-answer\",\"answer\":\"42\"}")

(def ^:private tool-call-reply
  "{\"action\":\"tool-call\",\"tool-name\":\"get-current-datetime\",\"tool-args\":{}}")

(defn- queued-call-fn
  "Returns a call-fn that returns replies from replies-vec in order."
  [replies-vec]
  (let [queue (atom replies-vec)]
    (fn [_msgs]
      (let [reply (first @queue)]
        (swap! queue rest)
        reply))))

(deftest agentic-turn-final-answer-on-first-call
  (testing "returns answer string and appended history"
    (let [call-fn (constantly final-answer-reply)
          [answer history] (agentic-turn [] "What is 6 times 7?" call-fn)]
      (is (= "42" answer))
      (is (= 2 (count history)))
      (is (= "user"      (:role (first history))))
      (is (= "assistant" (:role (second history)))))))

(deftest agentic-turn-tool-call-then-final-answer
  (testing "dispatches tool, appends result, then returns final answer"
    (let [call-fn (queued-call-fn [tool-call-reply final-answer-reply])
          [answer history] (agentic-turn [] "What time is it?" call-fn)]
      (is (= "42" answer))
      ;; user → assistant(tool-call) → user(tool-result) → assistant(final-answer)
      (is (= 4 (count history)))
      (is (str/includes? (:content (nth history 2)) "Tool result:")))))

(deftest agentic-turn-unknown-tool-continues-loop
  (testing "unknown tool returns error string but loop continues to final answer"
    (let [bad-tool-reply "{\"action\":\"tool-call\",\"tool-name\":\"no-such-tool\",\"tool-args\":{}}"
          call-fn        (queued-call-fn [bad-tool-reply final-answer-reply])
          [answer history] (agentic-turn [] "Do something" call-fn)]
      (is (= "42" answer))
      (is (str/includes? (:content (nth history 2)) "Unknown tool:")))))
