# API Endpoint Implementation Plan: Generations Entity

## 1. Endpoint Overview

The Generations API provides comprehensive endpoints to manage OpenRouter API request batches and track AI-generated summary acceptance metrics. The generations entity serves as the central hub for:

- **Audit Trail**: Recording each batch of AI-generated summaries with metadata (model used, duration, creation time)
- **Metrics Tracking**: Tracking acceptance counters (unedited vs edited) to measure AI quality and user adoption rates
- **Batch Operations**: Enabling bulk acceptance of all summaries within a generation batch

Generations are created automatically when CSV import triggers AI batch generation and can be queried for listing, detailed retrieval, and acceptance tracking. The endpoint enforces Row-Level Security (RLS) to prevent cross-user data access.

## 2. Request Details

### 2.1 List Generations for Authenticated User
- **HTTP Method**: `GET`
- **URL Structure**: `/api/generations`
- **Authentication**: Required (session-based)
- **Parameters**:
  - **Query Parameters (Optional)**:
    - `sort_by` (default: `created_at`): Field to sort by. Allowed values: `created_at`, `model`, `generated_count`, `accepted_count`
    - `sort_order` (default: `desc`): Sort direction. Allowed values: `asc`, `desc`
    - `model` (optional): Filter by AI model (e.g., "gpt-4-turbo", "claude-3-sonnet")
    - `limit` (default: `50`, range: 1-100): Maximum number of results to return
    - `offset` (default: `0`): Number of results to skip for pagination
- **Request Payload**: None
- **Content-Type**: `application/json`

### 2.2 Get Single Generation Details
- **HTTP Method**: `GET`
- **URL Structure**: `/api/generations/{id}`
- **Authentication**: Required (session-based)
- **Parameters**:
  - **Path Parameters**:
    - `id` (required): UUID of the generation to retrieve
- **Request Payload**: None
- **Content-Type**: `application/json`

### 2.3 Implicit: Create Generation (via CSV Import)
- **HTTP Method**: `POST`
- **URL Structure**: `/api/summaries/import`
- **Description**: Generation record is created automatically as part of CSV import workflow
- **Triggers Generation Record Creation** with:
  - Unique `generation/id` (UUID)
  - `generation/model` (from application configuration)
  - `generation/generated-count` (count of valid CSV rows)
  - Initial counters: `accepted-unedited-count: 0`, `accepted-edited-count: 0`
  - `duration-ms` (calculated from API request time)
  - Timestamps: `created-at` and `updated-at` (set to current time)

## 3. Response Details

### 3.1 Generation Response Object (DTO)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "model": "gpt-4-turbo",
  "generated_count": 14,
  "accepted_unedited_count": 8,
  "accepted_edited_count": 6,
  "total_accepted_count": 14,
  "acceptance_rate": 100.0,
  "duration_ms": 2145,
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T11:15:00Z"
}
```

### 3.2 List Generations Response (200 OK)
```json
{
  "generations": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "user_id": "550e8400-e29b-41d4-a716-446655440000",
      "model": "gpt-4-turbo",
      "generated_count": 14,
      "accepted_unedited_count": 8,
      "accepted_edited_count": 6,
      "total_accepted_count": 14,
      "acceptance_rate": 100.0,
      "duration_ms": 2145,
      "created_at": "2025-11-23T10:30:00Z",
      "updated_at": "2025-11-23T11:15:00Z"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440003",
      "user_id": "550e8400-e29b-41d4-a716-446655440000",
      "model": "claude-3-sonnet",
      "generated_count": 10,
      "accepted_unedited_count": 7,
      "accepted_edited_count": 2,
      "total_accepted_count": 9,
      "acceptance_rate": 90.0,
      "duration_ms": 1875,
      "created_at": "2025-11-22T14:20:00Z",
      "updated_at": "2025-11-22T14:25:00Z"
    }
  ],
  "total_count": 2,
  "limit": 50,
  "offset": 0
}
```

### 3.3 Get Single Generation Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "model": "gpt-4-turbo",
  "generated_count": 14,
  "accepted_unedited_count": 8,
  "accepted_edited_count": 6,
  "total_accepted_count": 14,
  "acceptance_rate": 100.0,
  "duration_ms": 2145,
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T11:15:00Z"
}
```

### 3.4 Error Response Format (400, 401, 403, 404, 500)
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

