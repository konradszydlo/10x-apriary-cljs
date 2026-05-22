# REST API Plan for Apriary Summary

## 1. Resources

### 1.1 User
- **Database Table**: `user`
- **Description**: Represents application users with authentication credentials
- **Key Attributes**: `user/id`, `user/email`, `user/joined-at`
- **Security**: All users have access control enforced at middleware level; Row-Level Security (RLS) isolates user data

### 1.2 Generation
- **Database Table**: `generation`
- **Description**: Tracks OpenRouter API request batches and audit trail for AI-generated summaries
- **Key Attributes**: `generation/id`, `generation/user-id`, `generation/model`, counters for accepted summaries, duration metrics
- **Purpose**: Supports metrics calculation and provides audit trail for batch operations

### 1.3 Summary
- **Database Table**: `summary`
- **Description**: Stores finalized summary records with flexible source tracking (AI-generated or manual)
- **Key Attributes**: `summary/id`, `summary/user-id`, `summary/generation-id` (optional), `summary/source`, `summary/content`, optional metadata (hive-number, observation-date, special-feature)
- **Supported Operations**: Full CRUD with inline editing capabilities

---

## 2. Endpoints

### 2.1 Summary Endpoints

#### 2.1.1 List User Summaries
- **HTTP Method**: `GET`
- **URL Path**: `/api/summaries`
- **Description**: Retrieve all summaries for authenticated user, sorted by creation date (newest first)
- **Query Parameters**:
  - `sort_by` (optional, default: `created_at`): Field to sort by (`created_at`, `hive_number`, `source`)
  - `sort_order` (optional, default: `desc`): Sort order (`asc` or `desc`)
  - `source` (optional): Filter by source (`:ai-full`, `:ai-partial`, `:manual`)
  - `limit` (optional, default: `50`): Maximum number of results
  - `offset` (optional, default: `0`): Number of results to skip for pagination
- **Request Payload**: None
- **Response Payload (Success - 200)**:
  ```json
  {
    "summaries": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "user_id": "550e8400-e29b-41d4-a716-446655440000",
        "generation_id": "550e8400-e29b-41d4-a716-446655440002",
        "source": "ai-full",
        "hive_number": "A-01",
        "observation_date": "23-11-2025",
        "special_feature": "Queen replaced",
        "content": "Summary of hive activities...",
        "created_at": "2025-11-23T10:30:00Z",
        "updated_at": "2025-11-23T10:30:00Z"
      }
    ],
    "total_count": 42,
    "limit": 50,
    "offset": 0
  }
  ```
- **Error Codes**:
  - `401 Unauthorized`: User not authenticated
  - `400 Bad Request`: Invalid query parameters
  - `500 Internal Server Error`: Database query failure

#### 2.1.2 Create Summary (Manual)
- **HTTP Method**: `POST`
- **URL Path**: `/api/summaries`
- **Description**: Create a new summary manually without AI generation
- **Query Parameters**: None
- **Request Payload**:
  ```json
  {
    "hive_number": "A-02",
    "observation_date": "23-11-2025",
    "special_feature": "New frames added",
    "content": "Detailed observation text here. Must be at least 50 characters long and no more than 50,000 characters."
  }
  ```
