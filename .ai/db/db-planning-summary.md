# XTDB Schema Design Summary - Apriary

## Overview

This document outlines the XTDB schema design for the Apriary application, an MVP for automating the creation of beekeeping work summaries. The schema consists of three main entities: `user`, `generation`, and `summary`, designed to support CSV import workflows, AI-generated proposals, and full CRUD operations on summaries.

## Entity Relationships

```
User (1) ──────────── (Many) Generation
  │
  └──────────────────── (Many) Summary
                             │
                             └── Generation (optional, references parent generation batch)
```

## Entity Definitions

### 1. User Entity

**Purpose**: Represents application users with authentication credentials and profile information.

**Attributes**:
- `user/id` (UUID): Unique identifier
- `user/email` (String): User's email address
- `user/joined-at` (Instant): Account creation timestamp

**Key Points**:
- Already present in the schema
- Serves as the root entity for row-level security enforcement
- All other entities reference `user/id` to maintain data isolation

---

### 2. Generation Entity

**Purpose**: Tracks each OpenRouter API request and the batch of AI-generated summary proposals returned. Acts as an audit trail for AI operations and metrics collection.

**Attributes**:
- `generation/id` (UUID): Unique identifier for each generation batch
- `generation/user-id` (UUID): Reference to the user who initiated the request
- `generation/model` (String): AI model used (e.g., "gpt-4", "claude-3-sonnet")
- `generation/generated-count` (Integer): Total number of summary proposals returned by OpenRouter
- `generation/accepted-unedited-count` (Integer): Count of proposals accepted without modification
- `generation/accepted-edited-count` (Integer): Count of proposals accepted after user edits
- `generation/duration-ms` (Integer): Time taken to generate proposals (in milliseconds)
- `generation/created-at` (Instant): Timestamp when the generation request was created
- `generation/updated-at` (Instant): Timestamp of the latest update to generation counters

**Key Points**:
- One generation = one CSV import session = one OpenRouter API call
- Counters track user engagement with AI proposals
- Provides metrics for calculating Metryka 1 (AI quality) and Metryka 2 (AI adoption)
- Serves as audit trail for generation operations (RF-010)

---

### 3. Summary Entity

**Purpose**: Stores finalized summary records with flexible source tracking and optional metadata. Supports both AI-generated and manually-created summaries.

**Attributes**:
- `summary/id` (UUID): Unique identifier
- `summary/user-id` (UUID): Reference to the summary's owner (enforces RLS)
- `summary/generation-id` (UUID, optional): Reference to parent generation batch (null for manually-created summaries)
- `summary/source` (Enum): Source categorization with three values:
  - `:ai-full` - AI proposal accepted without any edits
  - `:ai-partial` - AI proposal edited before acceptance
  - `:manual` - Manually created by user (not AI-generated)
- `summary/created-at` (Instant): Timestamp when summary was created
- `summary/hive-number` (String, optional): Hive identifier (can be blank)
- `summary/observation-date` (String, optional): Date of observation in DD-MM-YYYY format (can be blank)
- `summary/special-feature` (String, optional): Special notes or features (can be blank)
- `summary/content` (String): The actual summary text
- `summary/updated-at` (Instant): Timestamp of latest modification

**Key Points**:
- Source field enables tracking of user engagement with AI proposals
- Flexible optional fields support the workflow where users complete metadata after creation
- All fields are nullable except `id`, `user-id`, `source`, `content`, and timestamps
- Supports inline editing (US-005, US-006) with immediate persistence

---

## Data Flow & Workflows

### CSV Import Workflow

1. User uploads CSV file (UTF-8 encoding, ';' separator, with header row)
2. System validates each row:
   - Observation text must be 50-10,000 characters (trimmed)
   - Date format should be DD-MM-YYYY (but can be missing)
   - Hive number can be missing
3. Valid rows sent to OpenRouter API
4. **Generation** record created with:
   - `generated-count`: Number of valid rows processed
   - `model`: OpenRouter model used
   - `duration-ms`: Time to complete request
5. For each proposal:
   - User can accept unedited → creates **Summary** with `source: :ai-full`
   - User can edit then accept → creates **Summary** with `source: :ai-partial`
6. Generation counters updated: `accepted-unedited-count` and `accepted-edited-count`

### Manual Summary Creation

1. User clicks "Add new summary" button (US-009)
2. User fills form with optional metadata (hive-number, observation-date, special-feature) and content
3. **Summary** record created with:
   - `source: :manual`
   - `generation-id: null`
   - All user-provided values

