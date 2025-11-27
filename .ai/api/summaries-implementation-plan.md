# API Endpoints Implementation Plan: Summaries

## 1. Endpoint Overview

This implementation plan covers **8 REST API endpoints** for managing summaries in the Apriary application:

1. **GET /api/summaries** - List all summaries for authenticated user with filtering and pagination
2. **POST /api/summaries** - Create a manual summary without AI generation
3. **GET /api/summaries/{id}** - Retrieve a single summary by ID
4. **PATCH /api/summaries/{id}** - Update summary metadata or content with automatic source tracking
5. **DELETE /api/summaries/{id}** - Permanently delete a summary
6. **POST /api/summaries/{id}/accept** - Accept an AI-generated summary and update metrics
7. **POST /api/summaries/generation/accept** - Bulk accept all summaries from a generation batch
8. **POST /api/summaries/import** - Import CSV data and generate AI summaries via OpenRouter

These endpoints support the core workflow: CSV import → AI generation → user review/edit → acceptance/rejection → manual creation.

---

## 2. Request Details

**Important Naming Convention**: All request and response bodies use **kebab-case** for field names (e.g., `user-id`, `hive-number`, `observation-date`) to maintain consistency with Clojure conventions throughout the application.

### 2.1 List User Summaries

**HTTP Method:** `GET`
**URL Structure:** `/api/summaries`
**Authentication:** Required (session-based via Biff)

**Query Parameters:**
- `sort-by` (optional, default: `created-at`) - Field to sort by: `created-at`, `hive-number`, `source`
- `sort-order` (optional, default: `desc`) - Sort direction: `asc` or `desc`
- `source` (optional) - Filter by source: `ai-full`, `ai-partial`, `manual`
- `limit` (optional, default: `50`) - Maximum results (1-100)
- `offset` (optional, default: `0`) - Pagination offset (≥0)

**Request Body:** None

**Validation:**
- `sort-by` must be one of: `created-at`, `hive-number`, `source`
- `sort-order` must be `asc` or `desc`
- `source` must be valid enum value if provided
- `limit` must be integer between 1-100
- `offset` must be non-negative integer

---

### 2.2 Create Manual Summary

**HTTP Method:** `POST`
**URL Structure:** `/api/summaries`
**Authentication:** Required
**Content-Type:** `application/json`

**Request Body:**
```json
{
  "hive-number": "A-02",           // optional, can be empty string
  "observation-date": "23-11-2025", // optional, DD-MM-YYYY format or empty
  "special-feature": "New frames",  // optional, can be empty string
  "content": "Detailed observation text..." // required, 50-50,000 chars
}
```

**Required Fields:**
- `content` (string, 50-50,000 characters after trim)

**Optional Fields:**
- `hive-number` (string, can be empty)
- `observation-date` (string, DD-MM-YYYY format or empty)
- `special-feature` (string, can be empty)

**Validation Rules:**
- `content`: trim whitespace, then check 50 ≤ length ≤ 50,000
- `observation-date`: if provided and non-empty, validate format `^\d{2}-\d{2}-\d{4}$` and actual date validity
- All fields optional except `content`

---

### 2.3 Get Single Summary

**HTTP Method:** `GET`
**URL Structure:** `/api/summaries/{id}`
**Authentication:** Required

**Path Parameters:**
- `id` (UUID) - Summary identifier

**Query Parameters:** None
**Request Body:** None

**Validation:**
- `id` must be valid UUID format
- Summary must exist and belong to authenticated user (RLS check)

---

### 2.4 Update Summary (Inline Edit)

**HTTP Method:** `PATCH`
**URL Structure:** `/api/summaries/{id}`
**Authentication:** Required
**Content-Type:** `application/json`

**Path Parameters:**
- `id` (UUID) - Summary identifier

**Request Body (at least one field required):**
```json
{
  "hive-number": "A-01-Updated",     // optional
  "observation-date": "24-11-2025",  // optional
  "special-feature": "Updated",      // optional
  "content": "Updated content..."    // optional
}
```

**Validation:**
- At least one field must be provided
- `content`: if provided, validate 50-50,000 characters after trim
- `observation-date`: if provided, validate DD-MM-YYYY format
- `id` must be valid UUID and belong to user

**Business Logic:**
- If `content` is updated and current `source` is `:ai-full`, change to `:ai-partial`
- Update `updated-at` timestamp
- Metadata-only updates don't change `source`

---

### 2.5 Delete Summary

**HTTP Method:** `DELETE`
**URL Structure:** `/api/summaries/{id}`
**Authentication:** Required

**Path Parameters:**
- `id` (UUID) - Summary identifier

**Query Parameters:** None
**Request Body:** None

**Validation:**
- `id` must be valid UUID
- Summary must exist and belong to user
- Permanent deletion (no soft-delete)

---

### 2.6 Accept Summary

**HTTP Method:** `POST`
**URL Structure:** `/api/summaries/{id}/accept`
**Authentication:** Required

**Path Parameters:**
- `id` (UUID) - Summary identifier

**Request Body:** None (or optional empty JSON `{}`)

**Validation:**
- `id` must be valid UUID
- Summary must exist and belong to user
- `source` must be `:ai-full` or `:ai-partial` (cannot accept `:manual`)
- Summary must not already be accepted (prevent double-counting)

**Business Logic:**
- If `source == :ai-full`: increment `generation/accepted-unedited-count`
- If `source == :ai-partial`: increment `generation/accepted-edited-count`
- Update `generation/updated-at`
- Mark summary as accepted (implementation-specific flag or tracking)

---

### 2.7 Bulk Accept Summaries for Generation

**HTTP Method:** `POST`
**URL Structure:** `/api/generations/{id}/accept-summaries` *(Changed from `/api/summaries/generation/accept` to avoid route conflict with `/api/summaries/:id/accept`)*
**Authentication:** Required
**Content-Type:** `application/json`

**Path Parameters:**
- `id` (UUID) - Generation identifier

**Request Body:** Empty (or optional empty JSON `{}`)

**Validation:**
- `id` must be valid UUID
- Generation must exist and belong to user
- Generation must have associated summaries

