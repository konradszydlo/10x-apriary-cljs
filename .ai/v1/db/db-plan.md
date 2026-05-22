# XTDB Database Schema Plan - Apriary Summary

## Overview

This document provides the complete XTDB database schema for Apriary Summary, an MVP beekeeping work summary automation application. The schema uses XTDB's document database model with Datalog queries and Malli for schema validation in Clojure.

**Technology Stack**: XTDB 1.24, Clojure 1.12, Biff, Malli

---

## 1. Entity Definitions and Data Types

### 1.1 User Entity

**Purpose**: Represents application users with authentication credentials and profile information.

**XTDB Document Type**: `:db/doc-type "user"`

**Attributes**:

| Attribute | Data Type | Constraints | Purpose |
|-----------|-----------|-------------|---------|
| `xt/id` | UUID | Primary Key, Required | Unique identifier (XTDB built-in) |
| `user/id` | UUID | Required, Unique | Application-level user ID |
| `user/email` | String | Required, Unique | User's email address for authentication |
| `user/joined-at` | Instant | Required | Account creation timestamp |

**Status**: Already implemented in `src/com/apriary/schema.clj`

**Malli Schema**:
```clojure
[:map {:closed true}
 [:xt/id :uuid]
 [:user/id :uuid]
 [:user/email :string]
 [:user/joined-at inst?]]
```

**Row-Level Security**: Root entity for all data isolation; all other entities reference `user/id`

---

### 1.2 Generation Entity

**Purpose**: Tracks each OpenRouter API request batch and AI-generated summary proposals. Serves as audit trail for AI operations and metrics collection.

**XTDB Document Type**: `:db/doc-type "generation"`

**Attributes**:

| Attribute | Data Type | Constraints | Purpose |
|-----------|-----------|-------------|---------|
| `xt/id` | UUID | Primary Key, Required | XTDB document identifier |
| `generation/id` | UUID | Required, Unique | Application-level generation batch ID |
| `generation/user-id` | UUID | Required | FK to User; enforces RLS |
| `generation/model` | String | Required, Non-empty | AI model used (e.g., "gpt-4-turbo", "claude-3-sonnet") |
| `generation/generated-count` | Integer | Required, ≥0 | Total number of summary proposals returned by OpenRouter |
| `generation/accepted-unedited-count` | Integer | Required, ≥0 | Count of proposals accepted without modification |
| `generation/accepted-edited-count` | Integer | Required, ≥0 | Count of proposals accepted after user edits |
| `generation/duration-ms` | Integer | Required, ≥0 | Time taken to generate proposals in milliseconds |
| `generation/created-at` | Instant | Required | Timestamp when generation request was initiated |
| `generation/updated-at` | Instant | Required | Timestamp of latest update to counters |

**Key Business Rules**:
- One generation = one CSV import session = one OpenRouter API call
- Counters track user engagement with AI proposals
- Provides raw data for Metryka 1 (AI Quality) and Metryka 2 (AI Adoption)
- Serves as audit trail for generation operations (RF-010)

**Relationships**:
- **1-to-Many with Summary**: One generation can be referenced by multiple summaries (via `summary/generation-id`)
- **Many-to-One with User**: Many generations belong to one user

**Malli Schema**:
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

**Query Patterns**:
```clojure
;; Find all generations for a user
{:find '[?g ?model ?created]
 :where '[[?g :generation/user-id ?user-id]
          [?g :generation/model ?model]
          [?g :generation/created-at ?created]]}

;; Calculate acceptance metrics
{:find '[?g (sum ?accepted)]
 :where '[[?g :generation/user-id ?user-id]
          [?g :generation/accepted-unedited-count ?unedited]
          [?g :generation/accepted-edited-count ?edited]
          [(+ ?unedited ?edited) ?accepted]]}
```

---

### 1.3 Summary Entity

**Purpose**: Stores finalized summary records with flexible source tracking and optional metadata. Supports both AI-generated and manually-created summaries with full CRUD operations.

**XTDB Document Type**: `:db/doc-type "summary"`

**Attributes**:

