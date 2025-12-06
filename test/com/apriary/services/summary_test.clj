(ns com.apriary.services.summary-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.services.summary :as summary-service]
            [xtdb.api :as xt]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn create-test-summary
  "Helper to create a summary for testing"
  [node user-id & {:keys [content hive-number observation-date special-feature]
                   :or {content (apply str (repeat 50 "x"))}}]
  (summary-service/create-manual-summary
   node user-id
   {:content content
    :hive-number hive-number
    :observation-date observation-date
    :special-feature special-feature}))

;; =============================================================================
;; create-manual-summary tests
;; =============================================================================

(deftest create-manual-summary-valid-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          content (apply str (repeat 100 "test "))
          [status result] (summary-service/create-manual-summary
                           node user-id
                           {:content content
                            :hive-number "A-01"
                            :observation-date "23-11-2025"
                            :special-feature "Queen spotted"})]

      (is (= status :ok))
      (is (some? (:summary/id result)))
      (is (= (:summary/user-id result) user-id))
      (is (= (:summary/source result) :manual))
      (is (nil? (:summary/generation-id result)))
      (is (= (:summary/content result) (clojure.string/trim content)))
      (is (= (:summary/hive-number result) "A-01"))
      (is (= (:summary/observation-date result) "23-11-2025"))
      (is (= (:summary/special-feature result) "Queen spotted"))
      (is (some? (:summary/created-at result)))
      (is (some? (:summary/updated-at result))))))

(deftest create-manual-summary-minimal-test
  (with-open [node (test-xtdb-node [])]
    (testing "Only content field required"
      (let [user-id (java.util.UUID/randomUUID)
            content (apply str (repeat 50 "x"))
            [status result] (summary-service/create-manual-summary
                             node user-id
                             {:content content})]

        (is (= status :ok))
        (is (= (:summary/source result) :manual))
        (is (nil? (:summary/hive-number result)))
        (is (nil? (:summary/observation-date result)))
        (is (nil? (:summary/special-feature result)))))))

(deftest create-manual-summary-invalid-content-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)]

      (testing "content too short (less than 50 chars)"
        (let [[status result] (summary-service/create-manual-summary
                               node user-id
                               {:content "Short"})]
          (is (= status :error))
          (is (= (:code result) "VALIDATION_ERROR"))
          (is (str/includes? (:message result) "50 characters"))))

      (testing "content too long (more than 50,000 chars)"
        (let [long-content (apply str (repeat 50001 "x"))
              [status result] (summary-service/create-manual-summary
                               node user-id
                               {:content long-content})]
          (is (= status :error))
          (is (= (:code result) "VALIDATION_ERROR"))
          (is (str/includes? (:message result) "50,000"))))

      (testing "content is nil"
        (let [[status result] (summary-service/create-manual-summary
                               node user-id
                               {:content nil})]
          (is (= status :error))
          (is (= (:code result) "VALIDATION_ERROR"))))

      (testing "content with only whitespace"
        (let [[status result] (summary-service/create-manual-summary
                               node user-id
                               {:content "   "})]
          (is (= status :error))
          (is (= (:code result) "VALIDATION_ERROR")))))))

(deftest create-manual-summary-invalid-date-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          content (apply str (repeat 50 "x"))]

      (testing "invalid date format"
        (let [[status result] (summary-service/create-manual-summary
                               node user-id
                               {:content content
                                :observation-date "2025-11-23"})] ; Wrong format
          (is (= status :error))
          (is (= (:code result) "VALIDATION_ERROR"))
          (is (str/includes? (:message result) "DD-MM-YYYY")))))))

;; =============================================================================
;; list-summaries tests
;; =============================================================================

(deftest list-summaries-basic-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [s1 _] (create-test-summary node user-id)
          [s2 _] (create-test-summary node user-id)
          _ (xt/sync node)
          db (xt/db node)
          [status result] (summary-service/list-summaries db user-id)]

      (is (= s1 :ok))
      (is (= s2 :ok))
      (is (= status :ok))
      (is (>= (:total-count result) 2))
      (is (= (:limit result) 50))
      (is (= (:offset result) 0))
      (is (>= (count (:summaries result)) 2)))))