### 3.5 Status Codes Summary

| Status Code | Scenario |
|------------|----------|
| **200 OK** | Successful GET request for single generation or list |
| **400 Bad Request** | Invalid query parameters, malformed UUID, missing required fields |
| **401 Unauthorized** | User not authenticated or session expired |
| **403 Forbidden** | User attempting to access another user's generation (RLS violation) |
| **404 Not Found** | Generation with specified ID does not exist |
| **500 Internal Server Error** | Database query failure, unexpected server error |

## 4. Data Flow

### 4.1 Generation Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. USER SUBMITS CSV IMPORT REQUEST                              │
│    POST /api/summaries/import with CSV data                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. VALIDATE AND PARSE CSV                                       │
│    - Parse CSV string with semicolon delimiter                  │
│    - Validate headers (must have 'observation' column)           │
│    - Validate each row (observation text 50-10,000 chars)       │
│    - Separate valid rows from rejected rows                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. CREATE GENERATION RECORD                                     │
│    - Generate unique generation/id (UUID)                       │
│    - Set generation/user-id from session                        │
│    - Set generation/model from application config               │
│    - Set generation/generated-count = number of valid rows      │
│    - Set counters to 0: accepted-unedited, accepted-edited      │
│    - Record start time, end time, calculate duration-ms         │
│    - Set created-at and updated-at to now                       │
│    - Persist to XTDB                                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. SEND TO OPENROUTER API                                       │
│    - Batch request with valid observations                      │
│    - Record response time for duration-ms calculation           │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. CREATE SUMMARY RECORDS                                       │
│    - For each AI proposal, create summary/generation-id ref     │
│    - Link each summary to generation via generation/id          │
│    - Set summary/source = :ai-full initially                    │
│    - Persist all summaries to XTDB                              │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. RETURN IMPORT RESPONSE WITH GENERATION ID                    │
│    - 201 Created or 202 Accepted (depending on sync/async)      │
│    - Include generation_id for reference                        │
│    - Return created summaries in response body                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 GET /api/generations Request Flow

```
CLIENT REQUEST
    │
    ├─ GET /api/generations?sort_by=created_at&limit=50
    │
    ▼
AUTHENTICATION MIDDLEWARE
    ├─ Verify session cookie exists
    ├─ Extract user-id from session
    └─ Return 401 if not authenticated
    │
    ▼
VALIDATION LAYER
    ├─ Validate sort_by ∈ {created_at, model, generated_count, accepted_count}
    ├─ Validate sort_order ∈ {asc, desc}
    ├─ Validate limit ∈ [1, 100]
    ├─ Validate offset ≥ 0
    └─ Return 400 if validation fails
    │
    ▼
DATABASE QUERY
    ├─ Query: Find all generations where user-id = authenticated-user-id
    ├─ Filter by model (if provided)
    ├─ Sort by requested field and order
    ├─ Calculate total count
    ├─ Apply limit and offset for pagination
    └─ Fetch results from XTDB
    │
    ▼
RESPONSE TRANSFORMATION
    ├─ Convert XTDB entities to DTO format
    ├─ Calculate computed fields (total_accepted_count, acceptance_rate)
    ├─ Format timestamps as ISO-8601
    └─ Build response envelope
    │
    ▼
RESPONSE RETURN (200 OK)
    └─ Return list of generations with pagination metadata
```

### 4.3 GET /api/generations/{id} Request Flow

```
CLIENT REQUEST
    │
    ├─ GET /api/generations/550e8400-e29b-41d4-a716-446655440002
    │
    ▼
AUTHENTICATION MIDDLEWARE
    ├─ Verify session exists
    ├─ Extract user-id
    └─ Return 401 if not authenticated
    │
    ▼
VALIDATION LAYER
    ├─ Parse and validate UUID format of {id}
    └─ Return 400 if invalid UUID format
    │
    ▼
DATABASE QUERY
    ├─ Query: Fetch generation by id from XTDB
    ├─ Return empty if not found
    └─ Proceed to RLS check if found
    │
    ▼
ROW-LEVEL SECURITY CHECK
    ├─ Verify generation.user-id == authenticated-user-id
    ├─ Return 403 Forbidden if mismatch
    └─ Proceed if match (or return 404 if not found, don't leak existence)
    │
    ▼
RESPONSE TRANSFORMATION
    ├─ Convert XTDB entity to DTO format
    ├─ Calculate computed fields (total_accepted_count, acceptance_rate)
    ├─ Format timestamps as ISO-8601
    └─ Build response object
    │
    ▼
RESPONSE RETURN (200 OK)
    └─ Return single generation object
```

