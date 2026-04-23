(ns agent.structured-test
  (:require [clojure.test :refer [deftest is testing]]
            [agent.schemas :refer [classification-schema]]
            [agent.structured :refer [parse-json schema->system-message structured-call]]))

;; --- parse-json ---

(deftest parse-json-valid
  (testing "parses valid JSON and keywordizes keys"
    (is (= {:intent "question" :confidence 0.9}
           (parse-json "{\"intent\":\"question\",\"confidence\":0.9}")))))

(deftest parse-json-invalid
  (testing "returns nil for malformed JSON"
    (is (nil? (parse-json "not json"))))
  (testing "returns nil for empty string"
    (is (nil? (parse-json "")))))

;; --- schema->system-message ---

(deftest schema->system-message-shape
  (testing "returns a system role message"
    (let [msg (schema->system-message classification-schema)]
      (is (= "system" (:role msg)))
      (is (string? (:content msg)))))
  (testing "content contains JSON Schema instruction"
    (let [content (:content (schema->system-message classification-schema))]
      (is (clojure.string/includes? content "JSON Schema")))))

;; --- structured-call retry logic ---

(defn make-call-fn
  "Returns a call-fn that vends replies from a queue in order."
  [replies]
  (let [queue (atom replies)]
    (fn [_messages]
      (let [reply (first @queue)]
        (swap! queue rest)
        reply))))

(def valid-reply
  "{\"intent\":\"question\",\"confidence\":0.9}")

(def invalid-reply
  "this is not json")

(deftest structured-call-happy-path
  (testing "returns validated map when first reply is valid"
    (let [call-fn (make-call-fn [valid-reply])]
      (is (= {:intent "question" :confidence 0.9}
             (structured-call classification-schema "test" call-fn))))))

(deftest structured-call-retries-on-bad-reply
  (testing "retries after one bad reply and returns valid map"
    (let [call-fn (make-call-fn [invalid-reply valid-reply])]
      (is (= {:intent "question" :confidence 0.9}
             (structured-call classification-schema "test" call-fn 2))))))

(deftest structured-call-exhausted-retries
  (testing "throws ex-info when all retries are exhausted"
    (is (thrown? clojure.lang.ExceptionInfo
                 (structured-call classification-schema "test" (constantly invalid-reply) 2)))))