- **Response Payload (Success - 201)**:
  ```json
  {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "generation_id": null,
    "source": "manual",
    "hive_number": "A-02",
    "observation_date": "23-11-2025",
    "special_feature": "New frames added",
    "content": "Detailed observation text here. Must be at least 50 characters long and no more than 50,000 characters.",
    "created_at": "2025-11-23T10:35:00Z",
    "updated_at": "2025-11-23T10:35:00Z",
    "message": "Summary created successfully"
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Invalid request body or validation failure
    - Content length not between 50-50,000 characters
    - Invalid observation_date format (should be DD-MM-YYYY)
    - Missing required field: `content`
  - `401 Unauthorized`: User not authenticated
  - `500 Internal Server Error`: Database creation failure

#### 2.1.3 Get Single Summary
- **HTTP Method**: `GET`
- **URL Path**: `/api/summaries/{id}`
- **Description**: Retrieve a specific summary by ID with full details
- **Query Parameters**: None
- **Request Payload**: None
- **Response Payload (Success - 200)**:
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
- **Error Codes**:
  - `401 Unauthorized`: User not authenticated
  - `403 Forbidden`: User does not have permission to access this summary (RLS violation)
  - `404 Not Found`: Summary with given ID does not exist
  - `500 Internal Server Error`: Database query failure

#### 2.1.4 Update Summary (Inline Edit)
- **HTTP Method**: `PATCH`
- **URL Path**: `/api/summaries/{id}`
- **Description**: Update summary metadata (hive_number, observation_date, special_feature) or content. Updates are immediate with timestamp tracking.
- **Query Parameters**: None
- **Request Payload** (at least one field required):
  ```json
  {
    "hive_number": "A-01-Updated",
    "observation_date": "24-11-2025",
    "special_feature": "Updated feature",
    "content": "Updated summary content with substantial text..."
  }
  ```
- **Response Payload (Success - 200)**:
  ```json
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "generation_id": "550e8400-e29b-41d4-a716-446655440002",
    "source": "ai-partial",
    "hive_number": "A-01-Updated",
    "observation_date": "24-11-2025",
    "special_feature": "Updated feature",
    "content": "Updated summary content with substantial text...",
    "created_at": "2025-11-23T10:30:00Z",
    "updated_at": "2025-11-23T11:15:00Z",
    "message": "Summary updated successfully"
  }
  ```
- **Response Payload (Partial Update - 200)**:
  - If user edits content from AI-generated summary, source field automatically changes to `ai-partial` (if it was `ai-full`)
  ```json
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "generation_id": "550e8400-e29b-41d4-a716-446655440002",
    "source": "ai-partial",
    "content": "Modified content...",
    "updated_at": "2025-11-23T11:15:00Z",
    "message": "Summary updated and marked as ai-partial"
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Invalid data
    - Content length not between 50-50,000 characters (if content being updated)
    - Invalid observation_date format (should be DD-MM-YYYY)
    - Empty request body
  - `401 Unauthorized`: User not authenticated
  - `403 Forbidden`: User does not have permission to update this summary (RLS violation)
  - `404 Not Found`: Summary with given ID does not exist
  - `409 Conflict`: Summary already modified by another request
  - `500 Internal Server Error`: Database update failure

#### 2.1.5 Delete Summary
- **HTTP Method**: `DELETE`
- **URL Path**: `/api/summaries/{id}`
- **Description**: Permanently delete a summary from the database (hard delete, no soft-delete in MVP)
- **Query Parameters**: None
- **Request Payload**: None
- **Response Payload (Success - 204)**:
  ```
  No Content
  ```
- **Response Payload (Success - 200, Alternative)**:
  ```json
  {
    "message": "Summary deleted successfully",
    "id": "550e8400-e29b-41d4-a716-446655440001"
  }
  ```
- **Error Codes**:
  - `401 Unauthorized`: User not authenticated
  - `403 Forbidden`: User does not have permission to delete this summary (RLS violation)
  - `404 Not Found`: Summary with given ID does not exist
  - `500 Internal Server Error`: Database deletion failure

#### 2.1.6 Accept Summary
- **HTTP Method**: `POST`
- **URL Path**: `/api/summaries/{id}/accept`
- **Description**: Mark an AI-generated summary as accepted. Updates source field and generation counters.
- **Query Parameters**: None
- **Request Payload**: None (or optional notes for future extensions)
- **Response Payload (Success - 200)**:
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
- **Business Logic**:
  - If `source` was `:ai-full` (unedited AI): increment `generation/accepted-unedited-count`
  - If `source` was `:ai-partial` (edited AI): increment `generation/accepted-edited-count`
  - If `source` is `:manual`: return 400 error (cannot accept manual summaries)
  - Update `generation/updated-at` timestamp
