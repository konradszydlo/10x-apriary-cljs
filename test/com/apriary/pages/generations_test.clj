(ns com.apriary.pages.generations-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.pages.generations :as generations]
            [com.apriary.services.generation :as gen-service]
            [xtdb.api :as xt]))

(defn make-ctx
  "Create a test context with session and database"
  [node user-id & {:keys [body params path-params]}]
  {:session {:uid user-id}
   :biff.xtdb/node node
   :biff/db (xt/db node)
   :params (or params {})
   :path-params (or path-params {})
   :body (or body {})})

;; ============================================================================
;; Step 5: GET /api/generations - List generations
;; ============================================================================

(deftest list-generations-unauthorized-test
  "Test that unauthenticated requests are rejected"
  (with-open [node (test-xtdb-node [])]
    (let [ctx (assoc (make-ctx node nil) :session {})
          response (generations/list-generations-handler ctx)]
      (is (= (:status response) 401))
      (is (= (:code (:body response)) "UNAUTHORIZED")))))

(deftest list-generations-basic-test
  "Test basic generation listing"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          _ (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          ctx (make-ctx node user-id)
          response (generations/list-generations-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)]
        (is (some? (:generations body)))
        (is (>= (:total_count body) 1))
        (is (= (:limit body) 50))
        (is (= (:offset body) 0))))))

(deftest list-generations-with-pagination-test
  "Test generation listing with pagination parameters"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          _ (dorun (for [i (range 5)]
                     (gen-service/create-generation node user-id (str "model-" i) (+ 10 i) 2500)))
          _ (xt/sync node)
          ctx (make-ctx node user-id :params {:limit "2" :offset "0"})
          response (generations/list-generations-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)]
        (is (= (count (:generations body)) 2))
        (is (= (:limit body) 2))
        (is (= (:offset body) 0))
        (is (= (:total_count body) 5))))))

(deftest list-generations-with-invalid-limit-test
  "Test that invalid limit parameter returns error"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :params {:limit "999"})
          response (generations/list-generations-handler ctx)]

      (is (= (:status response) 400))
      (is (= (:code (:body response)) "INVALID_RANGE")))))

(deftest list-generations-with-model-filter-test
  "Test generation listing with model filter"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          _ (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _ (gen-service/create-generation node user-id "claude-3" 15 3000)
          _ (gen-service/create-generation node user-id "gpt-4-turbo" 20 4000)
          _ (xt/sync node)
          ctx (make-ctx node user-id :params {:model "gpt-4-turbo"})
          response (generations/list-generations-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)]
        (is (= (:total_count body) 2))
        (is (every? #(= (:model %) "gpt-4-turbo") (:generations body)))))))

(deftest list-generations-rls-test
  "Test that users only see their own generations"
  (with-open [node (test-xtdb-node [])]
    (let [user1 (java.util.UUID/randomUUID)
          user2 (java.util.UUID/randomUUID)
          _ (gen-service/create-generation node user1 "gpt-4-turbo" 10 2500)
          _ (gen-service/create-generation node user1 "claude-3" 15 3000)
          _ (gen-service/create-generation node user2 "gpt-4-turbo" 20 4000)
          _ (xt/sync node)
          ctx (make-ctx node user1)
          response (generations/list-generations-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)
            user1-str (str user1)]
        (is (= (:total_count body) 2))
        (is (every? #(= (:user_id %) user1-str) (:generations body)))))))

;; ============================================================================
;; Step 6: GET /api/generations/{id} - Get single generation
;; ============================================================================

(deftest get-generation-unauthorized-test
  "Test that unauthenticated requests are rejected"
  (with-open [node (test-xtdb-node [])]
    (let [ctx (assoc (make-ctx node nil :path-params {:id (str (java.util.UUID/randomUUID))})
                     :session {})
          response (generations/get-generation-handler ctx)]
      (is (= (:status response) 401))
      (is (= (:code (:body response)) "UNAUTHORIZED")))))

(deftest get-generation-invalid-uuid-test
  "Test that invalid UUID format returns error"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :path-params {:id "not-a-uuid"})
          response (generations/get-generation-handler ctx)]

      (is (= (:status response) 400))
      (is (= (:code (:body response)) "INVALID_UUID")))))

(deftest get-generation-found-test
  "Test retrieving an existing generation"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [_ created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          gen-id (:generation/id created)
          ctx (make-ctx node user-id :path-params {:id (str gen-id)})
          response (generations/get-generation-handler ctx)]
      (is (= (:status response) 200))
      (let [body (:body response)]
        (is (= (:id body) (str gen-id)))
        (is (= (:user_id body) (str user-id)))
        (is (= (:model body) "gpt-4-turbo"))))))

(deftest get-generation-not-found-test
  "Test that non-existent generation returns 404"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :path-params {:id (str fake-id)})
          response (generations/get-generation-handler ctx)]

      (is (= (:status response) 404))
      (is (= (:code (:body response)) "NOT_FOUND")))))

