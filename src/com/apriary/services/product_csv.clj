(ns com.apriary.services.product-csv
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))

;; Product CSV Validation Service
;;
;; Validates product CSV rows with domain-specific rules:
;; - hive_number: required, non-empty after trim
;; - date: optional (nil if blank), must match ^\d{2}-\d{2}-\d{4}$ if provided
;; - product: required, non-empty after trim
;; - quantity: required, must parse as integer > 0
;; - metric: required, must be exactly "kg", "ml", or "g" (case-sensitive)

;; =============================================================================
;; Validation Helpers
;; =============================================================================

(defn- find-column-index
  "Find the index of a column by name (case-insensitive).

   Returns index or nil if not found."
  [headers column-name]
  (let [lower-name (str/lower-case column-name)
        lower-headers (mapv str/lower-case headers)]
    (first (keep-indexed
            (fn [idx h] (when (= h lower-name) idx))
            lower-headers))))

(defn- validate-date
  "Validate date format: DD-MM-YYYY.

   Returns nil on success, error string on failure."
  [date-str]
  (when (and (some? date-str) (not (str/blank? date-str)))
    (when-not (re-matches #"^\d{2}-\d{2}-\d{4}$" date-str)
      (str "Invalid date format (expected DD-MM-YYYY): " date-str))))

(defn- validate-quantity
  "Validate quantity is a positive integer.

   Returns parsed integer on success, error string on failure."
  [quantity-str]
  (try
    (when (or (nil? quantity-str) (str/blank? quantity-str))
      (throw (Exception. "Quantity is required")))

    (let [parsed (Integer/parseInt (str/trim quantity-str))]
      (when (<= parsed 0)
        (throw (Exception. (str "Quantity must be positive (got: " parsed ")"))))
      parsed)

    (catch NumberFormatException _
      (str "Quantity must be a valid integer (got: " quantity-str ")"))

    (catch Exception e
      (.getMessage e))))

(defn- validate-metric
  "Validate metric is one of: kg, ml, g (case-sensitive).

   Returns trimmed metric on success, error string on failure."
  [metric-str]
  (if (or (nil? metric-str) (str/blank? metric-str))
    "Metric is required"
    (let [trimmed (str/trim metric-str)]
      (if (contains? #{"kg" "ml" "g"} trimmed)
        trimmed
        (str "Metric must be one of: kg, ml, g (got: " trimmed ")")))))

;; =============================================================================
;; Row Validation
;; =============================================================================

(defn validate-product-row
  "Validate a single product CSV row.

   Validation rules:
   - hive_number: required, non-empty after trim
   - date: optional (nil if blank), must match DD-MM-YYYY if provided
   - product: required, non-empty after trim
   - quantity: required, must parse as integer > 0
   - metric: required, must be exactly 'kg', 'ml', or 'g' (case-sensitive)

   Params:
   - row: Vector of CSV cell values
   - row-number: Integer row number (for error reporting, 1-indexed)
   - column-indices: Map of {:hive-number idx :date idx :product idx :quantity idx :metric idx}

   Returns:
   - [:valid {:hive-number ... :date ... :product ... :quantity ... :metric ...}]
   - [:invalid \"reason\"]"
  [row row-number column-indices]
  (try
    (let [hive-idx (:hive-number column-indices)
          hive-number (when hive-idx (nth row hive-idx nil))
          trimmed-hive (when hive-number (str/trim hive-number))

          date-idx (:date column-indices)
          date-str (when date-idx (nth row date-idx nil))

          product-idx (:product column-indices)
          product-str (when product-idx (nth row product-idx nil))
          trimmed-product (when product-str (str/trim product-str))

          quantity-idx (:quantity column-indices)
          quantity-str (when quantity-idx (nth row quantity-idx nil))

          metric-idx (:metric column-indices)
          metric-str (when metric-idx (nth row metric-idx nil))]

      ;; Guard clauses: validation
      (cond
        (or (nil? trimmed-hive) (str/blank? trimmed-hive))
        [:invalid "hive_number field is empty or missing"]

        (or (nil? trimmed-product) (str/blank? trimmed-product))
        [:invalid "product field is empty or missing"]

        :else
        (let [date-error (validate-date date-str)
              quantity-result (validate-quantity quantity-str)
              metric-result (validate-metric metric-str)
              ;; Check if metric is valid (one of the enum values)
              metric-is-valid (contains? #{"kg" "ml" "g"} metric-result)]

          (cond
            ;; Date validation error
            (some? date-error)
            [:invalid date-error]

            ;; Quantity validation error
            (string? quantity-result)
            [:invalid quantity-result]

            ;; Metric validation error (not a valid enum value)
            (not metric-is-valid)
            [:invalid metric-result]

            ;; Success
            :else
            [:valid {:hive-number trimmed-hive
                     :date (when-not (str/blank? date-str) date-str)
                     :product trimmed-product
                     :quantity quantity-result
                     :metric metric-result}]))))

    (catch Exception e
      (log/error "Error validating product CSV row" :row-number row-number :error e)
      [:invalid (str "Unexpected error: " (.getMessage e))])))

(defn process-product-csv
  "Process and validate product CSV data.

   This function:
   1. Uses csv_import/parse-csv-string for base parsing (shared)
   2. Validates headers (hive_number, date, product, quantity, metric all required)
   3. Validates each row with product-specific rules
   4. Returns valid rows and rejected rows separately

   Params:
   - parsed-csv: Result from csv_import/parse-csv-string {:headers [...] :rows [...]}

   Returns:
   - [:ok {:valid-rows [...] :rejected-rows [...] :rows-submitted n :rows-valid n :rows-rejected n}]
   - [:error {:code ... :message ...}] on fatal error (e.g., missing required columns)"
  [parsed-csv]
  (let [{:keys [headers rows]} parsed-csv

        ;; Find column indices
        hive-idx (find-column-index headers "hive_number")
        date-idx (find-column-index headers "date")
        product-idx (find-column-index headers "product")
        quantity-idx (find-column-index headers "quantity")
        metric-idx (find-column-index headers "metric")]

    ;; Guard clause: all columns required
    (cond
      (nil? hive-idx)
      (do
        (log/warn "CSV missing hive_number column" :headers headers)
        [:error {:code "INVALID_CSV"
                 :message "CSV must have 'hive_number' column"}])

      (nil? date-idx)
      (do
        (log/warn "CSV missing date column" :headers headers)
        [:error {:code "INVALID_CSV"
                 :message "CSV must have 'date' column"}])

      (nil? product-idx)
      (do
        (log/warn "CSV missing product column" :headers headers)
        [:error {:code "INVALID_CSV"
                 :message "CSV must have 'product' column"}])

      (nil? quantity-idx)
      (do
        (log/warn "CSV missing quantity column" :headers headers)
        [:error {:code "INVALID_CSV"
                 :message "CSV must have 'quantity' column"}])

      (nil? metric-idx)
      (do
        (log/warn "CSV missing metric column" :headers headers)
        [:error {:code "INVALID_CSV"
                 :message "CSV must have 'metric' column"}])

      :else
      (let [column-indices {:hive-number hive-idx
                            :date date-idx
                            :product product-idx
                            :quantity quantity-idx
                            :metric metric-idx}

            ;; Validate all rows
            results (map-indexed
                     (fn [idx row]
                       (let [result (validate-product-row row (+ idx 2) column-indices)] ; +2 for header row and 1-indexing
                         (if (= (first result) :valid)
                           result
                           [:error {:row-number (+ idx 2)
                                    :reason (second result)}])))
                     rows)

            ;; Separate valid and rejected rows
            valid-rows (keep (fn [result]
                               (when (= (first result) :valid)
                                 (second result)))
                             results)

            rejected-rows (keep (fn [result]
                                  (when (= (first result) :error)
                                    (second result)))
                                results)

            rows-submitted (count rows)
            rows-valid (count valid-rows)
            rows-rejected (count rejected-rows)]

        (log/info "Processed product CSV import"
                  :rows-submitted rows-submitted
                  :rows-valid rows-valid
                  :rows-rejected rows-rejected)

        [:ok {:valid-rows valid-rows
              :rejected-rows rejected-rows
              :rows-submitted rows-submitted
              :rows-valid rows-valid
              :rows-rejected rows-rejected}]))))
