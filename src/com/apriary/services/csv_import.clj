(ns com.apriary.services.csv-import
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.tools.logging :as log]))

;; CSV Import Service
;;
;; Handles parsing and validation of CSV data for summary import.
;; CSV Format:
;; - Delimiter: semicolon (;)
;; - Encoding: UTF-8
;; - Required header: observation
;; - Optional headers: hive_number, observation_date, special_feature

;; =============================================================================
;; CSV Parsing
;; =============================================================================

(defn parse-csv-string
  "Parse CSV string into rows.

   Expected format:
   - Semicolon-delimited
   - First row contains headers
   - Subsequent rows contain data

   Params:
   - csv-string: String containing CSV data

   Returns:
   - [:ok {:headers [...] :rows [...]}] on success
   - [:error {:code ... :message ...}] on failure"
  [csv-string]
  (try
    ;; Guard clause: empty CSV
    (when (or (nil? csv-string) (str/blank? csv-string))
      (throw (IllegalArgumentException. "CSV data cannot be empty")))

    ;; Parse CSV using clojure.data.csv
    (let [reader (java.io.StringReader. csv-string)
          parsed-data (csv/read-csv reader :separator \;)
          headers (first parsed-data)
          rows (rest parsed-data)]

      ;; Guard clause: no headers
      (when (empty? headers)
        (throw (IllegalArgumentException. "CSV must have headers")))

      ;; Guard clause: no data rows
      (when (empty? rows)
        (throw (IllegalArgumentException. "CSV must have at least one data row")))

      (log/info "Parsed CSV"
                :headers headers
                :row-count (count rows))

      [:ok {:headers (mapv str/lower-case headers)
            :rows rows}])

    (catch IllegalArgumentException e
      (log/warn "CSV parsing failed:" (.getMessage e))
      [:error {:code "INVALID_CSV" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Unexpected error parsing CSV:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to parse CSV data"}])))

;; =============================================================================
;; CSV Validation
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

(defn- validate-observation-date
  "Validate observation date format: DD-MM-YYYY.

   Returns nil on success, error string on failure."
  [date-str]
  (when (and (some? date-str) (not (str/blank? date-str)))
    (when-not (re-matches #"^\d{2}-\d{2}-\d{4}$" date-str)
      (str "Invalid date format (expected DD-MM-YYYY): " date-str))))

(defn validate-csv-row
  "Validate a single CSV row.

   Validation rules:
   - observation field: required, 50-10,000 characters after trim
   - observation_date: optional, DD-MM-YYYY format if provided
   - hive_number: optional
   - special_feature: optional

   Params:
   - row: Vector of CSV cell values
   - row-number: Integer row number (for error reporting, 1-indexed)
   - column-indices: Map of {:observation idx :hive-number idx ...}

   Returns:
   - [:ok {:observation ... :hive-number ... :observation-date ... :special-feature ...}]
   - [:error {:row-number n :reason ...}]"
  [row row-number column-indices]
  (try
    (let [obs-idx (:observation column-indices)
          observation (when obs-idx (nth row obs-idx nil))
          trimmed-obs (when observation (str/trim observation))

          hive-idx (:hive-number column-indices)
          hive-number (when hive-idx (nth row hive-idx nil))

          date-idx (:observation-date column-indices)
          obs-date (when date-idx (nth row date-idx nil))

          feature-idx (:special-feature column-indices)
          special-feature (when feature-idx (nth row feature-idx nil))]

      ;; Guard clauses: observation validation
      (cond
        (or (nil? trimmed-obs) (str/blank? trimmed-obs))
        [:error {:row-number row-number
                 :reason "observation field is empty or missing"}]

        (< (count trimmed-obs) 50)
        [:error {:row-number row-number
                 :reason (str "Observation text too short (" (count trimmed-obs)
                              " characters). Minimum: 50 characters.")}]

        (> (count trimmed-obs) 10000)
        [:error {:row-number row-number
                 :reason (str "Observation text too long (" (count trimmed-obs)
                              " characters). Maximum: 10,000 characters.")}]

        ;; Validate date format if provided
        :else
        (if-let [date-error (validate-observation-date obs-date)]
          [:error {:row-number row-number :reason date-error}]

          ;; Success
          [:ok {:observation trimmed-obs
                :hive-number (when-not (str/blank? hive-number) hive-number)
                :observation-date (when-not (str/blank? obs-date) obs-date)
                :special-feature (when-not (str/blank? special-feature) special-feature)}])))

    (catch Exception e
      (log/error "Error validating CSV row" :row-number row-number :error e)
      [:error {:row-number row-number
               :reason (str "Unexpected error: " (.getMessage e))}])))

(defn process-csv-import
  "Process and validate CSV data for import.

   This function:
   1. Parses CSV string
   2. Validates headers (observation required)
   3. Validates each row
   4. Returns valid rows and rejected rows separately

   Params:
   - csv-string: String containing CSV data

   Returns:
   - [:ok {:valid-rows [...] :rejected-rows [...] :rows-submitted n :rows-valid n :rows-rejected n}]
   - [:error {:code ... :message ...}] on fatal error (e.g., missing observation column)"
  [csv-string]
  (let [[status parse-result] (parse-csv-string csv-string)]

    (if (= status :error)
      [:error parse-result]

      (let [{:keys [headers rows]} parse-result

            ;; Find column indices
            obs-idx (find-column-index headers "observation")
            hive-idx (find-column-index headers "hive_number")
            date-idx (find-column-index headers "observation_date")
            feature-idx (find-column-index headers "special_feature")]

        ;; Guard clause: observation column required
        (if (nil? obs-idx)
          (do
            (log/warn "CSV missing observation column" :headers headers)
            [:error {:code "INVALID_CSV"
                     :message "CSV must have 'observation' column"}])

          (let [column-indices {:observation obs-idx
                                :hive-number hive-idx
                                :observation-date date-idx
                                :special-feature feature-idx}

              ;; Validate all rows
                results (map-indexed
                         (fn [idx row]
                           (validate-csv-row row (+ idx 2) column-indices)) ; +2 for header row and 1-indexing
                         rows)

              ;; Separate valid and rejected rows
                valid-rows (keep (fn [result]
                                   (when (= (first result) :ok)
                                     (second result)))
                                 results)

                rejected-rows (keep (fn [result]
                                      (when (= (first result) :error)
                                        (second result)))
                                    results)

                rows-submitted (count rows)
                rows-valid (count valid-rows)
                rows-rejected (count rejected-rows)]

            (log/info "Processed CSV import"
                      :rows-submitted rows-submitted
                      :rows-valid rows-valid
                      :rows-rejected rows-rejected)

            [:ok {:valid-rows valid-rows
                  :rejected-rows rejected-rows
                  :rows-submitted rows-submitted
                  :rows-valid rows-valid
                  :rows-rejected rows-rejected}]))))))
