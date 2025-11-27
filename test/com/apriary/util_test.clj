(ns com.apriary.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.apriary.util :as util]))

;; =============================================================================
;; parse-uuid tests
;; =============================================================================

(deftest parse-uuid-valid-test
  (testing "Valid UUID string"
    (let [uuid-str "550e8400-e29b-41d4-a716-446655440000"
          [status result] (util/parse-uuid uuid-str)]

      (is (= status :ok))
      (is (instance? java.util.UUID result))
      (is (= (str result) uuid-str))))

  (testing "UUID from randomUUID"
    (let [uuid (java.util.UUID/randomUUID)
          uuid-str (str uuid)
          [status result] (util/parse-uuid uuid-str)]

      (is (= status :ok))
      (is (= result uuid)))))

(deftest parse-uuid-invalid-test
  (testing "Invalid UUID format"
    (let [[status result] (util/parse-uuid "not-a-uuid")]
      (is (= status :error))
      (is (= (:code result) "INVALID_UUID"))
      (is (str/includes? (:message result) "Invalid"))))

  (testing "Nil UUID"
    (let [[status result] (util/parse-uuid nil)]
      (is (= status :error))
      (is (= (:code result) "INVALID_UUID"))
      (is (str/includes? (:message result) "required"))))

  (testing "Empty string"
    (let [[status result] (util/parse-uuid "")]
      (is (= status :error))
      (is (= (:code result) "INVALID_UUID"))))

  (testing "UUID with wrong format"
    (let [[status result] (util/parse-uuid "550e8400e29b41d4a716446655440000")] ; Missing dashes
      (is (= status :error))
      (is (= (:code result) "INVALID_UUID")))))

;; =============================================================================
;; parse-int tests
;; =============================================================================

(deftest parse-int-valid-test
  (testing "Valid integer string"
    (let [[status result] (util/parse-int "42")]
      (is (= status :ok))
      (is (= result 42))))

  (testing "Negative integer"
    (let [[status result] (util/parse-int "-10")]
      (is (= status :ok))
      (is (= result -10))))

  (testing "Zero"
    (let [[status result] (util/parse-int "0")]
      (is (= status :ok))
      (is (= result 0))))

  (testing "Integer with whitespace (trimmed)"
    (let [[status result] (util/parse-int "  123  ")]
      (is (= status :ok))
      (is (= result 123)))))

(deftest parse-int-with-constraints-test
  (testing "Integer within min/max range"
    (let [[status result] (util/parse-int "50" :min 1 :max 100)]
      (is (= status :ok))
      (is (= result 50))))

  (testing "Integer at min boundary"
    (let [[status result] (util/parse-int "1" :min 1 :max 100)]
      (is (= status :ok))
      (is (= result 1))))

  (testing "Integer at max boundary"
    (let [[status result] (util/parse-int "100" :min 1 :max 100)]
      (is (= status :ok))
      (is (= result 100))))

  (testing "Integer below min"
    (let [[status result] (util/parse-int "0" :min 1 :max 100)]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE"))
      (is (str/includes? (:message result) ">= 1"))))

  (testing "Integer above max"
    (let [[status result] (util/parse-int "101" :min 1 :max 100)]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE"))
      (is (str/includes? (:message result) "<= 100")))))

(deftest parse-int-default-test
  (testing "Nil value with default"
    (let [[status result] (util/parse-int nil :default 50)]
      (is (= status :ok))
      (is (= result 50))))

  (testing "Nil value without default"
    (let [[status result] (util/parse-int nil)]
      (is (= status :error))
      (is (= (:code result) "MISSING_PARAM")))))

(deftest parse-int-invalid-test
  (testing "Non-numeric string"
    (let [[status result] (util/parse-int "abc")]
      (is (= status :error))
      (is (= (:code result) "INVALID_INTEGER"))))

  (testing "Decimal number"
    (let [[status result] (util/parse-int "42.5")]
      (is (= status :error))
      (is (= (:code result) "INVALID_INTEGER"))))

  (testing "Mixed alphanumeric"
    (let [[status result] (util/parse-int "12abc")]
      (is (= status :error))
      (is (= (:code result) "INVALID_INTEGER")))))

;; =============================================================================
;; validate-sort-by tests
;; =============================================================================