**Business Logic:**
- Extract generation ID from path parameter
- Query all summaries with matching `generation-id` and `user-id`
- Count summaries by source type (`:ai-full` vs `:ai-partial`)
- Update generation counters in single operation
- Skip `:manual` summaries (shouldn't exist in AI batches)
- Return counts of accepted summaries by type

---

### 2.8 Import CSV and Generate Summaries

**HTTP Method:** `POST`
**URL Structure:** `/api/summaries/import`
**Authentication:** Required
**Content-Type:** `application/json`

**Request Body:**
```json
{
  "csv": "observation;hive_number;observation_date;special_feature\nFirst observation text...;A-01;23-11-2025;Queen active\nSecond observation...;A-02;24-11-2025;"
}
```

**Required Fields:**
- `csv` (string) - CSV data with semicolon delimiter

**CSV Format Requirements:**
- Encoding: UTF-8
- Delimiter: semicolon (`;`)
- Headers required (case-insensitive): `observation` (required), `hive_number`, `observation_date`, `special_feature` (all optional)
- Observation field: 50-10,000 characters after trim

**Validation:**
- CSV string non-empty
- Valid UTF-8 encoding
- Semicolon-delimited
- Required `observation` column present
- Per-row validation: observation length 50-10,000 chars
- Date format validation if provided
- Reject invalid rows but continue processing valid ones

**Business Logic:**
1. Parse CSV string
2. Validate each row
3. Send valid observations to OpenRouter API (batch request)
4. Create Generation record with metrics
5. Create Summary records for each AI-generated proposal
6. Return response with generation ID, counts, and rejected rows

---

## 3. Used Types

### 3.1 Malli Schemas for Validation

```clojure
(ns com.apriary.schema.api
  (:require [malli.core :as m]))

;; Query parameter schemas
(def list-summaries-query-schema
  [:map
   [:sort-by {:optional true} [:enum "created-at" "hive-number" "source"]]
   [:sort-order {:optional true} [:enum "asc" "desc"]]
   [:source {:optional true} [:enum "ai-full" "ai-partial" "manual"]]
   [:limit {:optional true} [:int {:min 1 :max 100}]]
   [:offset {:optional true} [:int {:min 0}]]])

;; Request body schemas
(def create-manual-summary-schema
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])

(def update-summary-schema
  [:map {:min 1}
   [:hive-number {:optional true} :string]
   [:observation-date {:optional true} [:re #"^\d{2}-\d{2}-\d{4}$"]]
   [:special-feature {:optional true} :string]
   [:content {:optional true} [:string {:min 50 :max 50000}]]])

(def bulk-accept-schema
  [:map
   [:generation-id :uuid]])

(def csv-import-schema
  [:map
   [:csv [:string {:min 1}]]])

;; Response DTOs (using kebab-case for consistency with Clojure conventions)
(def summary-dto-schema
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
  [:map
   [:summaries [:sequential summary-dto-schema]]
   [:total-count :int]
   [:limit :int]
   [:offset :int]])

(def rejected-row-schema
  [:map
   [:row-number :int]
   [:reason :string]])

(def csv-import-response-schema
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
  [:map
   [:generation-id :uuid]
   [:user-id :uuid]
   [:summaries-accepted :int]
   [:accepted-unedited :int]
   [:accepted-edited :int]
   [:message :string]])
```

### 3.2 Domain Models (from schema.clj)

```clojure
;; Summary entity (already defined in db-plan.md)
[:map {:closed true}
 [:xt/id :uuid]
 [:summary/id :uuid]
 [:summary/user-id :uuid]
 [:summary/generation-id {:optional true} [:maybe :uuid]]
 [:summary/source [:enum :ai-full :ai-partial :manual]]
 [:summary/created-at inst?]
 [:summary/updated-at inst?]
 [:summary/hive-number {:optional true} [:maybe :string]]
 [:summary/observation-date {:optional true} [:maybe :string]]
 [:summary/special-feature {:optional true} [:maybe :string]]
 [:summary/content :string]]

;; Generation entity
[:map {:closed true}
 [:xt/id :uuid]
 [:generation/id :uuid]
 [:generation/user-id :uuid]
 [:generation/model :string]
 [:generation/generated-count [:int {:min 0}]]
 [:generation/accepted-unedited-count [:int {:min 0}]]
 [:generation/accepted-edited-count [:int {:min 0}]]
 [:generation/duration-ms [:int {:min 0}]]
 [:generation/created-at inst?]
 [:generation/updated-at inst?]]
```

---

## 4. Response Details

**Important Naming Convention**: All JSON responses use **kebab-case** for field names (e.g., `user-id`, `hive-number`, `created-at`) to maintain consistency with Clojure conventions throughout the application. This differs from the common REST API convention of using snake_case.

### 4.1 List User Summaries (GET /api/summaries)

**Success Response (200 OK):**
```json
{
  "summaries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "user-id": "550e8400-e29b-41d4-a716-446655440000",
      "generation-id": "550e8400-e29b-41d4-a716-446655440002",
      "source": "ai-full",
      "hive-number": "A-01",
      "observation-date": "23-11-2025",
      "special-feature": "Queen replaced",
      "content": "Summary of hive activities...",
      "created-at": "2025-11-23T10:30:00Z",
      "updated-at": "2025-11-23T10:30:00Z"
    }
  ],
  "total-count": 42,
  "limit": 50,
  "offset": 0
}
```

**Error Responses:**
- **400 Bad Request** - Invalid query parameters
- **401 Unauthorized** - Not authenticated
- **500 Internal Server Error** - Database failure

---

### 4.2 Create Manual Summary (POST /api/summaries)

**Success Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440003",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": null,
  "source": "manual",
  "hive_number": "A-02",
  "observation_date": "23-11-2025",
  "special_feature": "New frames added",
  "content": "Detailed observation text...",
  "created_at": "2025-11-23T10:35:00Z",
  "updated_at": "2025-11-23T10:35:00Z",
  "message": "Summary created successfully"
}
```

**Error Responses:**
- **400 Bad Request** - Invalid data (content length, date format, missing required field)
- **401 Unauthorized** - Not authenticated
- **500 Internal Server Error** - Database creation failure

---

### 4.3 Get Single Summary (GET /api/summaries/{id})

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-partial",
  "hive_number": "A-01",
  "observation_date": "23-11-2025",
  "special_feature": "Queen replaced",
  "content": "Complete summary text...",
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T11:00:00Z"
}
```

**Error Responses:**
- **401 Unauthorized** - Not authenticated
- **403 Forbidden** - User doesn't own this summary (RLS violation)
- **404 Not Found** - Summary doesn't exist
- **500 Internal Server Error** - Database query failure

---

### 4.4 Update Summary (PATCH /api/summaries/{id})

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-partial",
  "hive_number": "A-01-Updated",
  "observation_date": "24-11-2025",
  "special_feature": "Updated feature",
  "content": "Updated summary content...",
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T11:15:00Z",
  "message": "Summary updated successfully"
}
```

**Success Response with Source Change (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "source": "ai-partial",
  "updated_at": "2025-11-23T11:15:00Z",
  "message": "Summary updated and marked as ai-partial"
}
```

