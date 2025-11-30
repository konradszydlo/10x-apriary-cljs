(ns com.apriary.pages.summaries
  (:require [com.apriary.util :as util]
            [com.apriary.services.summary :as summary-service]
            [com.apriary.services.csv-import :as csv-service]
            [com.apriary.services.openrouter :as openrouter-service]
            [com.apriary.services.generation :as gen-service]
            [com.apriary.dto.summary :as summary-dto]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]))

;; Route Handlers for Summary Endpoints
;;
;; All handlers follow Biff conventions:
;; - Receive context map with :session, :biff/db, :biff.xtdb/node, :params, :path-params, :body
;; - Return Ring response map with :status and :body
;; - Enforce authentication via session check
;; - Use guard clauses for validation

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- validate-sort-by-summary
  "Validate sort-by parameter for summaries endpoint.

   Allowed values: created-at, hive-number, source
   Default: created-at"
  [sort-by]
  (let [field (str/trim (or sort-by "created-at"))
        whitelist #{"created-at" "hive-number" "source"}]
    (if (contains? whitelist field)
      [:ok field]
      [:error {:code "INVALID_SORT_BY"
               :message (str "Invalid sort-by field. Allowed values: "
                             (str/join ", " (sort whitelist)))}])))

(defn- validate-source-filter
  "Validate source filter parameter.

   Allowed values: ai-full, ai-partial, manual
   Returns keyword or nil"
  [source-str]
  (when (and source-str (not (str/blank? source-str)))
    (let [source (keyword source-str)
          whitelist #{:ai-full :ai-partial :manual}]
      (if (contains? whitelist source)
        [:ok source]
        [:error {:code "INVALID_SOURCE"
                 :message "Invalid source. Must be one of: ai-full, ai-partial, manual"}]))))

;; =============================================================================
;; Route Handlers
;; =============================================================================

(defn list-summaries-handler
  "GET /api/summaries - List all summaries for authenticated user with filtering and pagination"
  [{:keys [session biff/db params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          sort-by-result (validate-sort-by-summary (:sort-by params))
          sort-order-result (util/validate-sort-order (:sort-order params))
          limit-result (util/validate-limit (:limit params))
          offset-result (util/validate-offset (:offset params))
          source-result (validate-source-filter (:source params))]

      ;; Guard clauses: validation errors
      (cond
        (= (first sort-by-result) :error)
        {:status 400 :body (second sort-by-result)}

        (= (first sort-order-result) :error)
        {:status 400 :body (second sort-order-result)}

        (= (first limit-result) :error)
        {:status 400 :body (second limit-result)}

        (= (first offset-result) :error)
        {:status 400 :body (second offset-result)}

        (and (some? source-result) (= (first source-result) :error))
        {:status 400 :body (second source-result)}

        ;; Happy path
        :else
        (let [sort-by (second sort-by-result)
              sort-order (second sort-order-result)
              limit (second limit-result)
              offset (second offset-result)
              source-filter (when (some? source-result) (second source-result))
              [status result] (summary-service/list-summaries
                               db user-id
                               :sort-by sort-by
                               :sort-order sort-order
                               :source source-filter
                               :limit limit
                               :offset offset)]

          (if (= status :ok)
            {:status 200
             :body (summary-dto/list->response
                    (:summaries result)
                    (:total-count result)
                    limit
                    offset)}

            {:status 500 :body result}))))))

(defn create-manual-summary-handler
  "POST /api/summaries - Create a manual summary without AI generation"
  [{:keys [session biff.xtdb/node body] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    ;; Guard clause: request body required
    (if-not (some? body)
      {:status 400 :body (util/validation-error "INVALID_REQUEST" "Request body is required")}

      ;; Guard clause: content field required
      (if-not (contains? body :content)
        {:status 400
         :body (util/validation-error "MISSING_FIELD" "Missing required field: content"
                                      :field "content")}

        ;; Happy path
        (let [user-id (:uid session)
              [status result] (summary-service/create-manual-summary
                               node user-id body)]

          (if (= status :ok)
            {:status 201 :body (summary-dto/created->response result)}

            ;; Error handling
            (case (:code result)
              "VALIDATION_ERROR" {:status 400 :body result}
              {:status 500 :body result})))))))

(defn get-single-summary-handler
  "GET /api/summaries/{id} - Retrieve a single summary by ID"
  [{:keys [session biff/db path-params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400 :body (second uuid-result)}

        ;; Happy path
        (let [summary-id (second uuid-result)
              [status result] (summary-service/get-summary-by-id
                               db summary-id user-id)]

          (if (= status :ok)
            {:status 200 :body (summary-dto/single->response result)}

            ;; Error handling - return 404 for both not found and forbidden (RLS)
            (case (:code result)
              "NOT_FOUND" {:status 404 :body result}
              {:status 500 :body result})))))))

(defn update-summary-handler
  "PATCH /api/summaries/{id} - Update summary metadata or content with automatic source tracking"
  [{:keys [session biff.xtdb/node path-params body] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    ;; Guard clause: request body required
    (if-not (some? body)
      {:status 400 :body (util/validation-error "INVALID_REQUEST" "Request body is required")}

      ;; Guard clause: at least one field required
      (if (empty? body)
        {:status 400
         :body (util/validation-error "INVALID_REQUEST" "At least one field must be provided")}

        (let [user-id (:uid session)
              summary-id-str (:id path-params)
              uuid-result (util/parse-uuid summary-id-str)]

          ;; Guard clause: invalid UUID
          (if (= (first uuid-result) :error)
            {:status 400 :body (second uuid-result)}

            ;; Happy path
            (let [summary-id (second uuid-result)
                  [status result] (summary-service/update-summary
                                   node summary-id user-id body)]

              (if (= status :ok)
                (let [source-changed (not= (:summary/source result) :ai-full)]
                  {:status 200 :body (summary-dto/updated->response result source-changed)})

                ;; Error handling
                (case (:code result)
                  "NOT_FOUND" {:status 404 :body result}
                  "FORBIDDEN" {:status 403 :body result}
                  "VALIDATION_ERROR" {:status 400 :body result}
                  {:status 500 :body result})))))))))

(defn delete-summary-handler
  "DELETE /api/summaries/{id} - Permanently delete a summary"
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400 :body (second uuid-result)}

        ;; Happy path
        (let [summary-id (second uuid-result)
              [status result] (summary-service/delete-summary
                               node summary-id user-id)]

          (if (= status :ok)
            ;; Return 204 No Content (no body) or 200 with message
            {:status 200 :body (summary-dto/deleted->response summary-id)}

            ;; Error handling - return 404 for both not found and forbidden
            (case (:code result)
              "NOT_FOUND" {:status 404 :body result}
              {:status 500 :body result})))))))