### CRUD Operations

**Create (RF-008)**:
- Via CSV import → Generation + Summary records
- Manually → Summary record only (source: `:manual`)

**Read (RF-008, US-004)**:
- Query summaries by `user-id` and `created-at`
- Display sorted chronologically (newest first)
- Show: `created-at`, `hive-number`, content preview, `source`

**Update (RF-008, US-005, US-006)**:
- Inline editing of `hive-number`, `observation-date`, `special-feature`
- Full editing of `summary/content`
- Updates `summary/updated-at` timestamp
- No version history maintained (no soft-deletes or changelogs)

**Delete (RF-008, US-008)**:
- Permanent deletion of Summary record
- No confirmation dialog required for MVP

---

## Metrics Collection

### Generation Entity as Audit Trail

The `generation` entity inherently provides data for success metrics:

**Metryka 1 (AI Quality)**:
- Formula: (Unique summaries with `source: :ai-full` or `:ai-partial`) / (Total `generated-count` across all generations)
- Data source: `generation/accepted-unedited-count + generation/accepted-edited-count`

**Metryka 2 (AI Adoption)**:
- Formula: (Summaries with `generation-id ≠ null`) / (Total summaries for user)
- Data source: Count summaries by `generation-id` presence

### Event Logging (RF-010)

Events are tracked indirectly through:
- Generation creation/update timestamps
- Summary creation/update timestamps
- Source field indicates creation method
- Generation counters track acceptance behavior

---

## Row-Level Security (RLS)

**Implementation Strategy**: Application-level RLS enforced in Biff middleware

**Rules**:
1. Every entity has a `user-id` attribute
2. All queries must filter by `user-id` of the authenticated user
3. Before any read/update/delete operation, verify that the document's `user-id` matches the requester's `user-id`
4. Never expose summaries, generations, or operations across user boundaries

**Middleware Pattern**:
```clojure
(fn [request]
  (let [user-id (get-in request [:session :user-id])]
    ;; Ensure all queries include [?summary :summary/user-id user-id]
    ;; Ensure all mutations check ownership
    ))
```

---

## Data Validation

**User Entity**: Already validated in auth system

**Generation Entity**:
- `model`: Non-empty string
- `generated-count`, `accepted-unedited-count`, `accepted-edited-count`: Non-negative integers
- `duration-ms`: Non-negative integer
- Timestamps: Valid instants

**Summary Entity**:
- `hive-number`: Optional; if provided, should be a reasonable string length
- `observation-date`: Optional; if provided, should match DD-MM-YYYY format or be handled gracefully
- `special-feature`: Optional; free-form text
- `content`: Non-empty string; used for storing AI-generated or user-entered summaries
- `source`: Must be one of `:ai-full`, `:ai-partial`, `:manual`
- `generation-id`: Optional; if provided, must reference a valid generation record owned by the same user

---

## Schema Validation with Malli

All entities use Malli schemas with:
- `:closed true` for strict validation (only declared keys allowed)
- `:optional true` for nullable/optional fields
- Type specifications for all attributes

---

## Implementation Notes

1. **Timestamps**: Use `inst?` type (Clojure instants)
2. **UUIDs**: Use `:uuid` type for all ID fields
3. **Enums**: `[:enum :ai-full :ai-partial :manual]` for source field
4. **Optional Fields**: Use `{:optional true}` wrapper in schema
5. **No Indexes Required for MVP**: Schema supports queries as-is for MVP scope

---

## Future Considerations (Out of Scope for MVP)

- Soft-delete mechanism for summaries (trash/recovery)
- Version history/changelog for summaries
- Duplicate detection during CSV import
- Advanced filtering and search on summary content
- Batch operations on multiple summaries
- Export functionality (CSV, PDF)
- Collaborative features (sharing summaries between users)
- Performance optimizations (caching, indexing strategies)

---

## Summary

The Apriary schema provides a minimal but complete data model for the MVP:

- **User**: Existing auth entity
- **Generation**: Tracks AI request batches and user engagement with proposals
- **Summary**: Stores final summaries with flexible source tracking

This design enables:
✅ Full CRUD operations on summaries
✅ CSV import workflow with AI integration
✅ Manual summary creation
✅ User engagement tracking for metrics
✅ Row-level security enforcement
✅ Distinction between AI-generated and manual content
✅ Audit trail via generation records and timestamps
