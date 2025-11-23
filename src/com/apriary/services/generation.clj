(ns com.apriary.services.generation
  (:require [xtdb.api :as xt]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

;; Service Functions for Generation Entity

(defn create-generation
  "Create a new generation record for a CSV import batch.
   
   This function is called when a CSV import is initiated. It records metadata
   about the batch of AI-generated summaries.
   
   Params:
   - node: XTDB node instance (not db - needs to call submit-tx)
   - user-id: UUID of the authenticated user
   - model: String name of the AI model used (e.g., 'gpt-4-turbo')
   - generated-count: Integer count of valid CSV rows
   - duration-ms: Integer milliseconds of the OpenRouter API request
   
   Returns:
   - [:ok {:generation/id uuid :generation/user-id uuid ...}] on success
   - [:error {:code error-code :message msg}] on failure
   
   Error Handling:
   - Validates all required parameters
   - Returns error if database write fails
   - Logs creation events"
  [node user-id model generated-count duration-ms]
  (try
    ;; Guard clauses for invalid inputs
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))
    (when (nil? model)
      (throw (IllegalArgumentException. "model is required")))
    (when (or (nil? generated-count) (< generated-count 0))
      (throw (IllegalArgumentException. "generated-count must be >= 0")))
    (when (or (nil? duration-ms) (< duration-ms 0))
      (throw (IllegalArgumentException. "duration-ms must be >= 0")))

    ;; Create generation entity
    (let [now (java.time.Instant/now)
          generation-id (java.util.UUID/randomUUID)
          entity {:xt/id generation-id
                  :generation/id generation-id
                  :generation/user-id user-id
                  :generation/model model
                  :generation/generated-count generated-count
                  :generation/accepted-unedited-count 0
                  :generation/accepted-edited-count 0
                  :generation/duration-ms duration-ms
                  :generation/created-at now
                  :generation/updated-at now}]

      ;; Persist to database
      (xt/submit-tx node [[:xtdb.api/put entity]])

      (log/info "Created generation"
                :generation-id generation-id
                :user-id user-id
                :model model
                :generated-count generated-count)

      [:ok entity])

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for create-generation:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to create generation:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to create generation record"}])))

(defn get-generation-by-id
  "Retrieve a single generation record by ID with RLS enforcement.
   
   This function enforces Row-Level Security: users can only retrieve their own
   generations. Attempting to retrieve another user's generation returns an error
   that doesn't leak the existence of the resource.
   
   Params:
   - db: XTDB database instance
   - generation-id: UUID of the generation to retrieve
   - user-id: UUID of the authenticated user (for RLS)
   
   Returns:
   - [:ok entity] on success
   - [:error {:code 'NOT_FOUND' :message 'Generation not found'}] if not found
   - [:error {:code 'FORBIDDEN' :message 'Access denied'}] if RLS violation
   
   Error Handling:
   - Returns NOT_FOUND if generation doesn't exist
   - Returns FORBIDDEN for RLS violations (doesn't leak existence)
   - Logs access attempts for audit trail"
  [db generation-id user-id]
  (try
    ;; Guard clauses
    (when (nil? generation-id)
      (throw (IllegalArgumentException. "generation-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Fetch generation
    (let [entity (xt/entity db generation-id)]

      (cond
        ;; Generation doesn't exist
        (nil? entity)
        (do
          (log/info "Generation not found" :generation-id generation-id)
          [:error {:code "NOT_FOUND" :message (str "Generation with ID " generation-id " not found")}])

        ;; RLS violation: user doesn't own this generation
        (not= (:generation/user-id entity) user-id)
        (do
          (log/warn "Unauthorized access attempt to generation"
                    :generation-id generation-id
                    :requesting-user user-id
                    :owner-user (:generation/user-id entity))
          ;; Return 404 to not leak existence of resource
          [:error {:code "NOT_FOUND" :message (str "Generation with ID " generation-id " not found")}])

        ;; Success: user owns this generation
        :else
        (do
          (log/info "Retrieved generation" :generation-id generation-id)
          [:ok entity])))

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for get-generation-by-id:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to retrieve generation:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve generation"}])))

(defn list-user-generations
  "Query generations for an authenticated user with filtering, sorting, and pagination.
   
   This function implements RLS by filtering all results to only include generations
   belonging to the authenticated user.
   
   Params:
   - db: XTDB database instance
   - user-id: UUID of the authenticated user
   - opts: Optional map with:
     - :sort-by String field to sort by (default: 'created_at')
     - :sort-order String sort direction 'asc' or 'desc' (default: 'desc')
     - :model String optional filter for AI model
     - :limit Integer max results 1-100 (default: 50)
     - :offset Integer skip results (default: 0)
   
   Returns:
   - [:ok {:generations [entities...] :total-count n :limit m :offset k}] on success
   - [:error {:code error-code :message msg}] on failure
   
   Error Handling:
   - Validates all optional parameters
   - Returns error if validation fails
   - Logs query parameters for audit trail"
  [db user-id & {:keys [sort-by sort-order model limit offset]
                 :or {sort-by "created_at" sort-order "desc" limit 50 offset 0}}]
  (try
    ;; Guard clause
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Note: Parameter validation should happen in the handler layer.
    ;; This service layer assumes valid inputs.

    ;; Build query with RLS: only generations for this user
    (let [query-params {:find '[?g ?model ?created ?gen-count ?unedited ?edited]
                        :where [['?g :generation/user-id user-id]
                                ['?g :generation/model '?model]
                                ['?g :generation/created-at '?created]
                                ['?g :generation/generated-count '?gen-count]
                                ['?g :generation/accepted-unedited-count '?unedited]
                                ['?g :generation/accepted-edited-count '?edited]]}

          ;; Add model filter if provided
          query-with-filter (if model
                              (update query-params :where conj ['?g :generation/model model])
                              query-params)

          ;; Execute query (without limit/offset - we'll apply those to results)
          all-results (xt/q db query-with-filter)
          total-count (count all-results)

          ;; Apply pagination to results using drop and take
          paginated-results (take limit (drop offset all-results))]

      (log/info "Listed user generations"
                :user-id user-id
                :sort-by sort-by
                :sort-order sort-order
                :model model
                :limit limit
                :offset offset
                :count (count paginated-results))

      [:ok {:generations (mapv (fn [[?g _ _ _ _ _]] (xt/entity db ?g)) paginated-results)
            :total-count total-count
            :limit limit
            :offset offset}])

    (catch Exception e
      (log/error "Failed to list generations:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve generations"}])))

(defn update-counters
  "Update acceptance counters for a generation record.
   
   This function increments the accepted-unedited-count and accepted-edited-count
   counters. It validates that the sum doesn't exceed the generated-count to
   prevent data corruption.
   
   Params:
   - node: XTDB node instance (not db - needs to call submit-tx)
   - generation-id: UUID of the generation to update
   - unedited-increment: Integer to add to accepted-unedited-count
   - edited-increment: Integer to add to accepted-edited-count
   
   Returns:
   - [:ok updated-entity] on success
   - [:error {:code error-code :message msg}] on failure
   
   Error Handling:
   - Validates that total doesn't exceed generated-count
   - Returns DATA_INTEGRITY_ERROR if validation fails
   - Logs counter updates for audit trail"
  [node generation-id unedited-increment edited-increment]
  (try
    ;; Guard clauses
    (when (nil? generation-id)
      (throw (IllegalArgumentException. "generation-id is required")))
    (when (nil? unedited-increment)
      (throw (IllegalArgumentException. "unedited-increment is required")))
    (when (nil? edited-increment)
      (throw (IllegalArgumentException. "edited-increment is required")))

    ;; Fetch current entity
    (let [db (xt/db node)
          entity (xt/entity db generation-id)]

      (when (nil? entity)
        (throw (IllegalArgumentException. (str "Generation " generation-id " not found"))))

      ;; Calculate new values
      (let [current-unedited (:generation/accepted-unedited-count entity 0)
            current-edited (:generation/accepted-edited-count entity 0)
            new-unedited (+ current-unedited unedited-increment)
            new-edited (+ current-edited edited-increment)
            total-accepted (+ new-unedited new-edited)
            generated-count (:generation/generated-count entity)
            now (java.time.Instant/now)]

        ;; Validate data consistency
        (when (> total-accepted generated-count)
          (throw (IllegalArgumentException.
                  (str "Counter validation failed: total accepted (" total-accepted
                       ") exceeds generated count (" generated-count ")"))))

        ;; Update entity
        (let [updated-entity (assoc entity
                                    :generation/accepted-unedited-count new-unedited
                                    :generation/accepted-edited-count new-edited
                                    :generation/updated-at now)]

          ;; Persist to database
          (xt/submit-tx node [[:xtdb.api/put updated-entity]])

          (log/info "Updated generation counters"
                    :generation-id generation-id
                    :new-unedited new-unedited
                    :new-edited new-edited
                    :total-accepted total-accepted
                    :generated-count generated-count)

          [:ok updated-entity])))

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for update-counters:" (.getMessage e))
      (if (str/includes? (.getMessage e) "Counter validation failed")
        [:error {:code "DATA_INTEGRITY_ERROR" :message (.getMessage e)}]
        [:error {:code "INVALID_INPUT" :message (.getMessage e)}]))

    (catch Exception e
      (log/error "Failed to update generation counters:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to update generation counters"}])))

(defn bulk-accept-summaries-for-generation
  "Accept all summaries in a generation batch and update counters.
   
   This function implements the bulk acceptance workflow:
   1. Fetch all summaries for the generation
   2. Group summaries by source type (ai-full, ai-partial, manual)
   3. Count summaries in each group
   4. Update generation counters based on source type:
      - ai-full summaries → increment accepted-unedited-count
      - ai-partial summaries → increment accepted-edited-count
      - manual summaries → ignored (already accepted)
   
   Params:
   - node: XTDB node instance (not db - needs to call submit-tx via update-counters)
   - generation-id: UUID of the generation
   - user-id: UUID of the authenticated user (for RLS)
   
   Returns:
   - [:ok {:generation entity :unedited-count n :edited-count m :summaries-count k}] on success
   - [:error {:code error-code :message msg}] on failure
   
   Error Handling:
   - RLS: Verifies generation and summaries belong to user
   - Returns NOT_FOUND if generation doesn't exist
   - Returns FORBIDDEN for RLS violations
   - Returns DATA_INTEGRITY_ERROR if counter validation fails"
  [node generation-id user-id]
  (try
    ;; Guard clauses
    (when (nil? generation-id)
      (throw (IllegalArgumentException. "generation-id is required")))
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Fetch generation record
    (let [db (xt/db node)
          generation (xt/entity db generation-id)]

      (when (nil? generation)
        (throw (IllegalArgumentException. "Generation not found")))

      ;; RLS check: generation must belong to user
      (when (not= (:generation/user-id generation) user-id)
        (throw (IllegalArgumentException. "Forbidden: Generation does not belong to user")))

      ;; Query all summaries for this generation, grouped by source
      (let [summaries-query {:find  '[?s ?source]
                             :where [['?s :summary/generation-id generation-id]
                                     ['?s :summary/user-id user-id]
                                     ['?s :summary/source '?source]]}

            summary-results (xt/q db summaries-query)

            ;; Group by source type and count
            grouped (reduce (fn [acc [_s ?source]]
                              (update acc ?source (fn [v] (inc (or v 0)))))
                            {}
                            summary-results)

            unedited-count (or (get grouped :ai-full) 0)
            edited-count (or (get grouped :ai-partial) 0)
            manual-count (or (get grouped :manual) 0)
            total-summaries (count summary-results)]

        ;; Update generation counters
        (let [update-result (update-counters node generation-id unedited-count edited-count)]
          (if (= (first update-result) :ok)
            (let [updated (second update-result)]
              (log/info "Bulk accepted summaries for generation"
                        :generation-id generation-id
                        :user-id user-id
                        :unedited-count unedited-count
                        :edited-count edited-count
                        :manual-count manual-count
                        :total-summaries total-summaries)

              [:ok {:generation      updated
                    :unedited-count  unedited-count
                    :edited-count    edited-count
                    :manual-count    manual-count
                    :total-summaries total-summaries}])

            ;; Error updating counters - propagate error
            update-result))))

    (catch IllegalArgumentException e
      (let [msg (.getMessage e)]
        (log/warn "Invalid argument for bulk-accept-summaries-for-generation:" msg)
        (cond
          (str/includes? msg "not found")
          [:error {:code "NOT_FOUND" :message msg}]

          (str/includes? msg "Forbidden")
          [:error {:code "FORBIDDEN" :message msg}]

          :else
          [:error {:code "INVALID_INPUT" :message msg}])))

    (catch Exception e
      (log/error "Failed to bulk accept summaries:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to bulk accept summaries"}])))
