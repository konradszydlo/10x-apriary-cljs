(ns com.apriary.dto.summary
  (:require [com.apriary.util :as util]))

;; Data Transfer Object (DTO) conversion functions for Summary entity
;;
;; These functions transform internal XTDB entities to public API response format.
;; zFollowing the implementation plan, we use kebab-case for JSON field names
;; to maintain consistency with Clojure conventions throughout the application.

(defn- source-keyword->string
  "Convert source keyword to string for JSON serialization.

   :ai-full → 'ai-full'
   :ai-partial → 'ai-partial'
   :manual → 'manual'"
  [source-keyword]
  (when source-keyword
    (name source-keyword)))

(defn entity->dto
  "Convert an XTDB summary entity to API response DTO format.

   This function transforms the internal XTDB entity representation to the
   public API format by:
   1. Removing namespace prefixes from field names (keeping kebab-case)
   2. Formatting timestamps as ISO-8601 strings
   3. Converting keyword enums to strings
   4. Converting UUIDs to strings
   5. Removing internal fields (xt/id)

   Params:
   - entity: XTDB summary entity map

   Returns:
   - DTO map with kebab-case field names and formatted values"
  [entity]
  {:id (str (:summary/id entity))
   :user-id (str (:summary/user-id entity))
   :generation-id (when-let [gen-id (:summary/generation-id entity)]
                    (str gen-id))
   :source (source-keyword->string (:summary/source entity))
   :hive-number (:summary/hive-number entity)
   :observation-date (:summary/observation-date entity)
   :special-feature (:summary/special-feature entity)
   :content (:summary/content entity)
   :created-at (util/format-iso-8601 (:summary/created-at entity))
   :updated-at (util/format-iso-8601 (:summary/updated-at entity))
   ;; Include accepted-at if present (for accept operation responses)
   :accepted-at (when-let [accepted (:summary/accepted-at entity)]
                  (util/format-iso-8601 accepted))})

(defn list->response
  "Build a list response with pagination metadata.

   Params:
   - summaries: List of XTDB summary entities
   - total-count: Total number of summaries matching query (before limit/offset)
   - limit: Pagination limit that was applied
   - offset: Pagination offset that was applied

   Returns:
   - Response map with summaries array and pagination metadata"
  [summaries total-count limit offset]
  {:summaries (mapv entity->dto summaries)
   :total-count total-count
   :limit limit
   :offset offset})

(defn single->response
  "Build a single summary response with success message.

   Params:
   - entity: XTDB summary entity
   - message: Optional success message

   Returns:
   - DTO map for single summary with optional message"
  [entity & {:keys [message]}]
  (cond-> (entity->dto entity)
    (some? message) (assoc :message message)))

(defn created->response
  "Build a response for newly created summary (201 Created).

   Params:
   - entity: XTDB summary entity

   Returns:
   - DTO map with success message"
  [entity]
  (assoc (entity->dto entity)
         :message "Summary created successfully"))

(defn updated->response
  "Build a response for updated summary (200 OK).

   Params:
   - entity: Updated XTDB summary entity
   - source-changed: Boolean indicating if source changed from ai-full to ai-partial

   Returns:
   - DTO map with appropriate success message"
  [entity source-changed]
  (let [message (if source-changed
                  "Summary updated and marked as ai-partial"
                  "Summary updated successfully")]
    (assoc (entity->dto entity)
           :message message)))

(defn deleted->response
  "Build a response for successful deletion (200 OK).

   Params:
   - summary-id: UUID of the deleted summary

   Returns:
   - Response map with deletion confirmation"
  [summary-id]
  {:message "Summary deleted successfully"
   :id (str summary-id)})

(defn accepted->response
  "Build a response for accepted summary (200 OK).

   Params:
   - summary: Accepted summary entity (with :summary/accepted-at)
   - generation: Updated generation entity (optional, for detailed response)

   Returns:
   - Response map with accepted summary DTO and success message"
  [summary & {:keys [generation]}]
  (cond-> (entity->dto summary)
    true (assoc :message "Summary accepted successfully")
    (some? generation) (assoc :generation-id (str (:generation/id generation)))))

;; Validation error responses specific to summaries

(defn validation-error
  "Build a validation error response for summary operations.

   Params:
   - field: Field name that failed validation
   - message: Error message

   Returns:
   - Error response body"
  [field message]
  (util/validation-error "VALIDATION_ERROR" message
                         :field field))

(defn content-too-short-error
  "Build error response for content that's too short.

   Params:
   - current-length: Actual content length

   Returns:
   - Validation error response"
  [current-length]
  (validation-error "content"
                    (str "content must be at least 50 characters (current: "
                         current-length " characters)")))

(defn content-too-long-error
  "Build error response for content that's too long.

   Params:
   - current-length: Actual content length

   Returns:
   - Validation error response"
  [current-length]
  (validation-error "content"
                    (str "content must not exceed 50,000 characters (current: "
                         current-length " characters)")))

(defn invalid-date-format-error
  "Build error response for invalid observation date format.

   Returns:
   - Validation error response"
  []
  (validation-error "observation-date"
                    "observation-date must be in DD-MM-YYYY format"))

(defn not-found-error
  "Build a 404 not found error response for summaries.

   Params:
   - summary-id: UUID of the summary

   Returns:
   - Error response body"
  [summary-id]
  (util/build-error-response 404 "NOT_FOUND"
                             (str "Summary with ID " summary-id " not found")
                             :details {:id (str summary-id)}))

(defn cannot-accept-manual-error
  "Build error response for attempting to accept a manual summary.

   Returns:
   - Error response body"
  []
  (util/build-error-response 400 "INVALID_OPERATION"
                             "Cannot accept manual summaries"
                             :details {:reason "Manual summaries are already considered accepted"}))

(defn already-accepted-error
  "Build error response for attempting to accept an already-accepted summary.

   Returns:
   - Error response body"
  []
  (util/build-error-response 409 "CONFLICT"
                             "Summary already accepted"
                             :details {:reason "This summary has already been accepted"}))
