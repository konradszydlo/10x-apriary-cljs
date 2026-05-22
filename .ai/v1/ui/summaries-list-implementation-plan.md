# View Implementation Plan: Summaries List Section

## 1. Overview

The Summaries List Section is the core component of the Apiary Summary application, displaying user summaries in a responsive grid layout. It combines CSV import results display, summary management, and acceptance tracking. The view supports inline editing, content expansion, individual and bulk acceptance of AI-generated summaries, and permanent deletion. Summaries are grouped by generation batch (for AI-imported summaries) with generation headers providing metadata and bulk accept functionality. The implementation uses server-side rendering with htmx for dynamic interactions, following Biff framework patterns.

## 2. View Routing

**Primary Access Path:** `/` or `/summaries` (main page after login)

**Authentication Required:** Yes (session-based via Biff middleware)

**Access Control:** Row-Level Security (RLS) ensures users only see their own summaries

## 3. Component Structure

```
SummariesListSection (main container, server-rendered)
├── ErrorMessageArea (conditional, page-level errors)
├── EmptyState (conditional, shown when summaries.length === 0)
└── SummariesContent (conditional, shown when summaries exist)
    ├── GenerationGroup (multiple, for AI-generated batches)
    │   ├── GenerationGroupHeader
    │   │   ├── GenerationMetadata
    │   │   └── BulkAcceptButton
    │   └── SummaryCardsGrid
    │       └── SummaryCard (multiple)
    │           ├── CardHeader
    │           │   ├── SourceBadge
    │           │   ├── MetadataFields (inline editable)
    │           │   └── ActionButtons
    │           ├── CardBody
    │           │   ├── SpecialFeatureTag (inline editable)
    │           │   └── ContentArea
    │           └── CardFooter
    │               ├── GenerationInfo (for AI summaries)
    │               └── ShowMoreToggle
    └── ManualSummariesSection (summaries without generation-id)
        └── SummaryCardsGrid
            └── SummaryCard (multiple)

ToastNotificationContainer (global, fixed position)
└── ToastNotification (multiple, stackable)
```

## 4. Component Details

### 4.1 SummariesListSection

**Component Description:**
Main container component that wraps the entire summaries list section. Handles the overall layout and decides whether to show EmptyState or the summaries content based on data availability.

**Main HTML Elements:**
- `<section>` with id `summaries-list-section`, semantic landmark
- Heading `<h2>` with text "Your Summaries" and optional count badge
- Container `<div>` for grid layout

**Child Components:**
- ErrorMessageArea (conditional)
- EmptyState (conditional)
- GenerationGroup (multiple, conditional)
- ManualSummariesSection (conditional)

**Handled Events:**
None (container component)

**Validation Conditions:**
None

**Types Required:**
```clojure
;; Server-side data structure
{:summaries [{:summary/id uuid
              :summary/generation-id uuid-or-nil
              :summary/source keyword  ;; :ai-full :ai-partial :manual
              :summary/hive-number string-or-nil
              :summary/observation-date string-or-nil
              :summary/special-feature string-or-nil
              :summary/content string
              :summary/created-at inst
              :summary/updated-at inst}]
 :generations [{:generation/id uuid
                :generation/created-at inst
                :generation/model string
                :generation/generated-count int
                :generation/accepted-unedited-count int
                :generation/accepted-edited-count int}]}
```

**Props:**
Server-rendered, receives data from controller:
- `summaries` - list of summary entities
- `generations` - map of generation-id to generation entity
- `user-id` - current authenticated user (implicit)

### 4.2 EmptyState

**Component Description:**
Displays a friendly message with icon and call-to-action when user has no summaries. Encourages users to create their first summary or import CSV data.

**Main HTML Elements:**
- Container `<div>` with centered text alignment, padding `py-12 px-6`
- Icon `<svg>` or icon element (beehive or document, size 64px-96px)
- Heading `<h3>` with text "No summaries yet" (text-xl, font-semibold)
- Paragraph `<p>` with descriptive text (text-gray-600)
- Link button to `/summaries/new` styled as primary CTA

**Child Components:**
None

**Handled Events:**
- Click on "New Summary" button → Navigate to `/summaries/new`

**Validation Conditions:**
None

**Types Required:**
None (static component)

**Props:**
None (static display)

### 4.3 GenerationGroupHeader

**Component Description:**
Header component for each AI generation batch, displaying import date, model used, summary count, and bulk accept functionality. Provides visual grouping for related summaries.

**Main HTML Elements:**
- Container `<div>` with blue-50 background, blue-500 left border (4px), padding `p-4`
- Flex layout container for horizontal alignment
- Heading `<h3>` with generation date "Import from [date]"
- Badge `<span>` for model name (e.g., "gpt-4-turbo")
- Text `<span>` with summary count "X summaries"
- Button or status text for bulk accept

**Child Components:**
- BulkAcceptButton (conditional, shown when not all accepted)

**Handled Events:**
None (events handled by child button)

**Validation Conditions:**
- Check if all summaries accepted: `accepted-unedited-count + accepted-edited-count >= generated-count`

**Types Required:**
```clojure
;; Generation entity
{:generation/id uuid
 :generation/created-at inst
 :generation/model string
 :generation/generated-count int
 :generation/accepted-unedited-count int
 :generation/accepted-edited-count int}

;; Derived values
{:all-accepted? boolean
 :acceptance-rate float}  ;; for future use
```

**Props:**
- `generation` - generation entity
- `summaries-count` - number of summaries in this group
- `all-accepted?` - derived boolean flag

### 4.4 BulkAcceptButton

**Component Description:**
Button that accepts all summaries in a generation batch with a single action. Visible only when there are unaccepted summaries in the generation. After all summaries are accepted, replaced with "All accepted ✓" text.

**Main HTML Elements:**
- Button element with primary styling when active
- htmx attributes:
  - `hx-post="/api/generations/{generation-id}/accept-summaries"`
  - `hx-target="closest .generation-group"`
  - `hx-swap="outerHTML"`
  - `hx-indicator="#bulk-accept-spinner-{generation-id}"`
- Loading spinner (hidden by default, shown during request)
- Text: "Accept All from This Import" or "All accepted ✓"

**Child Components:**
None

**Handled Events:**
- Click → Triggers POST request to bulk accept endpoint
- Success → Swaps entire generation group with updated HTML
- Error → Shows error toast

**Validation Conditions:**
- Button visible only if `all-accepted? = false`
- Generation must belong to current user (enforced by API RLS)

**Types Required:**
```clojure
;; Request (path parameter)
{:generation-id uuid}

;; Response
{:generation-id uuid
 :user-id uuid
 :summaries-accepted int
 :accepted-unedited int
 :accepted-edited int
 :message string}
```

**Props:**
- `generation-id` - UUID of generation to accept
- `all-accepted?` - boolean flag for rendering

### 4.5 SummaryCard

**Component Description:**
Individual summary card displaying all summary information with interactive elements for editing, accepting, and deleting. Supports both truncated and expanded content views, inline metadata editing, and full content editing.

**Main HTML Elements:**
- Card container `<div>` with border, rounded corners, shadow, padding
- Three main sections: header, body, footer
- CSS classes for responsive layout and hover effects
- Data attributes: `data-summary-id`, `data-content-expanded`, `data-editing`

**Child Components:**
- SourceBadge
- InlineEditableField (hive-number, observation-date, special-feature)
- ActionButtons
- ContentArea (preview or edit form)
- GenerationInfo (conditional)
- ShowMoreToggle (conditional)

**Handled Events:**
All events handled by child components

**Validation Conditions:**
All validation handled by child components

**Types Required:**
```clojure
;; Summary entity
{:summary/id uuid
 :summary/user-id uuid
 :summary/generation-id uuid-or-nil
 :summary/source keyword  ;; :ai-full :ai-partial :manual
 :summary/hive-number string-or-nil
 :summary/observation-date string-or-nil
 :summary/special-feature string-or-nil
 :summary/content string
 :summary/created-at inst
 :summary/updated-at inst}

;; Client-side state (via data attributes)
{:content-expanded? boolean
 :editing-field keyword-or-nil}  ;; :hive-number :observation-date :special-feature :content
```

**Props:**
- `summary` - summary entity
- `generation` - generation entity (optional, for AI summaries)
- `is-accepted?` - derived boolean from generation counters

### 4.6 SourceBadge

**Component Description:**
Visual indicator displaying the origin and edit status of a summary. Uses color-coded badges with icons to distinguish between AI-generated (unedited), AI-edited, and manual summaries.

**Main HTML Elements:**
- Inline badge `<span>` with color-coded background and text
- Icon `<svg>` or icon component (robot for AI, pencil for manual/edited)
- Text label
- ARIA label for accessibility

**Badge Variants:**
1. **ai-full**: Blue background (blue-100), blue text (blue-800), robot icon, "AI Generated"
2. **ai-partial**: Amber background (amber-100), amber text (amber-800), robot+pencil icon, "AI Edited"
3. **manual**: Gray background (gray-100), gray text (gray-800), pencil icon, "Manual"

**Child Components:**
None

**Handled Events:**
None (display only)

**Validation Conditions:**
None

**Types Required:**
```clojure
;; Source type
{:source keyword}  ;; :ai-full :ai-partial :manual
```

**Props:**
- `source` - keyword indicating summary source type
- `aria-label` - accessibility label (e.g., "AI Generated summary")

### 4.7 InlineEditableField

**Component Description:**
Reusable component for inline editing of metadata fields (hive-number, observation-date, special-feature). Displays as read-only text by default, transforms to input on click, and auto-saves on blur with visual feedback.

