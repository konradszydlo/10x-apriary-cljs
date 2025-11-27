(ns com.apriary.services.csv-import-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.apriary.services.csv-import :as csv-service]))

;; =============================================================================
;; parse-csv-string tests
;; =============================================================================

(deftest parse-csv-string-valid-test
  (testing "Parse valid CSV with semicolon delimiter"
    (let [csv "observation;hive_number;observation_date;special_feature\nFirst observation text;A-01;23-11-2025;Queen active\nSecond observation;A-02;24-11-2025;New frames"
          [status result] (csv-service/parse-csv-string csv)]

      (is (= status :ok))
      (is (some? (:headers result)))
      (is (some? (:rows result)))
      (is (= (count (:headers result)) 4))
      (is (= (count (:rows result)) 2))
      (is (= (first (:headers result)) "observation"))
      (is (= (second (:headers result)) "hive_number")))))

(deftest parse-csv-string-minimal-test
  (testing "Parse CSV with only required observation column"
    (let [csv (str "observation\n" (apply str (repeat 50 "x")))
          [status result] (csv-service/parse-csv-string csv)]

      (is (= status :ok))
      (is (= (count (:headers result)) 1))
      (is (= (count (:rows result)) 1)))))

(deftest parse-csv-string-empty-test
  (testing "Empty CSV string"
    (let [[status result] (csv-service/parse-csv-string "")]
      (is (= status :error))
      (is (= (:code result) "INVALID_CSV"))
      (is (str/includes? (:message result) "empty"))))

  (testing "Nil CSV string"
    (let [[status result] (csv-service/parse-csv-string nil)]
      (is (= status :error))
      (is (= (:code result) "INVALID_CSV")))))

(deftest parse-csv-string-no-headers-test
  (testing "CSV with no headers"
    (let [csv ""
          [status result] (csv-service/parse-csv-string csv)]
      (is (= status :error))
      (is (= (:code result) "INVALID_CSV")))))

(deftest parse-csv-string-no-data-rows-test
  (testing "CSV with headers but no data rows"
    (let [csv "observation;hive_number"
          [status result] (csv-service/parse-csv-string csv)]
      (is (= status :error))
      (is (= (:code result) "INVALID_CSV"))
      (is (str/includes? (:message result) "at least one data row")))))

;; =============================================================================
;; validate-csv-row tests
;; =============================================================================

(deftest validate-csv-row-valid-test
  (testing "Valid row with all fields"
    (let [row ["Test observation text that is long enough to pass validation" "A-01" "23-11-2025" "Queen spotted"]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (:observation result) "Test observation text that is long enough to pass validation"))
      (is (= (:hive-number result) "A-01"))
      (is (= (:observation-date result) "23-11-2025"))
      (is (= (:special-feature result) "Queen spotted"))))

  (testing "Valid row with only observation"
    (let [row [(apply str (repeat 50 "x"))]
          column-indices {:observation 0}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (count (:observation result)) 50))
      (is (nil? (:hive-number result)))
      (is (nil? (:observation-date result)))
      (is (nil? (:special-feature result))))))

(deftest validate-csv-row-observation-length-test
  (testing "Observation too short (< 50 chars)"
    (let [row ["Short" "A-01" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 5 column-indices)]

      (is (= status :error))
      (is (= (:row-number result) 5))
      (is (str/includes? (:reason result) "too short"))
      (is (str/includes? (:reason result) "50 characters"))))

  (testing "Observation too long (> 10,000 chars)"
    (let [long-text (apply str (repeat 10001 "x"))
          row [long-text "A-01" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 3 column-indices)]

      (is (= status :error))
      (is (= (:row-number result) 3))
      (is (str/includes? (:reason result) "too long"))
      (is (str/includes? (:reason result) "10,000"))))

  (testing "Observation at exactly 50 chars (boundary test)"
    (let [row [(apply str (repeat 50 "x")) "" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (count (:observation result)) 50))))

  (testing "Observation at exactly 10,000 chars (boundary test)"
    (let [row [(apply str (repeat 10000 "x")) "" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (count (:observation result)) 10000)))))

(deftest validate-csv-row-empty-observation-test
  (testing "Empty observation field"
    (let [row ["" "A-01" "23-11-2025" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 2 column-indices)]

      (is (= status :error))
      (is (= (:row-number result) 2))
      (is (str/includes? (:reason result) "empty"))))

  (testing "Whitespace-only observation"
    (let [row ["   " "A-01" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 4 column-indices)]

      (is (= status :error))
      (is (= (:row-number result) 4)))))