### 4.4 POST /api/summaries/generation/accept Request Flow

```
CLIENT REQUEST
    │
    ├─ POST /api/summaries/generation/accept
    ├─ Body: { "generation-id": "550e8400-e29b-41d4-a716-446655440002" }
    │
    ▼
AUTHENTICATION MIDDLEWARE
    ├─ Verify session exists
    ├─ Extract user-id
    └─ Return 401 if not authenticated
    │
    ▼
VALIDATION LAYER
    ├─ Parse request body JSON
    ├─ Validate generation-id field exists and is valid UUID
    └─ Return 400 if validation fails
    │
    ▼
DATABASE QUERY - FETCH GENERATION
    ├─ Query: Fetch generation by id
    ├─ Return 404 if not found
    └─ RLS check: Verify generation.user-id == user-id
    │
    ▼
DATABASE QUERY - FIND SUMMARIES
    ├─ Query: Find all summaries where:
    │   - summary.generation-id = provided generation-id
    │   - summary.user-id = authenticated user-id
    ├─ Group by source (:ai-full, :ai-partial, :manual)
    └─ Count summaries in each group
    │
    ▼
BUSINESS LOGIC - UPDATE COUNTERS
    ├─ Count of :ai-full summaries → increment accepted-unedited-count
    ├─ Count of :ai-partial summaries → increment accepted-edited-count
    ├─ Validate: total_unedited + total_edited ≤ generation.generated_count
    └─ If invalid, return 500 (data integrity error)
    │
    ▼
DATABASE UPDATE
    ├─ Update generation record:
    │   - generation/accepted-unedited-count += unedited_count
    │   - generation/accepted-edited-count += edited_count
    │   - generation/updated-at = now
    └─ Persist to XTDB
    │
    ▼
RESPONSE RETURN (200 OK)
    └─ Return bulk accept response with counters and summary counts
```

### 4.5 Data Consistency Guarantees

**Transaction Semantics**:
- Generation creation and summary creation as part of CSV import should be atomic
- Counter updates on acceptance should be atomic (update generation in single transaction)
- XTDB provides ACID guarantees for document operations

**Temporal Ordering**:
- `updated-at ≥ created-at` is always true
- `created-at` never changes after record creation
- `updated-at` updates with each counter increment

## 5. Security Considerations

### 5.1 Authentication

**Requirement**: All generation endpoints require valid authenticated session
- **Mechanism**: Session-based authentication via Biff framework
- **Enforcement**: Middleware intercepts all `/api/generations` requests before handler execution
- **Session Validation**: Verify session cookie exists, contains valid user-id, and is not expired
- **Error Handling**: Return 401 Unauthorized if session missing or invalid

**Implementation Pattern** (Biff middleware):
```clojure
(defn require-auth [handler]
  (fn [request]
    (if-let [user-id (get-in request [:session :user-id])]
      (handler request)
      {:status 401
       :body {:error "Unauthorized" :code "UNAUTHORIZED"}})))
```

### 5.2 Authorization - Row-Level Security (RLS)

**Principle**: Users can only access their own generations; no cross-user data visibility

**Implementation Rules**:
1. **Query Filtering**: All database queries must include `where user-id = authenticated-user-id`
2. **Access Control**: Before returning any generation, verify `generation/user-id == request.user-id`
3. **Error Response**: Return 403 Forbidden for mismatch; return 404 Not Found if resource doesn't exist (don't leak that resource exists to non-owners)

**Protected Operations**:
- **GET /api/generations**: Automatically filtered by user-id in query
- **GET /api/generations/{id}**: Check user-id before returning 200; return 403 or 404 on mismatch
- **POST /api/summaries/generation/accept**: Verify generation belongs to user; also verify all summaries belong to user

**Example RLS Check**:
```clojure
(defn get-generation [gen-id user-id]
  (let [gen (xt/entity db gen-id)]
    (cond
      (nil? gen) {:status 404 :error "Not found"}
      (not= (:generation/user-id gen) user-id) {:status 403 :error "Forbidden"}
      :else {:status 200 :body gen})))
```

