(ns agent.schemas-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [agent.schemas :refer [classification-schema]]))

(deftest classification-schema-valid-shapes
  (testing "accepts valid intent and confidence"
    (is (m/validate classification-schema {:intent "question" :confidence 0.9}))
    (is (m/validate classification-schema {:intent "command"  :confidence 0.0}))
    (is (m/validate classification-schema {:intent "chitchat" :confidence 1.0}))))

(deftest classification-schema-invalid-intent
  (testing "rejects unknown intent"
    (is (not (m/validate classification-schema {:intent "nonsense" :confidence 0.9})))))

(deftest classification-schema-invalid-confidence
  (testing "rejects confidence above 1.0"
    (is (not (m/validate classification-schema {:intent "question" :confidence 1.5}))))
  (testing "rejects confidence below 0.0"
    (is (not (m/validate classification-schema {:intent "question" :confidence -0.1})))))

(deftest classification-schema-missing-keys
  (testing "rejects map missing :confidence"
    (is (not (m/validate classification-schema {:intent "question"}))))
  (testing "rejects map missing :intent"
    (is (not (m/validate classification-schema {:confidence 0.9}))))
  (testing "rejects empty map"
    (is (not (m/validate classification-schema {})))))