| Attribute | Data Type | Constraints | Purpose |
|-----------|-----------|-------------|---------|
| `xt/id` | UUID | Primary Key, Required | XTDB document identifier |
| `summary/id` | UUID | Required, Unique | Application-level summary ID |
| `summary/user-id` | UUID | Required | FK to User; enforces RLS |
| `summary/generation-id` | UUID | Optional | FK to Generation; null for manually-created summaries |
| `summary/source` | Keyword | Required, Enum | Source categorization: `:ai-full`, `:ai-partial`, `:manual` |
| `summary/created-at` | Instant | Required | Timestamp when summary was created |
| `summary/updated-at` | Instant | Required | Timestamp of latest modification |
| `summary/hive-number` | String | Optional, Blank-allowed | Hive identifier (can be empty string) |
| `summary/observation-date` | String | Optional, Blank-allowed | Date of observation in DD-MM-YYYY format (can be empty) |
| `summary/special-feature` | String | Optional, Blank-allowed | Special notes or features (can be empty) |
| `summary/content` | String | Required, Non-empty | The actual summary text (AI-generated or user-entered) |

**Key Business Rules**:
- `source` field enables tracking of user engagement with AI proposals
- Flexible optional fields support workflow where users complete metadata after creation
- All fields except `id`, `user-id`, `source`, `content`, and timestamps can be null/empty
- Supports inline editing (US-005, US-006) with immediate persistence
- No version history maintained (no soft-deletes or changelogs in MVP)
- Permanent deletion supported (US-008)

**Source Values**:
- `:ai-full` - AI proposal accepted without any edits
- `:ai-partial` - AI proposal edited before acceptance
- `:manual` - Manually created by user (not AI-generated)

**Relationships**:
- **Many-to-One with User**: Many summaries belong to one user
- **Many-to-One with Generation**: Many summaries can reference one generation batch (optional)

**Malli Schema**:
```clojure
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

**Query Patterns**:
```clojure
;; Find all summaries for a user, sorted newest first
{:find '[?s ?created ?hive ?content ?source]
 :where '[[?s :summary/user-id ?user-id]
          [?s :summary/created-at ?created]
          [?s :summary/hive-number ?hive]
          [?s :summary/content ?content]
          [?s :summary/source ?source]]
 :order-by [[:created-at :desc]]}

;; Find AI-generated summaries for a generation batch
{:find '[?s ?content]
 :where '[[?s :summary/generation-id ?gen-id]
          [?s :summary/content ?content]]}

;; Count summaries by source for metrics
{:find '[?source (count ?s)]
 :where '[[?s :summary/user-id ?user-id]
          [?s :summary/source ?source]]}

;; Find accepted summaries (Metryka 1 calculation)
{:find '[(count ?s)]
 :where '[[?s :summary/user-id ?user-id]
          [?s :summary/source ?source]
          [(member? [?source :ai-full :ai-partial])]]}
```

---

## 2. Data Relationships and Cardinality

### Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        USER                                 │
│  (xt/id, user/id, email, joined-at)              │
│                      (1)                                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        │ (1)                         │ (1)
        ▼                             ▼
┌──────────────────────┐  ┌──────────────────────┐
│    GENERATION        │  │      SUMMARY         │
│       (Many)         │  │      (Many)          │
│                      │  │                      │
│  • id                │  │  • id                │
│  • user-id (FK)      │  │  • user-id (FK)      │
│  • model             │  │  • generation-id     │
│  • generated-count   │  │    (FK, optional)    │
│  • accepted-counts   │  │  • source (enum)     │
│  • duration-ms       │  │  • hive-number       │
│  • timestamps        │  │  • observation-date  │
│                      │  │  • special-feature   │
│                      │  │  • content           │
│                      │  │  • timestamps        │
└──────────────────────┘  └──────────────────────┘
                       │
                       │ (1-to-Many)
                       │ generation-id
                       │
                       └─────────────────────────
```

### Cardinality Matrix

| From | To | Cardinality | Foreign Key | Constraint |
|------|----|-----------|-|---------|
| User | Generation | 1-to-Many | `generation/user-id` → `user/id` | User must exist |
| User | Summary | 1-to-Many | `summary/user-id` → `user/id` | User must exist |
| Generation | Summary | 1-to-Many | `summary/generation-id` → `generation/id` | Optional; if present, generation must exist and belong to same user |

---

## 3. Data Validation Rules

### User Entity Validation

- **Email**: Non-empty string, should follow email format (validated by auth system)
- **joined-at**: Valid instant timestamp

### Generation Entity Validation