### 5.3 Rate Limiting

**Purpose**: Prevent API quota exhaustion from excessive CSV imports

**Recommended Limits**:
- **CSV Imports**: Maximum 5 imports per hour per user
- **Generation Queries**: Maximum 100 requests per minute per user (standard API limit)

**Implementation Strategy**:
- Use in-memory or Redis-backed rate limiter in middleware
- Return 429 Too Many Requests when limit exceeded
- Include rate limit headers in response:
  - `X-RateLimit-Limit`: Request limit for the window
  - `X-RateLimit-Remaining`: Requests remaining in current window
  - `X-RateLimit-Reset`: Unix timestamp when window resets

**Rate Limit Storage**:
- In-memory rate limiter (simple): Use atom/map with cleanup
- Redis (scalable): Store `user-id:timestamp` keys with TTL
- Database (XTDB): Store rate limit records with time windows

### 5.4 Input Validation

**UUID Validation**:
- All UUID parameters must validate using `parse-uuid` function
- Return 400 Bad Request for invalid format
- Pattern: `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`

**Query Parameter Validation**:
- **sort_by**: Whitelist validation against allowed fields
  - Allowed: `created_at`, `model`, `generated_count`, `accepted_count`
  - Return 400 if not in whitelist
- **sort_order**: Enum validation
  - Allowed: `asc`, `desc` (case-insensitive)
  - Return 400 if invalid
- **limit**: Integer range validation
  - Valid range: 1-100 (inclusive)
  - Return 400 if outside range
  - Use default 50 if omitted
- **offset**: Integer non-negative validation
  - Must be ≥ 0
  - Return 400 if negative
  - Use default 0 if omitted
- **model**: String filter (optional)
  - If provided, match against existing models in database
  - No length limits on filter string (database handles)

**Request Body Validation** (for bulk accept):
- Validate JSON can be parsed; return 400 if invalid JSON
- Validate `generation-id` field exists and is valid UUID
- No other fields expected in request body

### 5.5 CORS and Security Headers

**CORS Configuration**:
- Configure Biff middleware to allow requests only from Apriary domain
- Set `Access-Control-Allow-Origin` to https://apriary.example.com (not *)
- Allow credentials in CORS (session cookies)

**CSRF Protection**:
- Biff handles CSRF tokens automatically for POST requests
- Ensure CSRF token middleware is enabled
- POST /api/summaries/generation/accept must include valid CSRF token (or use header bypass for API)

**Security Headers**:
- `Content-Security-Policy`: Restrict script sources to same-origin
- `X-Content-Type-Options: nosniff`: Prevent MIME type sniffing
- `X-Frame-Options: DENY`: Prevent clickjacking
- `Strict-Transport-Security`: Force HTTPS (production only)

## 6. Error Handling

### 6.1 Error Response Format

All error responses must follow consistent format:

```json
{
  "error": "Human-readable error description",
  "code": "CONSTANT_ERROR_CODE",
  "details": {
    "field": "specific_field_name",
    "reason": "detailed_explanation"
  },
  "timestamp": "2025-11-23T12:00:00Z"
}
```

### 6.2 Error Scenarios and Responses

| HTTP Status | Error Code | Scenario | Response Message | Details |
|------------|-----------|----------|-----------------|---------|
| **400** | INVALID_REQUEST | Malformed JSON body | "Request body is not valid JSON" | Parse error location |
| **400** | INVALID_UUID | Invalid UUID format | "Invalid generation ID format" | `id: "not-a-uuid"` |
| **400** | INVALID_SORT_BY | sort_by not in allowed list | "Invalid sort_by field" | `reason: "allowed: created_at, model, generated_count, accepted_count"` |
| **400** | INVALID_SORT_ORDER | sort_order not asc/desc | "Invalid sort_order" | `reason: "must be 'asc' or 'desc'"` |
| **400** | INVALID_LIMIT | limit outside 1-100 range | "Invalid limit parameter" | `reason: "must be between 1 and 100"` |
| **400** | INVALID_OFFSET | offset is negative | "Invalid offset parameter" | `reason: "must be non-negative"` |
| **400** | MISSING_FIELD | Missing required field in body | "Missing required field: generation-id" | `field: "generation-id"` |
| **401** | UNAUTHORIZED | Session invalid or missing | "User not authenticated" | `reason: "session cookie missing or expired"` |
| **403** | FORBIDDEN | User accessing another user's generation | "You do not have permission to access this generation" | `reason: "RLS violation"` |
| **404** | NOT_FOUND | Generation ID doesn't exist | "Generation with ID {id} not found" | `id: "550e8400-..."` |
| **429** | RATE_LIMIT_EXCEEDED | Too many requests | "Rate limit exceeded for CSV imports" | `reset_time: "2025-11-23T12:30:00Z"` |
| **500** | INTERNAL_ERROR | Database query failure | "Failed to retrieve generation data" | `reason: "database connection timeout"` |
| **500** | DATA_INTEGRITY_ERROR | Counter validation failed | "Counter update failed validation" | `reason: "accepted_count > generated_count"` |

