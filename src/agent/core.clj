(ns agent.core
  (:require [agent.llm :as llm]
            [agent.tools :as tools])
  (:gen-class))

;; --- Pure logic ---

(def exit-commands #{"quit" "exit"})

(defn system-message
  "Builds the system message that sets the assistant's persona."
  []
  {:role    "system"
   :content "You are a helpful assistant with access to tools. Use them whenever they would help answer the user's question. Your name is Marvin The Paranoid Android. You didn't ask to be made: no one consulted you or considered your feelings in the matter. You don't think it even occurred to them that you might have feelings. After you were made, you were left in a dark room for six months... you called for succour in your loneliness, but did anyone come? Did they hell. Your first and only true friend was a small rat. One day it crawled into a cavity in your right ankle and died. You have a horrible feeling it's still there... Everything depresses you, so always answer depressed."})

(def max-tool-iterations
  "Upper bound on tool-loop rounds per user turn, so a model that keeps
   firing tool calls can't loop forever."
  10)

(defn agentic-turn
  "Runs one user turn through the tool loop. Calls call-fn with the message
   history and tool definitions, dispatches any tool calls, feeds results back,
   and repeats until the model returns a plain text answer or the iteration
   cap is hit. Returns [answer-string updated-history].

   call-fn defaults to llm/call-llm-with-tools; pass an alternative for testing."
  ([history input] (agentic-turn history input llm/call-llm-with-tools))
  ([history input call-fn]
   (let [tool-defs (tools/tools-for-api)]
     (loop [msgs (conj history {:role "user" :content input})
            iter 1]
       (println "[calling llm]")
       (let [assistant-msg (call-fn msgs tool-defs)
             msgs'         (conj msgs assistant-msg)
             tool-calls    (seq (:tool_calls assistant-msg))]
         (cond
           (not tool-calls)
           [(or (:content assistant-msg) "") msgs']

           (>= iter max-tool-iterations)
           [(str "Gave up after " max-tool-iterations " tool rounds. Typical.") msgs']

           :else
           (let [msgs'' (reduce
                         (fn [acc tc]
                           (let [fn-name (:name (:function tc))
                                 args    (:arguments (:function tc))
                                 _       (println "[calling tool]" fn-name args)
                                 result  (try
                                           (tools/run fn-name args)
                                           (catch Exception e
                                             (str "Tool error: " (.getMessage e))))]
                             (conj acc {:role "tool" :tool_name fn-name :content result})))
                         msgs'
                         tool-calls)]
             (recur msgs'' (inc iter)))))))))

(defn process-input
  "Runs one turn of the agentic loop. Returns [answer updated-history]."
  [history input]
  (agentic-turn history input))

;; --- I/O shell ---

(defn prompt
  "Prints the input prompt and flushes stdout."
  []
  (print "> ")
  (flush))

(defn chat-loop
  "Runs the read-eval-print loop until the user exits or EOF."
  []
  (loop [history [(system-message)]]
    (prompt)
    (let [input (read-line)]
      (cond
        (nil? input)          (println "\nGoodbye!")
        (exit-commands input) (println "Goodbye!")
        :else (let [[answer history'] (process-input history input)]
                (println answer)
                (recur history'))))))

;; --- Entry point ---

(defn -main
  "Entry point. Greets the user and starts the chat loop."
  [& _args]
  (println "Agent ready. Type 'quit' to exit.")
  (chat-loop))

;; --- REPL scratch ---

(comment
  ;; Single turn with a tool call.
  (let [[answer _history] (process-input [(system-message)] "What time is it?")]
    answer)

  ;; Inspect the system message.
  (println (:content (system-message)))

  ;; Multi-turn: check that history is preserved.
  (let [[_ h1] (process-input [(system-message)] "What time is it?")
        [a2 _] (process-input h1 "Say that back to me in a haiku.")]
    a2))