- **Error Codes**:
  - `400 Bad Request`: Cannot accept manual summary
  - `401 Unauthorized`: User not authenticated
  - `403 Forbidden`: User does not have permission to accept this summary (RLS violation)
  - `404 Not Found`: Summary with given ID does not exist
  - `409 Conflict`: Summary already accepted
  - `500 Internal Server Error`: Database update failure

#### 2.1.7 Bulk Accept Summaries for Generation
- **HTTP Method**: `POST`
- **URL Path**: `/api/summaries/generation/accept`
- **Description**: Accept all AI-generated summaries belonging to a specific generation batch. Updates counters for the entire batch.
- **Query Parameters**: None
- **Request Payload**:
  ```json
  {
    "generation-id": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```
- **Response Payload (Success - 200)**:
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
- **Business Logic**:
  - Query all summaries with `generation-id` matching the provided ID
  - For each summary with `source` `:ai-full`: increment `generation/accepted-unedited-count`
  - For each summary with `source` `:ai-partial`: increment `generation/accepted-edited-count`
  - Skip summaries with `source` `:manual` (should not exist for AI-generated batches)
  - Update `generation/updated-at` timestamp once
  - Return count of accepted summaries by type
- **Error Codes**:
  - `400 Bad Request`: Invalid generation-id format or missing required field
  - `401 Unauthorized`: User not authenticated
  - `403 Forbidden`: User does not own the generation (RLS violation)
  - `404 Not Found`: Generation with given ID does not exist
  - `500 Internal Server Error`: Database update failure

---

### 2.2 CSV Import & Generation Endpoint

#### 2.2.1 Import CSV and Generate Summaries
- **HTTP Method**: `POST`
- **URL Path**: `/api/summaries/import`
- **Description**: Submit CSV data as string and trigger batch AI generation. Validates each row and generates summaries via OpenRouter. Model is retrieved from application configuration.
- **Query Parameters**: None
- **Request Payload** (application/json):
  ```json
  {
    "csv": "observation;hive_number;observation_date;special_feature\nFirst observation with substantial details about hive activity...;A-01;23-11-2025;Queen active\nColony appears weak, only a few foragers. Might need to feed.;A-02;23-11-2025;"
  }
  ```
- **CSV Format Requirements**:
  - Encoding: UTF-8
  - Delimiter: semicolon (`;`)
  - Headers required (case-insensitive): `observation`, plus optional `hive_number`, `observation_date`, `special_feature`
  - Example CSV structure:
    ```
    observation;hive_number;observation_date;special_feature
    First observation with substantial details about hive activity...;A-01;23-11-2025;Queen active
    Second observation describing the state of the colony...;A-02;24-11-2025;
    ```

- **Response Payload (Success - 202 Accepted, Async Processing)**:
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

- **Response Payload (Success - 201, If Synchronous)**:
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

- **Validation Rules Applied**:
  - Observation field: must be 50-10,000 characters after trim (RFC-004)
  - observation_date (if provided): must match DD-MM-YYYY format or be empty
  - hive_number, special_feature: optional, can be empty
  - Rows failing validation are skipped; processing continues for valid rows
  - Empty CSV file triggers error

- **Generation Record Created**:
  - `generation/id`: Unique batch ID
  - `generation/user-id`: Current user ID
  - `generation/model`: Model retrieved from application configuration (e.g., "gpt-4-turbo")
  - `generation/generated-count`: Number of valid rows processed
  - `generation/accepted-unedited-count`: 0 (initial)
  - `generation/accepted-edited-count`: 0 (initial)
  - `generation/duration-ms`: Time in milliseconds
  - `generation/created-at`: Timestamp
  - `generation/updated-at`: Timestamp