**Main HTML Elements:**

**Read-only state:**
- Container `<div>` with class `editable-field`
- Display span with hover effects (bg-gray-100 on hover)
- Small pencil icon (visible on hover)
- ARIA label for accessibility

**Edit state:**
- Input element `<input>` (text or date type)
- htmx attributes:
  - `hx-trigger="blur changed delay:100ms"`
  - `hx-patch="/api/summaries/{id}"`
  - `hx-target="closest .editable-field"`
  - `hx-swap="outerHTML"`
  - `hx-indicator="#spinner-{field-id}"`
- Loading spinner
- JavaScript event listener for Escape key (cancel edit)

**Child Components:**
None

**Handled Events:**
1. **Click** → Transform to input, focus input
2. **Blur** → Auto-save via htmx PATCH request
3. **Escape** → Cancel edit, revert to original value
4. **Enter** (optional) → Trigger blur event for save

**Validation Conditions:**

For **observation-date** field:
- Format: DD-MM-YYYY (regex: `^\\d{2}-\\d{2}-\\d{2}$`)
- Valid date check (e.g., not 32-01-2025)
- Can be empty/null
- Client validation: pattern attribute + JavaScript
- Server validation: authoritative

For **hive-number** and **special-feature**:
- No format restrictions
- Can be empty/null
- No client-side validation needed

**Types Required:**
```clojure
;; Request DTO
{:hive-number string-or-nil}
;; or
{:observation-date string-or-nil}
;; or
{:special-feature string-or-nil}

;; Response: Updated field HTML (outerHTML swap)
```

**Props:**
- `summary-id` - UUID for PATCH endpoint
- `field-name` - keyword (:hive-number, :observation-date, :special-feature)
- `current-value` - string or nil
- `field-label` - display label for accessibility

### 4.8 ContentArea

**Component Description:**
Displays summary content with two modes: preview (truncated) and edit (full textarea). Handles content expansion/collapse and full content editing with source tracking.

**Main HTML Elements:**

**Preview mode (default):**
- Container `<div>` with id `summary-content-{id}`
- Truncated text (first 150 characters) or full text if expanded
- "Show more" link if content > 150 chars and not expanded
- "Show less" link if expanded

**Edit mode:**
- Form container
- Textarea `<textarea>` with current content
- Character counter div (updates on input)
- Save button (primary styling)
- Cancel button (secondary styling)
- htmx attributes on form or save button:
  - `hx-patch="/api/summaries/{id}"`
  - `hx-target="#summary-card-{id}"`
  - `hx-swap="outerHTML"`

**Child Components:**
- CharacterCounter (inline element, updates via JavaScript)

**Handled Events:**
1. **Click Edit button** → Show edit mode (swap or toggle)
2. **Click Save** → Trigger PATCH request with content
3. **Click Cancel** → Revert to preview mode
4. **Click Show More** → Expand content (may trigger GET if not loaded)
5. **Click Show Less** → Collapse content (no API call)
6. **Input in textarea** → Update character counter

**Validation Conditions:**
- Content length: 50 ≤ trimmed length ≤ 50,000 characters
- Client validation: JavaScript character counter, disable save if invalid
- Visual feedback: Red counter if invalid, gray if valid
- Server validation: Authoritative, returns 400 if invalid

**Types Required:**
```clojure
;; Update request
{:content string}

;; Response includes:
{:id uuid
 :source keyword  ;; May change from :ai-full to :ai-partial
 :content string
 :updated-at inst
 :message string}  ;; e.g., "Summary updated and marked as ai-partial"
```

**Props:**
- `summary-id` - UUID
- `content` - full content string
- `source` - current source (for detecting changes)
- `is-expanded` - boolean for show more/less state

### 4.9 ActionButtons

**Component Description:**
Group of action buttons for summary operations: Edit (toggle content edit mode), Accept (for AI summaries), and Delete. Button visibility and state depend on summary source and acceptance status.

**Main HTML Elements:**

**Edit Button:**
- Button with pencil icon
- `hx-get="/api/summaries/{id}/edit"` or JavaScript toggle
- ARIA label: "Edit summary"

**Accept Button (conditional):**
- Visible only if `source` is `:ai-full` or `:ai-partial`
- Hidden if already accepted
- Button with "Accept" text or checkmark icon
- htmx attributes:
  - `hx-post="/api/summaries/{id}/accept"`
  - `hx-target="closest .accept-button-container"`
  - `hx-swap="outerHTML"`
- ARIA label: "Accept summary"

**Accepted Badge (replaces Accept button):**
- Non-interactive badge
- Green background (green-100), green text (green-800)
- Checkmark icon + "Accepted" text

**Delete Button:**
- Button with trash icon, danger styling (red)
- htmx attributes:
  - `hx-delete="/api/summaries/{id}"`
  - `hx-target="closest .summary-card"`
  - `hx-swap="outerHTML swap:1s"`  ;; Fade out animation
  - `hx-confirm="Delete this summary?"`
- ARIA label: "Delete summary"

**Child Components:**
None

**Handled Events:**
1. **Edit Click** → Toggle content edit mode (handled by parent or htmx)
2. **Accept Click** → POST to accept endpoint
3. **Delete Click** → Show confirmation, then DELETE request
4. **Accept Success** → Swap button for Accepted badge
5. **Delete Success** → Remove card from DOM with fade animation

**Validation Conditions:**
- Accept button visibility: `source in [:ai-full :ai-partial]` AND not already accepted
- Delete confirmation: Browser native confirm dialog via `hx-confirm`

**Types Required:**
```clojure
;; Accept request: none (just POST to endpoint)
;; Accept response:
{:id uuid
 :accepted-at inst
 :message string}

;; Delete request: none
;; Delete response: 204 No Content or 200 with message
```

**Props:**
- `summary-id` - UUID
- `source` - summary source type
- `is-accepted?` - boolean derived from generation data
- `can-edit?` - boolean (always true for MVP)
- `can-delete?` - boolean (always true for MVP)

### 4.10 ToastNotification

**Component Description:**
Floating notification component for displaying success, error, or info messages. Appears in fixed position at top-right, supports stacking, and can auto-dismiss or require manual close.

**Main HTML Elements:**
- Container `<div>` with fixed position (top-20, right-4, z-50)
- Card with colored background (green/red/blue), border, shadow
- Icon (checkmark for success, X for error, info icon for info)
- Message text
- Close button (X icon, optional)
- Auto-dismiss handled via htmx or JavaScript timeout

**Toast Types:**
1. **Success**: Green background (green-100), green border (green-500), checkmark icon, auto-dismiss after 3-5s
2. **Error**: Red background (red-100), red border (red-500), X icon, manual dismiss or 10s
3. **Info**: Blue background (blue-100), blue border (blue-500), info icon, auto-dismiss after 5s

**Child Components:**
None

**Handled Events:**
1. **Auto-dismiss** → Fade out and remove from DOM after timeout
2. **Manual close** → Click close button, remove from DOM
3. **Load** → Trigger auto-dismiss timer

**Validation Conditions:**
None

**Types Required:**
```clojure
;; Toast data
{:id string  ;; unique ID for htmx targeting
 :type keyword  ;; :success :error :info
 :message string
 :auto-dismiss? boolean
 :dismiss-after-ms int}  ;; e.g., 3000, 5000
```

**Props:**
- `type` - toast type (success/error/info)
- `message` - text to display
- `auto-dismiss?` - boolean
- `dismiss-after-ms` - milliseconds before auto-dismiss

**Server-side Integration:**
Toasts delivered via htmx OOB (out-of-band) swaps:
```html
<!-- In API response -->
<div hx-swap-oob="afterbegin:#toast-container">
  <!-- Toast HTML here -->
  <div id="toast-{uuid}"
       hx-trigger="load delay:3s"
       hx-swap="delete"
       class="...">
    <!-- Toast content -->
  </div>
</div>
```

## 5. Types

### 5.1 Domain Types (from XTDB/Clojure)

```clojure
;; Summary entity (database)
{:xt/id uuid
 :summary/id uuid
 :summary/user-id uuid
 :summary/generation-id uuid-or-nil
 :summary/source keyword  ;; :ai-full :ai-partial :manual
 :summary/created-at inst
 :summary/updated-at inst
 :summary/hive-number string-or-nil
 :summary/observation-date string-or-nil  ;; DD-MM-YYYY format
 :summary/special-feature string-or-nil
 :summary/content string}

;; Generation entity (database)
{:xt/id uuid
 :generation/id uuid
 :generation/user-id uuid
 :generation/model string
 :generation/generated-count int
 :generation/accepted-unedited-count int
 :generation/accepted-edited-count int
 :generation/duration-ms int
 :generation/created-at inst
 :generation/updated-at inst}
```

### 5.2 API DTOs (kebab-case JSON)

```clojure
;; Summary DTO (from API, kebab-case keys)
{:id string  ;; UUID as string
 :user-id string
 :generation-id string-or-nil
 :source string  ;; "ai-full" | "ai-partial" | "manual"
 :hive-number string-or-nil
 :observation-date string-or-nil  ;; DD-MM-YYYY
 :special-feature string-or-nil
 :content string
 :created-at string  ;; ISO 8601 timestamp
 :updated-at string}

;; Summary List Response
{:summaries [summary-dto]
 :total-count int
 :limit int
 :offset int}

;; Update Summary Request (at least one field)
{:hive-number string-or-nil  ;; optional
 :observation-date string-or-nil  ;; optional
 :special-feature string-or-nil  ;; optional
 :content string}  ;; optional

;; Accept Summary Response
{:id string
 :user-id string
 :generation-id string
 :source string
 :content string
 :accepted-at string  ;; ISO 8601
 :message string}

;; Bulk Accept Response
{:generation-id string
 :user-id string
 :summaries-accepted int
 :accepted-unedited int
 :accepted-edited int
 :message string}

;; Delete Response
;; 204 No Content or:
{:message string
 :id string}
```