### 6.3 Error Logging

**Logging Strategy**:
- Use Biff's built-in logging (Timbre via SLF4J)
- Log all errors with appropriate severity levels:
  - **WARN**: Client errors (400, 401, 403, 404, 429) - informational
  - **ERROR**: Server errors (500) - actionable
  - **AUDIT**: All generation access for security audit trail

**Log Fields to Include**:
```clojure
{
  :timestamp "2025-11-23T12:00:00Z"
  :level "ERROR"
  :user-id "550e8400-e29b-41d4-a716-446655440000"
  :endpoint "/api/generations/{id}"
  :method "GET"
  :status 500
  :error-code "INTERNAL_ERROR"
  :error-message "Failed to retrieve generation data"
  :exception "com.xtdb.XTDBException: Connection timeout"
  :request-id "req-12345"
}
```

**Sensitive Data Handling**:
- Never log full generation content in error messages
- Do log generation IDs (UUIDs) for tracking
- Do log user IDs for audit purposes
- Don't log request/response bodies containing sensitive data

### 6.4 Error Recovery Strategies

**Rate Limiting (429)**:
- Client should wait until `X-RateLimit-Reset` timestamp before retrying
- Implement exponential backoff in client (1s, 2s, 4s, 8s)
- Return clear message with reset time

**Not Found (404)**:
- Don't provide suggestions (don't leak information)
- Return simple "not found" message
- Client should re-check ID, refresh data, or try again

**Forbidden (403)**:
- Don't explain why access denied (don't leak structure)
- Return simple "permission denied" message
- Log access attempt for security audit

**Server Error (500)**:
- Return generic message to client
- Log full error with stack trace for debugging
- Include request ID for tracing
- Consider fallback response format

## 7. Performance Considerations

### 7.1 Database Query Optimization

**Indexing Strategy**:
- Primary index on `generation/id` (XTDB automatic)
- Index on `(generation/user-id, generation/created-at desc)` for efficient user queries with sorting
- Index on `generation/user-id` for RLS filtering in bulk accept

**Query Patterns**:

```clojure
;; List generations for user (with sorting)
{:find '[?g ?model ?created ?count ?accepted]
 :where '[[?g :generation/user-id ?user-id]
          [?g :generation/model ?model]
          [?g :generation/created-at ?created]
          [?g :generation/generated-count ?count]
          [?g :generation/accepted-unedited-count ?unedited]
          [?g :generation/accepted-edited-count ?edited]
          [(+ ?unedited ?edited) ?accepted]]
 :order-by [[:created-at :desc]]}

;; Get single generation by ID
(xt/entity db generation-id)

;; Find summaries for bulk accept (with grouping)
{:find '[?s (count ?s) ?source]
 :where '[[?s :summary/generation-id ?gen-id]
          [?s :summary/user-id ?user-id]
          [?s :summary/source ?source]]
 :group-by [?source]}
```

**N+1 Query Prevention**:
- Avoid loading summaries when only generation data needed
- Use pull queries efficiently in Biff (single entity fetch)
- Cache generation list results if frequently accessed

### 7.2 Response Payload Optimization

**List Response Size**:
- Default limit: 50 generations per page
- Maximum limit: 100 (enforced in validation)
- Include pagination metadata (`total_count`, `limit`, `offset`)
- Don't include full summary list in generation response (keep separate endpoint)