**Required Validations**:
- `model`: Non-empty string
- `generated-count`: Non-negative integer ≥ 0
- `accepted-unedited-count`: Non-negative integer ≥ 0
- `accepted-edited-count`: Non-negative integer ≥ 0
- `duration-ms`: Non-negative integer ≥ 0
- `user-id`: Valid UUID, must reference existing user
- `created-at`, `updated-at`: Valid instant timestamps

**Business Rule Validations**:
- `accepted-unedited-count + accepted-edited-count ≤ generated-count`
- `updated-at ≥ created-at`

### Summary Entity Validation

**Required Fields**:
- `user-id`: Valid UUID, must reference existing user
- `source`: Must be one of `:ai-full`, `:ai-partial`, `:manual`
- `content`: Non-empty string; max 50,000 characters recommended
- `created-at`, `updated-at`: Valid instant timestamps

**Optional Fields** (nullable/blank-allowed):
- `generation-id`: If provided, must reference valid generation record owned by same user
- `hive-number`: Optional string, can be empty or missing
- `observation-date`: Optional string in DD-MM-YYYY format or empty
- `special-feature`: Optional free-form text

**Business Rule Validations**:
- `updated-at ≥ created-at`
- If `source` is `:ai-full` or `:ai-partial`, `generation-id` must be present and non-null
- If `source` is `:manual`, `generation-id` should be null
- CSV import validation: observation text must be 50-10,000 characters after trimming

---

## 4. Row-Level Security (RLS) Implementation

**Strategy**: Application-level RLS enforced in Biff middleware

**Rules**:
1. Every entity has a `user-id` attribute
2. All queries must filter by `user-id` of the authenticated user
3. Before any read/update/delete operation, verify that the document's `user-id` matches the requester's `user-id`
4. Never expose data across user boundaries

**Middleware Implementation Pattern** (Clojure pseudocode):
```clojure
(defn with-user-id-filter [request query]
  (let [user-id (get-in request [:session :user-id])]
    (assoc query :where
           (conj (:where query)
                 [(name (first-entity query)) :???/user-id user-id]))))

(defn enforce-rls [entity-type requested-id user-id]
  (let [doc (xt/entity db requested-id)]
    (when (and doc (= (:???/user-id doc) user-id))
      doc)))
```

---

## 5. CRUD Operations Mapping

### Create Operations

#### 5.1 CSV Import Workflow (RF-002, RF-003, US-002)

**Step 1**: User uploads CSV file
- File format: UTF-8 encoding, ';' separator, with header row
- Expected columns: `observation`, `hive_number` (optional), `observation_date` (optional), `special_feature` (optional)

**Step 2**: System validates rows (RF-004)
- Trim `observation` field
- Check: 50 ≤ length(`observation`) ≤ 10,000
- Ignore rows with invalid `observation`
- Warn user about rejected rows

**Step 3**: Send valid rows to OpenRouter (RF-005)
- Batch request to OpenRouter with validated observations
- Record start time

**Step 4**: Receive AI-generated proposals
- Process response from OpenRouter
- Record end time

**Step 5**: Create Generation record
```clojure
{:generation/id (java.util.UUID/randomUUID)
 :generation/user-id current-user-id
 :generation/model "selected-model"
 :generation/generated-count (count valid-rows)
 :generation/accepted-unedited-count 0
 :generation/accepted-edited-count 0
 :generation/duration-ms duration
 :generation/created-at (java.time.Instant/now)
 :generation/updated-at (java.time.Instant/now)}
```

**Step 6**: Create Summary records for each proposal
```clojure
{:summary/id (java.util.UUID/randomUUID)
 :summary/user-id current-user-id
 :summary/generation-id generation-id
 :summary/source :ai-full  ;; Initially unaccepted
 :summary/hive-number hive-from-csv
 :summary/observation-date date-from-csv
 :summary/special-feature feature-from-csv
 :summary/content generated-proposal
 :summary/created-at (java.time.Instant/now)
 :summary/updated-at (java.time.Instant/now)}
```

#### 5.2 Manual Summary Creation (US-009)

**User Action**: Fills form with:
- `hive-number` (optional, can be blank)
- `observation-date` (optional, DD-MM-YYYY format or blank)
- `special-feature` (optional, can be blank)
- `content` (required, user-entered text)

