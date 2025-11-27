(ns com.apriary.ui.error
  "Error message component for page-level error display.

  Error messages are prominently displayed below the header when critical errors occur.
  They provide detailed error information with an option to dismiss and view technical details.

  Usage:

    (require '[com.apriary.ui.error :as error])

    ;; In route handler
    {:status 400
     :body (layout/base-html
             ctx
             {:error-message (error/error-data ex)}
             (page-content))}"
  (:require [clojure.pprint :refer [pprint]]))

(def error-headings
  "Maps error codes to user-friendly headings."
  {"VALIDATION_ERROR" "Validation Error"
   "UNAUTHORIZED" "Authentication Required"
   "FORBIDDEN" "Access Denied"
   "NOT_FOUND" "Not Found"
   "RATE_LIMIT_EXCEEDED" "Rate Limit Exceeded"
   "INTERNAL_ERROR" "Server Error"
   "EXTERNAL_SERVICE_ERROR" "Service Error"
   "SERVICE_UNAVAILABLE" "Service Unavailable"
   "CONFLICT" "Conflict"
   "INVALID_REQUEST" "Invalid Request"})

(defn- error-icon
  "Returns error warning icon SVG."
  []
  [:svg.h-6.w-6.text-red-600
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M9.401 3.003c1.155-2 4.043-2 5.197 0l7.355 12.748c1.154 2-.29 4.5-2.599 4.5H4.645c-2.309 0-3.752-2.5-2.598-4.5L9.4 3.003zM12 8.25a.75.75 0 01.75.75v3.75a.75.75 0 01-1.5 0V9a.75.75 0 01.75-.75zm0 8.25a.75.75 0 100-1.5.75.75 0 000 1.5z"
           :clip-rule "evenodd"}]])

(defn- close-icon
  "Returns close (X) icon SVG."
  []
  [:svg.h-5.w-5.text-red-600
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 20 20"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:d "M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z"}]])

(defn error-data
  "Converts exception to error message ViewModel.

  Args:
    ex - Exception or throwable object

  Returns:
    Map with keys:
      :error - Human-readable error message
      :code - Error code constant
      :details - Additional structured error details (optional)
      :timestamp - ISO 8601 timestamp
      :heading - Error category/heading for display"
  [ex]
  (let [data (ex-data ex)]
    {:error (.getMessage ex)
     :code (or (:code data) "INTERNAL_ERROR")
     :details (when data (dissoc data :status :code))
     :timestamp (str (java.time.Instant/now))
     :heading (get error-headings (:code data) "Error")}))

(defn error-message-area
  "Renders page-level error message area.

  Args:
    error-data - Map with keys:
      :error (required) - Human-readable error message
      :code - Error code constant
      :details - Additional structured error details (optional)
      :heading - Error category/heading (derived from code if not provided)

  Returns:
    Hiccup vector representing the error message area, or nil if no error"
  [error-data]
  (when error-data
    (let [heading (or (:heading error-data)
                      (get error-headings (:code error-data))
                      "Error")
          message (or (:error error-data)
                      "An unexpected error occurred. Please try again.")]
      [:div#error-message-area.mb-6.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
       [:div.bg-red-50.border-2.border-red-500.rounded.p-4
        {:role "alert"
         :aria-live "assertive"}
        [:div.flex.items-start.gap-3
         ;; Error Icon
         [:div.flex-shrink-0
          (error-icon)]

         ;; Error Content
         [:div.flex-1
          [:h3.text-lg.font-semibold.text-red-800 heading]
          [:p.mt-1.text-sm.text-red-700 message]

          ;; Optional: Expandable details
          (when (:details error-data)
            [:details.mt-2
             [:summary.text-sm.text-red-600.cursor-pointer.hover:text-red-800.focus-visible:outline.focus-visible:outline-2
              "Show technical details"]
             [:pre.mt-2.text-xs.bg-red-100.p-2.rounded.overflow-x-auto.text-red-900
              (with-out-str (pprint (:details error-data)))]])]

         ;; Close Button
         [:button.flex-shrink-0.hover:bg-red-100.rounded.p-1.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2
          {:type "button"
           :hx-swap "delete"
           :hx-target "#error-message-area"
           :aria-label "Dismiss error"}
          (close-icon)]]]])))