**Computed Fields**:
- `total_accepted_count`: Calculated from `accepted-unedited-count + accepted-edited-count`
- `acceptance_rate`: Calculated as `(total_accepted_count / generated_count) * 100`
- Calculate on-demand (not stored) to ensure accuracy

**Response Compression**:
- Enable gzip compression for JSON responses (Biff middleware)
- Generation list with 100 items ~20-30KB → ~2-3KB compressed

### 7.3 Bulk Accept Performance

**Potential Bottleneck**: Finding all summaries for a generation with large batch sizes

**Optimization Strategy**:
- Use single query with grouping (not multiple queries)
- Fetch counts by source type directly from query result
- Update generation counter once per request (atomic)
- Example: Generation with 1000 summaries
  - Query time: ~50-100ms (with proper indexing)
  - Update time: ~10-20ms
  - Total: ~100-150ms

**Scalability**:
- If generation batches grow beyond 10,000 summaries:
  - Consider pagination in bulk accept endpoint
  - Or implement async processing with job queue
  - Monitor performance and add caching if needed

### 7.4 Caching Strategies

**What to Cache**:
- User's generation list (if accessed frequently)
- Single generation details (frequently accessed)
- Not recommended: Computed metrics (acceptance_rate) due to constant changes

**Cache Invalidation**:
- Invalidate generation list cache when new generation created
- Invalidate single generation cache when counters updated (accept operation)
- Use time-based expiry (5-10 minutes) as fallback

**Cache Implementation**:
- Use atom/map for in-memory caching (simple, single-server)
- Use Redis for distributed caching (multi-server deployment)
- Don't over-cache; keep cache simple and maintainable in MVP

## 8. Implementation Steps

### Phase 1: Core Infrastructure (Weeks 1-2)