(deftest validate-sort-by-valid-test
  (testing "Valid sort-by values"
    (let [[status result] (util/validate-sort-by "created_at")]
      (is (= status :ok))
      (is (= result "created_at")))

    (let [[status result] (util/validate-sort-by "model")]
      (is (= status :ok))
      (is (= result "model")))

    (let [[status result] (util/validate-sort-by "generated_count")]
      (is (= status :ok))
      (is (= result "generated_count")))

    (let [[status result] (util/validate-sort-by "accepted_count")]
      (is (= status :ok))
      (is (= result "accepted_count")))))

(deftest validate-sort-by-default-test
  (testing "Nil defaults to created_at"
    (let [[status result] (util/validate-sort-by nil)]
      (is (= status :ok))
      (is (= result "created_at"))))

  (testing "Empty string defaults to created_at"
    (let [[status result] (util/validate-sort-by "")]
      (is (= status :ok))
      (is (= result "created_at")))))

(deftest validate-sort-by-invalid-test
  (testing "Invalid sort-by value"
    (let [[status result] (util/validate-sort-by "invalid_field")]
      (is (= status :error))
      (is (= (:code result) "INVALID_SORT_BY"))
      (is (str/includes? (:message result) "Allowed values")))))

(deftest validate-sort-by-whitespace-test
  (testing "Whitespace is trimmed"
    (let [[status result] (util/validate-sort-by "  model  ")]
      (is (= status :ok))
      (is (= result "model")))))

;; =============================================================================
;; validate-sort-order tests
;; =============================================================================

(deftest validate-sort-order-valid-test
  (testing "Valid sort-order: asc"
    (let [[status result] (util/validate-sort-order "asc")]
      (is (= status :ok))
      (is (= result "asc"))))

  (testing "Valid sort-order: desc"
    (let [[status result] (util/validate-sort-order "desc")]
      (is (= status :ok))
      (is (= result "desc"))))

  (testing "Uppercase is normalized to lowercase"
    (let [[status result] (util/validate-sort-order "ASC")]
      (is (= status :ok))
      (is (= result "asc")))

    (let [[status result] (util/validate-sort-order "DESC")]
      (is (= status :ok))
      (is (= result "desc")))))

(deftest validate-sort-order-default-test
  (testing "Nil defaults to desc"
    (let [[status result] (util/validate-sort-order nil)]
      (is (= status :ok))
      (is (= result "desc"))))

  (testing "Empty string defaults to desc"
    (let [[status result] (util/validate-sort-order "")]
      (is (= status :ok))
      (is (= result "desc")))))

(deftest validate-sort-order-invalid-test
  (testing "Invalid sort-order value"
    (let [[status result] (util/validate-sort-order "invalid")]
      (is (= status :error))
      (is (= (:code result) "INVALID_SORT_ORDER"))
      (is (str/includes? (:message result) "asc"))
      (is (str/includes? (:message result) "desc")))))

;; =============================================================================
;; validate-limit tests
;; =============================================================================

(deftest validate-limit-valid-test
  (testing "Valid limit"
    (let [[status result] (util/validate-limit "25")]
      (is (= status :ok))
      (is (= result 25))))

  (testing "Limit at min boundary (1)"
    (let [[status result] (util/validate-limit "1")]
      (is (= status :ok))
      (is (= result 1))))

  (testing "Limit at max boundary (100)"
    (let [[status result] (util/validate-limit "100")]
      (is (= status :ok))
      (is (= result 100)))))

(deftest validate-limit-default-test
  (testing "Nil defaults to 50"
    (let [[status result] (util/validate-limit nil)]
      (is (= status :ok))
      (is (= result 50)))))