### 5.3 View Models (for rendering logic)

```clojure
;; Generation group view model
{:generation generation-entity
 :summaries [summary-entity]
 :all-accepted? boolean  ;; derived
 :acceptance-rate float}  ;; derived, for future use

;; Summary card view model (enhanced with UI state)
{:summary summary-entity
 :generation generation-entity-or-nil
 :is-accepted? boolean  ;; derived from generation counters
 :content-preview string  ;; first 150 chars
 :show-more-button? boolean  ;; content length > 150
 :is-ai? boolean  ;; source in [:ai-full :ai-partial]
 :can-accept? boolean}  ;; is-ai? AND not is-accepted?

;; Toast notification
{:id string  ;; UUID for targeting
 :type keyword  ;; :success :error :info
 :message string
 :auto-dismiss? boolean
 :dismiss-after-ms int}

;; Error message
{:error string  ;; Human-readable message
 :code string  ;; ERROR_CODE constant
 :details map  ;; Additional error context
 :timestamp string}  ;; ISO 8601
```

### 5.4 Validation Types

```clojure
;; Date validation
{:format #"^\d{2}-\d{2}-\d{4}$"  ;; DD-MM-YYYY
 :valid-date? boolean}  ;; Actual date check (not 32-01-2025)

;; Content validation
{:min-length 50
 :max-length 50000
 :trimmed-length int
 :valid? boolean}

;; Field update state
{:field keyword  ;; :hive-number :observation-date :special-feature :content
 :original-value any
 :current-value any
 :is-saving? boolean
 :validation-error string-or-nil}
```

## 6. State Management

Since this is a Biff/htmx application with server-side rendering, state management follows a different pattern than traditional SPA frameworks:

### 6.1 Server-Side State (Authoritative)

**Database State (XTDB):**
- Summary records with all fields
- Generation records with counters
- User session data

**Request State:**
- Authenticated user-id from session
- Query parameters (sort-by, limit, offset, source filter)
- Request body for updates

**Response State:**
- HTML fragments for htmx swaps
- Toast notifications via OOB swaps
- Updated entity data in responses

### 6.2 Client-Side State (Minimal, Progressive Enhancement)

**DOM Data Attributes:**
```html
<!-- Content expansion state -->
<div data-content-expanded="false">...</div>

<!-- Edit mode tracking -->
<div data-editing="hive-number">...</div>

<!-- Loading states via htmx -->
<div hx-indicator="#spinner-123">...</div>
```

**CSS State Classes:**
```html
<!-- Hover states -->
<div class="editable-field hover:bg-gray-100">...</div>

<!-- Focus states -->
<input class="focus:ring-2 focus:ring-blue-500">
```

**JavaScript State (Vanilla JS, No Framework):**
```javascript
// Character counter state
let characterCount = 0;

// Edit mode toggle
let isEditing = false;

// Escape key handler state
let originalValue = null;
```

### 6.3 htmx State Management Patterns

**Auto-save Pattern:**
```html
<input
  hx-trigger="blur changed delay:100ms"
  hx-patch="/api/summaries/{id}"
  hx-target="closest .editable-field"
  hx-swap="outerHTML">
```

**Loading Indicators:**
```html
<button hx-indicator="#spinner-{id}">Save</button>
<span id="spinner-{id}" class="htmx-indicator">
  <svg class="animate-spin">...</svg>
</span>
```

**Out-of-Band Updates:**
```html
<!-- Server response includes multiple updates -->
<div id="summary-card-123">...</div>
<div hx-swap-oob="outerHTML:#source-badge-123">...</div>
<div hx-swap-oob="afterbegin:#toast-container">...</div>
```

### 6.4 State Synchronization

**Optimistic vs Pessimistic:**
- MVP uses **pessimistic updates** (wait for server confirmation)
- Loading indicators provide feedback during operations
- Server response is source of truth

**State Derivation:**
```clojure
;; Server-side state derivation
(defn derive-acceptance-state [generation summaries]
  {:all-accepted?
   (>= (+ (:generation/accepted-unedited-count generation)
          (:generation/accepted-edited-count generation))
       (:generation/generated-count generation))

   :acceptance-rate
   (/ (+ (:generation/accepted-unedited-count generation)
         (:generation/accepted-edited-count generation))
      (:generation/generated-count generation))})
```

**No Custom Hooks Needed:**
- Server-side rendering eliminates need for React-style hooks
- htmx handles AJAX and DOM updates declaratively
- Vanilla JavaScript for simple interactions (character counter, escape key)

## 7. API Integration

### 7.1 List Summaries (Initial Page Load)

**Endpoint:** `GET /api/summaries`

**Trigger:** Page load, after CSV import, after delete operation

**Request:**
```clojure
;; Query parameters (optional)
{:sort-by "created-at"  ;; "created-at" | "hive-number" | "source"
 :sort-order "desc"     ;; "asc" | "desc"
 :source nil            ;; nil | "ai-full" | "ai-partial" | "manual"
 :limit 50              ;; 1-100
 :offset 0}             ;; >= 0
```

**Response:**
```clojure
{:summaries [{:id "550e8400-e29b-41d4-a716-446655440001"
              :user-id "550e8400-e29b-41d4-a716-446655440000"
              :generation-id "550e8400-e29b-41d4-a716-446655440002"
              :source "ai-full"
              :hive-number "A-01"
              :observation-date "23-11-2025"
              :special-feature "Queen replaced"
              :content "Summary of hive activities..."
              :created-at "2025-11-23T10:30:00Z"
              :updated-at "2025-11-23T10:30:00Z"}]
 :total-count 42
 :limit 50
 :offset 0}
```

**Frontend Handling:**
- Server renders complete HTML on initial load
- No JavaScript required for initial display
- htmx refreshes on subsequent updates

**Error Handling:**
- 401: Redirect to login
- 500: Show error message area, offer retry

### 7.2 Update Summary Metadata (Inline Edit)

**Endpoint:** `PATCH /api/summaries/{id}`

**Trigger:** Blur event on inline editable field (after 100ms delay)

**Request:**
```clojure
;; Single field update
{:hive-number "A-01-Updated"}
;; or
{:observation-date "24-11-2025"}
;; or
{:special-feature "Updated feature"}
```

**Response:**
```html
<!-- Server returns updated field HTML for outerHTML swap -->
<div class="editable-field">
  <span>A-01-Updated</span>
  <svg class="checkmark animate-fade">✓</svg>
</div>
```

**htmx Configuration:**
```html
<input
  name="hive-number"
  hx-trigger="blur changed delay:100ms"
  hx-patch="/api/summaries/{id}"
  hx-include="this"
  hx-target="closest .editable-field"
  hx-swap="outerHTML"
  hx-indicator="#spinner-hive-{id}">
```

**Error Handling:**
- 400 (validation): Show error below field, keep in edit mode
- 404: Show error toast, revert to original
- 409 (conflict): Show error toast, prompt refresh

### 7.3 Update Summary Content

**Endpoint:** `PATCH /api/summaries/{id}`

**Trigger:** Click save button in content edit form

**Request:**
```clojure
{:content "Updated summary content with substantial text..."}
```

**Response:**
```clojure
{:id "550e8400-e29b-41d4-a716-446655440001"
 :source "ai-partial"  ;; Changed from "ai-full"
 :content "Updated summary content with substantial text..."
 :updated-at "2025-11-23T11:15:00Z"
 :message "Summary updated and marked as ai-partial"}
```

**htmx Configuration:**
```html
<form
  hx-patch="/api/summaries/{id}"
  hx-target="#summary-card-{id}"
  hx-swap="outerHTML">
  <textarea name="content">...</textarea>
  <button type="submit">Save</button>
</form>
```

**Server Response Includes:**
```html
<!-- Main target: entire card with updated content and badge -->
<div id="summary-card-{id}">...</div>

<!-- OOB swap for toast notification -->
<div hx-swap-oob="afterbegin:#toast-container">
  <div class="toast success">Summary updated</div>
</div>
```

**Error Handling:**
- 400 (content too short/long): Show error in form, disable save
- Client-side validation prevents submission if invalid

### 7.4 Delete Summary

**Endpoint:** `DELETE /api/summaries/{id}`

**Trigger:** Click delete button, after confirmation dialog

**Request:** None (ID in path)

**Response:**
- Status: 204 No Content
- Or: `{:message "Summary deleted successfully", :id "..."}`

**htmx Configuration:**
```html
<button
  hx-delete="/api/summaries/{id}"
  hx-target="closest .summary-card"
  hx-swap="outerHTML swap:1s"
  hx-confirm="Delete this summary?">
  Delete
</button>
```

**Frontend Handling:**
- Browser native confirm dialog via `hx-confirm`
- Card fades out over 1 second (CSS transition)
- Card removed from DOM
- Toast notification via OOB swap

**Error Handling:**
- 404: Summary already deleted, remove card anyway
- 500: Show error toast, keep card visible

### 7.5 Accept Summary

**Endpoint:** `POST /api/summaries/{id}/accept`

**Trigger:** Click accept button

**Request:** None (empty body or no body)