- **Error Codes**:
  - `400 Bad Request`: Invalid CSV format or request payload
    - Missing required `csv` field
    - Missing or incorrect delimiter (not `;`)
    - Missing required `observation` column in headers
    - Empty CSV string
    - Invalid encoding
  - `401 Unauthorized`: User not authenticated
  - `429 Too Many Requests`: Rate limit exceeded (e.g., max 5 imports per hour per user)
  - `500 Internal Server Error`: CSV parsing failure or OpenRouter API failure
  - `503 Service Unavailable`: OpenRouter service unavailable

---

## 3. Authentication and Authorization

### 3.1 Authentication Mechanism (Already Implemented)

**Type**: Session-based authentication (Biff default)

**Current Implementation**: User registration and login endpoints are already implemented in the system. This plan assumes users are authenticated and have established sessions.

### 3.2 Authorization - Row-Level Security (RLS)

**Implementation Location**: Biff middleware layer

**Rules**:
1. **Principle**: Every entity (Generation, Summary) contains a `user-id` field
2. **Enforcement**: 
   - All database queries automatically filtered to include `where user-id = current-user-id`
   - Before any read/update/delete operation, middleware verifies document's user-id matches authenticated user
   - No cross-user data access permitted
3. **Error Handling**: 
   - Unauthorized access attempts return `403 Forbidden` with RLS violation message
   - Attempts to access non-existent resources return `404 Not Found` (do not leak that resource exists)

**Middleware Pattern**:
```
POST /api/summaries/{id}/accept
  → Verify user is authenticated
  → Load summary document
  → Check: summary.user-id == request.user-id
  → If mismatch: return 403 Forbidden
  → If match: proceed with operation
```

### 3.3 Rate Limiting

**OpenRouter API Requests**:
- Implement per-user rate limiting for CSV imports to prevent API quota exhaustion
- Recommended: Maximum 5 CSV imports per hour per user
- Recommended: Maximum 1000 summary rows processed per day per user
- Rate limit headers returned in responses: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

**Other Endpoints**:
- Standard API rate limiting: 100 requests per minute per user (configurable)

### 3.4 CORS and Security Headers

