(ns com.apriary.util
  (:require [clojure.string :as str]))

;; UUID Validation and Parsing

(defn parse-uuid
  "Parse a UUID string and return the UUID object or an error tuple.
   
   Returns:
   - [:ok uuid-object] on success
   - [:error {:code 'INVALID_UUID' :message 'Invalid UUID format'}] on failure"
  [uuid-str]
  (if (nil? uuid-str)
    [:error {:code "INVALID_UUID" :message "UUID is required"}]
    (try
      (let [parsed (java.util.UUID/fromString uuid-str)]
        [:ok parsed])
      (catch IllegalArgumentException _
        [:error {:code "INVALID_UUID" :message (str "Invalid generation ID format: " uuid-str)}]))))

;; Integer Parsing and Validation

(defn parse-int
  "Parse an integer string with optional min/max validation.
   
   Options:
   - :min Minimum value (inclusive)
   - :max Maximum value (inclusive)
   - :default Default value if string is nil or empty
   
   Returns:
   - [:ok integer] on success
   - [:error {:code error-code :message msg}] on failure"
  [value-str & {:keys [min max default]}]
  (if (nil? value-str)
    (if (some? default)
      [:ok default]
      [:error {:code "MISSING_PARAM" :message "Parameter is required"}])
    (try
      (let [parsed (Integer/parseInt (str/trim value-str))]
        (cond
          (and (some? min) (< parsed min))
          [:error {:code "INVALID_RANGE" :message (str "Value must be >= " min)}]

          (and (some? max) (> parsed max))
          [:error {:code "INVALID_RANGE" :message (str "Value must be <= " max)}]

          :else
          [:ok parsed]))
      (catch NumberFormatException _
        [:error {:code "INVALID_INTEGER" :message "Value must be a valid integer"}]))))

;; Query Parameter Validation

(def ^:private SORT_BY_WHITELIST
  #{"created_at" "model" "generated_count" "accepted_count"})

(defn validate-sort-by
  "Validate sort_by parameter against whitelist.

   Returns:
   - [:ok field] on success
   - [:error {:code 'INVALID_SORT_BY' :message msg}] on failure"
  [sort-by]
  (let [trimmed (str/trim (or sort-by ""))
        field (if (str/blank? trimmed)
                "created_at"
                trimmed)]
    (if (contains? SORT_BY_WHITELIST field)
      [:ok field]
      [:error {:code "INVALID_SORT_BY"
               :message (str "Invalid sort_by field. Allowed values: "
                             (str/join ", " (sort SORT_BY_WHITELIST)))}])))

(def ^:private SORT_ORDER_WHITELIST
  #{"asc" "desc"})

(defn validate-sort-order
  "Validate sort_order parameter.

   Returns:
   - [:ok order] on success (lowercase)
   - [:error {:code 'INVALID_SORT_ORDER' :message msg}] on failure"
  [sort-order]
  (let [trimmed (str/trim (or sort-order ""))
        order (if (str/blank? trimmed)
                "desc"
                (str/lower-case trimmed))]
    (if (contains? SORT_ORDER_WHITELIST order)
      [:ok order]
      [:error {:code "INVALID_SORT_ORDER"
               :message "Invalid sort_order. Must be 'asc' or 'desc'"}])))

(defn validate-limit
  "Validate pagination limit parameter.
   
   Range: 1-100, default 50
   
   Returns:
   - [:ok limit] on success
   - [:error {:code error-code :message msg}] on failure"
  [limit-str]
  (let [default 50]
    (if (nil? limit-str)
      [:ok default]
      (parse-int limit-str :min 1 :max 100 :default default))))

(defn validate-offset
  "Validate pagination offset parameter.
   
   Must be non-negative, default 0
   
   Returns:
   - [:ok offset] on success
   - [:error {:code error-code :message msg}] on failure"
  [offset-str]
  (let [default 0]
    (if (nil? offset-str)
      [:ok default]
      (parse-int offset-str :min 0 :default default))))

;; Timestamp Formatting

(defn format-iso-8601
  "Format an instant (java.time.Instant or java.util.Date) as ISO-8601 string."
  [instant]
  (if (instance? java.time.Instant instant)
    (str instant)
    (str (.toInstant instant))))

;; Error Response Builders

(defn build-error-response
  "Build a standard error response body object (for use inside {:body ...}).
   
   Params:
   - status: HTTP status code
   - code: Error code constant
   - message: Human-readable error message
   - details: Optional map of additional error details"
  [status code message & {:keys [details]}]
  {:error message
   :code code
   :details (or details {})
   :timestamp (format-iso-8601 (java.time.Instant/now))})

(defn validation-error
  "Build a 400 validation error response."
  [code message & {:keys [field reason]}]
  (build-error-response 400 code message
                        :details (cond-> {}
                                   (some? field) (assoc :field field)
                                   (some? reason) (assoc :reason reason))))

(defn unauthorized-error
  "Build a 401 authentication error response."
  []
  (build-error-response 401 "UNAUTHORIZED" "User not authenticated"
                        :details {:reason "Session cookie missing or expired"}))

(defn forbidden-error
  "Build a 403 authorization error response."
  []
  (build-error-response 403 "FORBIDDEN" "You do not have permission to access this generation"
                        :details {:reason "RLS violation"}))

(defn not-found-error
  "Build a 404 not found error response."
  [resource-id]
  (build-error-response 404 "NOT_FOUND" (str "Generation with ID " resource-id " not found")
                        :details {:id (str resource-id)}))

(defn internal-error
  "Build a 500 internal server error response."
  [message & {:keys [reason]}]
  (build-error-response 500 "INTERNAL_ERROR" message
                        :details (cond-> {}
                                   (some? reason) (assoc :reason reason))))