**Response:**
```clojure
{:id "550e8400-e29b-41d4-a716-446655440001"
 :user-id "550e8400-e29b-41d4-a716-446655440000"
 :generation-id "550e8400-e29b-41d4-a716-446655440002"
 :source "ai-full"
 :accepted-at "2025-11-23T11:20:00Z"
 :message "Summary accepted successfully"}
```

**htmx Configuration:**
```html
<button
  hx-post="/api/summaries/{id}/accept"
  hx-target="closest .accept-button-container"
  hx-swap="outerHTML">
  Accept
</button>
```

**Server Response:**
```html
<!-- Swap button for accepted badge -->
<div class="accepted-badge">
  <svg>✓</svg>
  <span>Accepted</span>
</div>

<!-- OOB: Update generation header if all now accepted -->
<div hx-swap-oob="outerHTML:#generation-header-{gen-id}">
  <!-- Updated header with "All accepted" status -->
</div>

<!-- OOB: Success toast -->
<div hx-swap-oob="afterbegin:#toast-container">
  <div class="toast success">Summary accepted</div>
</div>
```

**Error Handling:**
- 400 (manual summary): Should never happen (button not shown)
- 409 (already accepted): Button should show "Accepted" already

### 7.6 Bulk Accept Generation

**Endpoint:** `POST /api/generations/{generation-id}/accept-summaries`

**Trigger:** Click "Accept All from This Import" button

**Request:** None (generation ID in path)

**Response:**
```clojure
{:generation-id "550e8400-e29b-41d4-a716-446655440002"
 :user-id "550e8400-e29b-41d4-a716-446655440000"
 :summaries-accepted 14
 :accepted-unedited 8
 :accepted-edited 6
 :message "All summaries for generation accepted successfully"}
```

**htmx Configuration:**
```html
<button
  hx-post="/api/generations/{generation-id}/accept-summaries"
  hx-target="closest .generation-group"
  hx-swap="outerHTML">
  Accept All from This Import
</button>
```

**Server Response:**
```html
<!-- Entire generation group with updated state -->
<div class="generation-group">
  <!-- Header shows "All accepted ✓" -->
  <div class="generation-header">...</div>

  <!-- All summary cards show "Accepted" badge -->
  <div class="summary-cards-grid">
    <div class="summary-card">...</div>
  </div>
</div>

<!-- OOB: Success toast -->
<div hx-swap-oob="afterbegin:#toast-container">
  <div class="toast success">14 summaries accepted</div>
</div>
```

**Error Handling:**
- 403/404: Generation not found or not owned
- 500: Show error toast, keep UI unchanged

### 7.7 Get Full Content (Show More)

**Endpoint:** `GET /api/summaries/{id}`

**Trigger:** Click "Show more" button (if full content not already loaded)

**Request:** None

**Response:**
```clojure
{:id "550e8400-e29b-41d4-a716-446655440001"
 :content "Complete summary text..."
 ;; ... other fields
}
```

**htmx Configuration:**
```html
<button
  hx-get="/api/summaries/{id}"
  hx-target="#summary-content-{id}"
  hx-swap="innerHTML">
  Show more
</button>
```

**Alternative (No API Call):**
If full content already in DOM (hidden):
```html
<div id="summary-content-{id}">
  <div class="truncated">First 150 chars...</div>
  <div class="full hidden">Full content...</div>
  <button onclick="toggleContent('{id}')">Show more</button>
</div>
```

**Error Handling:**
- 404: Should not happen, show error toast
- 500: Show error toast, keep truncated view

## 8. User Interactions

### 8.1 Browse Summaries

**User Action:** User loads the main page after login

**Expected Outcome:**
- Page displays with "Your Summaries" heading
- Summaries grouped by generation (AI imports) at top
- Manual summaries (without generation-id) below
- Each group shows generation header with metadata
- Summary cards in responsive grid (1/2/3 columns)
- Sorted chronologically (newest first)

**Implementation:**
- Server-side query with RLS filter on user-id
- Datalog query groups by generation-id
- Server renders complete HTML structure
- No JavaScript required for initial display

### 8.2 Inline Edit Metadata Field

**User Action:** User hovers over hive number field

**Expected Outcome:**
- Background changes to light gray (bg-gray-100)
- Small pencil icon appears
- Cursor changes to pointer

**User Action:** User clicks on hive number field

**Expected Outcome:**
- Field transforms to text input
- Current value pre-populated
- Input receives focus
- User can type to change value

**User Action:** User types new value and tabs away (blur)

**Expected Outcome:**
- Auto-save triggers after 100ms delay
- Spinner icon appears next to field
- PATCH request sent to server
- Field swaps back to read-only with new value
- Green checkmark appears briefly (1-2 seconds)
- Toast notification: "Summary updated"

**User Action:** User presses Escape while editing

**Expected Outcome:**
- Field reverts to original value
- No API call made
- Field returns to read-only state

**Implementation:**
```html
<!-- Read-only state -->
<div class="editable-field group cursor-pointer hover:bg-gray-100">
  <span>A-01</span>
  <svg class="opacity-0 group-hover:opacity-100">✎</svg>
</div>

<!-- Edit state (swapped in) -->
<div class="editable-field">
  <input
    value="A-01"
    autofocus
    hx-trigger="blur changed delay:100ms"
    hx-patch="/api/summaries/{id}"
    hx-include="this"
    hx-target="closest .editable-field"
    hx-swap="outerHTML"
    onkeydown="if(event.key==='Escape') revertEdit(this)">
  <span id="spinner-{id}" class="htmx-indicator">⟳</span>
</div>
```

### 8.3 Edit Summary Content

**User Action:** User clicks "Edit" button in card header

**Expected Outcome:**
- Content area replaced with textarea
- Textarea shows full current content
- Character counter appears below (e.g., "523 / 50,000 characters")
- Save and Cancel buttons appear
- Edit button changes to "Cancel" or disappears

**User Action:** User types in textarea

**Expected Outcome:**
- Character counter updates in real-time
- Counter turns red if < 50 or > 50,000 chars
- Save button disabled if invalid length

**User Action:** User clicks Save (with valid content)

**Expected Outcome:**
- Loading indicator on save button
- PATCH request sent
- Entire card refreshes with updated content
- If source was "ai-full", badge changes to "ai-partial" (amber)
- Success toast: "Summary updated" (+ source change notice if applicable)
- Returns to read-only view

**User Action:** User clicks Cancel

**Expected Outcome:**
- Textarea hidden
- Original content displayed
- No API call made
- Returns to read-only view

**Implementation:**
```html
<!-- Edit button triggers -->
<button hx-get="/api/summaries/{id}/edit"
        hx-target="#content-area-{id}"
        hx-swap="innerHTML">
  Edit
</button>

<!-- Server returns edit form -->
<form hx-patch="/api/summaries/{id}"
      hx-target="#summary-card-{id}"
      hx-swap="outerHTML">
  <textarea name="content"
            oninput="updateCharCount(this)">...</textarea>
  <div id="char-counter">0 / 50,000 characters</div>
  <button type="submit">Save</button>
  <button type="button" onclick="cancelEdit()">Cancel</button>
</form>
```

### 8.4 Accept Summary

**User Action:** User clicks "Accept" button on AI-generated summary

**Expected Outcome:**
- Button shows loading state
- POST request to accept endpoint
- Button replaced with green "Accepted ✓" badge
- Generation header may update (if all now accepted)
- Success toast: "Summary accepted"

**Implementation:**
```html
<button
  hx-post="/api/summaries/{id}/accept"
  hx-target="closest .accept-button-container"
  hx-swap="outerHTML"
  hx-indicator="#accept-spinner-{id}">
  Accept
  <span id="accept-spinner-{id}" class="htmx-indicator">⟳</span>
</button>

<!-- Server response swaps to: -->
<div class="accepted-badge bg-green-100 text-green-800">
  <svg>✓</svg>
  <span>Accepted</span>
</div>
```

### 8.5 Bulk Accept Generation

**User Action:** User clicks "Accept All from This Import" button

**Expected Outcome:**
- Button shows loading state
- POST request to bulk accept endpoint
- Entire generation group refreshes
- All summary cards show "Accepted" badges
- Generation header shows "All accepted ✓" text (green)
- Success toast: "14 summaries accepted successfully"

**Implementation:**
```html
<button
  hx-post="/api/generations/{gen-id}/accept-summaries"
  hx-target="closest .generation-group"
  hx-swap="outerHTML"
  hx-indicator="#bulk-spinner-{gen-id}">
  Accept All from This Import
  <span id="bulk-spinner-{gen-id}" class="htmx-indicator">⟳</span>
</button>
```

### 8.6 Delete Summary

**User Action:** User clicks delete button (trash icon)

**Expected Outcome:**
- Browser confirmation dialog appears: "Delete this summary?"
- User must confirm or cancel

**User Action:** User clicks "OK" in confirmation

**Expected Outcome:**
- DELETE request sent to server
- Summary card fades out (1 second transition)
- Card removed from DOM
- Success toast: "Summary deleted"
- Generation header updates count if needed

**User Action:** User clicks "Cancel" in confirmation

**Expected Outcome:**
- No API call made
- Summary remains unchanged

**Implementation:**
```html
<button
  hx-delete="/api/summaries/{id}"
  hx-target="closest .summary-card"
  hx-swap="outerHTML swap:1s"
  hx-confirm="Delete this summary?">
  <svg>🗑</svg>
</button>
```

### 8.7 Show More / Show Less Content

**User Action:** User clicks "Show more" on truncated content

**Expected Outcome:**
- Full content displays (either from hidden div or API fetch)
- Button text changes to "Show less"
- data-content-expanded="true" set