**Create Record**:
```clojure
{:summary/id (java.util.UUID/randomUUID)
 :summary/user-id current-user-id
 :summary/generation-id nil
 :summary/source :manual
 :summary/hive-number user-input-or-empty
 :summary/observation-date user-input-or-empty
 :summary/special-feature user-input-or-empty
 :summary/content user-input
 :summary/created-at (java.time.Instant/now)
 :summary/updated-at (java.time.Instant/now)}
```

### Read Operations

#### 5.3 List User Summaries (US-004, RF-008)

**Query**:
```clojure
{:find '[?s ?created ?hive ?content ?source]
 :where '[[?s :summary/user-id user-id]
          [?s :summary/created-at ?created]
          [?s :summary/hive-number ?hive]
          [?s :summary/content ?content]
          [?s :summary/source ?source]]
 :order-by [[:created-at :desc]]}
```

**Display Fields**:
- `created-at`: Timestamp
- `hive-number`: Hive identifier (if available)
- `content`: Text preview (truncated to ~200 characters)
- `source`: Source indicator (`:ai-full`, `:ai-partial`, `:manual`)

#### 5.4 Get Single Summary

**Query**:
```clojure
(xt/entity db summary-id)
```

**Access Control**: Verify `summary/user-id` matches authenticated user

### Update Operations

#### 5.5 Inline Edit - Summary Metadata (US-005, RF-008)

**Editable Fields**:
- `summary/hive-number`
- `summary/observation-date`
- `summary/special-feature`

**Update Operation**:
```clojure
{:db/id summary-id
 :summary/field new-value
 :summary/updated-at (java.time.Instant/now)}
```

**Validation**:
- `observation-date` format: DD-MM-YYYY (if provided)
- All fields can be set to nil/empty

#### 5.6 Full Edit - Summary Content (US-006, RF-008)

**Editable Field**:
- `summary/content`

**Update Operation**:
```clojure
{:db/id summary-id
 :summary/content new-content
 :summary/updated-at (java.time.Instant/now)}
```

**Validation**:
- `content`: Non-empty string
- No version history maintained

#### 5.7 Update Generation Counters

**When Summary Accepted** (US-007, RF-009):
- If `source` was `:ai-full`: increment `accepted-unedited-count`
- If source will become `:ai-partial` (edited): increment `accepted-edited-count`

**Update Generation Record**:
```clojure
{:db/id generation-id
 :generation/accepted-unedited-count (+ current-value 1)
 :generation/updated-at (java.time.Instant/now)}
```

### Delete Operations

#### 5.8 Delete Summary (US-008, RF-008)

**Operation**: Permanent deletion (no soft-delete in MVP)

**XTDB Operation**:
```clojure
{:db/op :delete
 :xt/id summary-id}
```

**Access Control**: Verify `summary/user-id` matches authenticated user before deletion

---

## 6. Success Metrics Implementation

### Metryka 1: AI Quality (Acceptance Rate)

**Definition**: Percentage of AI-generated summaries that were accepted (with or without edits)

**Formula**: 
```
(Count of summaries with source :ai-full or :ai-partial) / 
(Total count of summaries with generation-id ≠ null) × 100%
```

**Query for Metryka 1**:
```clojure
;; Find accepted AI summaries
{:find '[(count ?s)]
 :where '[[?s :summary/user-id user-id]
          [?s :summary/source ?source]
          [(member? [?source :ai-full :ai-partial])]]}

;; Find total AI summaries
{:find '[(count ?s)]
 :where '[[?s :summary/user-id user-id]
          [?s :summary/generation-id ?gen-id]
          [(some? ?gen-id)]]}
```

**Target for MVP**: ≥ 75%

### Metryka 2: AI Adoption

**Definition**: Percentage of newly created summaries that use AI (vs. manual creation)

**Formula**:
```
(Count of summaries with generation-id ≠ null) / 
(Total count of all summaries) × 100%
```

**Query for Metryka 2**:
```clojure
;; Find AI-generated summaries (with generation-id)
{:find '[(count ?s)]
 :where '[[?s :summary/user-id user-id]
          [?s :summary/generation-id ?gen-id]
          [(some? ?gen-id)]]}

;; Find all summaries
{:find '[(count ?s)]
 :where '[[?s :summary/user-id user-id]]}
```

**Target for MVP**: ≥ 75%

---

## 7. Database Design Decisions and Rationale