(deftest validate-csv-row-date-format-test
  (testing "Valid date format (DD-MM-YYYY)"
    (let [row [(apply str (repeat 50 "x")) "A-01" "23-11-2025" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (:observation-date result) "23-11-2025"))))

  (testing "Invalid date format (YYYY-MM-DD)"
    (let [row [(apply str (repeat 50 "x")) "A-01" "2025-11-23" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 3 column-indices)]

      (is (= status :error))
      (is (= (:row-number result) 3))
      (is (str/includes? (:reason result) "Invalid date format"))))

  (testing "Empty date field is valid"
    (let [row [(apply str (repeat 50 "x")) "A-01" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (nil? (:observation-date result))))))

(deftest validate-csv-row-trimming-test
  (testing "Observation text is trimmed"
    (let [row ["  This is a test observation with enough characters to pass validation   " "A-01" "" ""]
          column-indices {:observation 0 :hive-number 1 :observation-date 2 :special-feature 3}
          [status result] (csv-service/validate-csv-row row 1 column-indices)]

      (is (= status :ok))
      (is (= (:observation result) "This is a test observation with enough characters to pass validation")))))

;; =============================================================================
;; process-csv-import tests
;; =============================================================================

(deftest process-csv-import-valid-test
  (testing "Process valid CSV with multiple rows"
    (let [obs1 (apply str (repeat 60 "First observation "))
          obs2 (apply str (repeat 70 "Second observation "))
          csv (str "observation;hive_number;observation_date;special_feature\n"
                   obs1 ";A-01;23-11-2025;Queen active\n"
                   obs2 ";A-02;24-11-2025;New frames")
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-submitted result) 2))
      (is (= (:rows-valid result) 2))
      (is (= (:rows-rejected result) 0))
      (is (= (count (:valid-rows result)) 2))
      (is (= (count (:rejected-rows result)) 0))

      (let [first-row (first (:valid-rows result))]
        (is (str/includes? (:observation first-row) "First observation"))
        (is (= (:hive-number first-row) "A-01"))
        (is (= (:observation-date first-row) "23-11-2025"))
        (is (= (:special-feature first-row) "Queen active"))))))

(deftest process-csv-import-mixed-valid-invalid-test
  (testing "Process CSV with both valid and invalid rows"
    (let [valid-obs (apply str (repeat 60 "Valid observation "))
          csv (str "observation;hive_number;observation_date\n"
                   valid-obs ";A-01;23-11-2025\n"
                   "Short;A-02;24-11-2025\n"  ; Too short
                   valid-obs ";A-03;2025-11-25") ; Invalid date format
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-submitted result) 3))
      (is (= (:rows-valid result) 1))
      (is (= (:rows-rejected result) 2))
      (is (= (count (:valid-rows result)) 1))
      (is (= (count (:rejected-rows result)) 2))

      (let [rejected (sort-by :row-number (:rejected-rows result))]
        (is (= (:row-number (first rejected)) 3))
        (is (str/includes? (:reason (first rejected)) "too short"))
        (is (= (:row-number (second rejected)) 4))
        (is (str/includes? (:reason (second rejected)) "Invalid date format"))))))

(deftest process-csv-import-missing-observation-column-test
  (testing "CSV missing required observation column"
    (let [csv "hive_number;observation_date\nA-01;23-11-2025"
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :error))
      (is (= (:code result) "INVALID_CSV"))
      (is (str/includes? (:message result) "observation")))))

(deftest process-csv-import-all-rows-invalid-test
  (testing "All rows fail validation"
    (let [csv "observation;hive_number\nShort;A-01\nTiny;A-02\nSmall;A-03"
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok)) ; Parsing succeeds
      (is (= (:rows-submitted result) 3))
      (is (= (:rows-valid result) 0))
      (is (= (:rows-rejected result) 3))
      (is (= (count (:valid-rows result)) 0))
      (is (= (count (:rejected-rows result)) 3)))))

(deftest process-csv-import-case-insensitive-headers-test
  (testing "Headers are case-insensitive"
    (let [obs (apply str (repeat 60 "Test observation "))
          csv (str "OBSERVATION;HIVE_NUMBER;OBSERVATION_DATE\n"  ; Uppercase headers
                   obs ";A-01;23-11-2025")
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-valid result) 1))
      (let [first-row (first (:valid-rows result))]
        (is (str/includes? (:observation first-row) "Test observation"))))))

(deftest process-csv-import-optional-fields-test
  (testing "Optional fields can be empty or missing"
    (let [obs (apply str (repeat 60 "Test observation "))
          csv (str "observation\n" obs)  ; Only observation column
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-valid result) 1))
      (let [first-row (first (:valid-rows result))]
        (is (nil? (:hive-number first-row)))
        (is (nil? (:observation-date first-row)))
        (is (nil? (:special-feature first-row)))))))

(deftest process-csv-import-empty-optional-fields-test
  (testing "Empty strings in optional fields become nil"
    (let [obs (apply str (repeat 60 "Test observation "))
          csv (str "observation;hive_number;observation_date;special_feature\n"
                   obs ";;;")  ; All optional fields empty
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-valid result) 1))
      (let [first-row (first (:valid-rows result))]
        (is (nil? (:hive-number first-row)))
        (is (nil? (:observation-date first-row)))
        (is (nil? (:special-feature first-row)))))))

(deftest process-csv-import-row-number-tracking-test
  (testing "Rejected rows track correct row numbers"
    (let [csv "observation\nShort\nTiny\nSmall"
          [status result] (csv-service/process-csv-import csv)]

      (is (= status :ok))
      (is (= (:rows-rejected result) 3))
      (let [rejected (sort-by :row-number (:rejected-rows result))]
        (is (= (:row-number (nth rejected 0)) 2))  ; First data row is row 2 (after header)
        (is (= (:row-number (nth rejected 1)) 3))
        (is (= (:row-number (nth rejected 2)) 4))))))
