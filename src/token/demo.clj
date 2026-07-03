(ns token.demo
  (:require [clojure.string :as str])
  (:import [com.knuddels.jtokkit Encodings]
           [com.knuddels.jtokkit.api EncodingType]))

(def default-input
  "Clojure makes data explicit.")

(def registry
  (Encodings/newDefaultEncodingRegistry))

(def tokenizer
  (.getEncoding registry EncodingType/O200K_BASE))

(defn encode
  [text]
  (.encode tokenizer text))

(defn decode
  [tokens]
  (.decode tokenizer tokens))

(defn summarize
  [text]
  (let [tokens  (encode text)
        ids     (vec (.boxed tokens))
        decoded (decode tokens)]
    {:text text
     :chars (count text)
     :token-count (.size tokens)
     :tokens ids
     :decoded decoded}))

(defn input-text
  [args]
  (let [text (str/join " " args)]
    (if (str/blank? text)
      default-input
      text)))

(defn -main
  [& args]
  (let [{:keys [text chars token-count tokens decoded]}
        (summarize (input-text args))]
    (println "input:" text)
    (println "chars:" chars)
    (println "tokens:" token-count)
    (println "ids:" (take 20 tokens))
    (println "decoded:" decoded)))

(comment
  ;; Run in terminal:
  ;;   clojure -M -m token.demo
  ;;   clojure -M -m token.demo "Clojure makes data explicit."

  (summarize default-input)

  (summarize "antidisestablishmentarianism")

  (summarize "The quick brown fox jumps over the lazy dog."))
