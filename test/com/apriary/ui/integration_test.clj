(ns com.apriary.ui.integration-test
  "Integration tests for UI components demonstrating proper usage patterns.

  These tests serve as both verification and documentation of how the UI
  components should be integrated into route handlers."
  (:require [clojure.test :refer [deftest is testing run-tests test-var]]
            [com.apriary.ui.toast :as toast]
            [com.apriary.ui.error :as error]
            [com.apriary.ui.header :as header]
            [com.apriary.ui.helpers :as ui-helpers]
            [clojure.string :as str]))

;; =============================================================================
;; Toast Component Tests
;; =============================================================================


(deftest toast-html-generation-test
  (testing "Success toast with auto-dismiss"
    (let [toast-html (toast/toast-html
                      {:type :success
                       :message "Operation successful"
                       :auto-dismiss true
                       :duration-ms 3000})]
      (is (vector? toast-html))
      (is (= :div (first toast-html)))
      ;; Check for success styling
      (is (str/includes? (str toast-html) "bg-green-100"))
      ;; Check for message content
      (is (str/includes? (str toast-html) "Operation successful"))))

  (testing "Error toast without auto-dismiss"
    (let [toast-html (toast/toast-html
                      {:type :error
                       :message "Operation failed"
                       :auto-dismiss false})]
      (is (vector? toast-html))
      ;; Check for error styling
      (is (str/includes? (str toast-html) "bg-red-100"))
      ;; Should have close button (no auto-dismiss)
      (is (str/includes? (str toast-html) "Close notification"))))

  (testing "Toast with generated ID"
    (let [toast-html (toast/toast-html
                      {:type :info
                       :message "Info message"})]
      ;; Should generate random ID
      (is (str/includes? (str toast-html) "toast-")))))

(deftest toast-oob-generation-test
  (testing "OOB wrapper includes hx-swap-oob"
    (let [oob-html (toast/toast-oob
                    {:type :success
                     :message "Test"})]
      (is (vector? oob-html))
      (is (= :div (first oob-html)))
      ;; Check for OOB swap directive
      (is (str/includes? (str oob-html) "afterbegin:#toast-container")))))

(deftest toast-container-test
  (testing "Container has proper ARIA attributes"
    (let [container (toast/toast-container)]
      (is (vector? container))
      (is (= :div (first container)))
      ;; Check attributes
      (let [attrs (second container)]
        (is (= "polite" (:aria-live attrs)))
        (is (= "false" (:aria-atomic attrs)))
        (is (= "status" (:role attrs)))))))

;; =============================================================================
;; Error Component Tests
;; =============================================================================

(deftest error-data-generation-test
  (testing "Exception to error data conversion"
    (let [ex (ex-info "Test error" {:code "VALIDATION_ERROR"})
          error-data (error/error-data ex)]
      (is (map? error-data))
      (is (= "Test error" (:error error-data)))
      (is (= "VALIDATION_ERROR" (:code error-data)))
      (is (= "Validation Error" (:heading error-data)))
      (is (some? (:timestamp error-data)))))

  (testing "Exception without code defaults to INTERNAL_ERROR"
    (let [ex (Exception. "Generic error")
          error-data (error/error-data ex)]
      (is (= "INTERNAL_ERROR" (:code error-data)))
      (is (= "Error" (:heading error-data))))))

(deftest error-message-area-test
  (testing "Error message area renders with all fields"
    (let [error-data {:error "Something went wrong"
                      :code "VALIDATION_ERROR"
                      :heading "Validation Error"
                      :details {:field "email"}}
          html (error/error-message-area error-data)]
      (is (vector? html))
      ;; Check for error message
      (is (str/includes? (str html) "Something went wrong"))
      ;; Check for heading
      (is (str/includes? (str html) "Validation Error"))
      ;; Check for details section
      (is (str/includes? (str html) "Show technical details"))))

  (testing "Error message area returns nil when no error"
    (is (nil? (error/error-message-area nil)))))

;; =============================================================================
;; Header Component Tests
;; =============================================================================