### 7.1 Choice of XTDB Document Database

**Decision**: Use XTDB as document database with Datalog queries

**Rationale**:
- Flexible schema allows optional fields and future extensions
- Datalog queries provide powerful, composable query capabilities
- Document model naturally maps to domain entities
- Built-in temporal queries support for audit trail (RF-010)
- Easy integration with Biff and Clojure ecosystem

### 7.2 Generation Entity as Audit Trail

**Decision**: Maintain Generation records with counters rather than separate audit log table

**Rationale**:
- Reduces storage overhead compared to event table per operation
- Counters provide immediate metrics without complex aggregations
- Creation/update timestamps serve as audit trail timestamps
- Aligns with business requirement to track acceptance metrics (RF-009, RF-010)
- Supports efficient queries for success metrics (Metryka 1, Metryka 2)

### 7.3 Source Enum for Summaries

**Decision**: Use `:source` enum field with three values (`:ai-full`, `:ai-partial`, `:manual`)

**Rationale**:
- Enables clear distinction between AI-generated (with/without edits) and manual content
- Supports user engagement tracking without separate acceptance table
- Simplifies queries for metrics calculations
- Handles the workflow where users can choose to accept, edit, or reject AI proposals
- Provides visibility into which content came from AI vs. manual entry

### 7.4 Optional Generation-ID for Summaries

**Decision**: `summary/generation-id` is optional and nullable

**Rationale**:
- Supports both CSV import workflow (generation-id present) and manual creation (generation-id null)
- Enables Metryka 2 calculation: distinguish AI-assisted vs. manual-only summaries
- Maintains referential integrity: if provided, generation must exist and belong to same user
- Natural representation of the product requirement (US-009: manual summary creation without generation batch)

### 7.5 Timestamps: created-at vs. updated-at

**Decision**: Track both creation and update timestamps for all entities

**Rationale**:
- `created-at`: Original submission date (immutable after creation)
- `updated-at`: Latest modification date (changes with edits)
- Supports sorting by creation date (US-004 requirement: newest first)
- Enables audit trail showing when content was last modified (RF-010)
- Prevents confusion about timeline of changes (no version history, so timestamps are critical)

### 7.6 No Soft-Delete in MVP

**Decision**: Permanent deletion only; no soft-delete mechanism

**Rationale**:
- Simplifies MVP implementation
- Supports hard requirement (US-008: permanent deletion)
- Reduces schema complexity
- Can be added in future iterations (out of scope for MVP)
- XTDB's temporal features can still provide audit via transaction history if needed later

### 7.7 RLS via Application Layer

**Decision**: Implement Row-Level Security in Biff middleware, not database-level

**Rationale**:
- XTDB does not have built-in column-level security
- Application-level RLS with `user-id` checks is simpler to implement and audit
- All queries filtered by `user-id` at query construction time
- Reduces risk of accidental data leakage through proper middleware design
- Easier to test and debug compared to database-level rules

### 7.8 String Fields for Dates

**Decision**: Store `observation-date` as String in DD-MM-YYYY format (not as Instant)

**Rationale**:
- User input from CSV is in DD-MM-YYYY format
- Field is optional and can be blank
- Allows flexible handling of partial/invalid dates without parsing errors
- Simpler UI: no timezone concerns or date picker complexity in MVP
- Can be validated on application layer before storage
- Can be upgraded to Instant type in future if needed

### 7.9 Datalog Queries Over Raw Lookups

**Decision**: Use Datalog queries for all non-trivial data retrieval

**Rationale**:
- Aligns with XTDB philosophy and best practices
- Queries are composable and testable
- Naturally supports RLS filtering (add where clause for user-id)
- Efficient for aggregate queries (metrics calculations)
- Supports complex filtering without procedural code

---

## 8. Schema Validation with Malli

All entities use Malli schemas with:
- `:closed true` for strict validation (only declared keys allowed)
- `:optional true` for nullable/optional fields
- Type specifications for all attributes
- `inst?` type for timestamps (Clojure instants)
- `:uuid` type for all ID fields
- `[:enum :ai-full :ai-partial :manual]` for source field

### Complete Malli Schemas

