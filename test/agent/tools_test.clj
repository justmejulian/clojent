(ns agent.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [agent.tools :refer [now bash read-file write-file edit-file run describe-all]]))

;; --- now ---

(deftest now-returns-string
  (testing "returns a non-empty string"
    (let [result (now)]
      (is (string? result))
      (is (pos? (count result))))))

;; --- bash ---

(deftest bash-success
  (testing "returns trimmed stdout on success"
    (is (= "hello" (bash "echo hello")))))

(deftest bash-failure
  (testing "returns error string on non-zero exit"
    (let [result (bash "ls /this-path-does-not-exist-clojent-test")]
      (is (str/starts-with? result "Error (exit ")))))

;; --- file tools ---

(deftest write-and-read-file
  (testing "write-file creates a file; read-file returns its content"
    (let [path (str (System/getProperty "java.io.tmpdir") "/clojent-test-" (System/currentTimeMillis) ".txt")
          content "hello\nworld\n"]
      (is (str/starts-with? (write-file path content) "Written"))
      (is (= content (read-file path))))))

(deftest edit-file-replaces-string
  (testing "edit-file replaces the first occurrence"
    (let [path (str (System/getProperty "java.io.tmpdir") "/clojent-edit-" (System/currentTimeMillis) ".txt")]
      (write-file path "foo bar foo")
      (is (str/starts-with? (edit-file path "foo" "baz") "Edited"))
      (is (= "baz bar foo" (read-file path))))))

(deftest edit-file-missing-old-string
  (testing "returns error when old-string is not present"
    (let [path (str (System/getProperty "java.io.tmpdir") "/clojent-edit2-" (System/currentTimeMillis) ".txt")]
      (write-file path "hello")
      (is (str/starts-with? (edit-file path "xyz" "abc") "Error:")))))

(deftest read-file-missing
  (testing "returns error string for nonexistent file"
    (is (str/starts-with? (read-file "/no/such/file/clojent.txt") "Error"))))

;; --- run ---

(deftest run-known-tool
  (testing "get-current-datetime returns a non-empty string"
    (let [result (run "get-current-datetime" {})]
      (is (string? result))
      (is (pos? (count result)))))
  (testing "bash tool executes command"
    (is (= "hi" (run "bash" {:command "echo hi"})))))

(deftest run-unknown-tool
  (testing "returns error string for unregistered tool"
    (is (= "Unknown tool: no-such-tool"
           (run "no-such-tool" {})))))

;; --- describe-all ---

(deftest describe-all-contains-tool-names
  (testing "mentions all registered tools"
    (let [result (describe-all)]
      (is (string? result))
      (is (str/includes? result "get-current-datetime"))
      (is (str/includes? result "bash"))
      (is (str/includes? result "read-file"))
      (is (str/includes? result "write-file"))
      (is (str/includes? result "edit-file")))))