**User Action:** User clicks "Show less"

**Expected Outcome:**
- Content truncates to first 150 chars
- Button text changes to "Show more"
- data-content-expanded="false" set
- No API call needed (content already loaded)

**Implementation (Client-side toggle):**
```html
<div id="content-{id}" data-expanded="false">
  <div class="preview">First 150 chars...</div>
  <div class="full hidden">Full content here...</div>
  <button onclick="toggleContent('{id}')">Show more</button>
</div>

<script>
function toggleContent(id) {
  const container = document.getElementById(`content-${id}`);
  const expanded = container.dataset.expanded === 'true';
  container.dataset.expanded = !expanded;
  container.querySelector('.preview').classList.toggle('hidden');
  container.querySelector('.full').classList.toggle('hidden');
  event.target.textContent = expanded ? 'Show more' : 'Show less';
}
</script>
```

**Alternative (API fetch if not loaded):**
```html
<button
  hx-get="/api/summaries/{id}"
  hx-target="#content-{id}"
  hx-swap="innerHTML">
  Show more
</button>
```

## 9. Conditions and Validation

### 9.1 Accept Button Visibility

**Condition:** Button shown only for AI-generated summaries that haven't been accepted

**Components Affected:** ActionButtons component

**Validation Logic:**
```clojure
;; Server-side check
(defn show-accept-button? [summary generation]
  (and
    ;; Is AI-generated
    (#{:ai-full :ai-partial} (:summary/source summary))
    ;; Not already accepted
    (not (accepted? summary generation))))

(defn accepted? [summary generation]
  ;; Check if summary counted in generation accepted totals
  ;; Implementation depends on tracking method
  ;; Option 1: Check if summary has :summary/accepted-at
  ;; Option 2: Query generation counters and compare
  (some? (:summary/accepted-at summary)))
```

**UI State Impact:**
- If `show-accept-button? = true`: Render "Accept" button
- If `show-accept-button? = false` AND is-ai?: Render "Accepted ✓" badge
- If source is `:manual`: Render nothing (no accept functionality)

### 9.2 Bulk Accept Button State

**Condition:** Button shown when not all summaries in generation are accepted

**Components Affected:** GenerationGroupHeader, BulkAcceptButton

**Validation Logic:**
```clojure
(defn all-accepted? [generation]
  (let [total-accepted (+ (:generation/accepted-unedited-count generation)
                         (:generation/accepted-edited-count generation))
        total-generated (:generation/generated-count generation)]
    (>= total-accepted total-generated)))
```

**UI State Impact:**
- If `all-accepted? = false`: Show "Accept All from This Import" button
- If `all-accepted? = true`: Show "All accepted ✓" text (green, non-interactive)

### 9.3 Content Length Validation

**Condition:** Content must be 50-50,000 characters after trimming whitespace

**Components Affected:** ContentEditForm, character counter

**Client-Side Validation:**
```javascript
function validateContentLength(textarea) {
  const trimmed = textarea.value.trim();
  const length = trimmed.length;
  const counter = document.getElementById('char-counter');
  const saveButton = document.getElementById('save-button');

  // Update counter display
  counter.textContent = `${length} / 50,000 characters`;

  // Validation
  if (length < 50) {
    counter.classList.add('text-red-600');
    counter.textContent = `Too short (${length} chars, minimum 50)`;
    saveButton.disabled = true;
  } else if (length > 50000) {
    counter.classList.add('text-red-600');
    counter.textContent = `Too long (${length} chars, maximum 50,000)`;
    saveButton.disabled = true;
  } else {
    counter.classList.remove('text-red-600');
    counter.classList.add('text-gray-600');
    saveButton.disabled = false;
  }
}

// Attach to textarea
textarea.addEventListener('input', (e) => validateContentLength(e.target));
```

**Server-Side Validation (authoritative):**
```clojure
(defn validate-content [content]
  (let [trimmed (str/trim content)
        length (count trimmed)]
    (when-not (<= 50 length 50000)
      (throw (ex-info "Content length invalid"
                      {:status 400
                       :code "VALIDATION_ERROR"
                       :field :content
                       :length length
                       :min 50
                       :max 50000})))))
```

**UI State Impact:**
- Valid (50-50,000): Gray counter, save button enabled
- Too short (< 50): Red counter with message, save button disabled
- Too long (> 50,000): Red counter with message, save button disabled

### 9.4 Date Format Validation

**Condition:** observation-date must match DD-MM-YYYY format or be empty

**Components Affected:** InlineEditableField (observation-date)

**Client-Side Validation:**
```javascript
function validateDate(input) {
  const value = input.value.trim();

  // Empty is valid
  if (value === '') return true;

  // Check format
  const formatRegex = /^\d{2}-\d{2}-\d{4}$/;
  if (!formatRegex.test(value)) {
    showFieldError(input, 'Invalid date format. Use DD-MM-YYYY');
    return false;
  }

  // Check actual date validity
  const [day, month, year] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);

  if (date.getDate() !== day ||
      date.getMonth() !== month - 1 ||
      date.getFullYear() !== year) {
    showFieldError(input, 'Invalid date. Please check day/month/year.');
    return false;
  }

  return true;
}
```

**Server-Side Validation:**
```clojure
(defn validate-observation-date [date-str]
  (when (and date-str (not (str/blank? date-str)))
    (when-not (re-matches #"^\d{2}-\d{2}-\d{4}$" date-str)
      (throw (ex-info "Invalid date format"
                      {:status 400
                       :code "VALIDATION_ERROR"
                       :field :observation-date
                       :message "Date must be in DD-MM-YYYY format"})))
    ;; Additional date validity check
    ;; Parse and validate actual date...
    ))
```

**UI State Impact:**
- Valid or empty: Field saves on blur
- Invalid format: Error message below field, field stays in edit mode
- Invalid date: Error message below field, field stays in edit mode

### 9.5 Empty List Detection

**Condition:** No summaries exist for user

**Components Affected:** SummariesListSection, EmptyState

**Validation Logic:**
```clojure
(defn render-summaries-section [summaries]
  (if (empty? summaries)
    (render-empty-state)
    (render-summaries-content summaries)))
```

**UI State Impact:**
- If `(empty? summaries)`: Show EmptyState component
- If `(seq summaries)`: Show generation groups and summary cards

### 9.6 Manual Summary Restrictions

**Condition:** Manual summaries cannot be accepted (no generation association)

**Components Affected:** ActionButtons, Accept button

**Validation Logic:**
```clojure
(defn can-accept? [summary]
  (and
    ;; Must be AI-generated
    (#{:ai-full :ai-partial} (:summary/source summary))
    ;; Must have generation-id
    (some? (:summary/generation-id summary))))
```

**UI State Impact:**
- Manual summaries: No accept button rendered
- AI summaries: Accept button or Accepted badge rendered

### 9.7 Source Change on Content Edit

**Condition:** When user edits content of ai-full summary, source changes to ai-partial

**Components Affected:** ContentEditForm, SourceBadge

**Validation Logic:**
```clojure
;; Server-side automatic source update
(defn update-summary [summary updates]
  (let [content-changed? (contains? updates :content)
        current-source (:summary/source summary)
        new-source (if (and content-changed?
                           (= current-source :ai-full))
                    :ai-partial
                    current-source)]
    (merge summary
           updates
           {:summary/source new-source
            :summary/updated-at (now)})))
```

**UI State Impact:**
- Badge updates from blue "AI Generated" to amber "AI Edited"
- Server includes updated badge in response (OOB swap)
- Toast notification mentions source change: "Summary updated and marked as ai-partial"

### 9.8 Concurrent Edit Detection

**Condition:** Detect if summary was modified by another request (409 Conflict)

**Components Affected:** All edit operations

**Validation Logic:**
```clojure
;; Server-side check using updated-at timestamp
(defn check-concurrent-modification [summary-id expected-updated-at]
  (let [current (get-summary summary-id)
        current-updated (:summary/updated-at current)]
    (when-not (= current-updated expected-updated-at)
      (throw (ex-info "Summary modified by another request"
                      {:status 409
                       :code "CONFLICT"})))))
```

**UI State Impact:**
- 409 response: Show error toast "Summary was modified. Please refresh."
- Field reverts to edit mode or read-only (depending on implementation)
- User prompted to reload page

## 10. Error Handling

### 10.1 Network Errors

**Scenario:** Request fails due to network connectivity

**Handling:**
- htmx shows error state via CSS classes
- Error toast appears: "Network error. Please check your connection."
- Field/component reverts to previous state
- User can retry operation

**Implementation:**
```html
<!-- htmx error handling -->
<div hx-on::after-on-load="handleSuccess"
     hx-on::after-request-error="handleNetworkError">
  ...
</div>

<script>
function handleNetworkError(event) {
  showToast('error', 'Network error. Please check your connection.');
  // Revert UI state if needed
}
</script>
```

### 10.2 Validation Errors (400)

**Scenario:** Server rejects request due to validation failure

**Examples:**
- Content too short/long
- Invalid date format
- Empty request body

**Handling:**
- Field-level errors: Show error message below field, keep in edit mode
- Form-level errors: Show error at top of form
- Error toast with specific message
- Save/submit button remains disabled until fixed

**Implementation:**
```html
<!-- Server returns error HTML -->
<div class="editable-field">
  <input value="..." class="border-red-500">
  <div class="error-message text-red-600 text-sm">
    Invalid date format. Use DD-MM-YYYY
  </div>
</div>
```

### 10.3 Authentication Errors (401)

**Scenario:** Session expired or user not authenticated

**Handling:**
- Biff middleware automatically redirects to login page
- Session data cleared
- User must log in again

