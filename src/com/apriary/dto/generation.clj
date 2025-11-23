(ns com.apriary.dto.generation
  (:require [com.apriary.util :as util]))

;; Data Transfer Object (DTO) conversion functions

(defn entity->dto
  "Convert an XTDB generation entity to API response DTO format.
   
   This function transforms the internal XTDB entity representation to the
   public API format by:
   1. Converting field names from kebab-case to snake_case
   2. Calculating computed fields (total_accepted_count, acceptance_rate)
   3. Formatting timestamps as ISO-8601 strings
   4. Removing internal fields (xt/id)
   
   Params:
   - entity: XTDB generation entity map
   
   Returns:
   - DTO map with snake_case field names and formatted timestamps"
  [entity]
  (let [unedited (or (:generation/accepted-unedited-count entity) 0)
        edited (or (:generation/accepted-edited-count entity) 0)
        total-accepted (+ unedited edited)
        generated (or (:generation/generated-count entity) 1) ; avoid division by zero
        acceptance-rate (if (> generated 0)
                          (double (* 100.0 (/ total-accepted generated)))
                          0.0)]

    {:id (str (:generation/id entity))
     :user_id (str (:generation/user-id entity))
     :model (:generation/model entity)
     :generated_count (:generation/generated-count entity)
     :accepted_unedited_count unedited
     :accepted_edited_count edited
     :total_accepted_count total-accepted
     :acceptance_rate acceptance-rate
     :duration_ms (:generation/duration-ms entity)
     :created_at (util/format-iso-8601 (:generation/created-at entity))
     :updated_at (util/format-iso-8601 (:generation/updated-at entity))}))

(defn list->response
  "Build a list response with pagination metadata.
   
   Params:
   - generations: List of XTDB generation entities
   - total-count: Total number of generations matching query (before limit/offset)
   - limit: Pagination limit that was applied
   - offset: Pagination offset that was applied
   
   Returns:
   - Response map with generations array and pagination metadata"
  [generations total-count limit offset]
  {:generations (mapv entity->dto generations)
   :total_count total-count
   :limit limit
   :offset offset})

(defn single->response
  "Build a single generation response.
   
   Params:
   - entity: XTDB generation entity
   
   Returns:
   - DTO map for single generation"
  [entity]
  (entity->dto entity))

(defn bulk-accept->response
  "Build a bulk accept operation response.
   
   Params:
   - generation: Updated generation entity
   - unedited-count: Number of ai-full summaries accepted
   - edited-count: Number of ai-partial summaries accepted
   - manual-count: Number of manual summaries (unchanged)
   - total-summaries: Total summaries processed
   
   Returns:
   - Response map with generation DTO and summary counts"
  [generation unedited-count edited-count manual-count total-summaries]
  {:generation (entity->dto generation)
   :unedited_accepted unedited-count
   :edited_accepted edited-count
   :manual_count manual-count
   :total_summaries_processed total-summaries
   :timestamp (util/format-iso-8601 (java.time.Instant/now))})

;; Helper function to wrap successful responses

(defn success-response
  "Build a successful API response.
   
   Params:
   - status: HTTP status code (default 200)
   - body: Response body (map or any JSON-serializable data)
   
   Returns:
   - Ring response map with status and body"
  [body & {:keys [status] :or {status 200}}]
  {:status status
   :body body})