(deftest get-generation-rls-violation-test
  "Test that users cannot access other users' generations"
  (with-open [node (test-xtdb-node [])]
    (let [owner-id (java.util.UUID/randomUUID)
          other-id (java.util.UUID/randomUUID)
          [_ created] (gen-service/create-generation node owner-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          gen-id (:generation/id created)
          ctx (make-ctx node other-id :path-params {:id (str gen-id)})
          response (generations/get-generation-handler ctx)]

      ;; Returns 404 to hide existence of non-owned resources
      (is (= (:status response) 404))
      (is (= (:code (:body response)) "NOT_FOUND")))))

;; ============================================================================
;; Step 7: POST /api/summaries/generation/accept - Bulk accept summaries
;; ============================================================================

(deftest bulk-accept-unauthorized-test
  "Test that unauthenticated requests are rejected"
  (with-open [node (test-xtdb-node [])]
    (let [ctx (assoc (make-ctx node nil :body {:generation-id (str (java.util.UUID/randomUUID))})
                     :session {})
          response (generations/bulk-accept-generation-handler ctx)]
      (is (= (:status response) 401))
      (is (= (:code (:body response)) "UNAUTHORIZED")))))

(deftest bulk-accept-no-body-test
  "Test that missing body returns error"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (assoc (make-ctx node user-id) :body nil)
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 400))
      (is (= (:code (:body response)) "INVALID_REQUEST")))))

(deftest bulk-accept-missing-generation-id-test
  "Test that missing generation-id field returns error"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :body {})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 400))
      (is (= (:code (:body response)) "MISSING_FIELD")))))

(deftest bulk-accept-invalid-uuid-test
  "Test that invalid UUID format returns error"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :body {:generation-id "not-a-uuid"})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 400))
      (is (= (:code (:body response)) "INVALID_UUID")))))

(deftest bulk-accept-not-found-test
  "Test that non-existent generation returns 404"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          fake-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :body {:generation-id (str fake-id)})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 404))
      (is (= (:code (:body response)) "NOT_FOUND")))))

(deftest bulk-accept-rls-violation-test
  "Test that users cannot accept summaries for other users' generations"
  (with-open [node (test-xtdb-node [])]
    (let [owner-id (java.util.UUID/randomUUID)
          other-id (java.util.UUID/randomUUID)
          [_ created] (gen-service/create-generation node owner-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          gen-id (:generation/id created)
          ctx (make-ctx node other-id :body {:generation-id (str gen-id)})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 403))
      (is (= (:code (:body response)) "FORBIDDEN")))))

(deftest bulk-accept-valid-test
  "Test valid bulk acceptance with no summaries"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [_ created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          gen-id (:generation/id created)
          ctx (make-ctx node user-id :body {:generation-id (str gen-id)})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)]
        (is (some? (:generation body)))
        (is (= (:unedited_accepted body) 0))
        (is (= (:edited_accepted body) 0))
        (is (= (:total_summaries_processed body) 0))
        (is (some? (:timestamp body)))))))

(deftest bulk-accept-response-format-test
  "Test that response includes all required fields"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          [_ created] (gen-service/create-generation node user-id "gpt-4-turbo" 10 2500)
          _ (xt/sync node)
          gen-id (:generation/id created)
          ctx (make-ctx node user-id :body {:generation-id (str gen-id)})
          response (generations/bulk-accept-generation-handler ctx)]

      (is (= (:status response) 200))
      (let [body (:body response)
            gen (:generation body)]
        ;; Check generation fields
        (is (= (:id gen) (str gen-id)))
        (is (= (:user_id gen) (str user-id)))
        (is (= (:model gen) "gpt-4-turbo"))
        (is (= (:generated_count gen) 10))

        ;; Check acceptance counts
        (is (= (:unedited_accepted body) 0))
        (is (= (:edited_accepted body) 0))
        (is (= (:manual_count body) 0))
        (is (= (:total_summaries_processed body) 0))

        ;; Check timestamp
        (is (some? (:timestamp body)))))))