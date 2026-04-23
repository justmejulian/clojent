(ns agent.schemas
  (:require [malli.core :as m]))

(def classification-schema
  [:map
   [:intent     [:enum "question" "command" "chitchat"]]
   [:confidence [:and :double [:>= 0.0] [:<= 1.0]]]])

(comment
  ;; Valid shapes.
  (m/validate classification-schema {:intent "question" :confidence 0.9})  ; => true
  (m/validate classification-schema {:intent "command"  :confidence 0.5})  ; => true

  ;; Invalid shapes.
  (m/validate classification-schema {:intent "nonsense" :confidence 0.9})  ; => false
  (m/validate classification-schema {:intent "question" :confidence 1.5})  ; => false

  ;; Inspect errors.
  (m/explain classification-schema {:intent "nonsense" :confidence 0.9}))
