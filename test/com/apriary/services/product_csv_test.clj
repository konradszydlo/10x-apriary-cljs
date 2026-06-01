(ns com.apriary.services.product-csv-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.apriary.services.product-csv :as sut]))

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