(defn accept-summary-handler
  "POST /api/summaries/{id}/accept - Accept an AI-generated summary and update metrics"
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400 :body (second uuid-result)}

        ;; Happy path
        (let [summary-id (second uuid-result)
              [status result] (summary-service/accept-summary
                               node summary-id user-id)]

          (if (= status :ok)
            {:status 200 :body (summary-dto/accepted->response
                                (:summary result)
                                :generation (:generation result))}

            ;; Error handling
            (case (:code result)
              "NOT_FOUND" {:status 404 :body result}
              "FORBIDDEN" {:status 403 :body result}
              "INVALID_OPERATION" {:status 400 :body result}
              "CONFLICT" {:status 409 :body result}
              {:status 500 :body result})))))))

(defn import-csv-handler
  "POST /api/summaries-import - Import CSV data and generate AI summaries via OpenRouter (unused - see summaries_view.clj)"
  [{:keys [session biff.xtdb/node body] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    ;; Guard clause: request body required
    (if-not (some? body)
      {:status 400 :body (util/validation-error "INVALID_REQUEST" "Request body is required")}

      ;; Guard clause: csv field required
      (if-not (contains? body :csv)
        {:status 400
         :body (util/validation-error "MISSING_FIELD" "Missing required field: csv"
                                      :field "csv")}

        ;; Happy path
        (let [user-id (:uid session)
              csv-string (:csv body)

              ;; Step 1: Parse and validate CSV
              [csv-status csv-result] (csv-service/process-csv-import csv-string)]

          ;; Guard clause: CSV processing failed
          (if (= csv-status :error)
            {:status 400 :body csv-result}

            (let [{:keys [valid-rows rejected-rows rows-submitted
                          rows-valid rows-rejected]} csv-result]

              ;; Guard clause: no valid rows
              (if (zero? rows-valid)
                {:status 400
                 :body {:error "All CSV rows failed validation"
                        :code "VALIDATION_ERROR"
                        :rows-submitted rows-submitted
                        :rows-rejected rows-rejected
                        :rejected-rows rejected-rows}}

                ;; Step 2: Generate AI summaries (mocked)
                (let [[ai-status ai-result] (openrouter-service/generate-summaries-batch
                                             valid-rows)]

                  ;; Guard clause: AI generation failed
                  (if (= ai-status :error)
                    {:status 500 :body ai-result}

                    (let [{:keys [summaries model duration-ms]} ai-result

                          ;; Step 3: Create Generation record
                          [gen-status gen-result] (gen-service/create-generation
                                                   node user-id model
                                                   rows-valid duration-ms)]

                      ;; Guard clause: generation creation failed
                      (if (= gen-status :error)
                        {:status 500 :body gen-result}

                        (let [generation gen-result
                              generation-id (:generation/id generation)
                              now (java.time.Instant/now)

                              ;; Step 4: Create Summary records for each AI-generated proposal
                              summary-entities (mapv (fn [summary]
                                                       (let [summary-id (java.util.UUID/randomUUID)]
                                                         {:xt/id summary-id
                                                          :summary/id summary-id
                                                          :summary/user-id user-id
                                                          :summary/generation-id generation-id
                                                          :summary/source :ai-full
                                                          :summary/hive-number (:hive-number summary)
                                                          :summary/observation-date (:observation-date summary)
                                                          :summary/special-feature (:special-feature summary)
                                                          :summary/content (:content summary)
                                                          :summary/created-at now
                                                          :summary/updated-at now}))
                                                     summaries)

                              ;; Submit all summaries in a single transaction
                              tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)
                              _ (xt/submit-tx node tx-ops)]

                          (log/info "CSV import completed"
                                    :user-id user-id
                                    :generation-id generation-id
                                    :rows-submitted rows-submitted
                                    :rows-valid rows-valid
                                    :rows-rejected rows-rejected
                                    :summaries-created (count summary-entities))

                          ;; Step 5: Build response
                          {:status 201
                           :body {:generation-id (str generation-id)
                                  :user-id (str user-id)
                                  :status "completed"
                                  :rows-submitted rows-submitted
                                  :rows-valid rows-valid
                                  :rows-rejected rows-rejected
                                  :rows-processed rows-valid
                                  :model model
                                  :duration-ms duration-ms
                                  :summaries-created (count summary-entities)
                                  :message "CSV import completed successfully"
                                  :summaries (mapv summary-dto/entity->dto summary-entities)
                                  :rejected-rows rejected-rows}})))))))))))))

;; Note: bulk-accept-generation-handler is already in generations.clj

;; =============================================================================
;; Route Definitions
;; =============================================================================

;; Note: API routes are now defined in summaries_view.clj as HTMX handlers
;; This module contains the service logic but routes are handled by the view layer
(def module
  {})
