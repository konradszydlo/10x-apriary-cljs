(ns com.apriary.services.openrouter-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.apriary.services.openrouter :as openrouter-service]))

;; =============================================================================
;; generate-summaries-batch tests (MOCKED)
;; =============================================================================

(deftest generate-summaries-batch-valid-test
  (testing "Mock generates summaries from observations"
    (let [observations [{:observation "First test observation with sufficient length for validation to pass"
                         :hive-number "A-01"
                         :observation-date "23-11-2025"
                         :special-feature "Queen active"}
                        {:observation "Second test observation also with enough text to meet minimum requirements"
                         :hive-number "A-02"
                         :observation-date "24-11-2025"
                         :special-feature "New frames"}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (some? (:summaries result)))
      (is (some? (:model result)))
      (is (some? (:duration-ms result)))
      (is (= (count (:summaries result)) 2))

      ;; Verify mock returns observation as summary
      (let [first-summary (first (:summaries result))]
        (is (= (:content first-summary) "First test observation with sufficient length for validation to pass"))
        (is (= (:observation first-summary) (:content first-summary)))
        (is (= (:hive-number first-summary) "A-01"))
        (is (= (:observation-date first-summary) "23-11-2025"))
        (is (= (:special-feature first-summary) "Queen active"))))))

(deftest generate-summaries-batch-single-observation-test
  (testing "Single observation"
    (let [observations [{:observation "Single test observation with sufficient length for validation"}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (= (count (:summaries result)) 1))
      (let [summary (first (:summaries result))]
        (is (= (:content summary) "Single test observation with sufficient length for validation"))))))

(deftest generate-summaries-batch-large-batch-test
  (testing "Large batch of observations"
    (let [observations (vec (for [i (range 100)]
                              {:observation (str "Test observation number " i
                                                 " with enough text to meet minimum length requirements for CSV import validation")
                               :hive-number (str "A-" (format "%02d" i))}))
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (= (count (:summaries result)) 100))
      (is (pos? (:duration-ms result)))
      (is (some? (:model result)))))

  (testing "Duration increases with batch size"
    (let [small-batch [{:observation (apply str (repeat 50 "x"))}]
          large-batch (vec (for [_i (range 50)]
                             {:observation (apply str (repeat 50 "x"))}))
          [_ small-result] (openrouter-service/generate-summaries-batch small-batch)
          [_ large-result] (openrouter-service/generate-summaries-batch large-batch)]

      ;; Large batch should take longer (mock simulates processing time)
      (is (> (:duration-ms large-result) (:duration-ms small-result))))))

(deftest generate-summaries-batch-custom-model-test
  (testing "Custom model parameter is returned in result"
    (let [observations [{:observation (apply str (repeat 50 "Test "))}]
          custom-model "claude-3-opus"
          [status result] (openrouter-service/generate-summaries-batch
                           observations
                           :model custom-model)]

      (is (= status :ok))
      (is (= (:model result) custom-model)))))

(deftest generate-summaries-batch-default-model-test
  (testing "Default model is used when not specified"
    (let [observations [{:observation (apply str (repeat 50 "Test "))}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (some? (:model result)))
      (is (= (:model result) "gpt-4-turbo"))))) ; Default model

(deftest generate-summaries-batch-preserves-metadata-test
  (testing "All metadata fields are preserved in mock"
    (let [observations [{:observation (apply str (repeat 50 "Test "))
                         :hive-number "X-99"
                         :observation-date "01-01-2025"
                         :special-feature "Test feature"}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (let [summary (first (:summaries result))]
        (is (= (:hive-number summary) "X-99"))
        (is (= (:observation-date summary) "01-01-2025"))
        (is (= (:special-feature summary) "Test feature"))
        (is (= (:observation summary) (:content summary)))))))

(deftest generate-summaries-batch-nil-metadata-test
  (testing "Observations with nil metadata fields"
    (let [observations [{:observation (apply str (repeat 50 "Test "))
                         :hive-number nil
                         :observation-date nil
                         :special-feature nil}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (let [summary (first (:summaries result))]
        (is (nil? (:hive-number summary)))
        (is (nil? (:observation-date summary)))
        (is (nil? (:special-feature summary)))))))

(deftest generate-summaries-batch-empty-batch-test
  (testing "Empty observations list"
    (let [[status result] (openrouter-service/generate-summaries-batch [])]

      (is (= status :ok))
      (is (= (count (:summaries result)) 0))
      (is (some? (:model result)))
      (is (some? (:duration-ms result))))))

(deftest generate-summaries-batch-mock-flag-test
  (testing "Mock is enabled in test environment"
    (let [observations [{:observation (apply str (repeat 50 "Test "))}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      ;; Verify it's actually mocked (returns observation as-is)
      (is (= status :ok))
      (let [summary (first (:summaries result))]
        (is (= (:content summary) (:observation summary)))))))

(deftest generate-summaries-batch-duration-measurement-test
  (testing "Duration is measured and returned"
    (let [observations [{:observation (apply str (repeat 50 "Test "))}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (pos? (:duration-ms result)))
      (is (integer? (:duration-ms result))))))

(deftest generate-summaries-batch-result-structure-test
  (testing "Result has correct structure"
    (let [observations [{:observation (apply str (repeat 50 "Test "))}]
          [status result] (openrouter-service/generate-summaries-batch observations)]

      (is (= status :ok))
      (is (contains? result :summaries))
      (is (contains? result :model))
      (is (contains? result :duration-ms))
      (is (vector? (:summaries result)))
      (is (string? (:model result)))
      (is (number? (:duration-ms result))))))

;; =============================================================================
;; Future production tests (commented out - for when API is integrated)
;; =============================================================================

(comment
  "These tests will be relevant when the actual OpenRouter API is integrated"

  (deftest generate-summaries-batch-api-error-test
    (testing "Handles API errors gracefully"
      ;; Will test HTTP errors, timeouts, rate limits
      ))

  (deftest generate-summaries-batch-retry-test
    (testing "Retries failed requests"
      ;; Will test retry logic
      ))

  (deftest generate-summaries-batch-api-key-validation-test
    (testing "Validates API key presence"
      ;; Will test API key validation
      )))