(deftest list-summaries-rls-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: users only see their own summaries"
      (let [user1 (java.util.UUID/randomUUID)
            user2 (java.util.UUID/randomUUID)
            [s1 _] (create-test-summary node user1)
            [s2 _] (create-test-summary node user1)
            [s3 _] (create-test-summary node user2)
            _ (xt/sync node)
            db (xt/db node)
            [status result] (summary-service/list-summaries db user1)]

        (is (= s1 :ok))
        (is (= s2 :ok))
        (is (= s3 :ok))
        (is (= status :ok))
        (is (= (:total-count result) 2)) ; Only user1's summaries
        (is (every? #(= (:summary/user-id %) user1) (:summaries result)))))))

(deftest list-summaries-pagination-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          _ (dorun (for [_i (range 5)]
                     (create-test-summary node user-id)))
          _ (xt/sync node)
          db (xt/db node)
          [status result] (summary-service/list-summaries db user-id
                                                          :limit 2 :offset 0)]

      (is (= status :ok))
      (is (= (count (:summaries result)) 2))
      (is (= (:limit result) 2))
      (is (= (:offset result) 0))
      (is (>= (:total-count result) 5)))))

(deftest list-summaries-source-filter-test
  (with-open [node (test-xtdb-node [])]
    (testing "Filter by source type"
      (let [user-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create manual summary
            [s1 _] (create-test-summary node user-id)

            ;; Create AI summaries manually for testing
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id (java.util.UUID/randomUUID)
                               :summary/id (java.util.UUID/randomUUID)
                               :summary/user-id user-id
                               :summary/generation-id gen-id
                               :summary/source :ai-full
                               :summary/content (apply str (repeat 50 "AI summary "))
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])

            _ (xt/sync node)
            db (xt/db node)

            [status-manual result-manual] (summary-service/list-summaries db user-id
                                                                          :source :manual)
            [status-ai result-ai] (summary-service/list-summaries db user-id
                                                                  :source :ai-full)]

        (is (= s1 :ok))
        (is (= status-manual :ok))
        (is (= status-ai :ok))
        (is (>= (:total-count result-manual) 1))
        (is (>= (:total-count result-ai) 1))
        (is (every? #(= (:summary/source %) :manual) (:summaries result-manual)))
        (is (every? #(= (:summary/source %) :ai-full) (:summaries result-ai)))))))

;; =============================================================================
;; get-summary-by-id tests
;; =============================================================================

(deftest get-summary-by-id-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status created] (create-test-summary node user-id)
          _ (xt/sync node)
          db (xt/db node)
          summary-id (:summary/id created)
          [status2 result] (summary-service/get-summary-by-id db summary-id user-id)]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:summary/id result) summary-id))
      (is (= (:summary/user-id result) user-id)))))

(deftest get-summary-by-id-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (summary-service/get-summary-by-id (xt/db node) fake-id user-id)]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

(deftest get-summary-by-id-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: cannot access another user's summary"
      (let [owner-id (java.util.UUID/randomUUID)
            other-id (java.util.UUID/randomUUID)
            [status created] (create-test-summary node owner-id)
            _ (xt/sync node)
            db (xt/db node)
            summary-id (:summary/id created)
            [status2 result] (summary-service/get-summary-by-id db summary-id other-id)]

        (is (= status :ok))
        (is (= status2 :error))
        (is (= (:code result) "NOT_FOUND")) ; Returns NOT_FOUND to hide existence
        ))))

;; =============================================================================
;; update-summary tests
;; =============================================================================

(deftest update-summary-content-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status created] (create-test-summary node user-id)
          _ (xt/sync node)
          summary-id (:summary/id created)
          new-content (apply str (repeat 60 "updated "))
          [status2 result] (summary-service/update-summary
                            node summary-id user-id
                            {:content new-content})]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:summary/content result) (clojure.string/trim new-content)))
      (is (.isAfter ^java.time.Instant (:summary/updated-at result)
                    ^java.time.Instant (:summary/created-at created))))))

(deftest update-summary-metadata-only-test
  (with-open [node (test-xtdb-node [])]
    (testing "Updating only metadata doesn't change source"
      (let [user-id (java.util.UUID/randomUUID)
            [status created] (create-test-summary node user-id)
            _ (xt/sync node)
            summary-id (:summary/id created)
            [status2 result] (summary-service/update-summary
                              node summary-id user-id
                              {:hive-number "B-02"
                               :special-feature "New queen"})]

        (is (= status :ok))
        (is (= status2 :ok))
        (is (= (:summary/hive-number result) "B-02"))
        (is (= (:summary/special-feature result) "New queen"))
        (is (= (:summary/source result) :manual)) ; Source unchanged
        (is (= (:summary/content result) (:summary/content created)))))))

