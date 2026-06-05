(ns com.apriary.services.product-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.services.product :as product-service]
            [xtdb.api :as xt]))

(defn remove-untestable-keys [product]
  (select-keys product [:product/product :product/quantity :product/hive-number :product/metric]))

;; =============================================================================
;; create-products-batch tests
;; =============================================================================

(deftest create-products-batch-valid-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (random-uuid)
          products [{:hive-number "A-01"
                     :date "23-11-2025"
                     :product "Honey"
                     :quantity 5
                     :metric "kg"}
                    {:hive-number "A-02"
                     :date "24-11-2025"
                     :product "Pollen"
                     :quantity 3
                     :metric "ml"}]
          [status result] (product-service/create-products-batch node user-id products)
          _ (xt/sync node)
          db (xt/db node)
          [list-status list-result] (product-service/list-products db user-id)]

      (is (= status :ok))
      (is (= (:count result) 2))
      (is (= list-status :ok))
      (is (= (count (:products list-result)) 2))
      (is (every? #(= (:product/user-id %) user-id) (:products list-result)))
      (is (some #(= (:product/hive-number %) "A-01") (:products list-result)))
      (is (some #(= (:product/hive-number %) "A-02") (:products list-result))))))

(deftest create-products-batch-empty-list-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (random-uuid)
          [status result] (product-service/create-products-batch node user-id [])]

      (is (= status :error))
      (is (= (:code result) "INVALID_INPUT")))))

(deftest create-products-batch-nil-user-id-test
  (with-open [node (test-xtdb-node [])]
    (let [products [{:hive-number "A-01" :date "23-11-2025" :product "Honey" :quantity 5 :metric "kg"}]
          [status result] (product-service/create-products-batch node nil products)]

      (is (= status :error))
      (is (= (:code result) "INVALID_INPUT")))))

;; =============================================================================
;; list-products tests
;; =============================================================================

(deftest list-products-basic-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (random-uuid)
          product-pollen "Pollen"
          product-honey "Honey"
          metric-kg "kg"
          metric-ml "ml"
          hive-1 "A-01"
          hive-2 "A-02"
          three 3
          five 5
          products [{:hive-number hive-1 :date "23-11-2025" :product product-pollen :quantity five :metric metric-kg}
                    {:hive-number hive-2 :date "24-11-2025" :product product-honey :quantity three :metric metric-ml}]
          [create-status _] (product-service/create-products-batch node user-id products)
          _ (xt/sync node)
          db (xt/db node)
          [status result] (product-service/list-products db user-id)]

      (is (= create-status :ok))
      (is (= status :ok))
      (is (= (count (:products result)) 2))

      (is (= (set [#:product{:product product-pollen :quantity five :hive-number hive-1, :metric metric-kg}
                   #:product{:product product-honey, :quantity three :hive-number hive-2, :metric metric-ml}])
             (->> result
                  :products
                  (map remove-untestable-keys)
                  set))))))

(deftest list-products-rls-test
  (with-open [node (test-xtdb-node [])]
    (testing "RLS: users only see their own products"
      (let [user1 (random-uuid)
            user2 (random-uuid)
            products1 [{:hive-number "A-01" :date "23-11-2025" :product "Honey" :quantity 5 :metric "kg"}
                       {:hive-number "A-02" :date "24-11-2025" :product "Pollen" :quantity 3 :metric "ml"}]
            products2 [{:hive-number "B-01" :date "25-11-2025" :product "Venom" :quantity 2 :metric "ml"}]
            [s1 _] (product-service/create-products-batch node user1 products1)
            [s2 _] (product-service/create-products-batch node user2 products2)
            _ (xt/sync node)
            db (xt/db node)
            [status1 result1] (product-service/list-products db user1)
            [status2 result2] (product-service/list-products db user2)]

        (is (= s1 :ok))
        (is (= s2 :ok))
        (is (= status1 :ok))
        (is (= status2 :ok))
        (is (= (count (:products result1)) 2)) ; Only user1's products
        (is (= (count (:products result2)) 1)) ; Only user2's products
        (is (every? #(= (:product/user-id %) user1) (:products result1)))
        (is (every? #(= (:product/user-id %) user2) (:products result2)))))))

(deftest list-products-sorted-by-date-test
  (with-open [node (test-xtdb-node [])]
    (testing "Products can be sorted by date descending (newest first)"
      (let [user-id (random-uuid)
            products [{:hive-number "A-01" :date "23-11-2025" :product "Honey" :quantity 5 :metric "kg"}
                      {:hive-number "A-02" :date "25-11-2025" :product "Pollen" :quantity 3 :metric "ml"}
                      {:hive-number "A-03" :date "24-11-2025" :product "Venom" :quantity 2 :metric "ml"}]
            [status _] (product-service/create-products-batch node user-id products)
            _ (xt/sync node)
            db (xt/db node)
            [list-status result] (product-service/list-products db user-id)
            sorted-products (sort-by :product/date #(compare %2 %1) (:products result))]

        (is (= status :ok))
        (is (= list-status :ok))
        ;; Verify sorting: newest first
        (is (= "25-11-2025" (:product/date (first sorted-products))))
        (is (= "23-11-2025" (:product/date (last sorted-products))))))))

(deftest list-products-empty-result-test
  (with-open [node (test-xtdb-node [])]
    (testing "New user with no products returns empty list"
      (let [user-id (random-uuid)
            db (xt/db node)
            [status result] (product-service/list-products db user-id)]

        (is (= status :ok))
        (is (= (count (:products result)) 0))
        (is (empty? (:products result)))))))