**Implementation:**
```clojure
;; Biff middleware handles automatically
(defn require-auth [handler]
  (fn [request]
    (if-let [user-id (get-in request [:session :user-id])]
      (handler request)
      {:status 302
       :headers {"Location" "/login"}})))
```

### 10.4 Authorization Errors (403)

**Scenario:** User attempts to access/modify resource they don't own

**Handling:**
- RLS violation should be rare (UI shouldn't show unauthorized actions)
- If occurs: Show error toast "Access denied"
- Log error server-side for investigation
- Don't leak that resource exists (return 404 instead)

**Implementation:**
```clojure
;; Server-side RLS check
(defn enforce-rls [entity user-id]
  (when-not (= (:entity/user-id entity) user-id)
    ;; Return 404 to not leak existence
    (throw (ex-info "Not found" {:status 404}))))
```

### 10.5 Not Found Errors (404)

**Scenario:** Summary or generation doesn't exist or was deleted

**Handling:**
- Show error toast: "Summary not found"
- Remove element from UI if appropriate
- Offer to refresh page

**Implementation:**
```javascript
// htmx response handler
htmx.on('htmx:responseError', (event) => {
  if (event.detail.xhr.status === 404) {
    showToast('error', 'Summary not found. It may have been deleted.');
    // Remove element if DELETE operation
    if (event.detail.requestConfig.verb === 'delete') {
      event.target.remove();
    }
  }
});
```

### 10.6 Conflict Errors (409)

**Scenario:** Concurrent modification or already accepted

**Handling:**
- Show error toast with specific message
- Prompt user to refresh page
- Don't auto-refresh (user may have unsaved work)

**Examples:**
- "Summary was modified by another request. Please refresh."
- "Summary already accepted"

**Implementation:**
```javascript
htmx.on('htmx:responseError', (event) => {
  if (event.detail.xhr.status === 409) {
    const message = JSON.parse(event.detail.xhr.responseText).error;
    showToast('error', message);
  }
});
```

### 10.7 Rate Limit Errors (429)

**Scenario:** Too many requests (relevant for list endpoint)

**Handling:**
- Show error toast: "Too many requests. Please wait X seconds."
- Extract retry-after from response headers
- Disable actions temporarily
- Auto-retry after delay (optional)

**Implementation:**
```javascript
htmx.on('htmx:responseError', (event) => {
  if (event.detail.xhr.status === 429) {
    const retryAfter = event.detail.xhr.getResponseHeader('Retry-After');
    showToast('error', `Too many requests. Please wait ${retryAfter} seconds.`);
  }
});
```

### 10.8 Server Errors (500)

**Scenario:** Internal server error during processing

**Handling:**
- Show error toast: "Something went wrong. Please try again."
- Log error details server-side
- UI reverts to previous state
- User can retry operation

**Implementation:**
```javascript
htmx.on('htmx:responseError', (event) => {
  if (event.detail.xhr.status === 500) {
    showToast('error', 'Something went wrong. Please try again.');
  }
});
```

### 10.9 Empty State (No Summaries)

**Scenario:** User has no summaries (not an error, but edge case)

**Handling:**
- Show EmptyState component with friendly message
- Provide clear call-to-action to create first summary
- No error message needed

**Implementation:**
```clojure
(defn render-summaries-list [summaries]
  (if (empty? summaries)
    [:div.empty-state
     [:svg.icon ...]
     [:h3 "No summaries yet"]
     [:p "Get started by importing CSV data or creating your first summary manually."]
     [:a.btn {:href "/summaries/new"} "+ New Summary"]]
    [:div.summaries-content ...]))
```

### 10.10 Validation Feedback (Client-Side)

**Scenario:** Client-side validation prevents submission

**Handling:**
- Immediate visual feedback (red border, error text)
- Disable submit/save button
- Clear, actionable error messages
- Server validation still authoritative

**Examples:**
- Character counter turns red with message
- Date field shows format hint
- Save button disabled with visual indication

**Implementation:**
```javascript
// Character counter example
function updateCharCounter(textarea) {
  const length = textarea.value.trim().length;
  const counter = document.getElementById('char-counter');
  const saveBtn = document.getElementById('save-btn');

  if (length < 50) {
    counter.className = 'text-red-600';
    counter.textContent = `Too short (${length} chars, minimum 50)`;
    saveBtn.disabled = true;
    saveBtn.className = 'btn-disabled';
  } else if (length > 50000) {
    counter.className = 'text-red-600';
    counter.textContent = `Too long (${length} chars, maximum 50,000)`;
    saveBtn.disabled = true;
    saveBtn.className = 'btn-disabled';
  } else {
    counter.className = 'text-gray-600';
    counter.textContent = `${length} / 50,000 characters`;
    saveBtn.disabled = false;
    saveBtn.className = 'btn-primary';
  }
}
```

## 11. Implementation Steps

### Step 1: Set Up Server-Side Data Layer

**Tasks:**
1. Ensure schema definitions are in place:
   - Summary entity with all required fields
   - Generation entity with counter fields
   - Proper indexes for efficient queries

2. Implement data query functions:
   ```clojure
   ;; In com.apriary.services.summary-service
   (defn list-summaries-with-generations [db user-id query-params]
     ;; XTDB query to fetch summaries with RLS filter
     ;; Join with generations table
     ;; Apply sorting and pagination
     ;; Return grouped structure
     )
   ```

3. Create view model transformation functions:
   ```clojure
   (defn summaries->view-model [summaries generations]
     ;; Group summaries by generation-id
     ;; Calculate derived fields (all-accepted?, etc.)
     ;; Return hierarchical structure for rendering
     )
   ```

**Deliverables:**
- Working data queries with RLS
- View model transformation functions
- Unit tests for data layer

### Step 2: Create Hiccup Templates for Components

**Tasks:**
1. Create main section template:
   ```clojure
   ;; In com.apriary.ui.summaries-list
   (defn summaries-list-section [{:keys [summaries generations user-id]}]
     [:section#summaries-list-section
      [:h2.section-heading "Your Summaries"
       [:span.count-badge (count summaries)]]
      (if (empty? summaries)
        (empty-state)
        (summaries-content summaries generations))])
   ```

2. Create EmptyState component:
   ```clojure
   (defn empty-state []
     [:div.empty-state.text-center.py-12.px-6
      [:svg.icon.mx-auto.w-16.h-16 ...]
      [:h3.text-xl.font-semibold.mt-4 "No summaries yet"]
      [:p.text-gray-600.mt-2
       "Get started by importing CSV data or creating your first summary manually."]
      [:a.btn.btn-primary.mt-6 {:href "/summaries/new"} "+ New Summary"]])
   ```

3. Create GenerationGroupHeader component:
   ```clojure
   (defn generation-group-header [{:keys [generation all-accepted?]}]
     [:div.generation-header.bg-blue-50.border-l-4.border-blue-500.p-4
      [:div.flex.justify-between.items-center
       [:div.flex.gap-4.items-center
        [:h3 (str "Import from " (format-date (:generation/created-at generation)))]
        [:span.badge.badge-blue (:generation/model generation)]
        [:span.text-sm.text-gray-600
         (str (:generation/generated-count generation) " summaries")]]
       (if all-accepted?
         [:span.text-green-600 "All accepted ✓"]
         (bulk-accept-button (:generation/id generation)))]])
   ```

4. Create SummaryCard component with all subcomponents:
   ```clojure
   (defn summary-card [{:keys [summary generation is-accepted?]}]
     [:div.summary-card.border.rounded.shadow.p-4
      {:data-summary-id (:summary/id summary)}
      (card-header summary is-accepted?)
      (card-body summary)
      (card-footer summary generation)])
   ```

5. Create all interactive components with htmx attributes

**Deliverables:**
- Complete Hiccup templates for all components
- Proper HTML structure with Tailwind classes
- htmx attributes configured

### Step 3: Implement Inline Editing Components

**Tasks:**
1. Create InlineEditableField component with two states:
   ```clojure
   ;; Read-only state
   (defn inline-field-readonly [{:keys [summary-id field-name value label]}]
     [:div.editable-field.group.cursor-pointer
      {:hx-get (str "/api/summaries/" summary-id "/edit/" (name field-name))
       :hx-target "this"
       :hx-swap "outerHTML"}
      [:span value]
      [:svg.pencil-icon.opacity-0.group-hover:opacity-100 ...]])

   ;; Edit state (returned by server on click)
   (defn inline-field-edit [{:keys [summary-id field-name value]}]
     [:div.editable-field
      [:input.field-input
       {:type (if (= field-name :observation-date) "text" "text")
        :value value
        :name (name field-name)
        :autofocus true
        :hx-trigger "blur changed delay:100ms"
        :hx-patch (str "/api/summaries/" summary-id)
        :hx-include "this"
        :hx-target "closest .editable-field"
        :hx-swap "outerHTML"
        :hx-indicator (str "#spinner-" (name field-name) "-" summary-id)
        :onkeydown "if(event.key==='Escape') this.closest('.editable-field').querySelector('[hx-get]').click()"}]
      [:span.htmx-indicator {:id (str "spinner-" (name field-name) "-" summary-id)}
       [:svg.animate-spin ...]]])
   ```

2. Add JavaScript for Escape key handling:
   ```javascript
   // In main.js or inline <script>
   function revertEdit(input) {
     const field = input.closest('.editable-field');
     // Trigger click on read-only state to cancel
     htmx.ajax('GET', field.dataset.cancelUrl, {target: field, swap: 'outerHTML'});
   }
   ```

3. Implement server-side routes:
   ```clojure
   ;; Get edit state
   (defn get-field-edit-state [{:keys [params]}]
     (let [{:keys [id field]} params
           summary (get-summary id)]
       {:status 200
        :body (inline-field-edit
                {:summary-id id
                 :field-name (keyword field)
                 :value (get summary (keyword (str "summary/" field)))})}))

   ;; Handle update
   (defn update-summary-field [{:keys [params body-params]}]
     (let [{:keys [id]} params
           updated (summary-service/update-summary id body-params)]
       {:status 200
        :body (inline-field-readonly
                {:summary-id id
                 :field-name (first (keys body-params))
                 :value (first (vals body-params))})}))
   ```

4. Add validation for observation-date field

**Deliverables:**
- Working inline edit for all metadata fields
- Auto-save on blur
- Escape key cancels edit
- Loading indicators
- Validation for date field

### Step 4: Implement Content Editing

**Tasks:**
1. Create ContentEditForm component:
   ```clojure
   (defn content-edit-form [{:keys [summary-id content]}]
     [:form.content-edit-form
      {:hx-patch (str "/api/summaries/" summary-id)
       :hx-target (str "#summary-card-" summary-id)
       :hx-swap "outerHTML"}
      [:textarea.content-textarea
       {:name "content"
        :rows 10
        :oninput "updateCharCount(this)"
        :id (str "content-textarea-" summary-id)}
       content]
      [:div.char-counter
       {:id (str "char-counter-" summary-id)}
       (str (count (str/trim content)) " / 50,000 characters")]
      [:div.button-group.mt-4
       [:button.btn.btn-primary
        {:type "submit"
         :id (str "save-btn-" summary-id)}
        "Save"]
       [:button.btn.btn-secondary
        {:type "button"
         :onclick (str "cancelContentEdit('" summary-id "')")}
        "Cancel"]]])
   ```

2. Add JavaScript for character counting:
   ```javascript
   function updateCharCount(textarea) {
     const id = textarea.id.replace('content-textarea-', '');
     const length = textarea.value.trim().length;
     const counter = document.getElementById(`char-counter-${id}`);
     const saveBtn = document.getElementById(`save-btn-${id}`);

     counter.textContent = `${length} / 50,000 characters`;

     if (length < 50) {
       counter.className = 'char-counter text-red-600';
       counter.textContent = `Too short (${length} chars, minimum 50)`;
       saveBtn.disabled = true;
     } else if (length > 50000) {
       counter.className = 'char-counter text-red-600';
       counter.textContent = `Too long (${length} chars, maximum 50,000)`;
       saveBtn.disabled = true;
     } else {
       counter.className = 'char-counter text-gray-600';
       saveBtn.disabled = false;
     }
   }

   function cancelContentEdit(summaryId) {
     // Trigger htmx to reload card in read-only state
     htmx.ajax('GET', `/api/summaries/${summaryId}`, {
       target: `#summary-card-${summaryId}`,
       swap: 'outerHTML'
     });
   }
   ```

3. Implement server-side content update with source tracking:
   ```clojure
   (defn update-summary-content [{:keys [params body-params]}]
     (let [{:keys [id]} params
           {:keys [content]} body-params
           summary (get-summary id)
           ;; Auto-update source if editing AI-full
           new-source (if (= (:summary/source summary) :ai-full)
                       :ai-partial
                       (:summary/source summary))
           updated (summary-service/update-summary
                     id
                     {:content content
                      :source new-source})]
       {:status 200
        :body [:div
               ;; Main response: entire card
               (summary-card {:summary updated
                             :generation (get-generation (:summary/generation-id updated))
                             :is-accepted? false})
               ;; OOB: Success toast
               [:div {:hx-swap-oob "afterbegin:#toast-container"}
                (toast-notification
                  {:type :success
                   :message (if (not= (:summary/source summary) new-source)
                             "Summary updated and marked as AI Edited"
                             "Summary updated")})]]}))
   ```

**Deliverables:**
- Working content edit form
- Real-time character counter
- Source tracking (ai-full → ai-partial)
- Save and cancel functionality

### Step 5: Implement Accept Functionality

**Tasks:**
1. Create AcceptButton component:
   ```clojure
   (defn accept-button [{:keys [summary-id]}]
     [:button.btn.btn-accept
      {:hx-post (str "/api/summaries/" summary-id "/accept")
       :hx-target "closest .accept-button-container"
       :hx-swap "outerHTML"
       :hx-indicator (str "#accept-spinner-" summary-id)}
      "Accept"
      [:span.htmx-indicator
       {:id (str "accept-spinner-" summary-id)}
       [:svg.animate-spin ...]]])

   (defn accepted-badge []
     [:div.accepted-badge.bg-green-100.text-green-800.px-3.py-1.rounded
      [:svg.checkmark ...]
      [:span "Accepted"]])
   ```

2. Implement server-side accept endpoint:
   ```clojure
   (defn accept-summary [{:keys [params user-id]}]
     (let [{:keys [id]} params
           summary (get-summary id)
           _ (validate-can-accept summary)
           generation (get-generation (:summary/generation-id summary))
           ;; Update generation counters
           _ (generation-service/increment-acceptance
               (:generation/id generation)
               (:summary/source summary))
           ;; Mark summary as accepted
           _ (summary-service/mark-accepted id)]
       {:status 200
        :body [:div
               ;; Main: Replace button with badge
               [:div.accept-button-container
                (accepted-badge)]
               ;; OOB: Update generation header if needed
               (when (check-all-accepted generation)
                 [:div {:hx-swap-oob (str "outerHTML:#generation-header-"
                                         (:generation/id generation))}
                  (generation-group-header
                    {:generation (reload-generation (:generation/id generation))
                     :all-accepted? true})])
               ;; OOB: Success toast
               [:div {:hx-swap-oob "afterbegin:#toast-container"}
                (toast-notification
                  {:type :success
                   :message "Summary accepted"})]]}))
   ```

3. Create BulkAcceptButton component:
   ```clojure
   (defn bulk-accept-button [{:keys [generation-id]}]
     [:button.btn.btn-primary
      {:hx-post (str "/api/generations/" generation-id "/accept-summaries")
       :hx-target "closest .generation-group"
       :hx-swap "outerHTML"
       :hx-indicator (str "#bulk-spinner-" generation-id)}
      "Accept All from This Import"
      [:span.htmx-indicator
       {:id (str "bulk-spinner-" generation-id)}
       [:svg.animate-spin ...]]])
   ```

4. Implement bulk accept endpoint:
   ```clojure
   (defn bulk-accept-generation [{:keys [params user-id]}]
     (let [{:keys [id]} params
           generation (get-generation id)
           summaries (get-summaries-by-generation id)
           _ (generation-service/bulk-accept id summaries)
           updated-generation (reload-generation id)
           updated-summaries (get-summaries-by-generation id)]
       {:status 200
        :body [:div
               ;; Main: Entire generation group refreshed
               [:div.generation-group
                (generation-group-header
                  {:generation updated-generation
                   :all-accepted? true})
                [:div.summary-cards-grid
                 (for [summary updated-summaries]
                   (summary-card
                     {:summary summary
                      :generation updated-generation
                      :is-accepted? true}))]]
               ;; OOB: Success toast
               [:div {:hx-swap-oob "afterbegin:#toast-container"}
                (toast-notification
                  {:type :success
                   :message (str (count summaries) " summaries accepted")})]]}))
   ```

**Deliverables:**
- Working individual accept
- Working bulk accept
- Generation counter updates
- UI state updates via OOB swaps

### Step 6: Implement Delete Functionality

**Tasks:**
1. Create DeleteButton component:
   ```clojure
   (defn delete-button [{:keys [summary-id]}]
     [:button.btn.btn-danger
      {:hx-delete (str "/api/summaries/" summary-id)
       :hx-target "closest .summary-card"
       :hx-swap "outerHTML swap:1s"
       :hx-confirm "Delete this summary?"}
      [:svg.trash-icon ...]])
   ```

2. Implement server-side delete endpoint:
   ```clojure
   (defn delete-summary [{:keys [params user-id]}]
     (let [{:keys [id]} params
           _ (summary-service/delete-summary id user-id)]
       {:status 200
        :body [:div
               ;; OOB: Success toast (card removed by htmx)
               [:div {:hx-swap-oob "afterbegin:#toast-container"}
                (toast-notification
                  {:type :success
                   :message "Summary deleted"})]]}))
   ```

3. Add CSS for fade-out animation:
   ```css
   .summary-card.htmx-swapping {
     opacity: 0;
     transition: opacity 1s ease-out;
   }
   ```

**Deliverables:**
- Working delete with confirmation
- Fade-out animation
- Success toast notification

### Step 7: Implement Show More/Less

**Tasks:**
1. Add content truncation logic:
   ```clojure
   (defn content-preview [{:keys [content summary-id]}]
     (let [truncated (subs content 0 (min 150 (count content)))
           show-more? (> (count content) 150)]
       [:div.content-area
        {:id (str "summary-content-" summary-id)}
        [:div.content-preview truncated]
        (when show-more?
          [:button.btn-link
           {:onclick (str "toggleContent('" summary-id "')")}
           "Show more"])]))
   ```

2. Add JavaScript toggle function:
   ```javascript
   function toggleContent(summaryId) {
     const container = document.getElementById(`summary-content-${summaryId}`);
     const expanded = container.dataset.expanded === 'true';

     if (!expanded && !container.dataset.fullContentLoaded) {
       // Fetch full content if not loaded
       htmx.ajax('GET', `/api/summaries/${summaryId}`, {
         target: `#summary-content-${summaryId}`,
         swap: 'innerHTML'
       });
       container.dataset.fullContentLoaded = 'true';
     } else {
       // Toggle visibility
       container.querySelector('.content-preview').classList.toggle('hidden');
       container.querySelector('.content-full').classList.toggle('hidden');
       container.dataset.expanded = !expanded;
       event.target.textContent = expanded ? 'Show more' : 'Show less';
     }
   }
   ```

**Deliverables:**
- Content truncation at 150 chars
- Show more/less toggle
- Lazy loading of full content (optional)

### Step 8: Implement Toast Notifications

**Tasks:**
1. Create ToastNotification component:
   ```clojure
   (defn toast-notification [{:keys [id type message auto-dismiss? dismiss-after-ms]}]
     (let [toast-id (or id (str "toast-" (random-uuid)))
           colors (case type
                   :success {:bg "bg-green-100" :border "border-green-500" :text "text-green-800"}
                   :error {:bg "bg-red-100" :border "border-red-500" :text "text-red-800"}
                   :info {:bg "bg-blue-100" :border "border-blue-500" :text "text-blue-800"})
           htmx-attrs (when auto-dismiss?
                       {:hx-trigger (str "load delay:" dismiss-after-ms "ms")
                        :hx-swap "delete"})]
       [:div.toast.flex.items-center.gap-3.p-4.rounded.shadow-lg.border-l-4.max-w-md
        (merge {:id toast-id
                :class (str (:bg colors) " " (:border colors) " " (:text colors))}
               htmx-attrs)
        [:svg.icon (toast-icon type)]
        [:span.message message]
        [:button.close-btn
         {:onclick (str "this.parentElement.remove()")}
         "×"]]))

   (defn toast-container []
     [:div#toast-container.fixed.top-20.right-4.z-50.space-y-2
      {:aria-live "polite"}])
   ```

2. Add toast helpers for server responses:
   ```clojure
   (defn success-toast [message]
     (toast-notification
       {:type :success
        :message message
        :auto-dismiss? true
        :dismiss-after-ms 3000}))

   (defn error-toast [message]
     (toast-notification
       {:type :error
        :message message
        :auto-dismiss? true
        :dismiss-after-ms 10000}))
   ```

3. Include toast container in main layout

**Deliverables:**
- Working toast notifications
- Auto-dismiss for success
- Manual dismiss for errors
- Stacking support

### Step 9: Add Responsive Styling

**Tasks:**
1. Implement responsive grid:
   ```clojure
   (defn summary-cards-grid [summaries]
     [:div.summary-cards-grid.grid.gap-6
      {:class "grid-cols-1 md:grid-cols-2 lg:grid-cols-3"}
      (for [summary summaries]
        (summary-card summary))])
   ```

2. Add responsive header layout:
   ```clojure
   (defn generation-group-header [data]
     [:div.generation-header.p-4
      [:div.flex.flex-col.md:flex-row.justify-between.items-start.md:items-center.gap-4
       [:div.flex.flex-col.sm:flex-row.gap-2.sm:gap-4.items-start.sm:items-center
        ;; Metadata elements
        ]
       ;; Button (stacks on mobile, aligns right on desktop)
       ]])
   ```

3. Test on different breakpoints:
   - Mobile (< 768px): Single column
   - Tablet (768px-1024px): Two columns
   - Desktop (≥ 1024px): Three columns

**Deliverables:**
- Responsive grid layout
- Mobile-friendly header
- Touch-friendly buttons (min 44x44px)

### Step 10: Add Accessibility Features

**Tasks:**
1. Add ARIA labels to icon buttons:
   ```clojure
   (defn delete-button [summary-id]
     [:button.btn-icon
      {:aria-label "Delete summary"
       :hx-delete (str "/api/summaries/" summary-id)
       ...}
      [:svg ...]])
   ```

2. Add ARIA live region for toasts:
   ```clojure
   [:div#toast-container.fixed.top-20.right-4.z-50
    {:aria-live "polite"
     :aria-atomic "true"}]
   ```

3. Add ARIA expanded for collapsible content:
   ```clojure
   [:button
    {:aria-expanded (if expanded "true" "false")
     :aria-controls (str "content-" summary-id)}
    "Show more"]
   ```

4. Ensure keyboard navigation:
   - All interactive elements focusable
   - Logical tab order
   - Enter activates buttons
   - Escape cancels edits

5. Test with screen reader (NVDA/VoiceOver)

**Deliverables:**
- ARIA attributes on all components
- Keyboard navigation support
- Screen reader compatibility
- Focus visible states

### Step 11: Add Error Handling

**Tasks:**
1. Create error message component:
   ```clojure
   (defn error-message-area [{:keys [error]}]
     (when error
       [:div.error-message-area.bg-red-50.border.border-red-500.p-4.mb-6.rounded
        [:div.flex.gap-3
         [:svg.icon.text-red-600 ...]
         [:div
          [:h4.font-semibold.text-red-800 "Error"]
          [:p.text-red-700 error]
          [:button.btn-link.text-red-600.mt-2
           {:onclick "this.closest('.error-message-area').remove()"}
           "Dismiss"]]]]))
   ```

2. Add global htmx error handler:
   ```javascript
   // In main.js
   htmx.on('htmx:responseError', function(event) {
     const status = event.detail.xhr.status;
     let message = 'An error occurred. Please try again.';

     switch(status) {
       case 400:
         const response = JSON.parse(event.detail.xhr.responseText);
         message = response.error || 'Validation error';
         break;
       case 401:
         window.location.href = '/login';
         return;
       case 403:
         message = 'Access denied';
         break;
       case 404:
         message = 'Resource not found';
         break;
       case 409:
         message = 'This item was modified. Please refresh.';
         break;
       case 429:
         const retryAfter = event.detail.xhr.getResponseHeader('Retry-After');
         message = `Too many requests. Please wait ${retryAfter} seconds.`;
         break;
       case 500:
         message = 'Server error. Please try again.';
         break;
     }

     showToast('error', message);
   });

   function showToast(type, message) {
     const container = document.getElementById('toast-container');
     const toast = createToastElement(type, message);
     container.insertAdjacentHTML('afterbegin', toast);
   }
   ```

3. Add field-level error display:
   ```clojure
   (defn field-with-error [{:keys [field-id error]}]
     [:div.field-container
      [:input.field-input
       {:id field-id
        :class (when error "border-red-500")
        :aria-invalid (boolean error)
        :aria-describedby (when error (str field-id "-error"))}]
      (when error
        [:div.field-error.text-red-600.text-sm.mt-1
         {:id (str field-id "-error")
          :role "alert"}
         error])])
   ```

**Deliverables:**
- Global error handler
- Error toast notifications
- Field-level error display
- User-friendly error messages

### Step 12: Testing and Refinement

**Tasks:**
1. Manual testing checklist:
   - [ ] List loads correctly on page load
   - [ ] Empty state shows when no summaries
   - [ ] Generation groups display correctly
   - [ ] Inline edit works for all metadata fields
   - [ ] Content edit saves and updates source
   - [ ] Accept button works for individual summaries
   - [ ] Bulk accept works for entire generation
   - [ ] Delete confirms and removes card
   - [ ] Show more/less toggles content
   - [ ] Toasts display and dismiss correctly
   - [ ] Responsive layout works on all breakpoints
   - [ ] Keyboard navigation works throughout
   - [ ] Screen reader announces changes
   - [ ] Error handling works for all scenarios

2. Browser testing:
   - Chrome/Edge (latest)
   - Firefox (latest)
   - Safari (latest)
   - Mobile Safari (iOS)
   - Chrome Mobile (Android)

3. Performance checks:
   - Page load time < 2 seconds
   - Inline edit response < 500ms
   - No layout shifts during interactions
   - Smooth animations

4. Accessibility audit:
   - Run Lighthouse accessibility check
   - Test with keyboard only
   - Test with screen reader
   - Check color contrast ratios

5. Code review:
   - Consistent naming conventions
   - Proper error handling
   - Clear comments where needed
   - No hardcoded values

**Deliverables:**
- Test results documented
- All issues fixed
- Code review completed
- Production-ready view

### Step 13: Documentation

**Tasks:**
1. Document component API:
   ```clojure
   ;; In component file
   (defn summary-card
     "Displays a single summary with interactive elements.

     Args:
       summary - Summary entity map with required keys:
                 :summary/id, :summary/content, :summary/source, etc.
       generation - Generation entity map (optional, for AI summaries)
       is-accepted? - Boolean indicating acceptance status

     Returns:
       Hiccup div element representing the summary card"
     [{:keys [summary generation is-accepted?]}]
     ...)
   ```

2. Create usage examples:
   ```clojure
   ;; Example: Rendering summaries list
   (summaries-list-section
     {:summaries (get-summaries db user-id)
      :generations (get-generations db user-id)})
   ```

3. Document htmx patterns used:
   - Auto-save on blur
   - OOB swaps for multi-section updates
   - Swap strategies (outerHTML, innerHTML, delete)
   - Loading indicators

4. Create troubleshooting guide:
   - Common issues and solutions
   - Debugging htmx requests
   - Checking server logs

**Deliverables:**
- Documented component functions
- Usage examples
- htmx patterns documented
- Troubleshooting guide

---

This implementation plan provides a comprehensive, step-by-step guide for building the Summaries List Section view using Biff, htmx, and Tailwind 4. The plan emphasizes server-side rendering with progressive enhancement, follows Clojure/Biff best practices, and ensures accessibility and user experience are prioritized throughout.