(deftest update-summary-source-transition-test
  (with-open [node (test-xtdb-node [])]
    (testing "Updating content of ai-full changes source to ai-partial"
      (let [user-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create AI-full summary manually
            ai-summary-id (java.util.UUID/randomUUID)
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id ai-summary-id
                               :summary/id ai-summary-id
                               :summary/user-id user-id
                               :summary/generation-id gen-id
                               :summary/source :ai-full
                               :summary/content (apply str (repeat 50 "AI summary "))
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])
            _ (xt/sync node)

            new-content (apply str (repeat 60 "edited AI summary "))
            [status result] (summary-service/update-summary
                             node ai-summary-id user-id
                             {:content new-content})]

        (is (= status :ok))
        (is (= (:summary/source result) :ai-partial)) ; Changed from ai-full
        (is (= (:summary/content result) (clojure.string/trim new-content)))))))

(deftest update-summary-invalid-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [_status created] (create-test-summary node user-id)
          _ (xt/sync node)
          summary-id (:summary/id created)]

      (testing "empty updates map"
        (let [[status2 result] (summary-service/update-summary
                                node summary-id user-id
                                {})]
          (is (= status2 :error))
          (is (= (:code result) "VALIDATION_ERROR"))))

      (testing "content too short"
        (let [[status2 result] (summary-service/update-summary
                                node summary-id user-id
                                {:content "Short"})]
          (is (= status2 :error))
          (is (= (:code result) "VALIDATION_ERROR"))))

      (testing "invalid date format"
        (let [[status2 result] (summary-service/update-summary
                                node summary-id user-id
                                {:observation-date "2025-11-23"})] ; Wrong format
          (is (= status2 :error))
          (is (= (:code result) "VALIDATION_ERROR")))))))

(deftest update-summary-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (summary-service/update-summary
                           node fake-id user-id
                           {:hive-number "X-99"})]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

(deftest update-summary-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: cannot update another user's summary"
      (let [owner-id (java.util.UUID/randomUUID)
            other-id (java.util.UUID/randomUUID)
            [status created] (create-test-summary node owner-id)
            _ (xt/sync node)
            summary-id (:summary/id created)
            [status2 result] (summary-service/update-summary
                              node summary-id other-id
                              {:hive-number "HACKED"})]

        (is (= status :ok))
        (is (= status2 :error))
        (is (= (:code result) "FORBIDDEN"))))))

;; =============================================================================
;; delete-summary tests
;; =============================================================================

(deftest delete-summary-success-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [status created] (create-test-summary node user-id)
          _ (xt/sync node)
          summary-id (:summary/id created)
          [status2 result] (summary-service/delete-summary node summary-id user-id)
          _ (xt/sync node)
          db (xt/db node)
          deleted (xt/entity db summary-id)]

      (is (= status :ok))
      (is (= status2 :ok))
      (is (= (:summary-id result) summary-id))
      (is (nil? deleted))))) ; Verify actually deleted

(deftest delete-summary-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (summary-service/delete-summary node fake-id user-id)]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

(deftest delete-summary-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: cannot delete another user's summary"
      (let [owner-id (java.util.UUID/randomUUID)
            other-id (java.util.UUID/randomUUID)
            [status created] (create-test-summary node owner-id)
            _ (xt/sync node)
            summary-id (:summary/id created)
            [status2 result] (summary-service/delete-summary node summary-id other-id)]

        (is (= status :ok))
        (is (= status2 :error))
        (is (= (:code result) "NOT_FOUND")))))) ; Returns NOT_FOUND to hide existence

;; =============================================================================
;; accept-summary tests
;; =============================================================================

(deftest accept-summary-ai-full-test
  (with-open [node (test-xtdb-node [])]
    (testing "Accepting ai-full summary increments unedited counter"
      (let [user-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create generation
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id gen-id
                               :generation/id gen-id
                               :generation/user-id user-id
                               :generation/model "gpt-4-turbo"
                               :generation/generated-count 1
                               :generation/accepted-unedited-count 0
                               :generation/accepted-edited-count 0
                               :generation/duration-ms 1000
                               :generation/created-at (java.time.Instant/now)
                               :generation/updated-at (java.time.Instant/now)}]])

            ;; Create AI-full summary
            summary-id (java.util.UUID/randomUUID)
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id summary-id
                               :summary/id summary-id
                               :summary/user-id user-id
                               :summary/generation-id gen-id
                               :summary/source :ai-full
                               :summary/content (apply str (repeat 50 "AI summary "))
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])
            _ (xt/sync node)

            [status _result] (summary-service/accept-summary node summary-id user-id)
            _ (xt/sync node)
            db (xt/db node)
            updated-gen (xt/entity db gen-id)]

        (is (= status :ok))
        (is (some? (:summary/accepted-at (:summary result))))
        (is (= (:generation/accepted-unedited-count updated-gen) 1))
        (is (= (:generation/accepted-edited-count updated-gen) 0))))))

