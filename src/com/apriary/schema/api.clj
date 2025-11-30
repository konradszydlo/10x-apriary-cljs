(ns com.apriary.schema.api)

;; API Request and Response Schemas using Malli
;;
;; These schemas validate incoming API requests and outgoing API responses.
;; All field names use kebab-case (Clojure convention) internally, but will be
;; transformed to snake_case for JSON responses via DTO layer.

;; =============================================================================
;; Query Parameter Schemas
;; =============================================================================

(def list-summaries-query-schema
  "Schema for GET /api/summaries query parameters"
  [:map
   [:sort-by {:optional true} [:enum "created-at" "hive-number" "source"]]
   [:sort-order {:optional true} [:enum "asc" "desc"]]
   [:source {:optional true} [:enum "ai-full" "ai-partial" "manual"]]
   [:limit {:optional true} [:int {:min 1 :max 100}]]
   [:offset {:optional true} [:int {:min 0}]]])

;; =============================================================================
;; Request Body Schemas
;; =============================================================================

(def create-manual-summary-schema
  "Schema for POST /api/summaries request body.

   Validates manual summary creation requests. Content is required with length
   50-50,000 characters after trimming. All other fields are optional.

   Date format: DD-MM-YYYY (e.g., '23-11-2025')"
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])

(def update-summary-schema
  "Schema for PATCH /api/summaries/{id} request body.

   At least one field must be provided. Content length validated if present.
   This schema enforces {:min 1} to ensure the request body is not empty."
  [:map {:min 1}
   [:hive-number {:optional true} :string]
   [:observation-date {:optional true} [:re #"^\d{2}-\d{2}-\d{4}$"]]
   [:special-feature {:optional true} :string]
   [:content {:optional true} [:string {:min 50 :max 50000}]]])

(def bulk-accept-schema
  "Schema for POST /api/summaries/generation/accept request body."
  [:map
   [:generation-id :uuid]])

(def csv-import-schema
  "Schema for POST /api/summaries-import request body.

   CSV format requirements:
   - UTF-8 encoding
   - Semicolon (;) delimiter
   - Required header: 'observation'
   - Optional headers: 'hive_number', 'observation_date', 'special_feature'
   - Each observation: 50-10,000 characters after trim"
  [:map
   [:csv [:string {:min 1}]]])

;; =============================================================================
;; Response DTO Schemas
;; =============================================================================

(def summary-dto-schema
  "Schema for summary entity in API responses.

   Field names will be transformed to snake_case for JSON responses.
   Timestamps will be formatted as ISO-8601 strings."
  [:map
   [:id :uuid]
   [:user-id :uuid]
   [:generation-id {:optional true} [:maybe :uuid]]
   [:source [:enum "ai-full" "ai-partial" "manual"]]
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe :string]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content :string]
   [:created-at inst?]
   [:updated-at inst?]])

(def summary-list-response-schema
  "Schema for GET /api/summaries response."
  [:map
   [:summaries [:sequential summary-dto-schema]]
   [:total-count :int]
   [:limit :int]
   [:offset :int]])

(def rejected-row-schema
  "Schema for rejected CSV rows in import response."
  [:map
   [:row-number :int]
   [:reason :string]])

(def csv-import-response-schema
  "Schema for POST /api/summaries-import response.

   Status values:
   - 'processing': Async operation initiated (202 response)
   - 'completed': Synchronous operation completed (201 response)"
  [:map
   [:generation-id :uuid]
   [:user-id :uuid]
   [:status [:enum "processing" "completed"]]
   [:rows-submitted :int]
   [:rows-valid :int]
   [:rows-rejected :int]
   [:rows-processed {:optional true} :int]
   [:model {:optional true} :string]
   [:duration-ms {:optional true} :int]
   [:summaries-created {:optional true} :int]
   [:message :string]
   [:summaries {:optional true} [:sequential summary-dto-schema]]
   [:rejected-rows [:sequential rejected-row-schema]]])

(def bulk-accept-response-schema
  "Schema for POST /api/summaries/generation/accept response."
  [:map
   [:generation-id :uuid]
   [:user-id :uuid]
   [:summaries-accepted :int]
   [:accepted-unedited :int]
   [:accepted-edited :int]
   [:message :string]])