(deftest validate-limit-invalid-test
  (testing "Limit too small (< 1)"
    (let [[status result] (util/validate-limit "0")]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE"))))

  (testing "Limit too large (> 100)"
    (let [[status result] (util/validate-limit "101")]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE"))))

  (testing "Negative limit"
    (let [[status result] (util/validate-limit "-5")]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE")))))

;; =============================================================================
;; validate-offset tests
;; =============================================================================

(deftest validate-offset-valid-test
  (testing "Valid offset"
    (let [[status result] (util/validate-offset "10")]
      (is (= status :ok))
      (is (= result 10))))

  (testing "Offset at boundary (0)"
    (let [[status result] (util/validate-offset "0")]
      (is (= status :ok))
      (is (= result 0))))

  (testing "Large offset"
    (let [[status result] (util/validate-offset "1000")]
      (is (= status :ok))
      (is (= result 1000)))))

(deftest validate-offset-default-test
  (testing "Nil defaults to 0"
    (let [[status result] (util/validate-offset nil)]
      (is (= status :ok))
      (is (= result 0)))))

(deftest validate-offset-invalid-test
  (testing "Negative offset"
    (let [[status result] (util/validate-offset "-1")]
      (is (= status :error))
      (is (= (:code result) "INVALID_RANGE"))
      (is (str/includes? (:message result) ">= 0")))))

;; =============================================================================
;; format-iso-8601 tests
;; =============================================================================

(deftest format-iso-8601-instant-test
  (testing "Format java.time.Instant"
    (let [instant (java.time.Instant/parse "2025-11-23T10:30:00Z")
          formatted (util/format-iso-8601 instant)]

      (is (string? formatted))
      (is (str/includes? formatted "2025-11-23"))
      (is (str/includes? formatted "10:30")))))

(deftest format-iso-8601-date-test
  (testing "Format java.util.Date"
    (let [date (java.util.Date. 1700000000000) ; Unix timestamp
          formatted (util/format-iso-8601 date)]

      (is (string? formatted))
      (is (str/includes? formatted "Z"))))) ; Should end with Z

(deftest format-iso-8601-current-time-test
  (testing "Format current time"
    (let [now (java.time.Instant/now)
          formatted (util/format-iso-8601 now)]

      (is (string? formatted))
      (is (str/includes? formatted "T")) ; ISO format has T separator
      (is (str/includes? formatted "Z"))))) ; UTC timezone

;; =============================================================================
;; Error response builder tests
;; =============================================================================

(deftest build-error-response-test
  (testing "Build standard error response"
    (let [response (util/build-error-response 400 "VALIDATION_ERROR" "Invalid input")]

      (is (map? response))
      (is (= (:error response) "Invalid input"))
      (is (= (:code response) "VALIDATION_ERROR"))
      (is (contains? response :timestamp))
      (is (map? (:details response)))))

  (testing "Build error response with details"
    (let [response (util/build-error-response 400 "VALIDATION_ERROR" "Invalid field"
                                              :details {:field "content" :reason "too short"})]

      (is (= (:field (:details response)) "content"))
      (is (= (:reason (:details response)) "too short")))))

(deftest validation-error-test
  (testing "Validation error helper"
    (let [response (util/validation-error "INVALID_CONTENT" "Content too short"
                                          :field "content" :reason "length < 50")]

      (is (= (:code response) "INVALID_CONTENT"))
      (is (= (:error response) "Content too short"))
      (is (= (get-in response [:details :field]) "content"))
      (is (= (get-in response [:details :reason]) "length < 50")))))

(deftest unauthorized-error-test
  (testing "Unauthorized error helper"
    (let [response (util/unauthorized-error)]

      (is (= (:code response) "UNAUTHORIZED"))
      (is (= (:error response) "User not authenticated"))
      (is (contains? response :timestamp)))))

(deftest forbidden-error-test
  (testing "Forbidden error helper"
    (let [response (util/forbidden-error)]

      (is (= (:code response) "FORBIDDEN"))
      (is (str/includes? (:error response) "permission"))
      (is (contains? response :timestamp)))))

(deftest not-found-error-test
  (testing "Not found error helper"
    (let [gen-id (java.util.UUID/randomUUID)
          response (util/not-found-error gen-id)]

      (is (= (:code response) "NOT_FOUND"))
      (is (str/includes? (:error response) (str gen-id)))
      (is (= (get-in response [:details :id]) (str gen-id))))))

(deftest internal-error-test
  (testing "Internal error helper"
    (let [response (util/internal-error "Database connection failed"
                                        :reason "Connection timeout")]

      (is (= (:code response) "INTERNAL_ERROR"))
      (is (= (:error response) "Database connection failed"))
      (is (= (get-in response [:details :reason]) "Connection timeout")))))
