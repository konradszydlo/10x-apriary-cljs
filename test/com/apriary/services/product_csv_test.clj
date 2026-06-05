(ns com.apriary.services.product-csv-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.apriary.services.product-csv :as sut]
            [malli.core :as m]
            [com.apriary.schema :as schema]))

(deftest validate-product-row-test
  (let [_ ["hive_number" "date" "product" "quantity" "metric"]
        column-indices {:hive-number 0 :date 1 :product 2 :quantity 3 :metric 4}]

    (testing "Valid row with all fields"
      (let [row ["A-01" "23-11-2025" "Honey" "5" "kg"]
            [status result] (sut/validate-product-row row 2 column-indices)]
        (is (= :valid status))
        (is (= "A-01" (:hive-number result)))
        (is (= "23-11-2025" (:date result)))
        (is (= "Honey" (:product result)))
        (is (= 5 (:quantity result)))
        (is (= "kg" (:metric result)))))

    (testing "Valid row with empty date (optional)"
      (let [row ["A-02" "" "Pollen" "3" "ml"]
            [status result] (sut/validate-product-row row 3 column-indices)]
        (is (= :valid status))
        (is (nil? (:date result)))))

    (testing "Missing hive_number"
      (let [row ["" "23-11-2025" "Honey" "5" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"hive_number.*empty" reason))))

    (testing "Missing product"
      (let [row ["A-01" "23-11-2025" "" "5" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"product.*empty" reason))))

    (testing "Invalid date format (DD/MM/YYYY with slashes)"
      (let [row ["A-01" "23/11/2025" "Honey" "5" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"Invalid date format" reason))))

    (testing "Quantity = 0"
      (let [row ["A-01" "23-11-2025" "Honey" "0" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"Quantity must be positive" reason))))

    (testing "Quantity = -5"
      (let [row ["A-01" "23-11-2025" "Honey" "-5" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"Quantity must be positive" reason))))

    (testing "Quantity = non-numeric"
      (let [row ["A-01" "23-11-2025" "Honey" "abc" "kg"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"Quantity must be a valid integer" reason))))

    (testing "Invalid metric (not in enum)"
      (let [row ["A-01" "23-11-2025" "Honey" "5" "liter"]
            [status reason] (sut/validate-product-row row 2 column-indices)]
        (is (= :invalid status))
        (is (re-find #"Metric must be one of" reason))))

    (testing "Valid metric: kg"
      (let [row ["A-01" "23-11-2025" "Honey" "5" "kg"]
            [status result] (sut/validate-product-row row 2 column-indices)]
        (is (= :valid status))
        (is (= "kg" (:metric result)))))

    (testing "Valid metric: ml"
      (let [row ["A-01" "23-11-2025" "Venom" "2" "ml"]
            [status result] (sut/validate-product-row row 2 column-indices)]
        (is (= :valid status))
        (is (= "ml" (:metric result)))))

    (testing "Valid metric: g"
      (let [row ["A-01" "23-11-2025" "Pollen" "100" "g"]
            [status result] (sut/validate-product-row row 2 column-indices)]
        (is (= :valid status))
        (is (= "g" (:metric result)))))))

(deftest process-product-csv-test
  (testing "Missing required column (hive_number)"
    (let [parsed-csv {:headers ["date" "product" "quantity" "metric"]
                      :rows [["23-11-2025" "Honey" "5" "kg"]]}
          [status result] (sut/process-product-csv parsed-csv)]
      (is (= :error status))
      (is (= "INVALID_CSV" (:code result)))
      (is (re-find #"hive_number" (:message result)))))

  (testing "Missing required column (quantity)"
    (let [parsed-csv {:headers ["hive_number" "date" "product" "metric"]
                      :rows [["A-01" "23-11-2025" "Honey" "kg"]]}
          [status result] (sut/process-product-csv parsed-csv)]
      (is (= :error status))
      (is (= "INVALID_CSV" (:code result)))
      (is (re-find #"quantity" (:message result)))))

  (testing "Valid CSV with all rows valid"
    (let [parsed-csv {:headers ["hive_number" "date" "product" "quantity" "metric"]
                      :rows [["A-01" "23-11-2025" "Honey" "5" "kg"]
                             ["A-02" "24-11-2025" "Pollen" "3" "ml"]]}
          [status result] (sut/process-product-csv parsed-csv)]
      (is (= :ok status))
      (is (= 2 (:rows-submitted result)))
      (is (= 2 (:rows-valid result)))
      (is (= 0 (:rows-rejected result)))
      (is (= 2 (count (:valid-rows result))))
      (is (= 0 (count (:rejected-rows result))))))

  (testing "Mixed valid and invalid rows"
    (let [parsed-csv {:headers ["hive_number" "date" "product" "quantity" "metric"]
                      :rows [["A-01" "23-11-2025" "Honey" "5" "kg"]      ; valid
                             ["" "24-11-2025" "Pollen" "3" "ml"]          ; invalid: missing hive_number
                             ["A-03" "25-11-2025" "Venom" "0" "ml"]]}     ; invalid: quantity = 0
          [status result] (sut/process-product-csv parsed-csv)]
      (is (= :ok status))
      (is (= 3 (:rows-submitted result)))
      (is (= 1 (:rows-valid result)))
      (is (= 2 (:rows-rejected result)))
      (is (= 1 (count (:valid-rows result))))
      (is (= 2 (count (:rejected-rows result))))
      ;; Check rejected row numbers
      (is (= 3 (:row-number (first (:rejected-rows result)))))
      (is (= 4 (:row-number (second (:rejected-rows result))))))))

(deftest csv-validator-matches-schema-test
  "Verify CSV validator output matches Malli :product schema (prevents drift)"
  (let [product-schema (:product (:schema schema/module))
        user-id (random-uuid)
        now (java.util.Date.)]

    (testing "Valid CSV row output passes Malli validation - metric kg"
      (let [csv-row {:hive-number "A-01"
                     :date "23-11-2025"
                     :product "Honey"
                     :quantity 5
                     :metric "kg"}
            ;; Construct entity as product service would
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        ;; Malli validation should pass (explain returns nil on success)
        (is (nil? (m/explain product-schema entity)))))

    (testing "Valid CSV row output passes Malli validation - metric ml"
      (let [csv-row {:hive-number "B-02"
                     :date "24-11-2025"
                     :product "Venom"
                     :quantity 2
                     :metric "ml"}
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        (is (nil? (m/explain product-schema entity)))))

    (testing "Valid CSV row output passes Malli validation - metric g"
      (let [csv-row {:hive-number "C-03"
                     :date "25-11-2025"
                     :product "Pollen"
                     :quantity 100
                     :metric "g"}
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        (is (nil? (m/explain product-schema entity)))))

    (testing "Negative test: invalid metric fails Malli validation"
      (let [csv-row {:hive-number "D-04"
                     :date "26-11-2025"
                     :product "Honey"
                     :quantity 50
                     :metric "liters"} ; Invalid metric
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        ;; Malli validation should fail (explain returns non-nil)
        (is (some? (m/explain product-schema entity)))
        ;; Verify the error is about the metric field
        (is (contains? (set (map :in (:errors (m/explain product-schema entity))))
                       [:product/metric]))))

    (testing "Quantity constraint matches CSV validator rule (> 0)"
      (let [csv-row {:hive-number "E-05"
                     :date "27-11-2025"
                     :product "Honey"
                     :quantity 1  ; Minimum valid quantity
                     :metric "kg"}
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        ;; Quantity 1 should pass (min 1)
        (is (nil? (m/explain product-schema entity))))

      ;; Quantity 0 should fail
      (let [csv-row {:hive-number "E-06"
                     :date "27-11-2025"
                     :product "Honey"
                     :quantity 0  ; Invalid: below min
                     :metric "kg"}
            entity {:xt/id (random-uuid)
                    :product/id (random-uuid)
                    :product/user-id user-id
                    :product/hive-number (:hive-number csv-row)
                    :product/date (:date csv-row)
                    :product/product (:product csv-row)
                    :product/quantity (:quantity csv-row)
                    :product/metric (:metric csv-row)
                    :product/created-at now
                    :product/updated-at now}]
        (is (some? (m/explain product-schema entity)))))))