- **CORS**: Configure to allow requests only from Apriary domain
- **CSRF Protection**: Biff handles CSRF tokens for POST/PATCH/DELETE requests
- **Security Headers**:
  - `Content-Security-Policy`: Restrict script sources
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Strict-Transport-Security` (HTTPS only)

---

## 4. Validation and Business Logic

### 4.1 Summary Entity Validation

#### Creation & Update Validation

| Field | Type | Required | Constraints | Validation Logic |
|-------|------|----------|-------------|------------------|
| `content` | String | ✅ Yes | Min 50, Max 50,000 chars | Validate after trim; reject if outside range |
| `hive_number` | String | ❌ No | Any length | Allow empty string; no max length enforcement |
| `observation_date` | String | ❌ No | DD-MM-YYYY format or empty | If provided: validate regex `^\d{2}-\d{2}-\d{4}$` and date validity; allow empty |
| `special_feature` | String | ❌ No | Any length | Allow empty string; no max length enforcement |
| `source` | Enum | ✅ Yes (AI) | `:ai-full`, `:ai-partial`, `:manual` | Validate against enum; required for AI-generated summaries |

#### CSV Import Validation

| Rule | Implementation |
|------|-----------------|
| Observation field length | After trim: `50 ≤ length ≤ 10,000`; reject row if outside range |
| Missing observation field | Reject row; log as invalid |
| observation_date format | Validate against `DD-MM-YYYY` or allow empty; reject row if invalid |
| Empty CSV file | Return 400 error before processing |
| hive_number, special_feature | Optional; no length constraints in MVP |
| Invalid UTF-8 encoding | Return 400 error; reject entire file |
| Wrong delimiter | Return 400 error if not semicolon-separated |

### 4.2 Generation Entity Validation

| Field | Constraint | Logic |
|-------|-----------|--------|
| `model` | Non-empty string | Validate before creating; reject if empty |
| `generated_count` | ≥ 0, must equal valid rows | Set automatically from import validation |
| `accepted_unedited_count` | ≥ 0, ≤ generated_count | Incremented on acceptance; validate during update |
| `accepted_edited_count` | ≥ 0, ≤ generated_count | Incremented on acceptance after edit; validate during update |
| `duration_ms` | ≥ 0, integer | Calculated from start/end timestamps |
| `user-id` | Must reference existing user | Enforce foreign key constraint |
| Counter validation | `accepted_unedited + accepted_edited ≤ generated_count` | Validate on every counter update |

### 4.3 Business Logic Implementation

#### 4.3.1 CSV Import Workflow

**Step 1: CSV String Parsing**
- Receive CSV data as string in request payload
- Validate UTF-8 encoding; reject if invalid
- Parse with semicolon (`;`) delimiter
- Extract headers (case-insensitive); validate presence of `observation` column
- Reject with 400 error if `observation` column missing or CSV string is empty

**Step 2: Row Validation**
- For each row:
  1. Extract `observation`, `hive_number`, `observation_date`, `special_feature`
  2. Trim `observation` field (remove leading/trailing whitespace)
  3. Check: `50 ≤ length(observation) ≤ 10,000` characters
  4. If valid: add to valid rows list
  5. If invalid: add to rejected rows list with reason
- If all rows invalid: return 400 error with rejection summary
- If some rows valid: proceed to next step

**Step 3: OpenRouter API Request**
- Retrieve AI model from application configuration (e.g., from `config.edn`)
- Prepare batch request for valid observations
- Send to OpenRouter with configured model
- Record start timestamp
- Wait for response
- Record end timestamp
- Calculate duration in milliseconds

**Step 4: Create Generation Record**
```
POST to database:
{
  :generation/id <new-uuid>
  :generation/user-id <current-user-id>
  :generation/model <model-from-config>
  :generation/generated-count <count-of-valid-rows>
  :generation/accepted-unedited-count 0
  :generation/accepted-edited-count 0
  :generation/duration-ms <end-time - start-time>
  :generation/created-at <now>
  :generation/updated-at <now>
}
```

**Step 5: Create Summary Records**
- For each valid row + AI-generated proposal:
```
POST to database:
{
  :summary/id <new-uuid>
  :summary/user-id <current-user-id>
  :summary/generation-id <generation-id>
  :summary/source :ai-full  ; initially unaccepted
  :summary/hive-number <from-csv or empty>
  :summary/observation-date <from-csv or empty>
  :summary/special-feature <from-csv or empty>
  :summary/content <ai-generated-text>
  :summary/created-at <now>
  :summary/updated-at <now>
}
```

**Step 6: Response**
- Return 201 Created with list of created summaries and generation_id
- Include list of rejected rows with reasons (if any)

#### 4.3.2 Summary Acceptance Workflow

**Preconditions**:
- Summary source must be `:ai-full` or `:ai-partial`
- Manual summaries (`:manual`) cannot be accepted

**Operation on Accept**:

1. **Determine counter to increment**:
   - If `source == :ai-full`: increment `generation/accepted-unedited-count`
   - If `source == :ai-partial`: increment `generation/accepted-edited-count`

2. **Update Generation record**:
```
PATCH generation:
{
  :generation/accepted-unedited-count <current + 1 if ai-full>
  :generation/accepted-edited-count <current + 1 if ai-partial>
  :generation/updated-at <now>
}
```

3. **Response Success**:
   - Return 200 OK with updated summary
   - Include acceptance timestamp

4. **Error Handling**:
   - If source is `:manual`: return 400 Bad Request with message "Cannot accept manual summaries"
   - If already accepted: return 409 Conflict with message "Summary already accepted"

#### 4.3.2 Bulk Summary Acceptance Workflow

**Preconditions**:
- Generation ID must exist and belong to authenticated user (RLS check)
- Generation must have associated summaries

**Operation on Bulk Accept**:

1. **Query all summaries for generation**:
   - Fetch all summaries where `summary/generation-id == provided-generation-id`
   - Filter by `summary/user-id == current-user-id` (RLS enforcement)

2. **Process each summary**:
   - Count summaries by source type
   - `:ai-full` summaries: increment count for unedited acceptances
   - `:ai-partial` summaries: increment count for edited acceptances
   - Skip `:manual` summaries (should not exist in AI batch)

3. **Update Generation record once**:
```
PATCH generation:
{
  :generation/accepted-unedited-count <incremented-count>
  :generation/accepted-edited-count <incremented-count>
  :generation/updated-at <now>
}
```

4. **Response Success**:
   - Return 200 OK with summary counts
   - Include breakdown of accepted summaries by type (unedited vs edited)
   - Include total summaries accepted for the batch

5. **Error Handling**:
   - If generation not found or doesn't belong to user: return 404 or 403
   - If no summaries exist for generation: return 400 with message "No summaries found for this generation"
   - If database update fails: return 500

#### 4.3.3 Summary Edit Workflow

**Inline Edit (Metadata)**:
- User edits `hive_number`, `observation_date`, or `special_feature`
- Validate new value (date format if applicable)
- Update field in database
- Set `updated-at` to current timestamp
- Source field remains unchanged

**Content Edit**:
- User edits `content` field
- Validate: `50 ≤ length(trimmed-content) ≤ 50,000`
- Update field in database
- Set `updated-at` to current timestamp
- **Important**: If summary was `ai-full`, automatically change source to `ai-partial`
- Return response indicating source change if applicable

### 4.4 Error Handling Strategy

**Error Response Format**:
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

**Common Error Codes**:
- `INVALID_REQUEST`: Malformed request body
- `VALIDATION_ERROR`: Field validation failure
- `UNAUTHORIZED`: User not authenticated
- `FORBIDDEN`: User not authorized (RLS violation)
- `NOT_FOUND`: Resource does not exist
- `CONFLICT`: Operation conflicts with current state (e.g., already accepted)
- `RATE_LIMIT_EXCEEDED`: Too many requests
- `EXTERNAL_SERVICE_ERROR`: OpenRouter or other external service failure
- `INTERNAL_ERROR`: Unexpected server error

### 4.5 Data Integrity Rules

1. **Referential Integrity**:
   - `summary/user-id` must reference valid user
   - `summary/generation-id` (if provided) must reference valid generation
   - `generation/user-id` must reference valid user

2. **Foreign Key Constraints**:
   - If summary references a generation, generation must exist and belong to same user
   - Deleting a generation should not delete associated summaries (orphan them)
   - Deleting a user should cascade-delete all their summaries and generations

3. **Temporal Integrity**:
   - `updated_at ≥ created_at` (always)
   - Timestamps must be valid Instant objects

4. **Enumeration Integrity**:
   - `source` field must be exactly one of: `:ai-full`, `:ai-partial`, `:manual`

---

## 5. Request/Response Examples

### 5.1 Complete CSV Import Workflow Example

**Request**: Submit CSV data
```
POST /api/summaries/import HTTP/1.1
Content-Type: application/json
Cookie: session_id=...

