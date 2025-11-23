(ns com.apriary.services.generation-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.services.generation :as gen-service]
            [xtdb.api :as xt]))

;; Test: create-generation with valid inputs
(deftest create-generation-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id  (java.util.UUID/randomUUID)
          model    "gpt-4-turbo"
          gen-count 10
          duration 2500
          [status result] (gen-service/create-generation node user-id model gen-count duration)]

      (is (= status :ok))
      (is (some? (:generation/id result)))
      (is (= (:generation/user-id result) user-id))
      (is (= (:generation/model result) model))
      (is (= (:generation/generated-count result) gen-count))
      (is (= (:generation/duration-ms result) duration))
      (is (= (:generation/accepted-unedited-count result) 0))
      (is (= (:generation/accepted-edited-count result) 0))
      (is (some? (:generation/created-at result)))
      (is (some? (:generation/updated-at result))))))

;; Test: create-generation with invalid inputs
(deftest create-generation-invalid-inputs-test
  (with-open [node (test-xtdb-node [])]
    (testing "missing user-id"
      (let [[status result] (gen-service/create-generation node nil "gpt-4" 10 100)]
        (is (= status :error))
        (is (= (:code result) "INVALID_INPUT"))))

    (testing "missing model"
      (let [[status result] (gen-service/create-generation node (java.util.UUID/randomUUID) nil 10 100)]
        (is (= status :error))
        (is (= (:code result) "INVALID_INPUT"))))

    (testing "negative generated-count"
      (let [[status result] (gen-service/create-generation node (java.util.UUID/randomUUID) "gpt-4" -1 100)]
        (is (= status :error))
        (is (= (:code result) "INVALID_INPUT"))))

    (testing "negative duration-ms"
      (let [[status result] (gen-service/create-generation node (java.util.UUID/randomUUID) "gpt-4" 10 -1)]
        (is (= status :error))
        (is (= (:code result) "INVALID_INPUT"))))))

;; Test: get-generation-by-id - found
(deftest get-generation-by-id-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id  (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _        (xt/sync node) ; Wait for transaction to be indexed
          gen-id   (:generation/id created)
          db       (xt/db node) ; Get fresh db after sync
          [status2 result] (gen-service/get-generation-by-id db gen-id user-id)]
      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:generation/id result) gen-id))
      (is (= (:generation/user-id result) user-id)))))

;; Test: get-generation-by-id - not found
(deftest get-generation-by-id-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (gen-service/get-generation-by-id (xt/db node) fake-id user-id)]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

;; Test: get-generation-by-id - RLS violation (returns NOT_FOUND to hide existence)
(deftest get-generation-by-id-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (let [owner-id  (java.util.UUID/randomUUID)
          other-id  (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node owner-id "gpt-4-turbo" 10 2500)
          _         (xt/sync node) ; Wait for transaction to be indexed
          db        (xt/db node) ; Get fresh db after sync
          gen-id    (:generation/id created)
          [status2 result] (gen-service/get-generation-by-id db gen-id other-id)]

      (is (= status :ok))
      (is (= status2 :error))
      (is (= (:code result) "NOT_FOUND")) ; Should return NOT_FOUND, not FORBIDDEN, to hide existence
      (is (str/includes? (:message result) "not found")))))

;; Test: list-user-generations - basic listing
(deftest list-user-generations-basic-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status _] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _      (xt/sync node) ; Wait for transaction to be indexed
          db      (xt/db node) ; Get fresh db after sync
          [status2 result] (gen-service/list-user-generations db user-id)]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (some? (:generations result)))
      (is (>= (:total-count result) 1))
      (is (= (:limit result) 50))
      (is (= (:offset result) 0)))))

