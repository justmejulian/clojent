(ns agent.tools
  (:require [clojure.string :as str]
            [clojure.java.shell :as sh]))

;; --- Tool implementations ---

(defn read-file
  "Returns the content of the file at path, or an error string."
  [path]
  (try
    (slurp path)
    (catch Exception e
      (str "Error reading file: " (.getMessage e)))))

(defn write-file
  "Writes content to path, creating or overwriting the file.
   Returns a confirmation string, or an error string."
  [path content]
  (try
    (spit path content)
    (str "Written " (count content) " bytes to " path)
    (catch Exception e
      (str "Error writing file: " (.getMessage e)))))

(defn edit-file
  "Replaces the first occurrence of old-string with new-string in the file at path.
   Returns a confirmation string, or an error string."
  [path old-string new-string]
  (try
    (let [content (slurp path)]
      (if (str/includes? content old-string)
        (do (spit path (str/replace-first content old-string new-string))
            (str "Edited " path))
        (str "Error: old-string not found in " path)))
    (catch Exception e
      (str "Error editing file: " (.getMessage e)))))

(defn now
  "Returns the current date and time as a string."
  []
  (str (new java.util.Date)))

(defn bash
  "Runs a shell command string and returns stdout, or stderr on failure."
  [command]
  (let [{:keys [out err exit]} (sh/sh "bash" "-c" command)]
    (if (zero? exit)
      (str/trim out)
      (str "Error (exit " exit "): " (str/trim err)))))

;; --- Registry ---

(def registry
  {"get-current-datetime"
   {:name        "get-current-datetime"
    :description "Returns the current date and time."
    :fn          (fn [_args] (now))}

   "bash"
   {:name        "bash"
    :description "Runs a bash command. Args: {\"command\": \"<shell command string>\"}. Returns stdout on success, or stderr on failure."
    :fn          (fn [{:keys [command]}] (bash command))}

   "read-file"
   {:name        "read-file"
    :description "Reads a file and returns its content. Args: {\"path\": \"<file path>\"}."
    :fn          (fn [{:keys [path]}] (read-file path))}

   "write-file"
   {:name        "write-file"
    :description "Creates or overwrites a file with the given content. Args: {\"path\": \"<file path>\", \"content\": \"<text>\"}."
    :fn          (fn [{:keys [path content]}] (write-file path content))}

   "edit-file"
   {:name        "edit-file"
    :description "Replaces the first occurrence of old-string with new-string in a file. Args: {\"path\": \"<file path>\", \"old-string\": \"<text to replace>\", \"new-string\": \"<replacement>\"}."
    :fn          (fn [{:keys [path old-string new-string]}] (edit-file path old-string new-string))}})

;; --- Dispatch ---

(defn run
  "Executes a tool by name, passing args map. Returns the result as a string.
   Returns an error string if the tool is unknown."
  [tool-name args]
  (if-let [tool (get registry tool-name)]
    ((:fn tool) args)
    (str "Unknown tool: " tool-name)))

;; --- Description for system prompt ---

(defn describe-all
  "Renders all registered tools as a human-readable list."
  []
  (str/join "\n"
            (map (fn [[name {:keys [description]}]]
                   (str "- " name ": " description))
                 registry)))

(comment
  ;; Check the time.
  (now)
  ;; => "Wed Apr 23 10:00:00 CEST 2026"

  ;; Run a bash command directly.
  (bash "echo hello")
  ;; => "hello"

  (bash "ls /tmp | head -5")

  ;; File tools.
  (let [path "/tmp/clojent-test.txt"]
    (write-file path "hello\nworld\n")
    (read-file path)
    (edit-file path "world" "clojent")
    (read-file path))

  ;; Dispatch via registry.
  (run "get-current-datetime" {})
  (run "bash" {"command" "uname -a"})
  (run "read-file" {:path "/tmp/clojent-test.txt"})
  (run "write-file" {:path "/tmp/clojent-test.txt" :content "hi\n"})
  (run "edit-file" {:path "/tmp/clojent-test.txt" :old-string "hi" :new-string "bye"})

  ;; Unknown tool.
  (run "does-not-exist" {})

  ;; Render tool list.
  (println (describe-all)))
