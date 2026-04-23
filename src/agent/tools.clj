(ns agent.tools
  (:require [clojure.string :as str]
            [clojure.java.shell :as sh]))

;; --- Tool implementations ---

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
    :fn          (fn [{:keys [command]}] (bash command))}})

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

  ;; Dispatch via registry.
  (run "get-current-datetime" {})
  (run "bash" {"command" "uname -a"})

  ;; Unknown tool.
  (run "does-not-exist" {})

  ;; Render tool list.
  (println (describe-all)))