;; Test: list-user-generations - RLS filtering
(deftest list-user-generations-rls-test
  (with-open [node (test-xtdb-node [])]
    (let [user1 (java.util.UUID/randomUUID)
          user2 (java.util.UUID/randomUUID)
          [s1 _] (gen-service/create-generation node user1 "gpt-4-turbo" 10 2500)
          [s2 _] (gen-service/create-generation node user1 "claude-3" 15 3000)
          [s3 _] (gen-service/create-generation node user2 "gpt-4-turbo" 20 4000)
          _     (xt/sync node) ; Wait for transactions to be indexed
          db    (xt/db node) ; Get fresh db after sync
          [status result] (gen-service/list-user-generations db user1)]

      (is (= s1 :ok))
      (is (= s2 :ok))
      (is (= s3 :ok))
      (is (= status :ok))
      (is (= (:total-count result) 2)) ; Only user1's generations
      (is (every? #(= (:generation/user-id %) user1) (:generations result))))))

;; Test: list-user-generations - pagination
(deftest list-user-generations-pagination-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          _ (dorun (for [i (range 5)]
                     (gen-service/create-generation node user-id (str "model-" i) (+ 10 i) 2500)))
          _      (xt/sync node) ; Wait for transactions to be indexed
          db      (xt/db node) ; Get fresh db after sync
          [status result] (gen-service/list-user-generations db user-id
                                                             :limit 2 :offset 0)]

      (is (= status :ok))
      (is (= (count (:generations result)) 2))
      (is (= (:limit result) 2))
      (is (= (:offset result) 0)))))

;; Test: list-user-generations - model filtering
(deftest list-user-generations-model-filter-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [s1 _] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          [s2 _] (gen-service/create-generation node user-id "claude-3" 15 3000)
          [s3 _] (gen-service/create-generation node user-id "gpt-4-turbo" 20 4000)
          _      (xt/sync node) ; Wait for transactions to be indexed
          db      (xt/db node) ; Get fresh db after sync
          [status result] (gen-service/list-user-generations db user-id
                                                             :model "gpt-4-turbo")]

      (is (= s1 :ok))
      (is (= s2 :ok))
      (is (= s3 :ok))
      (is (= status :ok))
      (is (= (:total-count result) 2))
      (is (every? #(= (:generation/model %) "gpt-4-turbo") (:generations result))))))

;; Test: update-counters - valid increment
(deftest update-counters-valid-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _       (xt/sync node) ; Wait for transaction to be indexed
          gen-id  (:generation/id created)
          [status2 result] (gen-service/update-counters node gen-id 5 3)]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:generation/accepted-unedited-count result) 5))
      (is (= (:generation/accepted-edited-count result) 3))
      (is (.isAfter ^java.time.Instant (:generation/updated-at result)
                    ^java.time.Instant (:generation/created-at created))))))

;; Test: update-counters - overflow validation
(deftest update-counters-overflow-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _       (xt/sync node) ; Wait for transaction to be indexed
          gen-id  (:generation/id created)
          [status2 result] (gen-service/update-counters node gen-id 8 5)]

      (is (= status :ok))
      (is (= status2 :error))
      (is (= (:code result) "DATA_INTEGRITY_ERROR")))))

;; Test: update-counters - not found
(deftest update-counters-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [fake-id (java.util.UUID/randomUUID)
          [status result] (gen-service/update-counters node fake-id 5 3)]

      (is (= status :error))
      (is (= (:code result) "INVALID_INPUT")))))

;; Test: bulk-accept-summaries-for-generation - generation not found
(deftest bulk-accept-summaries-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (gen-service/bulk-accept-summaries-for-generation node fake-id user-id)]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

;; Test: bulk-accept-summaries-for-generation - RLS violation
(deftest bulk-accept-summaries-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (let [owner-id (java.util.UUID/randomUUID)
          other-id (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node owner-id "gpt-4-turbo" 10 2500)
          _        (xt/sync node) ; Wait for transaction to be indexed
          gen-id   (:generation/id created)
          [status2 result] (gen-service/bulk-accept-summaries-for-generation node gen-id other-id)]

      (is (= status :ok))
      (is (= status2 :error))
      (is (= (:code result) "FORBIDDEN")))))

;; Test: bulk-accept-summaries-for-generation - no summaries (valid case)
(deftest bulk-accept-summaries-no-summaries-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id  (java.util.UUID/randomUUID)
          [status created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _        (xt/sync node) ; Wait for transaction to be indexed
          gen-id   (:generation/id created)
          [status2 result] (gen-service/bulk-accept-summaries-for-generation node gen-id user-id)]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:unedited-count result) 0))
      (is (= (:edited-count result) 0))
      (is (= (:manual-count result) 0))
      (is (= (:total-summaries result) 0)))))