(deftest application-header-test
  (testing "Header renders for authenticated user"
    (let [ctx {:session {:uid "user-123"}}
          html (header/application-header ctx)]
      (is (vector? html))
      (is (= :header (first html)))
      ;; Check for app name
      (is (str/includes? (str html) "Apriary Summary"))
      ;; Check for New Summary button
      (is (str/includes? (str html) "New Summary"))
      ;; Check for Logout button
      (is (str/includes? (str html) "Logout"))))

  (testing "Header returns nil for unauthenticated user"
    (let [ctx {:session {}}
          html (header/application-header ctx)]
      (is (nil? html)))))

;; =============================================================================
;; Helper Functions Tests
;; =============================================================================

(deftest flash-message-helpers-test
  (testing "Success flash helper"
    (let [flash (ui-helpers/success-flash "Operation successful")]
      (is (map? flash))
      (is (= :success (get-in flash [:message :type])))
      (is (= "Operation successful" (get-in flash [:message :text])))))

  (testing "Error flash helper"
    (let [flash (ui-helpers/error-flash "Failed" "VALIDATION_ERROR")]
      (is (map? flash))
      (is (= "Failed" (get-in flash [:error :error])))
      (is (= "VALIDATION_ERROR" (get-in flash [:error :code])))
      (is (= "Validation Error" (get-in flash [:error :heading])))))

  (testing "Info flash helper"
    (let [flash (ui-helpers/info-flash "Note")]
      (is (map? flash))
      (is (= :info (get-in flash [:message :type])))
      (is (= "Note" (get-in flash [:message :text]))))))

(deftest toast-oob-helpers-test
  (testing "Success toast OOB helper"
    (let [html (ui-helpers/success-toast-oob "Success")]
      (is (vector? html))
      (is (str/includes? (str html) "Success"))
      (is (str/includes? (str html) "bg-green-100"))))

  (testing "Error toast OOB helper"
    (let [html (ui-helpers/error-toast-oob "Error")]
      (is (vector? html))
      (is (str/includes? (str html) "Error"))
      (is (str/includes? (str html) "bg-red-100"))))

  (testing "Info toast OOB helper"
    (let [html (ui-helpers/info-toast-oob "Info")]
      (is (vector? html))
      (is (str/includes? (str html) "Info"))
      (is (str/includes? (str html) "bg-blue-100")))))

;; =============================================================================
;; Integration Pattern Tests
;; =============================================================================

(deftest flash-message-integration-pattern-test
  (testing "Success redirect with flash message pattern"
    (let [session {:uid "user-123"}
          response {:status 303
                    :headers {"Location" "/summaries"}
                    :session (assoc session :flash
                                    (ui-helpers/success-flash "Summary created!"))}]
      (is (= 303 (:status response)))
      (is (= "/summaries" (get-in response [:headers "Location"])))
      (is (= "Summary created!" (get-in response [:session :flash :message :text])))))

  (testing "Error redirect with flash message pattern"
    (let [session {:uid "user-123"}
          response {:status 303
                    :headers {"Location" "/summaries"}
                    :session (assoc session :flash
                                    (ui-helpers/error-flash "Failed" "VALIDATION_ERROR"))}]
      (is (= 303 (:status response)))
      (is (= "Failed" (get-in response [:session :flash :error :error])))
      (is (= "VALIDATION_ERROR" (get-in response [:session :flash :error :code]))))))

(deftest htmx-toast-integration-pattern-test
  (testing "HTMX response with success toast"
    (let [main-content "<div>Updated content</div>"
          response {:status 200
                    :headers {"content-type" "text/html"}
                    :body (str main-content
                               (ui-helpers/success-toast-oob "Update successful"))}]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "Updated content"))
      (is (str/includes? (:body response) "Update successful"))
      (is (str/includes? (:body response) "afterbegin:#toast-container"))))

  (testing "HTMX error response with toast"
    (let [error-content "<div>Error occurred</div>"
          response {:status 400
                    :headers {"content-type" "text/html"}
                    :body (str error-content
                               (ui-helpers/error-toast-oob "Validation failed"))}]
      (is (= 400 (:status response)))
      (is (str/includes? (:body response) "Validation failed")))))

;; =============================================================================
;; Run All Tests
;; =============================================================================

(comment
  ;; Run all tests in this namespace
  (run-tests 'com.apriary.ui.integration-test)

  ;; Run specific test
  (test-var #'toast-html-generation-test)
  )