**Error Responses:**
- **400 Bad Request** - Invalid data, empty request body
- **401 Unauthorized** - Not authenticated
- **403 Forbidden** - User doesn't own summary
- **404 Not Found** - Summary doesn't exist
- **409 Conflict** - Concurrent modification
- **500 Internal Server Error** - Database update failure

---

### 4.5 Delete Summary (DELETE /api/summaries/{id})

**Success Response (204 No Content):**
```
No Content
```

**Alternative Success Response (200 OK):**
```json
{
  "message": "Summary deleted successfully",
  "id": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Error Responses:**
- **401 Unauthorized** - Not authenticated
- **403 Forbidden** - User doesn't own summary
- **404 Not Found** - Summary doesn't exist
- **500 Internal Server Error** - Database deletion failure

---

### 4.6 Accept Summary (POST /api/summaries/{id}/accept)

**Success Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-full",
  "content": "Summary content...",
  "accepted_at": "2025-11-23T11:20:00Z",
  "message": "Summary accepted successfully"
}
```

**Error Responses:**
- **400 Bad Request** - Cannot accept manual summary
- **401 Unauthorized** - Not authenticated
- **403 Forbidden** - User doesn't own summary
- **404 Not Found** - Summary doesn't exist
- **409 Conflict** - Already accepted
- **500 Internal Server Error** - Database update failure

---

### 4.7 Bulk Accept (POST /api/summaries/generation/accept)

**Success Response (200 OK):**
```json
{
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "summaries_accepted": 14,
  "accepted_unedited": 8,
  "accepted_edited": 6,
  "message": "All summaries for generation accepted successfully"
}
```

**Error Responses:**
- **400 Bad Request** - Invalid generation-id, no summaries for generation
- **401 Unauthorized** - Not authenticated
- **403 Forbidden** - User doesn't own generation
- **404 Not Found** - Generation doesn't exist
- **500 Internal Server Error** - Database update failure

---

### 4.8 CSV Import (POST /api/summaries/import)

**Success Response - Synchronous (201 Created):**
```json
{
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "completed",
  "rows_submitted": 15,
  "rows_valid": 14,
  "rows_rejected": 1,
  "rows_processed": 14,
  "model": "gpt-4-turbo",
  "duration_ms": 3450,
  "summaries_created": 14,
  "message": "CSV import completed successfully",
  "summaries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440003",
      "source": "ai-full",
      "hive_number": "A-01",
      "observation_date": "23-11-2025",
      "content": "Generated summary text..."
    }
  ],
  "rejected_rows": [
    {
      "row_number": 3,
      "reason": "Observation text too short (23 characters). Minimum: 50 characters."
    }
  ]
}
```

**Success Response - Async (202 Accepted):**
```json
{
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "processing",
  "rows_submitted": 15,
  "rows_valid": 14,
  "rows_rejected": 1,
  "message": "CSV import initiated. Summaries are being generated.",
  "rejected_rows": [
    {
      "row_number": 3,
      "reason": "Observation text too short (23 characters)"
    }
  ]
}
```

**Error Responses:**
- **400 Bad Request** - Invalid CSV format, missing columns, empty CSV
- **401 Unauthorized** - Not authenticated
- **429 Too Many Requests** - Rate limit exceeded (max 5 imports/hour)
- **500 Internal Server Error** - CSV parsing failure, OpenRouter API failure
- **503 Service Unavailable** - OpenRouter service unavailable

---

## 5. Data Flow

### 5.1 List User Summaries Flow

```
Client Request (GET /api/summaries?sort-by=created-at&limit=50)
    ↓
Biff Middleware (Authentication Check)
    ↓
Route Handler (/api/summaries)
    ↓
Validate Query Parameters (Malli schema)
    ↓
Summary Service: list-summaries
    ↓
Apply RLS Filter (user-id = authenticated user)
    ↓
XTDB Datalog Query with:
  - WHERE clause: summary/user-id = ?user-id
  - Optional WHERE: summary/source = ?source (if filtered)
  - ORDER BY: ?sort-by ?sort-order
  - LIMIT/OFFSET: pagination
    ↓
Transform XTDB docs → DTOs (namespace removal)
    ↓
Build Response:
  - summaries: [list of DTOs]
  - total_count: count query result
  - limit: from query params
  - offset: from query params
    ↓
Return 200 OK with JSON response
```

---

### 5.2 Create Manual Summary Flow

```
Client Request (POST /api/summaries)
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Route Handler (/api/summaries POST)
    ↓
Parse JSON body
    ↓
Validate Request Body (Malli schema)
  - content: 50-50,000 chars after trim
  - observation_date: DD-MM-YYYY format or empty
  - Guard clause: if validation fails → 400 error
    ↓
Extract user-id from session
    ↓
Summary Service: create-manual-summary
    ↓
Build Summary Document:
  - :xt/id = (random-uuid)
  - :summary/id = (random-uuid)
  - :summary/user-id = authenticated user
  - :summary/generation-id = nil
  - :summary/source = :manual
  - :summary/content = trimmed content
  - :summary/hive-number = from request or nil
  - :summary/observation-date = from request or nil
  - :summary/special-feature = from request or nil
  - :summary/created-at = (now)
  - :summary/updated-at = (now)
    ↓
XTDB Transaction: submit-tx [{:xt/id ... :summary/... ...}]
    ↓
Await transaction completion
    ↓
Transform domain model → DTO
    ↓
Return 201 Created with summary DTO + message
```

---

### 5.3 Get Single Summary Flow

```
Client Request (GET /api/summaries/{id})
    ↓
Biff Middleware (Authentication Check)
    ↓
Route Handler (/api/summaries/:id GET)
    ↓
Parse UUID from path parameter
  - Guard clause: if invalid UUID → 400 error
    ↓
Summary Service: get-summary-by-id
    ↓
XTDB Query: (xt/entity db id)
    ↓
RLS Check: summary/user-id == authenticated user?
  - If no match → 404 Not Found (don't leak existence)
  - If no doc found → 404 Not Found
    ↓
Transform domain model → DTO
    ↓
Return 200 OK with summary DTO
```

---

### 5.4 Update Summary Flow

```
Client Request (PATCH /api/summaries/{id})
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Route Handler (/api/summaries/:id PATCH)
    ↓
Parse UUID from path
Parse JSON body
    ↓
Validate Request:
  - At least one field present
  - content: 50-50,000 chars if provided
  - observation_date: DD-MM-YYYY format if provided
  - Guard clause: if validation fails → 400 error
    ↓
Summary Service: update-summary
    ↓
Load existing summary from XTDB
    ↓
RLS Check: summary/user-id == authenticated user?
  - If no match → 403 Forbidden
    ↓
Build Update Document:
  - Merge existing fields with updates
  - If content changed AND source = :ai-full → source = :ai-partial
  - :summary/updated-at = (now)
    ↓
XTDB Transaction: submit-tx [updated-doc]
    ↓
Await transaction
    ↓
Transform → DTO
    ↓
Return 200 OK with updated summary + message
```

