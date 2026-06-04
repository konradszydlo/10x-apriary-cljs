(ns com.apriary.services.product-rankings-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.services.product :as product-service]
            [com.apriary.services.product-rankings :as rankings]
            [xtdb.api :as xt]))

(defn create-test-products
  "Helper to create product records for ranking tests"
  [node user-id products]
  (product-service/create-products-batch node user-id products)
  (xt/sync node))

;; =============================================================================
;; calculate-rankings tests
;; =============================================================================

(deftest calculate-rankings-basic-test
  "Happy path: 10 hives, verify top 5 and bottom 5 are correct"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; Create 10 hives with varying honey quantities (kg)
          products (mapv (fn [i]
                          {:hive-number (str "H-" (format "%02d" (inc i)))
                           :date "01-01-2025"
                           :product "Honey"
                           :quantity (* (inc i) 10) ; 10, 20, 30, ..., 100
                           :metric "kg"})
                        (range 10))
          _ (create-test-products node user-id products)
          db (xt/db node)
          [status result] (rankings/calculate-rankings db user-id :n 5)]

      (is (= status :ok))
      (let [honey-rankings (get-in result [:rankings "Honey"])]
        ;; Verify top 5: highest quantities first
        (is (= (count (:top honey-rankings)) 5))
        (is (= (map :hive-number (:top honey-rankings))
               ["H-10" "H-09" "H-08" "H-07" "H-06"]))
        (is (= (map :total-quantity (:top honey-rankings))
               [100 90 80 70 60]))

        ;; Verify bottom 5: lowest quantities
        (is (= (count (:bottom honey-rankings)) 5))
        (is (= (map :hive-number (:bottom honey-rankings))
               ["H-01" "H-02" "H-03" "H-04" "H-05"]))
        (is (= (map :total-quantity (:bottom honey-rankings))
               [10 20 30 40 50]))))))

(deftest calculate-rankings-fewer-than-five-hives-test
  ; Risk #5 edge case
  "3 hives total → verify top/bottom both return 3 entries, not 5"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          products [{:hive-number "A-01" :date "01-01-2025" :product "Honey" :quantity 30 :metric "kg"}
                    {:hive-number "A-02" :date "01-01-2025" :product "Honey" :quantity 10 :metric "kg"}
                    {:hive-number "A-03" :date "01-01-2025" :product "Honey" :quantity 20 :metric "kg"}]
          _ (create-test-products node user-id products)
          db (xt/db node)
          [status result] (rankings/calculate-rankings db user-id :n 5)]

      (is (= status :ok))
      (let [honey-rankings (get-in result [:rankings "Honey"])]
        ;; With only 3 hives, both top and bottom should return 3 entries
        (is (= (count (:top honey-rankings)) 3))
        (is (= (count (:bottom honey-rankings)) 3))

        ;; Verify ordering
        (is (= (map :total-quantity (:top honey-rankings)) [30 20 10]))
        (is (= (map :total-quantity (:bottom honey-rankings)) [10 20 30]))))))

(deftest calculate-rankings-zero-quantity-test
  ; Risk #5 edge case
  "Hive with 0 total quantity → verify it appears in bottom rankings"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; Create one hive with 0 quantity, others with positive
          products [{:hive-number "Z-00" :date "01-01-2025" :product "Honey" :quantity 0 :metric "kg"}
                    {:hive-number "A-01" :date "01-01-2025" :product "Honey" :quantity 50 :metric "kg"}
                    {:hive-number "A-02" :date "01-01-2025" :product "Honey" :quantity 30 :metric "kg"}
                    {:hive-number "A-03" :date "01-01-2025" :product "Honey" :quantity 40 :metric "kg"}]
          _ (create-test-products node user-id products)
          db (xt/db node)
          [status result] (rankings/calculate-rankings db user-id :n 5)]

      (is (= status :ok))
      (let [honey-rankings (get-in result [:rankings "Honey"])
            bottom-hives (map :hive-number (:bottom honey-rankings))]
        ;; Zero-quantity hive should be in bottom rankings (lowest)
        (is (some #(= % "Z-00") bottom-hives))
        ;; Verify it's the first entry (lowest quantity)
        (is (= (:hive-number (first (:bottom honey-rankings))) "Z-00"))
        (is (= (:total-quantity (first (:bottom honey-rankings))) 0))))))

(deftest calculate-rankings-tie-scenario-test
  ; Risk #5 edge case
  "Two hives with identical totals → verify both appear in results (order may vary)"
  (testing "Tie-breaking is undefined (relies on XTDB result order)"
    (with-open [node (test-xtdb-node [])]
      (let [user-id (java.util.UUID/randomUUID)
            ;; Create two hives with identical totals (50 kg each)
            products [{:hive-number "TIE-A" :date "01-01-2025" :product "Honey" :quantity 50 :metric "kg"}
                      {:hive-number "TIE-B" :date "01-01-2025" :product "Honey" :quantity 50 :metric "kg"}
                      {:hive-number "H-01" :date "01-01-2025" :product "Honey" :quantity 10 :metric "kg"}
                      {:hive-number "H-02" :date "01-01-2025" :product "Honey" :quantity 20 :metric "kg"}]
            _ (create-test-products node user-id products)
            db (xt/db node)
            [status result] (rankings/calculate-rankings db user-id :n 5)]

        (is (= status :ok))
        (let [honey-rankings (get-in result [:rankings "Honey"])
              top-hives (set (map :hive-number (:top honey-rankings)))]
          ;; Both tied hives should appear in results
          (is (contains? top-hives "TIE-A"))
          (is (contains? top-hives "TIE-B"))

          ;; Both should have the same quantity
          (is (every? #(= (:total-quantity %) 50)
                     (filter #(#{"TIE-A" "TIE-B"} (:hive-number %))
                            (:top honey-rankings)))))))))

(deftest calculate-rankings-multi-product-test
  "Multiple product types → verify each has independent top/bottom rankings"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; Create products: Honey and Pollen across different hives
          products [{:hive-number "H-01" :date "01-01-2025" :product "Honey" :quantity 100 :metric "kg"}
                    {:hive-number "H-02" :date "01-01-2025" :product "Honey" :quantity 50 :metric "kg"}
                    {:hive-number "H-03" :date "01-01-2025" :product "Honey" :quantity 75 :metric "kg"}

                    {:hive-number "P-01" :date "01-01-2025" :product "Pollen" :quantity 20 :metric "kg"}
                    {:hive-number "P-02" :date "01-01-2025" :product "Pollen" :quantity 40 :metric "kg"}
                    {:hive-number "P-03" :date "01-01-2025" :product "Pollen" :quantity 30 :metric "kg"}]
          _ (create-test-products node user-id products)
          db (xt/db node)
          [status result] (rankings/calculate-rankings db user-id :n 5)]

      (is (= status :ok))
      (let [rankings (:rankings result)]
        ;; Verify both product types have rankings
        (is (contains? rankings "Honey"))
        (is (contains? rankings "Pollen"))

        ;; Verify Honey rankings are independent
        (let [honey-rankings (get rankings "Honey")]
          (is (= (map :hive-number (:top honey-rankings))
                 ["H-01" "H-03" "H-02"]))
          (is (= (map :total-quantity (:top honey-rankings))
                 [100 75 50])))

        ;; Verify Pollen rankings are independent
        (let [pollen-rankings (get rankings "Pollen")]
          (is (= (map :hive-number (:top pollen-rankings))
                 ["P-02" "P-03" "P-01"]))
          (is (= (map :total-quantity (:top pollen-rankings))
                 [40 30 20])))))))