#### Step 1.1: Add Generation Malli Schema
- **File**: `src/com/apriary/schema.clj`
- **Action**: Define Malli schema for generation entity (already may exist)
- **Schema**:
```clojure
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

#### Step 1.2: Create Utility Functions for UUID Parsing
- **File**: `src/com/apriary/util.clj` (new file)
- **Functions**:
  - `parse-uuid`: Parse and validate UUID string format
  - `parse-int`: Parse and validate integer with range
  - `validate-sort-by`: Validate sort field against whitelist
  - `validate-sort-order`: Validate sort direction
- **Error Handling**: Return validation error tuples `[:error {:code "..." :message "..."}]`

#### Step 1.3: Implement Generation Service Layer
- **File**: `src/com/apriary/services/generation.clj` (new file)
- **Functions**:
  - `create-generation`: Create generation record for CSV import
    - Inputs: user-id, model, generated-count, duration-ms
    - Output: Generation entity with id, created-at, updated-at
    - Error handling: Validate input, log failures
  - `get-generation-by-id`: Fetch single generation
    - RLS enforcement: Check user-id matches
    - Error: Return error tuple if not found or RLS violation
  - `list-user-generations`: Query generations for user with filtering/sorting
    - Inputs: user-id, sort-by, sort-order, model-filter, limit, offset
    - Output: List of generations + total_count
  - `update-counters`: Increment acceptance counters
    - Inputs: generation-id, unedited-count-increment, edited-count-increment
    - Validation: Ensure sum doesn't exceed generated-count
    - Error: Return error if validation fails
  - `bulk-accept-summaries-for-generation`: Accept all summaries in batch
    - Inputs: generation-id, user-id
    - Process: Query summaries, group by source, update generation
    - Output: Counts and summary statistics

#### Step 1.4: Create Generation DTO and Response Builders
- **File**: `src/com/apriary/dto/generation.clj` (new file)
- **Functions**:
  - `entity->dto`: Convert XTDB generation entity to DTO format
    - Include computed fields: total_accepted_count, acceptance_rate
    - Format timestamps as ISO-8601 strings
  - `dto->json`: Convert DTO to JSON object (via jsonista/transit)
  - `list->response`: Build list response with pagination
  - `error->response`: Build error response with code and details

### Phase 2: API Endpoints (Weeks 2-3)

#### Step 2.1: GET /api/generations Handler
- **File**: `src/com/apriary/pages/generations.clj` (new file)
- **Function**: `list-generations-handler`
- **Implementation**:
  1. Extract authenticated user-id from session (or return 401)
  2. Parse and validate query parameters
  3. Call `generation/list-user-generations` service
  4. Transform results to DTO + response format
  5. Return 200 OK with generation list
- **Error Handling**: Return appropriate status codes (400, 401, 500)

#### Step 2.2: GET /api/generations/{id} Handler
- **File**: `src/com/apriary/pages/generations.clj`
- **Function**: `get-generation-handler`
- **Implementation**:
  1. Extract user-id from session (or return 401)
  2. Parse and validate UUID path parameter
  3. Call `generation/get-generation-by-id` service
  4. RLS check: Verify user-id (service handles this)
  5. Transform to DTO format
  6. Return 200 OK or appropriate error code
- **Error Handling**: Return 400 (invalid UUID), 403 (RLS), 404 (not found), 500

#### Step 2.3: POST /api/summaries/generation/accept Handler
- **File**: `src/com/apriary/pages/summaries.clj` (extend existing)
- **Function**: `bulk-accept-generations-handler`
- **Implementation**:
  1. Extract user-id from session (or return 401)
  2. Parse request JSON body
  3. Validate `generation-id` field exists and is valid UUID
  4. Call `generation/bulk-accept-summaries-for-generation` service
  5. Build response with counts and statistics
  6. Return 200 OK with acceptance summary
- **Error Handling**: Return 400 (invalid input), 403 (RLS), 404 (not found), 500

#### Step 2.4: Integrate Generation Creation into CSV Import
- **File**: `src/com/apriary/pages/summaries.clj` (existing CSV import)
- **Modification**: Update existing `/api/summaries/import` handler
- **Changes**:
  1. After CSV validation, create generation record (before OpenRouter call)
  2. Record generation-id for use in summary creation
  3. Set duration-ms after OpenRouter API completes
  4. Create summary records with generation-id reference
  5. Return generation-id in response
- **Ensure**: All database writes are atomic (transaction handling)

### Phase 3: Middleware and Security (Week 3)

#### Step 3.1: Implement Rate Limiting Middleware
- **File**: `src/com/apriary/middleware.clj` (extend existing)
- **Function**: `rate-limit-middleware`
- **Implementation**:
  1. Check if endpoint requires rate limiting (CSV import endpoints)
  2. Extract user-id from session
  3. Check rate limit storage (in-memory atom or Redis)
  4. If limit exceeded: return 429 Too Many Requests
  5. If within limit: increment counter and call next handler
  6. Include X-RateLimit-* headers in response
- **Configuration**: CSV import limit 5/hour per user (configurable)

#### Step 3.2: Add Input Validation Middleware Layer
- **File**: `src/com/apriary/middleware.clj`
- **Function**: `validate-input-middleware`
- **Implementation**:
  1. Middleware for validating common parameters
  2. Validate UUID formats
  3. Validate pagination parameters
  4. Validate enum fields
  5. Return 400 if validation fails
  6. Add validation errors to request for handler use
- **Note**: Can call validation utility functions from Phase 1.2

#### Step 3.3: Ensure RLS Enforcement in Service Layer
- **File**: `src/com/apriary/services/generation.clj`
- **Review**: All query functions must include user-id filtering
- **Pattern**: All `where` clauses must include `[:? :???/user-id ?user-id]`
- **Testing**: Write tests to verify RLS is enforced

### Phase 4: Error Handling and Logging (Week 4)

#### Step 4.1: Implement Consistent Error Response Format
- **File**: `src/com/apriary/responses.clj` (new file or extend existing)
- **Functions**:
  - `error-response`: Build standard error response
  - `validation-error`: Build 400 validation error
  - `not-found-error`: Build 404 response
  - `forbidden-error`: Build 403 RLS violation response
  - `internal-error`: Build 500 server error response
- **Format**: Consistent JSON structure with code, message, details, timestamp

#### Step 4.2: Add Comprehensive Error Logging
- **File**: `src/com/apriary/logging.clj` (new file or extend)
- **Implementation**:
  1. Log all API request/response with request-id
  2. Log errors with full context (user-id, endpoint, status)
  3. Log security events (RLS violations, rate limit hits)
  4. Use Timbre (SLF4J) for logging
  5. Don't log sensitive data (content, passwords)
- **Log Levels**: INFO (requests), WARN (client errors), ERROR (server errors)

#### Step 4.3: Add Request/Response Validation
- **File**: Extend existing pages handlers
- **Implementation**:
  1. Validate request format before processing
  2. Validate response format before sending
  3. Use Malli schemas for validation
  4. Return 400 if request doesn't match schema
  5. Log validation errors

### Phase 5: Testing and Documentation (Week 4-5)

#### Step 5.1: Unit Tests for Service Layer
- **File**: `test/com/apriary/services/generation_test.clj` (new file)
- **Test Coverage**:
  - `create-generation`: Valid input, invalid input, database error
  - `get-generation-by-id`: Found, not found, RLS violation
  - `list-user-generations`: Sorting, filtering, pagination
  - `update-counters`: Increment, validation failure, counter overflow
  - `bulk-accept-summaries`: Multiple summaries, mixed sources, no summaries found
- **Minimum Coverage**: 80% of service functions

#### Step 5.2: Integration Tests for Endpoints
- **File**: `test/com/apriary/pages/generations_test.clj` (new file)
- **Test Coverage**:
  - GET /api/generations: Auth, pagination, filtering, sorting
  - GET /api/generations/{id}: Auth, valid ID, invalid ID, RLS violation
  - POST /api/summaries/generation/accept: Auth, valid generation, not found, RLS violation
  - Error scenarios: Malformed input, server errors
- **Setup**: Use test database and mock session

#### Step 5.3: API Documentation
- **File**: Create `docs/API-GENERATIONS.md` or update README
- **Content**:
  - Endpoint descriptions with examples
  - Required/optional parameters
  - Example request/response payloads
  - Error codes and meanings
  - cURL examples for testing
  - Rate limiting information

#### Step 5.4: Manual Testing Checklist
- **Test Cases**:
  1. GET /api/generations with valid session → 200 with list
  2. GET /api/generations without session → 401
  3. GET /api/generations/{id} with RLS violation → 403 or 404
  4. GET /api/generations with invalid sort_by → 400
  5. GET /api/generations with pagination → correct limit/offset applied
  6. POST /api/summaries/generation/accept with valid generation → 200 with counts
  7. POST /api/summaries/generation/accept with invalid generation-id → 404
  8. CSV import creates generation record → Verify generation-id returned
  9. Verify generation counters incremented on acceptance → Check in database
  10. Rate limiting on CSV import → Verify 429 after 5 imports

### Phase 6: Optimization and Monitoring (Week 5)

#### Step 6.1: Add Database Indexes
- **File**: `src/com/apriary/schema.clj` or migration script
- **Indexes to Create**:
  1. `(generation/user-id, generation/created-at desc)` - for list queries
  2. `generation/user-id` - for RLS filtering
  3. Consider: `generation/model` - if filtering by model is common
- **Testing**: Monitor query performance before/after indexes

#### Step 6.2: Performance Monitoring
- **Implementation**:
  1. Add query execution time logging
  2. Monitor endpoint response times (latency metrics)
  3. Track rate limiting hit rates
  4. Set alerts for slow queries (>500ms)
- **Tools**: Use Timbre + metrics library (or built-in to Biff)

#### Step 6.3: Documentation Updates
- **Update**:
  - README.md with new endpoints
  - Copilot instructions (already provided)
  - API plan with implementation status
  - Database schema documentation

---

## Summary

This implementation plan provides a complete roadmap for developing the Generations REST API with:

✅ **Three main endpoints**:
- GET /api/generations (list with filtering/sorting/pagination)
- GET /api/generations/{id} (single record retrieval)
- POST /api/summaries/generation/accept (bulk accept workflow)

✅ **Security Features**:
- Session-based authentication requirement
- Row-Level Security enforcement
- Rate limiting on CSV imports
- Input validation on all parameters

✅ **Error Handling**:
- Consistent error response format
- Appropriate HTTP status codes
- Comprehensive logging and audit trail
- Clear error messages for debugging

✅ **Performance Optimizations**:
- Database indexing strategy
- Query optimization patterns
- Response pagination
- Computed field calculation on-demand

✅ **Quality Assurance**:
- Unit test coverage for services
- Integration tests for endpoints
- Manual testing checklist
- Documentation and examples

The implementation follows REST conventions, adheres to the Biff framework patterns, and integrates seamlessly with the existing Summary and CSV import infrastructure. All work respects Row-Level Security requirements and provides audit trails through comprehensive logging.

