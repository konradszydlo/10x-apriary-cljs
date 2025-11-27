(ns com.apriary.ui.helpers
  "Helper functions for working with toasts and error messages in route handlers."
  (:require [com.apriary.ui.toast :as toast]
            [com.apriary.ui.error :as error]))

;; =============================================================================
;; Flash Message Helpers
;; =============================================================================

(defn success-flash
  "Creates a success flash message for use in redirects.

  Args:
    message - Success message text

  Returns:
    Flash map suitable for :session :flash

  Example:
    {:status 303
     :headers {\"Location\" \"/\"}
     :session (assoc session :flash (success-flash \"Summary created!\"))}"
  [message]
  {:message {:type :success
             :text message}})

(defn error-flash
  "Creates an error flash message for use in redirects.

  Args:
    message - Error message text
    code - Optional error code (default: \"ERROR\")
    details - Optional error details map

  Returns:
    Flash map suitable for :session :flash

  Example:
    {:status 303
     :headers {\"Location\" \"/\"}
     :session (assoc session :flash (error-flash \"Operation failed\" \"VALIDATION_ERROR\"))}"
  ([message]
   (error-flash message "ERROR" nil))
  ([message code]
   (error-flash message code nil))
  ([message code details]
   {:error {:error message
            :code code
            :details details
            :timestamp (str (java.time.Instant/now))
            :heading (get error/error-headings code "Error")}}))

(defn info-flash
  "Creates an info flash message for use in redirects.

  Args:
    message - Info message text

  Returns:
    Flash map suitable for :session :flash"
  [message]
  {:message {:type :info
             :text message}})

;; =============================================================================
;; Toast Response Helpers (for HTMX responses)
;; =============================================================================

(defn success-toast-oob
  "Generates success toast OOB HTML for appending to HTMX responses.

  Args:
    message - Success message text

  Returns:
    Hiccup HTML for OOB swap

  Example:
    {:status 200
     :body (str
             main-content-html
             (success-toast-oob \"Operation successful\"))}"
  [message]
  (toast/toast-oob
   {:type :success
    :message message
    :auto-dismiss true
    :duration-ms 3000}))

(defn error-toast-oob
  "Generates error toast OOB HTML for appending to HTMX responses.

  Args:
    message - Error message text

  Returns:
    Hiccup HTML for OOB swap"
  [message]
  (toast/toast-oob
   {:type :error
    :message message
    :auto-dismiss false}))

(defn info-toast-oob
  "Generates info toast OOB HTML for appending to HTMX responses.

  Args:
    message - Info message text

  Returns:
    Hiccup HTML for OOB swap"
  [message]
  (toast/toast-oob
   {:type :info
    :message message
    :auto-dismiss true
    :duration-ms 5000}))

;; =============================================================================
;; Error Response Helpers
;; =============================================================================

(defn exception->error-data
  "Converts an exception to error data suitable for error-message-area.

  This is a convenience wrapper around error/error-data.

  Args:
    ex - Exception or throwable

  Returns:
    Error data map"
  [ex]
  (error/error-data ex))