{
  "csv": "observation;hive_number;observation_date;special_feature\nHive very active today, lots of bees returning with pollen. Need to monitor for swarming soon.;A-01;23-11-2025;Pollen activity high\nColony appears weak, only a few foragers. Might need to feed.;A-02;23-11-2025;\nQueen sighting confirmed today;A-03;24-11-2025;Queen present"
}
```

**Response**: 201 Created (if synchronous)
```json
{
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "completed",
  "rows_submitted": 3,
  "rows_valid": 3,
  "rows_rejected": 0,
  "rows_processed": 3,
  "model": "gpt-4-turbo",
  "duration_ms": 2145,
  "summaries_created": 3,
  "message": "CSV import completed successfully",
  "summaries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440011",
      "source": "ai-full",
      "hive_number": "A-01",
      "observation_date": "23-11-2025",
      "special_feature": "Pollen activity high",
      "content": "Hive A-01 is showing high activity with strong pollen foraging. Monitor closely for swarming behavior in coming days."
    }
  ]
}
```

### 5.2 Accept Summary Workflow Example

**Request**: Accept Summary
```
POST /api/summaries/550e8400-e29b-41d4-a716-446655440011/accept HTTP/1.1
Cookie: session_id=...
```

**Response**: 200 OK
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440011",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-full",
  "hive_number": "A-01",
  "observation_date": "23-11-2025",
  "special_feature": "Pollen activity high",
  "content": "Hive A-01 is showing high activity with strong pollen foraging. Monitor closely for swarming behavior in coming days.",
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T10:30:00Z",
  "accepted_at": "2025-11-23T11:00:00Z",
  "message": "Summary accepted successfully"
}
```

