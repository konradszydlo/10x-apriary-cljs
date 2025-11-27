(ns com.apriary.services.summary
  (:require [xtdb.api :as xt]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

;; Service Functions for Summary Entity
;;
;; All functions follow the pattern:
;; - Returns: [:ok result] on success, [:error {:code ... :message ...}] on failure
;; - Implements Row-Level Security (RLS) checks
;; - Uses guard clauses for early error handling
;; - Logs operations for audit trail

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- validate-content
  "Validate summary content length after trimming.

   Content must be 50-50,000 characters after trim.

   Returns:
   - [:ok trimmed-content] on success
   - [:error {:code ... :message ...}] on failure"
  [content]
  (when (nil? content)
    (throw (IllegalArgumentException. "content is required")))

  (let [trimmed (str/trim content)
        length (count trimmed)]
    (cond
      (< length 50)
      (throw (IllegalArgumentException.
              (str "content must be at least 50 characters (current: " length " characters)")))

      (> length 50000)
      (throw (IllegalArgumentException.
              (str "content must not exceed 50,000 characters (current: " length " characters)")))

      :else
      [:ok trimmed])))

(defn- validate-observation-date
  "Validate observation date format: DD-MM-YYYY.

   Returns:
   - [:ok date-str] on success (or nil if not provided)
   - [:error {:code ... :message ...}] on failure"
  [date-str]
  (when (and (some? date-str)
             (not (str/blank? date-str)))
    (when-not (re-matches #"^\d{2}-\d{2}-\d{4}$" date-str)
      (throw (IllegalArgumentException.
              (str "observation-date must be in DD-MM-YYYY format (got: " date-str ")")))))
  [:ok date-str])

;; =============================================================================
;; CRUD Operations
;; =============================================================================

(defn list-summaries
  "Query summaries for an authenticated user with filtering, sorting, and pagination.

   This function implements RLS by filtering all results to only include summaries
   belonging to the authenticated user.

   Params:
   - db: XTDB database instance
   - user-id: UUID of the authenticated user
   - opts: Optional map with:
     - :sort-by String field to sort by (default: 'created-at')
     - :sort-order String sort direction 'asc' or 'desc' (default: 'desc')
     - :source Keyword filter for source type (:ai-full, :ai-partial, :manual)
     - :limit Integer max results 1-100 (default: 50)
     - :offset Integer skip results (default: 0)

   Returns:
   - [:ok {:summaries [...] :total-count n :limit m :offset k}] on success
   - [:error {:code ... :message ...}] on failure"
  [db user-id & {:keys [sort-by sort-order source limit offset]
                 :or {sort-by "created-at" sort-order "desc" limit 50 offset 0}}]
  (try
    ;; Guard clause
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Build query with RLS: only summaries for this user
    (let [base-where [['?s :summary/user-id user-id]
                      ['?s :summary/content '?content]
                      ['?s :summary/created-at '?created]
                      ['?s :summary/source '?source]]

          ;; Add source filter if provided
          where-clause (if source
                         (conj base-where ['?s :summary/source source])
                         base-where)

          query-params {:find '[?s]
                        :where where-clause}

          ;; Execute query
          all-results (xt/q db query-params)
          total-count (count all-results)

          ;; Apply pagination
          paginated-results (take limit (drop offset all-results))]

      (log/info "Listed user summaries"
                :user-id user-id
                :sort-by sort-by
                :sort-order sort-order
                :source source
                :limit limit
                :offset offset
                :count (count paginated-results))

      [:ok {:summaries (mapv (fn [[?s]] (xt/entity db ?s)) paginated-results)
            :total-count total-count
            :limit limit
            :offset offset}])

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for list-summaries:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to list summaries:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve summaries"}])))

(defn get-summary-by-id
  "Retrieve a single summary record by ID with RLS enforcement.

   This function enforces Row-Level Security: users can only retrieve their own
   summaries. Attempting to retrieve another user's summary returns an error
   that doesn't leak the existence of the resource.

   Params:
   - db: XTDB database instance
   - summary-id: UUID of the summary to retrieve
   - user-id: UUID of the authenticated user (for RLS)

   Returns:
   - [:ok entity] on success
   - [:error {:code 'NOT_FOUND' :message ...}] if not found or RLS violation"
  [db summary-id user-id]
  (try
    ;; Guard clauses
    (when (nil? summary-id)
      (throw (IllegalArgumentException. "summary-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Fetch summary
    (let [entity (xt/entity db summary-id)]

      (cond
        ;; Summary doesn't exist
        (nil? entity)
        (do
          (log/info "Summary not found" :summary-id summary-id)
          [:error {:code "NOT_FOUND" :message (str "Summary with ID " summary-id " not found")}])

        ;; RLS violation: user doesn't own this summary
        (not= (:summary/user-id entity) user-id)
        (do
          (log/warn "Unauthorized access attempt to summary"
                    :summary-id summary-id
                    :requesting-user user-id
                    :owner-user (:summary/user-id entity))
          ;; Return 404 to not leak existence of resource
          [:error {:code "NOT_FOUND" :message (str "Summary with ID " summary-id " not found")}])

        ;; Success: user owns this summary
        :else
        (do
          (log/info "Retrieved summary" :summary-id summary-id)
          [:ok entity])))

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for get-summary-by-id:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to retrieve summary:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve summary"}])))

(defn create-manual-summary
  "Create a new manual summary (not AI-generated).

   Manual summaries are created directly by users without AI assistance.
   All fields except content are optional. Content is validated for length.

   Params:
   - node: XTDB node instance (not db - needs to call submit-tx)
   - user-id: UUID of the authenticated user
   - summary-data: Map with keys:
     - :content (required) String 50-50,000 chars
     - :hive-number (optional) String
     - :observation-date (optional) String DD-MM-YYYY format
     - :special-feature (optional) String

   Returns:
   - [:ok entity] on success
   - [:error {:code ... :message ...}] on failure"
  [node user-id summary-data]
  (try
    ;; Guard clauses
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Validate content
    (let [[status trimmed-content] (validate-content (:content summary-data))]
      (when (= status :error)
        (throw (IllegalArgumentException. (:message trimmed-content))))

      ;; Validate observation date if provided
      (validate-observation-date (:observation-date summary-data))

      ;; Create summary entity
      (let [now (java.time.Instant/now)
            summary-id (java.util.UUID/randomUUID)
            entity {:xt/id summary-id
                    :summary/id summary-id
                    :summary/user-id user-id
                    :summary/generation-id nil
                    :summary/source :manual
                    :summary/content trimmed-content
                    :summary/hive-number (:hive-number summary-data)
                    :summary/observation-date (:observation-date summary-data)
                    :summary/special-feature (:special-feature summary-data)
                    :summary/created-at now
                    :summary/updated-at now}]

        ;; Persist to database
        (xt/submit-tx node [[:xtdb.api/put entity]])

        (log/info "Created manual summary"
                  :summary-id summary-id
                  :user-id user-id
                  :hive-number (:hive-number summary-data))

        [:ok entity]))

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for create-manual-summary:" (.getMessage e))
      [:error {:code "VALIDATION_ERROR" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to create manual summary:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to create summary"}])))

(defn update-summary
  "Update an existing summary with partial data.

   This function implements the inline edit workflow:
   - If content is updated and source is :ai-full, change source to :ai-partial
   - Metadata-only updates don't change source
   - Enforces RLS: users can only update their own summaries

   Params:
   - node: XTDB node instance
   - summary-id: UUID of the summary to update
   - user-id: UUID of the authenticated user
   - updates: Map with optional keys:
     - :content String 50-50,000 chars
     - :hive-number String
     - :observation-date String DD-MM-YYYY format
     - :special-feature String

   Returns:
   - [:ok updated-entity] on success
   - [:error {:code ... :message ...}] on failure"
  [node summary-id user-id updates]
  (try
    ;; Guard clauses
    (when (nil? summary-id)
      (throw (IllegalArgumentException. "summary-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))
    (when (or (nil? updates) (empty? updates))
      (throw (IllegalArgumentException. "At least one field must be provided for update")))

    ;; Load existing summary
    (let [db (xt/db node)
          existing (xt/entity db summary-id)]

      (when (nil? existing)
        (throw (IllegalArgumentException. "Summary not found")))

      ;; RLS check
      (when (not= (:summary/user-id existing) user-id)
        (throw (IllegalArgumentException. "Forbidden: Summary does not belong to user")))

      ;; Validate content if provided
      (let [trimmed-content (when (:content updates)
                              (let [[status result] (validate-content (:content updates))]
                                (when (= status :error)
                                  (throw (IllegalArgumentException. (:message result))))
                                result))

            ;; Validate observation date if provided
            _ (when (:observation-date updates)
                (validate-observation-date (:observation-date updates)))

            ;; Determine if source should change
            content-changed? (some? trimmed-content)
            current-source (:summary/source existing)
            new-source (if (and content-changed? (= current-source :ai-full))
                         :ai-partial
                         current-source)

            ;; Build updated entity
            now (java.time.Instant/now)
            updated-entity (cond-> existing
                             ;; Always update timestamp
                             true (assoc :summary/updated-at now)

                             ;; Update content if provided
                             (some? trimmed-content) (assoc :summary/content trimmed-content)

                             ;; Update metadata fields if provided
                             (contains? updates :hive-number) (assoc :summary/hive-number (:hive-number updates))
                             (contains? updates :observation-date) (assoc :summary/observation-date (:observation-date updates))
                             (contains? updates :special-feature) (assoc :summary/special-feature (:special-feature updates))

                             ;; Update source if content changed
                             (not= new-source current-source) (assoc :summary/source new-source))]

        ;; Persist to database
        (xt/submit-tx node [[:xtdb.api/put updated-entity]])

        (log/info "Updated summary"
                  :summary-id summary-id
                  :user-id user-id
                  :content-changed content-changed?
                  :source-changed (not= new-source current-source)
                  :new-source new-source)

        [:ok updated-entity]))

    (catch IllegalArgumentException e
      (let [msg (.getMessage e)]
        (log/warn "Invalid argument for update-summary:" msg)
        (cond
          (str/includes? msg "not found")
          [:error {:code "NOT_FOUND" :message msg}]

          (str/includes? msg "Forbidden")
          [:error {:code "FORBIDDEN" :message msg}]

          :else
          [:error {:code "VALIDATION_ERROR" :message msg}])))

    (catch Exception e
      (log/error "Failed to update summary:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to update summary"}])))

(defn delete-summary
  "Permanently delete a summary record.

   This is a hard delete (not soft delete). Enforces RLS.

   Params:
   - node: XTDB node instance
   - summary-id: UUID of the summary to delete
   - user-id: UUID of the authenticated user

   Returns:
   - [:ok {:summary-id ...}] on success
   - [:error {:code ... :message ...}] on failure"
  [node summary-id user-id]
  (try
    ;; Guard clauses
    (when (nil? summary-id)
      (throw (IllegalArgumentException. "summary-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Load existing summary
    (let [db (xt/db node)
          existing (xt/entity db summary-id)]

      (when (nil? existing)
        (throw (IllegalArgumentException. "Summary not found")))

      ;; RLS check
      (when (not= (:summary/user-id existing) user-id)
        (throw (IllegalArgumentException. "Forbidden: Summary does not belong to user")))

      ;; Delete from database
      (xt/submit-tx node [[:xtdb.api/delete summary-id]])

      (log/info "Deleted summary" :summary-id summary-id :user-id user-id)

      [:ok {:summary-id summary-id}])

    (catch IllegalArgumentException e
      (let [msg (.getMessage e)]
        (log/warn "Invalid argument for delete-summary:" msg)
        (cond
          (or (str/includes? msg "not found") (str/includes? msg "Forbidden"))
          [:error {:code "NOT_FOUND" :message (str "Summary with ID " summary-id " not found")}]

          :else
          [:error {:code "INVALID_INPUT" :message msg}])))

    (catch Exception e
      (log/error "Failed to delete summary:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to delete summary"}])))

(defn accept-summary
  "Accept an AI-generated summary and update generation metrics.

   This function:
   1. Validates the summary can be accepted (must be AI-generated, not manual)
   2. Determines the counter to increment based on source:
      - :ai-full → increment accepted-unedited-count
      - :ai-partial → increment accepted-edited-count
   3. Updates the generation record counters
   4. Marks the summary as accepted (adds :summary/accepted-at timestamp)

   Params:
   - node: XTDB node instance
   - summary-id: UUID of the summary to accept
   - user-id: UUID of the authenticated user

   Returns:
   - [:ok {:summary ... :generation ...}] on success
   - [:error {:code ... :message ...}] on failure"
  [node summary-id user-id]
  (try
    ;; Guard clauses
    (when (nil? summary-id)
      (throw (IllegalArgumentException. "summary-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Load summary
    (let [db (xt/db node)
          summary (xt/entity db summary-id)]

      (when (nil? summary)
        (throw (IllegalArgumentException. "Summary not found")))

      ;; RLS check
      (when (not= (:summary/user-id summary) user-id)
        (throw (IllegalArgumentException. "Forbidden: Summary does not belong to user")))

      ;; Validate source type
      (when (= (:summary/source summary) :manual)
        (throw (IllegalArgumentException. "Cannot accept manual summaries")))

      ;; Check if already accepted
      (when (:summary/accepted-at summary)
        (throw (IllegalArgumentException. "Summary already accepted")))

      ;; Load generation record
      (let [generation-id (:summary/generation-id summary)
            generation (xt/entity db generation-id)]

        (when (nil? generation)
          (throw (IllegalArgumentException. "Generation record not found")))

        ;; Determine which counter to increment
        (let [source (:summary/source summary)
              unedited-increment (if (= source :ai-full) 1 0)
              edited-increment (if (= source :ai-partial) 1 0)

              ;; Calculate new generation values
              current-unedited (:generation/accepted-unedited-count generation 0)
              current-edited (:generation/accepted-edited-count generation 0)
              new-unedited (+ current-unedited unedited-increment)
              new-edited (+ current-edited edited-increment)
              now (java.time.Instant/now)

              ;; Update generation entity
              updated-generation (assoc generation
                                        :generation/accepted-unedited-count new-unedited
                                        :generation/accepted-edited-count new-edited
                                        :generation/updated-at now)

              ;; Mark summary as accepted
              accepted-summary (assoc summary :summary/accepted-at now)]

          ;; Persist both updates in a transaction
          (xt/submit-tx node [[:xtdb.api/put updated-generation]
                              [:xtdb.api/put accepted-summary]])

          (log/info "Accepted summary"
                    :summary-id summary-id
                    :user-id user-id
                    :source source
                    :generation-id generation-id
                    :unedited-increment unedited-increment
                    :edited-increment edited-increment)

          [:ok {:summary accepted-summary
                :generation updated-generation}])))

    (catch IllegalArgumentException e
      (let [msg (.getMessage e)]
        (log/warn "Invalid argument for accept-summary:" msg)
        (cond
          (str/includes? msg "not found")
          [:error {:code "NOT_FOUND" :message msg}]

          (str/includes? msg "Forbidden")
          [:error {:code "FORBIDDEN" :message msg}]

          (str/includes? msg "Cannot accept")
          [:error {:code "INVALID_OPERATION" :message msg}]

          (str/includes? msg "already accepted")
          [:error {:code "CONFLICT" :message msg}]

          :else
          [:error {:code "INVALID_INPUT" :message msg}])))

    (catch Exception e
      (log/error "Failed to accept summary:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to accept summary"}])))