---

### 5.5 Delete Summary Flow

```
Client Request (DELETE /api/summaries/{id})
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Route Handler (/api/summaries/:id DELETE)
    ↓
Parse UUID from path
    ↓
Summary Service: delete-summary
    ↓
Load existing summary from XTDB
    ↓
RLS Check: summary/user-id == authenticated user?
  - If no match → 404 Not Found
    ↓
XTDB Transaction: submit-tx [{:xt/id id, :xtdb.api/op :delete}]
    ↓
Await transaction
    ↓
Return 204 No Content or 200 OK with message
```

---

### 5.6 Accept Summary Flow

```
Client Request (POST /api/summaries/{id}/accept)
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Route Handler (/api/summaries/:id/accept POST)
    ↓
Parse UUID from path
    ↓
Summary Service: accept-summary
    ↓
Load existing summary from XTDB
    ↓
RLS Check: summary/user-id == authenticated user?
  - Guard clause: if no match → 403 Forbidden
    ↓
Validate source:
  - Guard clause: if source = :manual → 400 "Cannot accept manual summary"
  - Guard clause: if already accepted → 409 Conflict
    ↓
Load Generation record: (xt/entity db generation-id)
    ↓
Determine counter to increment:
  - If source = :ai-full → increment accepted-unedited-count
  - If source = :ai-partial → increment accepted-edited-count
    ↓
XTDB Transaction: update generation document
  - :generation/accepted-[type]-count += 1
  - :generation/updated-at = (now)
    ↓
Mark summary as accepted (implementation-specific, e.g., add :summary/accepted-at)
    ↓
Await transaction
    ↓
Build response with accepted_at timestamp
    ↓
Return 200 OK with summary + message
```

---

### 5.7 Bulk Accept Flow

```
Client Request (POST /api/summaries/generation/accept)
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Route Handler (/api/summaries/generation/accept POST)
    ↓
Parse JSON body
Validate generation-id (UUID)
    ↓
Generation Service: bulk-accept-summaries
    ↓
Load Generation record
    ↓
RLS Check: generation/user-id == authenticated user?
  - Guard clause: if no match → 403 Forbidden
  - Guard clause: if not found → 404 Not Found
    ↓
XTDB Query: find all summaries with generation-id
  - WHERE: summary/generation-id = ?gen-id
  - WHERE: summary/user-id = ?user-id (RLS)
    ↓
Guard clause: if no summaries found → 400 "No summaries for generation"
    ↓
Count summaries by source:
  - Count :ai-full → unedited-count
  - Count :ai-partial → edited-count
  - Skip :manual (shouldn't exist)
    ↓
XTDB Transaction: update generation
  - :generation/accepted-unedited-count += unedited-count
  - :generation/accepted-edited-count += edited-count
  - :generation/updated-at = (now)
    ↓
Await transaction
    ↓
Build response:
  - summaries_accepted = total count
  - accepted_unedited = unedited count
  - accepted_edited = edited count
    ↓
Return 200 OK with bulk accept response
```

---

### 5.8 CSV Import Flow

```
Client Request (POST /api/summaries/import)
    ↓
Biff Middleware (Authentication + CSRF Check)
    ↓
Rate Limiting Check (5 imports/hour per user)
  - Guard clause: if exceeded → 429 Too Many Requests
    ↓
Route Handler (/api/summaries/import POST)
    ↓
Parse JSON body
Validate csv field present
    ↓
CSV Service: parse-csv
    ↓
Step 1: Parse CSV String
  - Check UTF-8 encoding
  - Guard clause: if invalid encoding → 400 error
  - Split by semicolon delimiter
  - Extract headers (case-insensitive)
  - Guard clause: if no "observation" column → 400 error
  - Guard clause: if CSV empty → 400 error
    ↓
Step 2: Validate Rows
  For each row:
    - Extract observation, hive_number, observation_date, special_feature
    - Trim observation
    - Check: 50 ≤ length(observation) ≤ 10,000
    - Validate date format if provided
    - If valid: add to valid-rows
    - If invalid: add to rejected-rows with reason
  - Guard clause: if all rows invalid → 400 with rejection summary
    ↓
Step 3: OpenRouter API Request (MOCKED for MVP)
  - **NOTE**: For initial implementation, OpenRouter API will be mocked
  - Mock will return observations as-is (observation text = generated summary)
  - Implementation details for mock will be provided during development
  - Proper OpenRouter integration will be implemented later
  - Retrieve model from config (e.g., config.edn) - used for generation metadata
  - Record start-time
  - OpenRouter Service: generate-summaries-batch (mocked)
  - Mock response: return each observation text as the summary content
  - Record end-time
  - Calculate duration-ms
  - Guard clause: if API fails → 500 or 503 error
    ↓
Step 4: Create Generation Record
  - Build generation document:
    - :generation/id = (random-uuid)
    - :generation/user-id = authenticated user
    - :generation/model = from config
    - :generation/generated-count = count(valid-rows)
    - :generation/accepted-unedited-count = 0
    - :generation/accepted-edited-count = 0
    - :generation/duration-ms = calculated duration
    - :generation/created-at = (now)
    - :generation/updated-at = (now)
  - XTDB Transaction: submit-tx [generation-doc]
    ↓
Step 5: Create Summary Records
  For each valid row + AI proposal:
    - Build summary document:
      - :summary/id = (random-uuid)
      - :summary/user-id = authenticated user
      - :summary/generation-id = generation-id
      - :summary/source = :ai-full
      - :summary/hive-number = from CSV or nil
      - :summary/observation-date = from CSV or nil
      - :summary/special-feature = from CSV or nil
      - :summary/content = AI-generated text
      - :summary/created-at = (now)
      - :summary/updated-at = (now)
  - XTDB Transaction: submit-tx [list of summary-docs]
    ↓
Await all transactions
    ↓
Build Response:
  - generation_id, user_id
  - status = "completed"
  - rows_submitted, rows_valid, rows_rejected
  - rows_processed, model, duration_ms
  - summaries_created
  - summaries = list of created summaries (DTOs)
  - rejected_rows = list of rejections
  - message = success message
    ↓
Return 201 Created with CSV import response
```

---

## 6. Security Considerations

### 6.1 Authentication