**Backend Effect**: 
- `generation/accepted-unedited-count` incremented from 0 to 1
- `generation/updated-at` updated to current timestamp

### 5.3 Bulk Accept Summaries Workflow Example

**Request**: Bulk accept all summaries for a generation
```
POST /api/summaries/generation/accept HTTP/1.1
Content-Type: application/json
Cookie: session_id=...

{
  "generation-id": "550e8400-e29b-41d4-a716-446655440002"
}
```

**Response**: 200 OK
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

**Backend Effect**: 
- All 14 summaries for the generation are now marked as accepted in the source tracking
- `generation/accepted-unedited-count` incremented by 8 (unedited AI summaries)
- `generation/accepted-edited-count` incremented by 6 (user-edited AI summaries)
- `generation/updated-at` updated to current timestamp

### 5.4 Edit Summary Workflow Example

**Request**: Edit Summary Content
```
PATCH /api/summaries/550e8400-e29b-41d4-a716-446655440011 HTTP/1.1
Content-Type: application/json
Cookie: session_id=...

{
  "content": "Hive A-01 shows robust activity with excellent pollen return. Colony is strong and healthy. Continue monitoring but no immediate action needed. Swarming risk is low."
}
```

**Response**: 200 OK
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440011",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "generation_id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-partial",
  "hive_number": "A-01",
  "observation_date": "23-11-2025",
  "special_feature": "Pollen activity high",
  "content": "Hive A-01 shows robust activity with excellent pollen return. Colony is strong and healthy. Continue monitoring but no immediate action needed. Swarming risk is low.",
  "created_at": "2025-11-23T10:30:00Z",
  "updated_at": "2025-11-23T11:05:00Z",
  "message": "Summary updated and marked as ai-partial"
}
```

**Backend Effect**:
- `summary/source` changed from `:ai-full` to `:ai-partial`
- `summary/updated-at` updated to current timestamp
- `generation/accepted-edited-count` will be incremented when user calls `/accept` again

---

## 6. API Design Decisions and Rationale

### 6.1 RESTful vs Alternative Approaches

**Decision**: Use RESTful architecture with standard HTTP methods

**Rationale**:
- Aligns with Biff framework conventions
- Simpler to implement and test in MVP
- Better developer experience with standard patterns
- GraphQL adds complexity not needed for MVP scope

### 6.2 CSV Import Endpoint Location

**Decision**: Single endpoint `/api/summaries/import` instead of separate steps

**Rationale**:
- Simplifies client workflow
- Validates, generates, and persists in one request
- Reduces API complexity for MVP
- Can be refactored to background job pattern later if needed

### 6.3 Acceptance as Separate Endpoint

**Decision**: `POST /api/summaries/{id}/accept` as dedicated endpoint

**Rationale**:
- Clear semantic meaning (action verb)
- Updates generation counters automatically
- Simplifies client logic (dedicated button click)
- Aligns with REST sub-resource pattern
- Clear audit trail for acceptance event

### 6.4 Inline Edit with PATCH

**Decision**: Use PATCH for partial updates; source field changes automatically

**Rationale**:
- PATCH semantics match "update some fields" use case
- Single request updates both metadata and content
- Automatic source tracking (`ai-full` → `ai-partial`) provides audit trail
- Simplifies UI (no need to track source manually)

### 6.5 Permanent Delete (No Soft-Delete)

**Decision**: HTTP DELETE permanently removes records; no soft-delete mechanism

**Rationale**:
- Matches PRD requirement (US-008)
- Simpler implementation for MVP
- XTDB temporal features can provide audit trail if needed
- Soft-delete can be added in future iterations

### 6.6 Source Enum in Summary

**Decision**: Track `:ai-full`, `:ai-partial`, `:manual` in single field

**Rationale**:
- Enables clear classification without separate acceptance table
- Supports metrics calculation without complex joins
- Handles editing workflow naturally
- One field vs. multiple boolean fields = cleaner schema

### 6.7 Row-Level Security Implementation

**Decision**: Enforce RLS at middleware/query-building layer, not database

**Rationale**:
- XTDB doesn't have built-in column-level security
- Application-level RLS is simpler to implement and audit
- All queries filtered by user-id at construction time
- Easier to test and debug than database-level rules

---

## 7. Performance Considerations

### 7.1 Query Optimization

- **Summary List**: Index on `(summary/user-id, summary/created-at desc)` for efficient list queries
- **Generation Lookup**: Index on `generation/user-id` for quick generation retrieval

### 7.2 CSV Import Performance

- **Batch Processing**: Send multiple observations to OpenRouter in single request
- **String Parsing**: Efficiently parse CSV string format for data extraction

### 7.3 Response Size Limits

- Summary list endpoint: paginate with default limit 50
- Content field: consider truncating in list response (store full text in detail view)

---

## 8. Assumptions and Future Extensions

### 8.1 Assumptions Made

1. **Authentication**: Session-based via Biff (not token-based JWT)
2. **CSV Import**: Accepts CSV data as string parameter, not file upload
3. **CSV Processing**: Synchronous processing (no background job queue)
4. **CSV Format**: Fixed column names; no user-configurable mappings in MVP
5. **Date Format**: DD-MM-YYYY as specified; no timezone conversion needed
6. **Metrics**: Will be implemented in a future iteration (not in MVP)
7. **No Soft-Delete**: Hard deletion only; no archive/trash in MVP
8. **No Version History**: Content edits overwrite previous versions
9. **No Batch Delete API**: Generation deletion endpoints not provided

---

## 8. API Versioning

**Current Version**: v1 (implied in paths)

**Versioning Strategy** (if needed):
- Path-based: `/api/v1/summaries`, `/api/v2/summaries`
- Avoid versioning until breaking changes necessary
- Maintain backward compatibility as long as possible
- Document deprecation periods before removing endpoints

---

## 9. Documentation and SDK

### 9.1 API Documentation
- OpenAPI/Swagger specification for all endpoints
- Interactive API explorer (optional)
- Curl examples for each endpoint
- Common error scenarios with solutions

---

## Summary

This comprehensive REST API plan aligns with the Apriary Summary PRD and database schema. The API includes:

✅ **User authentication**: Already implemented (not included in this plan)  
✅ **CSV import with string input**: Submit CSV data as string parameter; model from config  
✅ **Full CRUD operations**: Create, read, update, delete summaries with proper validation  
✅ **Summary acceptance tracking**: Individual and bulk acceptance of AI-generated summaries  
✅ **Enforces Row-Level Security**: User data isolation at application layer  
✅ **Implements proper validation**: Content length, date format, CSV structure  
✅ **Follows REST conventions**: Standard HTTP methods, appropriate status codes  
✅ **Handles errors gracefully**: Consistent error format, actionable messages  

**Endpoints Summary**:
- 6 Summary CRUD endpoints (list, create, read, update, delete)
- 1 Summary acceptance endpoint (individual)
- 1 Bulk acceptance endpoint (entire generation)
- 1 CSV import endpoint

**Note**: Metrics endpoints (`/api/metrics` and `/api/generations`) will be implemented in a future iteration.

The API is ready for implementation using the Biff framework with XTDB backend and OpenRouter AI integration.