(deftest accept-summary-ai-partial-test
  (with-open [node (test-xtdb-node [])]
    (testing "Accepting ai-partial summary increments edited counter"
      (let [user-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create generation
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id gen-id
                               :generation/id gen-id
                               :generation/user-id user-id
                               :generation/model "gpt-4-turbo"
                               :generation/generated-count 1
                               :generation/accepted-unedited-count 0
                               :generation/accepted-edited-count 0
                               :generation/duration-ms 1000
                               :generation/created-at (java.time.Instant/now)
                               :generation/updated-at (java.time.Instant/now)}]])

            ;; Create AI-partial summary
            summary-id (java.util.UUID/randomUUID)
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id summary-id
                               :summary/id summary-id
                               :summary/user-id user-id
                               :summary/generation-id gen-id
                               :summary/source :ai-partial
                               :summary/content (apply str (repeat 50 "Edited AI summary "))
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])
            _ (xt/sync node)

            [status _result] (summary-service/accept-summary node summary-id user-id)
            _ (xt/sync node)
            db (xt/db node)
            updated-gen (xt/entity db gen-id)]

        (is (= status :ok))
        (is (= (:generation/accepted-unedited-count updated-gen) 0))
        (is (= (:generation/accepted-edited-count updated-gen) 1))))))

(deftest accept-summary-manual-rejected-test
  (with-open [node (test-xtdb-node [])]
    (testing "Cannot accept manual summary"
      (let [user-id (java.util.UUID/randomUUID)
            [status created] (create-test-summary node user-id)
            _ (xt/sync node)
            summary-id (:summary/id created)
            [status2 result] (summary-service/accept-summary node summary-id user-id)]

        (is (= status :ok))
        (is (= status2 :error))
        (is (= (:code result) "INVALID_OPERATION"))
        (is (str/includes? (:message result) "manual"))))))

(deftest accept-summary-already-accepted-test
  (with-open [node (test-xtdb-node [])]
    (testing "Cannot accept already-accepted summary"
      (let [user-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create generation
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id gen-id
                               :generation/id gen-id
                               :generation/user-id user-id
                               :generation/model "gpt-4-turbo"
                               :generation/generated-count 1
                               :generation/accepted-unedited-count 0
                               :generation/accepted-edited-count 0
                               :generation/duration-ms 1000
                               :generation/created-at (java.time.Instant/now)
                               :generation/updated-at (java.time.Instant/now)}]])

            ;; Create AI summary with accepted-at already set
            summary-id (java.util.UUID/randomUUID)
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id summary-id
                               :summary/id summary-id
                               :summary/user-id user-id
                               :summary/generation-id gen-id
                               :summary/source :ai-full
                               :summary/content (apply str (repeat 50 "AI summary "))
                               :summary/accepted-at (java.time.Instant/now)
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])
            _ (xt/sync node)

            [status _result] (summary-service/accept-summary node summary-id user-id)]

        (is (= status :error))
        (is (= (:code result) "CONFLICT"))
        (is (str/includes? (:message result) "already accepted"))))))

(deftest accept-summary-not-found-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          [status result] (summary-service/accept-summary node fake-id user-id)]

      (is (= status :error))
      (is (= (:code result) "NOT_FOUND")))))

(deftest accept-summary-rls-violation-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: cannot accept another user's summary"
      (let [owner-id (java.util.UUID/randomUUID)
            other-id (java.util.UUID/randomUUID)
            gen-id (java.util.UUID/randomUUID)

            ;; Create generation for owner
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id gen-id
                               :generation/id gen-id
                               :generation/user-id owner-id
                               :generation/model "gpt-4-turbo"
                               :generation/generated-count 1
                               :generation/accepted-unedited-count 0
                               :generation/accepted-edited-count 0
                               :generation/duration-ms 1000
                               :generation/created-at (java.time.Instant/now)
                               :generation/updated-at (java.time.Instant/now)}]])

            ;; Create AI summary for owner
            summary-id (java.util.UUID/randomUUID)
            _ (xt/submit-tx node
                            [[:xtdb.api/put
                              {:xt/id summary-id
                               :summary/id summary-id
                               :summary/user-id owner-id
                               :summary/generation-id gen-id
                               :summary/source :ai-full
                               :summary/content (apply str (repeat 50 "AI summary "))
                               :summary/created-at (java.time.Instant/now)
                               :summary/updated-at (java.time.Instant/now)}]])
            _ (xt/sync node)

            [status result] (summary-service/accept-summary node summary-id other-id)]

        (is (= status :error))
        (is (= (:code result) "FORBIDDEN"))))))