```clojure
(ns com.apriary.schema
  (:require [malli.core :as m]))

(def schema
  {;; USER ENTITY (existing)
   :user/id :uuid
   :user
   [:map {:closed true}
    [:xt/id :uuid]
    [:user/id :uuid]
    [:user/email :string]
    [:user/joined-at inst?]]

   ;; GENERATION ENTITY
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

   ;; SUMMARY ENTITY
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
    [:summary/content :string]]})

(def module
  {:schema schema})
```

---



---

## 9. Query Examples and Usage Patterns

### Find all summaries for current user, newest first
```clojure
(xt/q db
  '{:find [?s ?created ?source ?content]
    :where [[?s :summary/user-id user-id]
            [?s :summary/created-at ?created]
            [?s :summary/source ?source]
            [?s :summary/content ?content]]
    :order-by [[:created-at :desc]]})
```

### Find summaries from a specific generation batch
```clojure
(xt/q db
  '{:find [?s ?content ?source]
    :where [[?s :summary/generation-id generation-id]
            [?s :summary/content ?content]
            [?s :summary/source ?source]]})
```

### Calculate AI acceptance rate (Metryka 1)
```clojure
(let [accepted (count (xt/q db
                       '{:find [?s]
                         :where [[?s :summary/user-id user-id]
                                 [?s :summary/source ?source]
                                 [(member? [?source :ai-full :ai-partial])]]}))
      total-ai (count (xt/q db
                       '{:find [?s]
                         :where [[?s :summary/user-id user-id]
                                 [?s :summary/generation-id ?gen-id]
                                 [(some? ?gen-id)]]}))]
  (if (zero? total-ai)
    0
    (* 100 (/ accepted total-ai))))
```

### Calculate AI adoption rate (Metryka 2)
```clojure
(let [ai-summaries (count (xt/q db
                           '{:find [?s]
                             :where [[?s :summary/user-id user-id]
                                     [?s :summary/generation-id ?gen-id]
                                     [(some? ?gen-id)]]}))
      all-summaries (count (xt/q db
                            '{:find [?s]
                              :where [[?s :summary/user-id user-id]]}))]
  (if (zero? all-summaries)
    0
    (* 100 (/ ai-summaries all-summaries))))
```

---

## 10. Compliance with Requirements

### Functional Requirements Coverage

| Requirement | Entity | Implementation | Status |
|-------------|--------|-----------------|--------|
| RF-001 | User, All Entities | `user-id` field + middleware RLS | ✅ |
| RF-002 | Generation, Summary | CSV parser + batch creation | ✅ |
| RF-003 | Generation, Summary | One row = one summary record | ✅ |
| RF-004 | Summary (create flow) | Validation: 50-10k chars | ✅ |
| RF-005 | Generation | OpenRouter integration | ✅ |
| RF-006 | Summary | `content` field format + metadata | ✅ |
| RF-007 | Summary | Optional fields: hive-number, observation-date | ✅ |
| RF-008 | Summary | CRUD: create, read, update, delete | ✅ |
| RF-009 | Generation (counter) | `accepted-unedited-count`, `accepted-edited-count` | ✅ |
| RF-010 | Generation, Summary | Timestamps + source tracking | ✅ |

### User Story Coverage

| Story | Entity | Implementation | Status |
|-------|--------|-----------------|--------|
| US-001 | User | Auth system integration | ✅ |
| US-002 | Generation, Summary | CSV import + batch creation | ✅ |
| US-003 | Summary (validation) | 50-10k character check | ✅ |
| US-004 | Summary | Read with sorting | ✅ |
| US-005 | Summary | Update metadata fields | ✅ |
| US-006 | Summary | Update content | ✅ |
| US-007 | Generation | Counter increment on acceptance | ✅ |
| US-008 | Summary | Delete operation | ✅ |
| US-009 | Summary | Manual create (source: :manual) | ✅ |

---

## Summary

The Apriary Summary XTDB schema provides a complete, minimal, but comprehensive data model for the MVP:

- **User**: Authentication and RLS root
- **Generation**: AI request batch tracking and metrics foundation
- **Summary**: Full CRUD support with flexible source tracking

**Key Design Principles**:
✅ Document-oriented (XTDB native)
✅ User isolation via application-level RLS
✅ Metrics-ready for success tracking
✅ Audit-trail via timestamps
✅ Flexible optional fields
✅ Clear enumeration for source tracking
✅ Ready for CSV import workflows
✅ Support for both AI-assisted and manual workflows
