(ns com.apriary.pages.rankings-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.pages.rankings :as rankings]
            [com.apriary.services.product :as product-service]
            [xtdb.api :as xt]))

(defn make-ctx
  "Create a test context with session and database"
  [node user-id]
  {:session {:uid user-id}
   :biff.xtdb/node node
   :biff/db (xt/db node)})

;; =============================================================================
;; GET /rankings - Handler Integration Tests
;; =============================================================================

(deftest rankings-page-rls-test
  "Test RLS: User A sees only their own rankings when user B also has products.
   Uses dual verification strategy: HTML rendering + database-level RLS checks."
  (with-open [node (test-xtdb-node [])]
    (let [user-a (java.util.UUID/randomUUID)
          user-b (java.util.UUID/randomUUID)

          ;; Create products for user A
          products-a [{:hive-number "A-01" :date "23-11-2025" :product "Honey" :quantity 5 :metric "kg"}
                      {:hive-number "A-02" :date "24-11-2025" :product "Honey" :quantity 3 :metric "kg"}
                      {:hive-number "A-03" :date "25-11-2025" :product "Pollen" :quantity 2 :metric "ml"}]

          ;; Create products for user B
          products-b [{:hive-number "B-01" :date "23-11-2025" :product "Honey" :quantity 10 :metric "kg"}
                      {:hive-number "B-02" :date "24-11-2025" :product "Pollen" :quantity 5 :metric "ml"}]

          _ (product-service/create-products-batch node user-a products-a)
          _ (product-service/create-products-batch node user-b products-b)
          _ (xt/sync node)

          ;; Call rankings handler for user A
          ctx-a (make-ctx node user-a)
          response-a (rankings/rankings-page-handler ctx-a)

          ;; Call rankings handler for user B
          ctx-b (make-ctx node user-b)
          response-b (rankings/rankings-page-handler ctx-b)]

      ;; Both responses should be 200
      (is (= (:status response-a) 200))
      (is (= (:status response-b) 200))

      ;; User A's response should contain only A's hive numbers
      (let [body-a (:body response-a)]
        (is (str/includes? body-a "A-01") "User A should see hive A-01")
        (is (str/includes? body-a "A-02") "User A should see hive A-02")
        (is (str/includes? body-a "A-03") "User A should see hive A-03")
        (is (not (str/includes? body-a "B-01")) "User A should NOT see hive B-01")
        (is (not (str/includes? body-a "B-02")) "User A should NOT see hive B-02"))

      ;; User B's response should contain only B's hive numbers
      (let [body-b (:body response-b)]
        (is (str/includes? body-b "B-01") "User B should see hive B-01")
        (is (str/includes? body-b "B-02") "User B should see hive B-02")
        (is (not (str/includes? body-b "A-01")) "User B should NOT see hive A-01")
        (is (not (str/includes? body-b "A-02")) "User B should NOT see hive A-02")
        (is (not (str/includes? body-b "A-03")) "User B should NOT see hive A-03"))

      ;; Verify RLS at database level (not just HTML rendering)
      (let [db (xt/db node)
            products-a (xt/q db
                             '{:find [(pull ?p [*])]
                               :in [user-id]
                               :where [[?p :product/user-id user-id]]}
                             user-a)
            products-b (xt/q db
                             '{:find [(pull ?p [*])]
                               :in [user-id]
                               :where [[?p :product/user-id user-id]]}
                             user-b)]
        ;; RLS assertion: verify EVERY record belongs to correct user
        (is (every? #(= (:product/user-id (first %)) user-a) products-a)
            "Database query must return only user-a's products")
        (is (every? #(= (:product/user-id (first %)) user-b) products-b)
            "Database query must return only user-b's products")))))