**Mechanism:** Session-based authentication via Biff framework

**Implementation:**
- All endpoints require valid session cookie
- Middleware extracts `user-id` from session
- Guard clause at route handler entry: if no session → 401 Unauthorized
- Use Biff's built-in auth system (already implemented)

**Code Pattern:**
```clojure
(defn require-auth [handler]
  (fn [request]
    (if-let [user-id (get-in request [:session :user-id])]
      (handler (assoc request :user-id user-id))
      {:status 401
       :body {:error "Unauthorized"
              :code "UNAUTHORIZED"}})))
```

---

### 6.2 Row-Level Security (RLS)

**Principle:** Every entity has `user-id`; users can only access their own data

**Implementation Pattern:**
1. **Query Filtering:** Always add `user-id` filter to XTDB queries
2. **Document Verification:** Before update/delete, verify document's `user-id` matches authenticated user
3. **403 vs 404:** Return 404 for non-existent OR unauthorized resources (don't leak existence)

**Code Pattern:**
```clojure
(defn enforce-rls [db entity-id user-id]
  (when-let [doc (xt/entity db entity-id)]
    (when (= (:summary/user-id doc) user-id)
      doc)))

;; Usage in route handler
(if-let [summary (enforce-rls db summary-id user-id)]
  ;; proceed with operation
  {:status 404 :body {:error "Summary not found"}})
```

**RLS in Queries:**
```clojure
;; Always include user-id filter
(xt/q db
  '{:find [?s ?content]
    :where [[?s :summary/user-id ?user-id]  ; RLS filter
            [?s :summary/content ?content]]
    :in [?user-id]}
  user-id)
```

---

### 6.3 Input Validation

**Defense Against:**
- Mass assignment attacks
- XSS via user-generated content
- CSV injection
- Invalid data types

**Validation Layers:**
1. **Schema Validation (Malli):** Validate structure and types
2. **Business Logic Validation:** Content length, date format, enum values
3. **Sanitization:** Trim whitespace, remove dangerous characters

**CSV Injection Prevention:**
```clojure
(defn sanitize-csv-field [field]
  (when field
    (let [trimmed (str/trim field)]
      ;; Prevent CSV formula injection
      (if (re-matches #"^[=+\-@].*" trimmed)
        (str "'" trimmed)  ; Escape with single quote
        trimmed))))
```

**Content Validation:**
```clojure
(defn validate-content [content]
  (let [trimmed (str/trim content)]
    (when-not (<= 50 (count trimmed) 50000)
      (throw (ex-info "Content must be 50-50,000 characters"
                      {:type :validation-error
                       :field :content
                       :length (count trimmed)})))
    trimmed))
```

---

### 6.4 CSRF Protection

**Mechanism:** Biff's built-in CSRF token handling

**Implementation:**
- All POST/PATCH/DELETE requests require valid CSRF token
- Token automatically validated by Biff middleware
- No additional code needed (handled by framework)

---

### 6.5 Rate Limiting

**CSV Import Rate Limiting:**
- Max 5 imports per hour per user
- Max 1000 rows per day per user
- Return 429 Too Many Requests if exceeded
- Include rate limit headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

**Implementation:**
```clojure
(defn check-csv-import-rate-limit [db user-id]
  (let [one-hour-ago (-> (java.time.Instant/now)
                         (.minus 1 java.time.temporal.ChronoUnit/HOURS))
        recent-imports (xt/q db
                         '{:find [(count ?g)]
                           :where [[?g :generation/user-id ?user-id]
                                   [?g :generation/created-at ?created]
                                   [(> ?created ?cutoff)]]
                           :in [?user-id ?cutoff]}
                         user-id one-hour-ago)]
    (when (>= (ffirst recent-imports) 5)
      (throw (ex-info "Rate limit exceeded"
                      {:type :rate-limit-exceeded
                       :reset-at (.plus one-hour-ago 1 java.time.temporal.ChronoUnit/HOURS)})))))
```

**General API Rate Limiting:**
- 100 requests per minute per user (configurable)
- Implemented at middleware level

---

### 6.6 Security Headers (CORS, CSP)

**Configuration:**
```clojure
;; In Biff config or middleware
{:cors {:allowed-origins ["https://apriary.example.com"]}
 :security-headers {:content-security-policy "default-src 'self'; script-src 'self'"
                    :x-content-type-options "nosniff"
                    :x-frame-options "DENY"
                    :strict-transport-security "max-age=31536000; includeSubDomains"}}
```

---

### 6.7 Secrets Management

**OpenRouter API Key (for future production integration):**
- **NOTE**: Not required for MVP (using mocked service)
- For production: Store in environment variable: `OPENROUTER_API_KEY`
- Never log API key
- Access via `(System/getenv "OPENROUTER_API_KEY")`
- Validate presence at startup when `mock?` is false

---

## 7. Error Handling

### 7.1 Error Response Format

**Standard Error Structure:**
```json
{
  "error": "Human-readable error message",
  "code": "ERROR_CODE_CONSTANT",
  "details": {
    "field": "value",
    "reason": "explanation"
  },
  "timestamp": "2025-11-23T12:00:00Z"
}
```

### 7.2 Error Scenarios by Endpoint

#### List User Summaries (GET /api/summaries)

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Invalid sort-by value | 400 | INVALID_PARAMETER | "sort-by must be one of: created-at, hive-number, source" |
| Invalid sort-order | 400 | INVALID_PARAMETER | "sort-order must be 'asc' or 'desc'" |
| Invalid limit range | 400 | INVALID_PARAMETER | "limit must be between 1 and 100" |
| Negative offset | 400 | INVALID_PARAMETER | "offset must be non-negative" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to retrieve summaries" |

#### Create Manual Summary (POST /api/summaries)

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Missing content | 400 | VALIDATION_ERROR | "content is required" |
| Content too short | 400 | VALIDATION_ERROR | "content must be at least 50 characters" |
| Content too long | 400 | VALIDATION_ERROR | "content must not exceed 50,000 characters" |
| Invalid date format | 400 | VALIDATION_ERROR | "observation-date must be in DD-MM-YYYY format" |
| Invalid date value | 400 | VALIDATION_ERROR | "observation-date is not a valid date" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to create summary" |

#### Get Single Summary (GET /api/summaries/{id})

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Invalid UUID format | 400 | INVALID_REQUEST | "Invalid summary ID format" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| User doesn't own | 403 | FORBIDDEN | "Access denied" |
| Summary not found | 404 | NOT_FOUND | "Summary not found" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to retrieve summary" |

#### Update Summary (PATCH /api/summaries/{id})

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Empty request body | 400 | INVALID_REQUEST | "At least one field must be provided" |
| Content too short | 400 | VALIDATION_ERROR | "content must be at least 50 characters" |
| Content too long | 400 | VALIDATION_ERROR | "content must not exceed 50,000 characters" |
| Invalid date format | 400 | VALIDATION_ERROR | "observation-date must be in DD-MM-YYYY format" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| User doesn't own | 403 | FORBIDDEN | "Access denied" |
| Summary not found | 404 | NOT_FOUND | "Summary not found" |
| Concurrent modification | 409 | CONFLICT | "Summary was modified by another request" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to update summary" |

#### Delete Summary (DELETE /api/summaries/{id})

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Invalid UUID | 400 | INVALID_REQUEST | "Invalid summary ID format" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| User doesn't own | 403 | FORBIDDEN | "Access denied" |
| Summary not found | 404 | NOT_FOUND | "Summary not found" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to delete summary" |

#### Accept Summary (POST /api/summaries/{id}/accept)

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Manual summary | 400 | INVALID_OPERATION | "Cannot accept manual summaries" |
| Already accepted | 409 | CONFLICT | "Summary already accepted" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| User doesn't own | 403 | FORBIDDEN | "Access denied" |
| Summary not found | 404 | NOT_FOUND | "Summary not found" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to accept summary" |

#### Bulk Accept (POST /api/summaries/generation/accept)

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Missing generation-id | 400 | INVALID_REQUEST | "generation-id is required" |
| Invalid UUID | 400 | INVALID_REQUEST | "Invalid generation-id format" |
| No summaries found | 400 | INVALID_OPERATION | "No summaries found for this generation" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| User doesn't own | 403 | FORBIDDEN | "Access denied" |
| Generation not found | 404 | NOT_FOUND | "Generation not found" |
| Database failure | 500 | INTERNAL_ERROR | "Failed to accept summaries" |

#### CSV Import (POST /api/summaries/import)

| Scenario | Status | Code | Message |
|----------|--------|------|---------|
| Missing csv field | 400 | INVALID_REQUEST | "csv field is required" |
| Empty CSV | 400 | INVALID_REQUEST | "CSV data cannot be empty" |
| Invalid encoding | 400 | INVALID_REQUEST | "CSV must be UTF-8 encoded" |
| Wrong delimiter | 400 | INVALID_REQUEST | "CSV must use semicolon (;) delimiter" |
| Missing observation column | 400 | INVALID_REQUEST | "CSV must have 'observation' column" |
| All rows invalid | 400 | VALIDATION_ERROR | "All CSV rows failed validation" |
| No session | 401 | UNAUTHORIZED | "Authentication required" |
| Rate limit exceeded | 429 | RATE_LIMIT_EXCEEDED | "CSV import limit exceeded. Try again in X minutes." |
| CSV parsing error | 500 | INTERNAL_ERROR | "Failed to parse CSV data" |
| OpenRouter API error | 500 | EXTERNAL_SERVICE_ERROR | "Failed to generate summaries" |
| OpenRouter unavailable | 503 | SERVICE_UNAVAILABLE | "AI service temporarily unavailable" |

---

### 7.3 Error Handling Implementation

**Guard Clause Pattern:**
```clojure
(defn create-manual-summary [request]
  ;; Guard clause: authentication
  (when-not (:user-id request)
    (throw (ex-info "Unauthorized" {:status 401 :code "UNAUTHORIZED"})))

  ;; Guard clause: validation
  (let [body (:body request)
        content (get body :content)]
    (when-not content
      (throw (ex-info "content is required"
                      {:status 400 :code "VALIDATION_ERROR" :field :content})))

    (let [trimmed (str/trim content)]
      (when-not (<= 50 (count trimmed) 50000)
        (throw (ex-info "content length invalid"
                        {:status 400 :code "VALIDATION_ERROR"
                         :field :content :length (count trimmed)})))

      ;; Happy path
      (summary-service/create-summary ...))))
```

**Error Response Builder:**
```clojure
(defn build-error-response [ex]
  (let [data (ex-data ex)]
    {:status (or (:status data) 500)
     :body {:error (.getMessage ex)
            :code (or (:code data) "INTERNAL_ERROR")
            :details (dissoc data :status :code)
            :timestamp (str (java.time.Instant/now))}}))
```

**Centralized Error Handler:**
```clojure
(defn wrap-error-handler [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (let [response (build-error-response ex)]
          (log/error ex "Request failed"
                     {:user-id (:user-id request)
                      :uri (:uri request)
                      :method (:request-method request)})
          response)))))
```

---

## 8. Performance Considerations

### 8.1 Database Query Optimization

**Indexes:**
```clojure
;; Recommended XTDB indexes (if supported in XTDB 1.24)
;; - Composite index on (summary/user-id, summary/created-at)
;; - Index on summary/generation-id
;; - Index on generation/user-id
```

**Query Patterns:**
- Use `xt/q` for filtering and sorting (avoid loading all docs then filtering in memory)
- Leverage XTDB's datalog for efficient joins
- Avoid N+1 queries (fetch related entities in single query)

**Pagination:**
- Default limit: 50
- Max limit: 100
- Use offset-based pagination for MVP (cursor-based for future)

---

### 8.2 CSV Import Optimization

**Batch Processing:**
- Send all valid observations to OpenRouter in single batch request
- Use XTDB's batch transaction for creating multiple summaries
- Process CSV parsing in streaming fashion for large files (future optimization)

**Async Processing (Future):**
- For MVP: synchronous processing (201 response after completion)
- For production: use background job queue (202 response immediately, poll for status)

---

### 8.3 Response Size Management

**List Endpoint:**
- Paginate results (default 50, max 100)
- Consider truncating `content` field in list view (e.g., first 200 chars)
- Add `include_content=false` query param for ultra-fast list retrieval (future)

**CSV Import Response:**
- For large imports, consider streaming response
- Limit summaries array in response (e.g., first 20, provide link to full list)

---

### 8.4 Caching Strategy (Future)

**Not in MVP, but consider:**
- Cache user's summary count for metrics
- Cache generation statistics
- Use ETags for conditional GET requests

---

## 9. Implementation Steps

### Step 1: Update Database Schema

**File:** `src/com/apriary/schema.clj`

1. Add Generation entity schema to existing schema map:
```clojure
:generation/id :uuid
:generation
[:map {:closed true}
 [:xt/id :uuid]
 [:generation/id :uuid]
 [:generation/user-id :uuid]
 [:generation/model :string]
 [:generation/generated-count [:int {:min 0}]]
 [:generation/accepted-unedited-count [:int {:min 0}]]
 [:generation/accepted-edited-count [:int {:min 0}]]
 [:generation/duration-ms [:int {:min 0}]]
 [:generation/created-at inst?]
 [:generation/updated-at inst?]]
```

2. Add Summary entity schema:
```clojure
:summary/id :uuid
:summary
[:map {:closed true}
 [:xt/id :uuid]
 [:summary/id :uuid]
 [:summary/user-id :uuid]
 [:summary/generation-id {:optional true} [:maybe :uuid]]
 [:summary/source [:enum :ai-full :ai-partial :manual]]
 [:summary/created-at inst?]
 [:summary/updated-at inst?]
 [:summary/hive-number {:optional true} [:maybe :string]]
 [:summary/observation-date {:optional true} [:maybe :string]]
 [:summary/special-feature {:optional true} [:maybe :string]]
 [:summary/content :string]]
```

---

### Step 2: Create API Schemas

**File:** `src/com/apriary/schema/api.clj` (new file)

1. Define all request/response Malli schemas as shown in section 3.1
2. Export schemas for use in route handlers

---

### Step 3: Create Service Layer

#### 3.1 Summary Service

**File:** `src/com/apriary/services/summary_service.clj` (new file)

Implement functions:
- `list-summaries [db user-id query-params]`
- `get-summary-by-id [db summary-id user-id]`
- `create-manual-summary [db user-id summary-data]`
- `update-summary [db summary-id user-id updates]`
- `delete-summary [db summary-id user-id]`
- `accept-summary [db summary-id user-id]`

Each function should:
- Enforce RLS
- Use guard clauses for validation
- Return domain models (not DTOs)
- Throw `ex-info` with structured error data

#### 3.2 Generation Service

**File:** `src/com/apriary/services/generation_service.clj` (new file)

Implement functions:
- `create-generation [db user-id generation-data]`
- `get-generation-by-id [db generation-id user-id]`
- `update-generation-counters [db generation-id counter-updates]`
- `bulk-accept-summaries [db generation-id user-id]`

#### 3.3 CSV Import Service

**File:** `src/com/apriary/services/csv_import_service.clj` (new file)

Implement functions:
- `parse-csv-string [csv-string]` → returns `{:headers [...] :rows [...]}`
- `validate-csv-row [row row-number]` → returns `{:valid? true/false :errors [...]}`
- `process-csv-import [csv-string]` → returns `{:valid-rows [...] :rejected-rows [...]}`

#### 3.4 OpenRouter Service (MOCKED for MVP)

**File:** `src/com/apriary/services/openrouter_service.clj` (new file)

**NOTE**: For initial implementation, this service will return mocked responses.

Implement functions:
- `generate-summaries-batch [observations model]` → **MOCKED**: returns observations as summaries
  - For now: return each observation text as-is as the generated summary
  - Implementation details will be provided during development
  - Proper OpenRouter API integration will be added later
- `parse-openrouter-response [response]` → extracts summaries from response (not needed for mock)

Mock implementation pattern:
```clojure
(defn generate-summaries-batch [observations model]
  ;; Mock: return observations as summaries
  (map (fn [obs] {:content (:observation obs)}) observations))
```

Future: Use HTTP client (e.g., `clj-http` or `http-kit`) to call actual OpenRouter API.

#### 3.5 Validation Service

**File:** `src/com/apriary/services/validation_service.clj` (new file)

Implement functions:
- `validate-content [content]` → validates length after trim
- `validate-observation-date [date-str]` → validates DD-MM-YYYY format
- `validate-uuid [uuid-str]` → parses and validates UUID
- `sanitize-csv-field [field]` → prevents CSV injection

---

### Step 4: Create Route Handlers

**File:** `src/com/apriary/routes/summaries.clj` (new file)

Implement route handlers for all 8 endpoints:

1. **GET /api/summaries**
   - Extract query params
   - Validate with `list-summaries-query-schema`
   - Call `summary-service/list-summaries`
   - Transform results to DTOs
   - Return 200 with list response

2. **POST /api/summaries**
   - Parse JSON body
   - Validate with `create-manual-summary-schema`
   - Call `summary-service/create-manual-summary`
   - Transform to DTO
   - Return 201

3. **GET /api/summaries/:id**
   - Parse UUID from path
   - Call `summary-service/get-summary-by-id`
   - Transform to DTO
   - Return 200

4. **PATCH /api/summaries/:id**
   - Parse UUID from path
   - Parse JSON body
   - Validate with `update-summary-schema`
   - Call `summary-service/update-summary`
   - Transform to DTO
   - Return 200

5. **DELETE /api/summaries/:id**
   - Parse UUID from path
   - Call `summary-service/delete-summary`
   - Return 204 or 200

6. **POST /api/summaries/:id/accept**
   - Parse UUID from path
   - Call `summary-service/accept-summary`
   - Transform to DTO
   - Return 200

7. **POST /api/summaries/generation/accept**
   - Parse JSON body
   - Validate with `bulk-accept-schema`
   - Call `generation-service/bulk-accept-summaries`
   - Return 200 with response

8. **POST /api/summaries/import**
   - Parse JSON body
   - Validate with `csv-import-schema`
   - Check rate limit
   - Call `csv-import-service/process-csv-import`
   - Call `openrouter-service/generate-summaries-batch`
   - Call `generation-service/create-generation`
   - Call `summary-service/create-manual-summary` for each AI proposal
   - Return 201 with import response

---

### Step 5: Register Routes

**File:** `src/com/apriary/middleware.clj` (existing file)

Add summary routes to Biff route configuration:

```clojure
(def routes
  [["/api/summaries" {:middleware [require-auth wrap-csrf]}
    ["" {:get list-summaries
         :post create-manual-summary}]
    ["/:id" {:get get-single-summary
             :patch update-summary
             :delete delete-summary}]
    ["/:id/accept" {:post accept-summary}]
    ["/generation/accept" {:post bulk-accept-summaries}]
    ["/import" {:post import-csv-summaries}]]])
```

---

### Step 6: Implement Middleware

**File:** `src/com/apriary/middleware.clj`

1. **Authentication Middleware** (may already exist):
```clojure
(defn require-auth [handler]
  (fn [request]
    (if-let [user-id (get-in request [:session :user-id])]
      (handler (assoc request :user-id user-id))
      {:status 401
       :body {:error "Authentication required"
              :code "UNAUTHORIZED"}})))
```

2. **Error Handler Middleware:**
```clojure
(defn wrap-error-handler [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (build-error-response ex)))))
```

3. **Rate Limiting Middleware:**
```clojure
(defn wrap-rate-limit [handler]
  (fn [request]
    (when (= (:uri request) "/api/summaries/import")
      (check-csv-import-rate-limit (:db request) (:user-id request)))
    (handler request)))
```

---

### Step 7: Create DTO Transformation Utilities

**File:** `src/com/apriary/utils/transformers.clj` (new file)

**NOTE**: We use kebab-case throughout (Clojure convention), so transformation is minimal - just removing namespaces and converting types for JSON serialization.

Implement functions:
- `domain->dto [entity]` → removes namespace prefixes from XTDB documents
- `dto->domain [dto]` → adds namespace prefixes for XTDB documents
- `instant->iso-string [instant]` → formats timestamps for JSON
- `keyword->string [keyword]` → converts `:ai-full` to `"ai-full"`

Example:
```clojure
(defn summary->dto [summary]
  {:id (:summary/id summary)
   :user-id (:summary/user-id summary)
   :generation-id (:summary/generation-id summary)
   :source (name (:summary/source summary))
   :hive-number (:summary/hive-number summary)
   :observation-date (:summary/observation-date summary)
   :special-feature (:summary/special-feature summary)
   :content (:summary/content summary)
   :created-at (instant->iso-string (:summary/created-at summary))
   :updated-at (instant->iso-string (:summary/updated-at summary))})
```

---

### Step 8: Configure OpenRouter Integration (Optional for MVP)

**File:** `config/config.edn` (or environment variables)

**NOTE**: For MVP with mocked OpenRouter service, minimal configuration is needed.

Add configuration:
```clojure
{:openrouter {:model "gpt-4-turbo"  ; used for generation metadata only
              :mock? true}}          ; enable mock mode for MVP
```

**For future production integration:**
```clojure
{:openrouter {:api-key #env OPENROUTER_API_KEY
              :model "gpt-4-turbo"
              :endpoint "https://openrouter.ai/api/v1/chat/completions"
              :mock? false}}
```

**Validation (for production only):**
```clojure
(when (and (not (get-in config [:openrouter :mock?]))
           (not (System/getenv "OPENROUTER_API_KEY")))
  (throw (ex-info "OPENROUTER_API_KEY environment variable required when mock? is false" {})))
```

---

### Step 9: Write Unit Tests

**Files:** `test/com/apriary/services/*_test.clj`

Test coverage for:
1. **Summary Service Tests:**
   - List summaries with various query params
   - Create manual summary with valid/invalid data
   - Get summary by ID (found, not found, wrong user)
   - Update summary (metadata, content, source transitions)
   - Delete summary (success, not found, wrong user)
   - Accept summary (success, manual summary rejection, already accepted)

2. **Generation Service Tests:**
   - Create generation
   - Update counters
   - Bulk accept (various scenarios)

3. **CSV Import Service Tests:**
   - Parse valid CSV
   - Reject invalid CSV (encoding, delimiter, missing columns)
   - Validate rows (too short, too long, invalid dates)
   - Handle mixed valid/invalid rows

4. **Validation Service Tests:**
   - Content validation (edge cases: exactly 50, exactly 50000, trim behavior)
   - Date validation (valid dates, invalid formats, invalid dates like 32-01-2025)
   - UUID parsing

5. **OpenRouter Service Tests (Mocked):**
   - Test mock returns observations as summaries
   - Verify correct data structure returned
   - Future: Test error handling for real API (API failure, timeout, invalid response)

---

### Step 10: Write Integration Tests

**Files:** `test/com/apriary/integration/*_test.clj`

Test full request/response cycles:
1. End-to-end CSV import workflow
2. Summary CRUD workflow
3. Accept workflow (individual and bulk)
4. RLS enforcement (cross-user access attempts)
5. Rate limiting
6. Concurrent modification handling

Use test fixtures to:
- Set up test database
- Create test users
- Seed test data
- Clean up after tests

---

### Step 11: Add Logging

**File:** All service files

Add structured logging:
```clojure
(require '[clojure.tools.logging :as log])

(log/info "Creating manual summary" {:user-id user-id :hive-number hive-number})
(log/error ex "Failed to create summary" {:user-id user-id :error-type (:type (ex-data ex))})
(log/warn "CSV row rejected" {:row-number row-num :reason reason})
```

---

### Step 12: Documentation

1. **API Documentation:**
   - Create OpenAPI/Swagger spec from api-plan.md
   - Generate interactive API explorer (optional)

2. **Code Documentation:**
   - Add docstrings to all public functions
   - Document complex business logic
   - Add examples in doc comments

3. **Developer Guide:**
   - Setup instructions
   - Environment variables required
   - How to run tests
   - How to test with OpenRouter (mock vs real API)

---

### Step 13: Manual Testing

1. **Postman/Insomnia Collection:**
   - Create collection with all 8 endpoints
   - Include example requests
   - Include authentication setup

2. **Test Scenarios:**
   - Happy path for each endpoint
   - Error scenarios (invalid input, unauthorized access, etc.)
   - CSV import with various CSV formats
   - Rate limiting behavior
   - Concurrent requests

---

### Step 14: Deployment Preparation

1. **Environment Variables:**
   - Document all required env vars
   - For MVP: No environment variables required (using mocked OpenRouter)
   - For future production: Set up production secrets (OPENROUTER_API_KEY)

2. **Database Migration:**
   - No explicit migration needed (XTDB is schemaless)
   - Verify schema validation works in production

3. **Monitoring:**
   - Set up error tracking (e.g., Sentry)
   - Add metrics for API usage
   - Monitor OpenRouter API usage and costs (for future production integration)

4. **Rate Limiting Configuration:**
   - Configure rate limits based on expected load
   - Set up alerts for rate limit violations

---

## Summary

This implementation plan provides comprehensive guidance for building 8 REST API endpoints for the Apriary Summary application. The plan follows Biff/Clojure best practices with:

✅ **Row-Level Security** enforced at every layer
✅ **Guard clause pattern** for early error handling
✅ **Malli schemas** for validation
✅ **Service layer separation** for business logic
✅ **Comprehensive error handling** with proper status codes
✅ **Security considerations** (CSRF, rate limiting, input sanitization)
✅ **Performance optimizations** (pagination, batch processing)
✅ **Test coverage** plan
✅ **Clear data flow** documentation
✅ **Mocked OpenRouter service** for MVP (real integration later)

**Important MVP Note:** The OpenRouter API integration is mocked for initial implementation. The mock service returns observation text as-is for generated summaries. Implementation details for the mock will be provided during development, and proper OpenRouter integration will be added in a future iteration.

The implementation should be done incrementally, starting with the core CRUD operations (Steps 1-7), then adding the more complex CSV import functionality with mocked AI generation (Step 8), followed by testing and deployment preparation.
