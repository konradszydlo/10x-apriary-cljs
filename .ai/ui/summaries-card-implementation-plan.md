# View Implementation Plan: Summary Card Component

## 1. Overview

The Summary Card Component is a self-contained card interface that displays a single beekeeping summary with comprehensive interaction capabilities. It supports inline editing of metadata fields, content modification, acceptance tracking for AI-generated summaries, and deletion. The component is designed to work within a server-rendered architecture using Biff and htmx, providing dynamic interactions without client-side JavaScript frameworks.

**Key Features:**
- Visual source indicators (AI-generated vs manual)
- Inline editable metadata fields (hive number, observation date, special feature)
- Content preview with expansion/collapse
- Content editing with validation
- Accept/Accepted status for AI summaries
- Delete functionality with confirmation
- Generation metadata display
- Full accessibility support

## 2. View Routing

The Summary Card Component is not a standalone view but a reusable component rendered as part of:

**Primary Location:** `/` or `/summaries` (Main Summaries Page)
**Context:** Rendered within the summaries list section, potentially grouped by generation for AI-generated batches

**Related Routes:**
- `GET /api/summaries/{id}` - Fetch full summary details (for content expansion)
- `PATCH /api/summaries/{id}` - Update summary fields
- `DELETE /api/summaries/{id}` - Delete summary
- `POST /api/summaries/{id}/accept` - Accept AI summary

## 3. Component Structure

The Summary Card follows a hierarchical structure optimized for server-side rendering with htmx enhancements:

```
summary-card (article.summary-card)
├── card-header (div.card-header)
│   ├── source-badge (span.source-badge)
│   ├── metadata-section (div.metadata-section)
│   │   ├── inline-editable-field [hive-number] (div.editable-field)
│   │   └── inline-editable-field [observation-date] (div.editable-field)
│   └── action-buttons (div.action-buttons)
│       ├── edit-button (button.edit-btn)
│       ├── accept-button | accepted-badge (button.accept-btn | span.accepted-badge)
│       └── delete-button (button.delete-btn)
├── card-body (div.card-body)
│   ├── special-feature-tag (div.editable-field) [if present]
│   └── content-area (div.content-area)
│       ├── content-display (div.content-display) [default]
│       └── content-edit-form (form.content-edit-form) [edit mode]
└── card-footer (div.card-footer)
    ├── generation-metadata (div.generation-metadata) [AI summaries only]
    └── content-toggle (button.content-toggle) [if content > 150 chars]
```

## 4. Component Details

### 4.1 Summary Card (Main Container)

**Component Description:**
The root container element that wraps all summary card content. Provides structural organization and serves as the htmx target for whole-card operations (delete, replace).

**Main HTML Elements:**
```html
<article class="summary-card bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow"
         data-summary-id="{id}"
         data-source="{source}"
         id="summary-{id}">
  <!-- Child components -->
</article>
```

**Child Components:**
- CardHeader
- CardBody
- CardFooter

**Handled Events:**
- None directly (delegated to child components)

**Validation Conditions:**
- None

**Types:**
- Summary DTO (full object)

**Props:**
- `summary` (Summary DTO): Complete summary data

**Styling Considerations:**
- Responsive: Full width on mobile, adapts to grid on tablet/desktop
- Border-radius for modern card appearance
- Shadow for depth perception
- Hover effect to indicate interactivity

---

### 4.2 Source Badge

**Component Description:**
A visual indicator displaying the origin of the summary (AI-generated unedited, AI-edited, or manual). Uses color-coded badges with icons to convey information at a glance.

**Main HTML Elements:**
```html
<span class="source-badge inline-flex items-center gap-1 px-2 py-1 rounded text-sm font-medium {variant-classes}"
      aria-label="{aria-description}">
  <svg class="w-4 h-4" aria-hidden="true"><!-- Icon --></svg>
  <span>{label}</span>
</span>
```

**Variants:**
1. **ai-full**: Blue badge (bg-blue-100 text-blue-800) with robot icon, label "AI Generated"
2. **ai-partial**: Amber badge (bg-amber-100 text-amber-800) with robot+pencil icon, label "AI Edited"
3. **manual**: Gray badge (bg-gray-100 text-gray-800) with pencil icon, label "Manual"

**Handled Events:**
- None (display-only component)

**Validation Conditions:**
- None

**Types:**
- `source`: string enum ("ai-full" | "ai-partial" | "manual")

**Props:**
- `source` (string): Summary source type

**Accessibility:**
- `aria-label` provides context for screen readers
- Icon marked with `aria-hidden="true"`
- Sufficient color contrast (4.5:1 minimum)

---

### 4.3 Inline Editable Field

**Component Description:**
A dual-mode component that displays metadata as read-only text by default and transforms into an editable input on user interaction. Implements auto-save pattern with visual feedback.

**Main HTML Elements:**

**Read-only State:**
```html
<div class="editable-field group cursor-pointer hover:bg-gray-100 rounded px-2 py-1 transition-colors"
     data-field-name="{field-name}"
     data-original-value="{value}">
  <span class="field-value">{value || placeholder}</span>
  <svg class="inline w-4 h-4 ml-1 opacity-0 group-hover:opacity-100 transition-opacity" aria-hidden="true">
    <!-- Pencil icon -->
  </svg>
</div>
```

**Edit State:**
```html
<div class="editable-field editing">
  <input type="text"
         name="{field-name}"
         value="{current-value}"
         placeholder="{placeholder}"
         class="border rounded px-2 py-1 focus:ring-2 focus:ring-blue-500"
         hx-patch="/api/summaries/{id}"
         hx-trigger="blur changed delay:100ms"
         hx-target="closest .editable-field"
         hx-swap="outerHTML"
         hx-indicator="#spinner-{field-name}"
         aria-label="{field-label}">
  <svg id="spinner-{field-name}" class="htmx-indicator inline w-4 h-4 ml-1 animate-spin">
    <!-- Spinner icon -->
  </svg>
</div>
```

**Child Components:**
- None

**Handled Events:**
1. **Click** (read-only state): Transform to edit state, focus input
2. **Blur** (edit state): Trigger htmx PATCH with delay to allow for value change
3. **Escape key** (edit state): Cancel edit, revert to original value

**Validation Conditions:**

**For `observation-date` field:**
- Pattern: `^\\d{2}-\\d{2}-\\d{4}$` (DD-MM-YYYY)
- Valid date check (e.g., not 32-01-2025)
- Can be empty (optional field)

**For `hive-number` and `special-feature`:**
- Any string value allowed
- Can be empty

**Types:**
```typescript
type FieldName = "hive-number" | "observation-date" | "special-feature"
type FieldValue = string | null
```

**Props:**
- `fieldName` (FieldName): Identifier for the field being edited
- `value` (FieldValue): Current field value
- `summaryId` (string): UUID of the summary
- `placeholder` (string): Placeholder text when value is empty

**Accessibility:**
- `aria-label` on input describes the field
- Focus visible states
- Keyboard accessible (Tab to focus, Enter to exit, Escape to cancel)

**Implementation Notes:**
- htmx triggers PATCH request with debounce (100ms delay after blur+change)
- Server returns updated field HTML for swap
- On success, show green checkmark briefly
- On error, revert to edit state with error message below field

---

### 4.4 Content Area

**Component Description:**
The main content display and editing section. Supports two distinct modes: display mode (showing truncated or full content) and edit mode (textarea for modification).

**Main HTML Elements:**

**Display Mode:**
```html
<div class="content-area" id="summary-content-{id}" data-content-expanded="false">
  <div class="content-display prose prose-sm max-w-none">
    {content-text}
  </div>
</div>
```

**Edit Mode:**
```html
<div class="content-area editing">
  <form hx-patch="/api/summaries/{id}"
        hx-swap="outerHTML"
        hx-target="closest .content-area">
    <textarea name="content"
              rows="10"
              class="w-full border rounded p-3 focus:ring-2 focus:ring-blue-500 resize-y"
              aria-label="Summary content"
              minlength="50"
              maxlength="50000">{content}</textarea>
    <div class="flex gap-2 mt-2">
      <button type="submit"
              class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
              hx-indicator="#save-spinner">
        <svg id="save-spinner" class="htmx-indicator inline w-4 h-4 mr-1 animate-spin">
          <!-- Spinner -->
        </svg>
        Save
      </button>
      <button type="button"
              class="px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
              hx-get="/api/summaries/{id}"
              hx-target="closest .content-area"
              hx-swap="outerHTML">
        Cancel
      </button>
    </div>
  </form>
</div>
```

**Child Components:**
- None (contains form elements directly)

**Handled Events:**
1. **Edit button click** (from ActionButtons): Server returns edit mode HTML
2. **Save button click**: Submits PATCH request with new content
3. **Cancel button click**: Fetches original content, exits edit mode

**Validation Conditions:**
1. **Content length**: 50 ≤ length ≤ 50,000 characters (after trim)
   - Client-side: `minlength` and `maxlength` attributes (UX hint)
   - Server-side: Authoritative validation
2. **Required field**: Content cannot be empty

**Types:**
```typescript
type ContentMode = "display" | "edit"
type Content = string
```

**Props:**
- `content` (Content): Summary content text
- `summaryId` (string): UUID of the summary
- `mode` (ContentMode): Current display mode
- `source` (string): Summary source (for potential source change feedback)

**Accessibility:**
- Textarea has `aria-label`
- Form has proper submit/cancel flow
- Focus managed on mode transitions

**Implementation Notes:**
- When content is edited and source is "ai-full", server changes source to "ai-partial"
- Response includes updated source badge via htmx OOB swap
- On validation error, form re-renders with error message
- Character counter could be added client-side for better UX

---

### 4.5 Action Buttons

**Component Description:**
A group of action buttons providing core summary operations: edit content, accept (for AI summaries), and delete.

**Main HTML Elements:**
```html
<div class="action-buttons flex gap-2 items-center">
  <!-- Edit Button -->
  <button type="button"
          class="btn-icon"
          hx-get="/api/summaries/{id}/edit"
          hx-target="closest .summary-card .content-area"
          hx-swap="outerHTML"
          aria-label="Edit summary content">
    <svg class="w-5 h-5"><!-- Pencil icon --></svg>
  </button>

  <!-- Accept Button (conditional: only for AI summaries) -->
  {#if source === "ai-full" || source === "ai-partial"}
    {#if not accepted}
      <button type="button"
              class="btn-accept px-3 py-1 bg-green-600 text-white rounded hover:bg-green-700"
              hx-post="/api/summaries/{id}/accept"
              hx-target="closest .action-buttons"
              hx-swap="outerHTML"
              hx-confirm="Accept this summary?">
        Accept
      </button>
    {:else}
      <span class="accepted-badge inline-flex items-center gap-1 px-2 py-1 rounded bg-green-100 text-green-800">
        <svg class="w-4 h-4"><!-- Check icon --></svg>
        Accepted
      </span>
    {/if}
  {/if}

  <!-- Delete Button -->
  <button type="button"
          class="btn-icon text-red-600 hover:text-red-700"
          hx-delete="/api/summaries/{id}"
          hx-target="closest .summary-card"
          hx-swap="outerHTML swap:1s"
          hx-confirm="Delete this summary? This action cannot be undone."
          aria-label="Delete summary">
    <svg class="w-5 h-5"><!-- Trash icon --></svg>
  </button>
</div>
```

**Child Components:**
- None (atomic buttons)

**Handled Events:**
1. **Edit button click**: GET request for edit mode HTML
2. **Accept button click**: POST request to accept endpoint (with confirmation)
3. **Delete button click**: DELETE request (with confirmation)

**Validation Conditions:**
1. **Accept button visibility**: Only render if `source` is "ai-full" or "ai-partial"
2. **Accept button state**: Replace with "Accepted" badge after acceptance
3. **Delete confirmation**: Browser native confirm dialog via htmx

**Types:**
```typescript
type Source = "ai-full" | "ai-partial" | "manual"
type AcceptanceStatus = boolean
```

**Props:**
- `summaryId` (string): UUID of the summary
- `source` (Source): Summary source type
- `accepted` (AcceptanceStatus): Whether summary has been accepted

**Accessibility:**
- All icon-only buttons have `aria-label`
- Minimum 44x44px touch targets
- Clear focus indicators
- Confirm dialogs announce to screen readers

**Implementation Notes:**
- Edit button triggers GET to fetch edit mode HTML for content area
- Accept button uses POST with htmx confirm for user confirmation
- Delete button uses DELETE with confirm and fade-out swap animation
- After accept, server returns updated action buttons HTML with "Accepted" badge
- Delete success removes entire card from DOM

---

### 4.6 Generation Metadata

**Component Description:**
Displays contextual information about AI-generated summaries, including the import date and model used. Only visible for AI-generated summaries.

**Main HTML Elements:**
```html
{#if generation-id}
  <div class="generation-metadata text-sm text-gray-600">
    <span>From import on {generation-date}</span>
    <span class="mx-2">•</span>
    <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-gray-100 text-gray-700 text-xs">
      {model-name}
    </span>
  </div>
{/if}
```

**Child Components:**
- None

**Handled Events:**
- None (display-only)

**Validation Conditions:**
- Only render if `generation-id` is not null

**Types:**
```typescript
type GenerationMetadata = {
  generationId: string | null
  generationDate: string  // DD-MM-YYYY format
  modelName: string
}
```

**Props:**
- `generationId` (string | null): UUID of generation batch
- `generationDate` (string): Date of import
- `modelName` (string): AI model used

**Accessibility:**
- Semantic text with proper contrast
- Screen readers announce all metadata

---

### 4.7 Content Toggle

**Component Description:**
A button that toggles between truncated content preview (first 150 characters) and full content display. Only appears when content exceeds 150 characters.

**Main HTML Elements:**
```html
{#if content.length > 150}
  <button type="button"
          class="content-toggle text-blue-600 hover:text-blue-700 hover:underline text-sm"
          hx-get="/api/summaries/{id}"
          hx-target="#summary-content-{id}"
          hx-swap="innerHTML"
          aria-expanded="{expanded}"
          aria-controls="summary-content-{id}">
    {expanded ? "Show less" : "Show more"}
    <svg class="inline w-4 h-4 ml-1 {expanded ? 'rotate-180' : ''} transition-transform">
      <!-- Chevron down icon -->
    </svg>
  </button>
{/if}
```

**Child Components:**
- None

**Handled Events:**
1. **Click**: Toggle content expansion
   - If collapsed: Fetch full content via GET (if not cached)
   - If expanded: Collapse to truncated view (no API call)

**Validation Conditions:**
- Only render if content length > 150 characters

**Types:**
```typescript
type ContentLength = number
type ExpandedState = boolean
```

**Props:**
- `contentLength` (ContentLength): Full content character count
- `expanded` (ExpandedState): Current expansion state
- `summaryId` (string): UUID of the summary

**Accessibility:**
- `aria-expanded` indicates current state
- `aria-controls` links to content area
- Button text clearly describes action
- Icon rotation provides visual feedback

**Implementation Notes:**
- First click: htmx GET request fetches full content
- Subsequent toggles: Client-side show/hide (content cached in DOM)
- Server can optimize by storing expanded state in session
- Use CSS transitions for smooth expansion

---

## 5. Types

### 5.1 Summary DTO (API Response Type)

Complete summary object returned from backend API endpoints.

```clojure
{:id uuid-string
 :user-id uuid-string
 :generation-id (or nil uuid-string)
 :source ("ai-full" | "ai-partial" | "manual")
 :hive-number (or nil string)
 :observation-date (or nil string)  ; DD-MM-YYYY format
 :special-feature (or nil string)
 :content string
 :created-at iso-timestamp-string
 :updated-at iso-timestamp-string
 :accepted-at (optional) iso-timestamp-string}
```

**Field Descriptions:**
- `id`: Unique identifier for the summary
- `user-id`: Owner of the summary (RLS enforcement)
- `generation-id`: Link to AI generation batch (null for manual summaries)
- `source`: Origin classification (ai-full = unedited AI, ai-partial = edited AI, manual = user-created)
- `hive-number`: Optional hive identifier
- `observation-date`: Optional observation date in DD-MM-YYYY format
- `special-feature`: Optional special characteristic tag
- `content`: Main summary text (50-50,000 chars)
- `created-at`: Creation timestamp in ISO 8601 format
- `updated-at`: Last modification timestamp
- `accepted-at`: Acceptance timestamp (only present after acceptance)

### 5.2 Generation Metadata Type

Information about the AI generation batch (embedded in summary for display purposes).

```clojure
{:generation-id (or nil uuid-string)
 :generation-date (or nil string)  ; DD-MM-YYYY format
 :model-name (or nil string)}
```

**Usage:**
- Derived from summary's `generation-id` and fetched separately or embedded
- Only relevant for AI-generated summaries
- Used by GenerationMetadata component

### 5.3 Field Update Request Type

Partial update payload for PATCH requests.

```clojure
{:hive-number (optional) string
 :observation-date (optional) string  ; DD-MM-YYYY
 :special-feature (optional) string
 :content (optional) string}  ; 50-50,000 chars after trim
```

**Constraints:**
- At least one field must be provided
- Server validates all fields authoritatively
- Content update on ai-full summary triggers source change to ai-partial

### 5.4 Component State Types (Implicit, DOM-based)

Since this is a server-rendered application with htmx, state is managed through DOM attributes rather than TypeScript/JavaScript state objects.

**Edit State Tracking:**
```html
<!-- Inline field edit state -->
<div class="editable-field" data-editing="true" data-original-value="A-01">

<!-- Content area mode -->
<div class="content-area" data-mode="edit">

<!-- Content expansion state -->
<div class="content-area" data-content-expanded="true">
```

**htmx State Indicators:**
```html
<!-- Loading states -->
<button hx-indicator="#spinner-id">
<svg id="spinner-id" class="htmx-indicator">
```

## 6. State Management

### 6.1 Server-Side State

**Primary State Storage:** XTDB database
- Summary entities stored with all metadata
- Generation entities track batch information
- Acceptance events recorded with timestamps

**Session State:**
- User authentication via Biff session
- CSRF tokens for mutation requests

### 6.2 Client-Side State (DOM-based)

**No JavaScript State Management Framework Required**

State is managed through:

1. **DOM Attributes:**
   - `data-summary-id`: Summary identifier
   - `data-source`: Current source type
   - `data-editing`: Edit mode flag
   - `data-original-value`: Original field value (for cancel)
   - `data-content-expanded`: Content expansion state

2. **HTML Structure:**
   - Presence/absence of edit mode elements indicates state
   - Class names indicate visual states (`editing`, `expanded`, `loading`)

3. **htmx Response Handling:**
   - Server returns updated HTML fragments
   - htmx swaps replace DOM sections
   - OOB (Out of Band) swaps update multiple sections atomically

### 6.3 State Transitions

**Inline Edit Flow:**
```
Read-only → (click) → Edit mode → (blur+change) → Saving → Read-only (updated)
                                      ↓
                                  (escape) → Read-only (original)
```

**Content Edit Flow:**
```
Display → (edit button) → Edit mode → (save) → Saving → Display (updated)
                                    → (cancel) → Display (original)
```

**Accept Flow:**
```
Not accepted → (accept button + confirm) → Accepting → Accepted
```

**Delete Flow:**
```
Card visible → (delete button + confirm) → Deleting → Card removed
```

**Content Expansion Flow:**
```
Truncated → (show more) → Fetching → Expanded
Expanded → (show less) → Truncated (cached)
```

### 6.4 State Synchronization

**Server as Source of Truth:**
- All mutations go through API
- Server validates and persists changes
- Responses contain updated state
- htmx swaps ensure UI reflects server state

**Optimistic UI Updates:**
Not used in MVP (pessimistic updates for reliability)
- All actions wait for server confirmation
- Loading indicators provide feedback
- Error handling reverts to previous state

## 7. API Integration

### 7.1 Endpoint: Get Single Summary

**Purpose:** Fetch full summary details (primarily for content expansion)

**HTTP Method:** GET
**URL:** `/api/summaries/{id}`
**Authentication:** Required (session-based)

**Request:**
- Path parameter: `id` (UUID)
- No request body

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user-id": "550e8400-e29b-41d4-a716-446655440000",
  "generation-id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-partial",
  "hive-number": "A-01",
  "observation-date": "23-11-2025",
  "special-feature": "Queen replaced",
  "content": "Complete summary text...",
  "created-at": "2025-11-23T10:30:00Z",
  "updated-at": "2025-11-23T11:00:00Z"
}
```

**htmx Integration:**
```html
<button hx-get="/api/summaries/{id}"
        hx-target="#summary-content-{id}"
        hx-swap="innerHTML">
  Show more
</button>
```

**Error Responses:**
- 401: Not authenticated → Redirect to login
- 403: Not authorized → Show error message
- 404: Summary not found → Show error message

---

### 7.2 Endpoint: Update Summary

**Purpose:** Update metadata fields or content with inline editing

**HTTP Method:** PATCH
**URL:** `/api/summaries/{id}`
**Authentication:** Required

**Request Body (at least one field):**
```json
{
  "hive-number": "A-01-Updated",
  "observation-date": "24-11-2025",
  "special-feature": "Updated feature",
  "content": "Updated summary content..."
}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user-id": "550e8400-e29b-41d4-a716-446655440000",
  "generation-id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-partial",
  "hive-number": "A-01-Updated",
  "observation-date": "24-11-2025",
  "special-feature": "Updated feature",
  "content": "Updated summary content...",
  "created-at": "2025-11-23T10:30:00Z",
  "updated-at": "2025-11-23T11:15:00Z",
  "message": "Summary updated successfully"
}
```

**htmx Integration (Inline Field):**
```html
<input type="text"
       name="hive-number"
       hx-patch="/api/summaries/{id}"
       hx-trigger="blur changed delay:100ms"
       hx-target="closest .editable-field"
       hx-swap="outerHTML"
       hx-include="this">
```

**htmx Integration (Content Edit):**
```html
<form hx-patch="/api/summaries/{id}"
      hx-target="closest .content-area"
      hx-swap="outerHTML">
  <textarea name="content">{content}</textarea>
  <button type="submit">Save</button>
</form>
```

**Important Behavior:**
- If content is updated and source is "ai-full", server automatically changes to "ai-partial"
- Server returns updated source badge via htmx OOB swap

**Error Responses:**
- 400: Validation error → Show field-level error message
- 401: Not authenticated → Redirect to login
- 403: Not authorized → Show error message
- 404: Not found → Show error message
- 409: Concurrent modification → Show conflict error

---

### 7.3 Endpoint: Delete Summary

**Purpose:** Permanently delete a summary

**HTTP Method:** DELETE
**URL:** `/api/summaries/{id}`
**Authentication:** Required

**Request:**
- No request body

**Response (204 No Content):**
- Empty response body

**Alternative Response (200 OK):**
```json
{
  "message": "Summary deleted successfully",
  "id": "550e8400-e29b-41d4-a716-446655440001"
}
```

**htmx Integration:**
```html
<button hx-delete="/api/summaries/{id}"
        hx-target="closest .summary-card"
        hx-swap="outerHTML swap:1s"
        hx-confirm="Delete this summary? This action cannot be undone.">
  Delete
</button>
```

**Error Responses:**
- 401: Not authenticated → Redirect to login
- 403: Not authorized → Show error message
- 404: Not found → Show error message

---

### 7.4 Endpoint: Accept Summary

**Purpose:** Mark an AI-generated summary as accepted

**HTTP Method:** POST
**URL:** `/api/summaries/{id}/accept`
**Authentication:** Required

**Request:**
- No request body (or empty JSON `{}`)

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "user-id": "550e8400-e29b-41d4-a716-446655440000",
  "generation-id": "550e8400-e29b-41d4-a716-446655440002",
  "source": "ai-full",
  "content": "Summary content...",
  "accepted-at": "2025-11-23T11:20:00Z",
  "message": "Summary accepted successfully"
}
```

**htmx Integration:**
```html
<button hx-post="/api/summaries/{id}/accept"
        hx-target="closest .action-buttons"
        hx-swap="outerHTML"
        hx-confirm="Accept this summary?">
  Accept
</button>
```

**Business Logic:**
- Only valid for source "ai-full" or "ai-partial"
- Updates generation counters (accepted-unedited or accepted-edited)
- Returns updated action buttons HTML with "Accepted" badge

**Error Responses:**
- 400: Cannot accept manual summary → Show error message
- 401: Not authenticated → Redirect to login
- 403: Not authorized → Show error message
- 404: Not found → Show error message
- 409: Already accepted → Show info message

---

## 8. User Interactions

### 8.1 View Summary Content

**Trigger:** Page load, card rendered

**User Action:** None (passive viewing)

**System Response:**
1. Server renders card with summary data
2. Content truncated to 150 characters if longer
3. "Show more" button appears if content exceeds threshold

**Visual Feedback:**
- Card displays with all metadata
- Source badge shows origin type
- Readable typography with proper spacing

---

### 8.2 Edit Metadata Field (Inline Edit)

**Trigger:** User wants to update hive number, date, or special feature

**User Action Sequence:**
1. Hover over editable field → See hover effect (light background, pencil icon)
2. Click field → Field transforms to input, receives focus
3. Type new value
4. Tab away or click outside field (blur event)

**System Response:**
1. On click: Replace read-only span with input element, focus input
2. On blur+change (after 100ms debounce):
   - htmx triggers PATCH request with field name and new value
   - Show spinner next to field
   - Send request to `/api/summaries/{id}`
3. On success:
   - Replace input with updated read-only span
   - Show green checkmark briefly (1-2 seconds)
   - Update `updated-at` timestamp (if visible)
4. On validation error:
   - Keep field in edit mode
   - Show red error message below field
   - Allow user to correct and retry

**Alternative Flow (Cancel):**
- Press Escape key → Revert to original value, exit edit mode

**Visual Feedback:**
- Hover: Light gray background, visible pencil icon
- Edit mode: Input with blue focus ring
- Saving: Spinner animation
- Success: Green checkmark, fade to normal
- Error: Red error text, red border on input

**Accessibility:**
- Focus moves to input on click
- Escape key cancels
- Screen reader announces "Edit {field name}"

---

### 8.3 Edit Summary Content

**Trigger:** User wants to modify summary text

**User Action Sequence:**
1. Click "Edit" button in action buttons
2. Content area transforms to textarea with current content
3. Modify text in textarea
4. Click "Save" button

**System Response:**
1. On Edit button click:
   - htmx GET request to fetch edit mode HTML (or server-side toggle)
   - Replace content display with textarea form
   - Focus textarea
   - Show Save and Cancel buttons
2. On Save button click:
   - htmx PATCH request with new content
   - Show spinner on Save button
   - Disable Save button during request
3. On success:
   - Replace edit form with updated content display
   - If source was "ai-full", update source badge to "ai-partial" (OOB swap)
   - Show success feedback (checkmark or toast)
4. On validation error:
   - Keep textarea in edit mode
   - Show error message (e.g., "Content must be 50-50,000 characters")
   - Allow user to correct

**Alternative Flow (Cancel):**
- Click "Cancel" button → Fetch original content, exit edit mode (no save)

**Visual Feedback:**
- Edit button highlighted on hover
- Textarea with blue focus ring
- Save button shows spinner when saving
- Success: Smooth transition back to display mode
- Error: Red error message above buttons

**Accessibility:**
- Textarea labeled "Summary content"
- Save/Cancel buttons clearly identified
- Focus managed on mode transitions
- Character count displayed for guidance

**Client-Side Validation (UX):**
- Enable Save button only when content is 50-50,000 chars
- Show character counter: "X / 50,000 characters"
- Visual indication if too short/long

---

### 8.4 Accept AI Summary

**Trigger:** User confirms AI-generated summary is good quality

**User Action Sequence:**
1. Click "Accept" button
2. Confirm in dialog (optional: "Accept this summary?")

**System Response:**
1. On Accept button click:
   - Show browser confirm dialog via htmx
   - If confirmed, htmx POST to `/api/summaries/{id}/accept`
   - Show spinner on Accept button
2. On success:
   - Replace Accept button with "Accepted" badge (green, with checkmark)
   - Update generation counters server-side
   - Show success toast (optional)
3. On error:
   - Show error message
   - Keep Accept button visible

**Visual Feedback:**
- Accept button: Green background, white text
- Hover: Darker green
- Saving: Spinner in button
- Success: Button replaced with static "Accepted" badge
- Accepted badge: Green-100 background, green-800 text, checkmark icon

**Accessibility:**
- Button labeled "Accept summary"
- Confirm dialog announced to screen readers
- Accepted badge conveys status visually and semantically

**Constraints:**
- Accept button only visible for AI summaries (source "ai-full" or "ai-partial")
- Manual summaries don't show Accept button
- Once accepted, cannot un-accept (in MVP)

---

### 8.5 Delete Summary

**Trigger:** User wants to permanently remove summary

**User Action Sequence:**
1. Click "Delete" button (trash icon)
2. Confirm in browser dialog: "Delete this summary? This action cannot be undone."
3. If confirmed, proceed with deletion

**System Response:**
1. On Delete button click:
   - Show browser confirm dialog via htmx
   - If confirmed, htmx DELETE to `/api/summaries/{id}`
   - Show loading state (card slightly faded)
2. On success:
   - Fade out card with CSS transition (1 second)
   - Remove card from DOM
   - Show success toast: "Summary deleted"
3. On error:
   - Keep card visible
   - Show error toast: "Failed to delete. Please try again."

**Visual Feedback:**
- Delete button: Red icon on hover
- Confirming: Browser native dialog
- Deleting: Card opacity reduced, subtle animation
- Success: Smooth fade-out and removal
- Error: Card returns to normal, error toast

**Accessibility:**
- Delete button labeled "Delete summary"
- Confirm dialog clearly states action is permanent
- Success announcement to screen readers
- Error announced if deletion fails

**Important Notes:**
- Deletion is permanent (hard delete, no undo)
- RLS ensures users can only delete their own summaries
- Server returns 204 No Content or 200 OK

---

### 8.6 Expand/Collapse Content

**Trigger:** User wants to read full summary when preview is truncated

**User Action Sequence:**
1. Click "Show more" button (only visible if content > 150 chars)
2. Read full content
3. Optionally click "Show less" to collapse

**System Response:**
1. On first "Show more" click:
   - htmx GET to `/api/summaries/{id}` (or use cached content)
   - Show loading indicator (optional subtle spinner)
   - Fetch full summary content
2. On success:
   - Replace truncated content with full content via innerHTML swap
   - Change button text to "Show less"
   - Rotate chevron icon 180 degrees
   - Set `aria-expanded="true"`
3. On subsequent "Show less" click:
   - No API call (content already loaded)
   - Toggle visibility via CSS or DOM manipulation
   - Change button text to "Show more"
   - Rotate chevron back
   - Set `aria-expanded="false"`

**Visual Feedback:**
- Show more button: Blue text, underline on hover
- Loading: Subtle spinner (if needed)
- Expansion: Smooth height transition
- Chevron icon: Rotates 180 degrees
- Show less: Same styling, icon rotated

**Accessibility:**
- Button has `aria-expanded` attribute
- `aria-controls` links to content area
- Button text clearly describes action
- Content expansion announced to screen readers

**Optimization:**
- After first fetch, content cached in DOM
- Subsequent toggles don't require API calls
- Use CSS transitions for smooth expansion

---

## 9. Conditions and Validation

### 9.1 Field-Level Validation

#### Hive Number
**Component:** InlineEditableField

**Conditions:**
- Optional field (can be empty)
- Any string value accepted
- No max length enforced in MVP

**Validation:**
- Client-side: None (accepts any input)
- Server-side: Accepts any string or null

**UI Behavior:**
- Empty value shows placeholder: "e.g., A-01"
- No validation errors expected

---

#### Observation Date
**Component:** InlineEditableField

**Conditions:**
- Optional field (can be empty or null)
- If provided: Must match DD-MM-YYYY format
- Must be a valid calendar date

**Validation:**
- Client-side (UX):
  - Input type="text" with pattern attribute: `pattern="\\d{2}-\\d{2}-\\d{4}"`
  - Placeholder: "DD-MM-YYYY"
  - Helper text below field
- Server-side (authoritative):
  - Regex validation: `^\\d{2}-\\d{2}-\\d{4}$`
  - Date validity check (rejects 32-01-2025, 31-02-2025, etc.)

**UI Behavior:**
- Empty value shows placeholder: "DD-MM-YYYY"
- Invalid format:
  - Server returns 400 error
  - Field stays in edit mode
  - Red error message: "Invalid date format. Use DD-MM-YYYY"
  - Input border turns red
- Valid format:
  - Field updates successfully
  - Green checkmark briefly shown

**Error Messages:**
- Format error: "Invalid date format. Use DD-MM-YYYY"
- Invalid date: "Invalid date (e.g., 32-01-2025 is not valid)"

---

#### Special Feature
**Component:** InlineEditableField

**Conditions:**
- Optional field (can be empty)
- Any string value accepted
- No max length in MVP

**Validation:**
- Client-side: None
- Server-side: Accepts any string or null

**UI Behavior:**
- Empty value shows placeholder: "e.g., Queen active"
- No validation errors expected
- Displays as purple badge if present

---

#### Content
**Component:** ContentArea

**Conditions:**
- Required field (cannot be empty)
- Must be 50-50,000 characters after whitespace trim

**Validation:**
- Client-side (UX hint):
  - Textarea with minlength="50" maxlength="50000"
  - Character counter below textarea: "X / 50,000 characters"
  - Color-coded counter:
    - Red if < 50: "Too short (X chars, minimum 50)"
    - Gray if 50-50,000: "X / 50,000 characters"
    - Red if > 50,000: "Too long (X chars, maximum 50,000)"
  - Save button disabled if outside valid range
- Server-side (authoritative):
  - Trim whitespace from both ends
  - Check: 50 ≤ length ≤ 50,000
  - Return 400 if invalid

**UI Behavior:**
- Edit mode:
  - Textarea pre-filled with current content
  - Character counter updates on input
  - Save button enabled/disabled based on length
- Invalid submission:
  - Server returns 400 error
  - Form stays in edit mode
  - Red error message: "Content must be between 50 and 50,000 characters"
  - Textarea border turns red
- Valid submission:
  - Content updates successfully
  - Exit edit mode
  - If source was "ai-full", badge updates to "ai-partial"

**Error Messages:**
- Too short: "Content must be at least 50 characters"
- Too long: "Content must not exceed 50,000 characters"

---

### 9.2 Component Visibility Conditions

#### Accept Button
**Component:** ActionButtons

**Condition:** `source === "ai-full" || source === "ai-partial"`

**Validation:**
- Render Accept button only for AI-generated summaries
- Manual summaries (source === "manual") never show Accept button
- After acceptance, button replaced with "Accepted" badge

**UI Logic:**
```clojure
{#if (or (= source "ai-full") (= source "ai-partial"))}
  {#if (not accepted-at)}
    <!-- Accept button -->
  {:else}
    <!-- Accepted badge -->
  {/if}
{/if}
```

---

#### Generation Metadata
**Component:** GenerationMetadata

**Condition:** `generation-id !== null`

**Validation:**
- Only render for AI-generated summaries
- Manual summaries have null generation-id, so metadata hidden

**UI Logic:**
```clojure
{#if generation-id}
  <!-- Generation metadata: date, model -->
{/if}
```

---

#### Content Toggle
**Component:** ContentToggle

**Condition:** `content.length > 150`

**Validation:**
- Only render "Show more/less" if content exceeds 150 characters
- Short summaries show full content immediately

**UI Logic:**
```clojure
{#if (> (count content) 150)}
  <!-- Show more/less button -->
{/if}
```

---

#### Special Feature Tag
**Component:** CardBody

**Condition:** `special-feature !== null && special-feature !== ""`

**Validation:**
- Only render purple badge if special-feature has value
- Empty/null values hide the badge

**UI Logic:**
```clojure
{#if special-feature}
  <!-- Purple badge with special-feature text -->
{/if}
```

---

### 9.3 State-Based Validation

#### Edit Mode Exclusivity
**Condition:** Only one edit mode active at a time per card

**Implementation:**
- Disable Edit button while inline field is being edited
- Disable inline edit while content edit mode is active
- Use htmx indicators to show loading state

**UI Behavior:**
- Edit button disabled (opacity-50) when any field is saving
- Inline fields non-interactive during content edit mode

---

#### Source Transition
**Condition:** Content edit on "ai-full" summary triggers source change

**Implementation:**
- Server-side logic automatically changes source
- Response includes updated source badge via OOB swap

**UI Behavior:**
- After content save, badge may change from blue "AI Generated" to amber "AI Edited"
- Optional info toast: "Summary marked as edited"

---

## 10. Error Handling

### 10.1 Network Errors

#### Scenario: Request fails due to network issues

**Detection:**
- htmx triggers error event
- No response received from server

**Handling:**
```html
<div hx-patch="/api/summaries/{id}"
     hx-on="htmx:responseError: showNetworkError()">
```

**UI Response:**
1. Keep component in current state (don't clear edits)
2. Show error toast: "Network error. Please check your connection and try again."
3. For inline edits: Revert to edit mode with error message
4. For delete: Don't remove card, show retry option

**User Action:**
- Retry operation
- Check network connection

---

### 10.2 Validation Errors (400)

#### Scenario: Server rejects input due to validation failure

**Examples:**
- Content too short/long
- Invalid date format
- Empty required field

**Handling:**
Server returns 400 with error details:
```json
{
  "error": "Validation failed",
  "code": "VALIDATION_ERROR",
  "details": {
    "field": "observation-date",
    "reason": "Invalid date format. Use DD-MM-YYYY"
  }
}
```

**UI Response:**
1. Keep field/form in edit mode
2. Display field-level error message below input
3. Highlight invalid field with red border
4. Allow user to correct and retry

**Example (Inline Edit):**
```html
<div class="editable-field error">
  <input type="text" class="border-red-500" value="invalid-date">
  <p class="text-red-600 text-sm mt-1">Invalid date format. Use DD-MM-YYYY</p>
</div>
```

**Example (Content Edit):**
```html
<form>
  <textarea class="border-red-500">{content}</textarea>
  <p class="text-red-600 text-sm mt-1">Content must be at least 50 characters</p>
  <button type="submit">Save</button>
</form>
```

---

### 10.3 Authentication Errors (401)

#### Scenario: Session expired or user not authenticated

**Detection:**
- Server returns 401 Unauthorized

**Handling:**
1. Detect 401 response
2. Redirect to login page with return URL
3. Show message: "Your session has expired. Please log in again."

**htmx Configuration:**
```html
<body hx-on="htmx:responseError: handleAuthError(event)">
```

**JavaScript Handler:**
```javascript
function handleAuthError(event) {
  if (event.detail.xhr.status === 401) {
    window.location.href = '/login?return=' + encodeURIComponent(window.location.pathname);
  }
}
```

---

### 10.4 Authorization Errors (403)

#### Scenario: User attempts to access/modify summary they don't own

**Detection:**
- Server returns 403 Forbidden (RLS violation)

**Handling:**
1. Show error message: "You don't have permission to perform this action."
2. Keep card visible (don't remove or modify)
3. Disable action buttons temporarily

**UI Response:**
- Error toast with red styling
- Log error for monitoring
- Suggest user refresh page

**Note:** Should rarely happen if UI is properly filtered by user-id

---

### 10.5 Not Found Errors (404)

#### Scenario: Summary deleted by another session or doesn't exist

**Detection:**
- Server returns 404 Not Found

**Handling:**

**For GET (show more):**
- Show error message: "Summary not found"
- Disable show more button

**For PATCH (update):**
- Show error: "This summary no longer exists"
- Offer to refresh page

**For DELETE:**
- Assume already deleted
- Remove card from UI
- Show info toast: "Summary already deleted"

---

### 10.6 Conflict Errors (409)

#### Scenario: Concurrent modification detected

**Detection:**
- Server returns 409 Conflict

**Handling:**
1. Show error: "This summary was modified by another window/device. Please refresh to see the latest version."
2. Keep edit mode active (don't lose user's changes)
3. Offer options:
   - Refresh to see latest (lose changes)
   - Copy changes and refresh
   - Override (if allowed)

**UI Response:**
```html
<div class="error-banner bg-amber-100 border-amber-500 text-amber-800 p-4 rounded">
  <p>This summary was modified elsewhere. Your changes may conflict.</p>
  <button onclick="location.reload()">Refresh to see latest</button>
</div>
```

---

### 10.7 Server Errors (500)

#### Scenario: Unexpected server error

**Detection:**
- Server returns 500 Internal Server Error

**Handling:**
1. Log error details for debugging
2. Show user-friendly message: "Something went wrong. Please try again."
3. Keep user's input/state (don't lose data)
4. Offer retry option

**UI Response:**
- Error toast with retry button
- Don't modify card state
- Optionally send error report

---

### 10.8 Rate Limit Errors (429)

#### Scenario: Too many requests (unlikely for card operations, more for CSV import)

**Detection:**
- Server returns 429 Too Many Requests

**Handling:**
1. Parse rate limit headers: `X-RateLimit-Reset`
2. Show message: "Too many requests. Please try again in X minutes."
3. Disable action buttons temporarily
4. Re-enable after reset time

---

### 10.9 Edge Cases

#### Empty or Missing Data
**Scenario:** Summary has null/empty optional fields

**Handling:**
- Show placeholders in read-only mode
- Allow editing to add values
- Display "Not set" or similar indicator

#### Very Long Content
**Scenario:** Content at max length (50,000 chars)

**Handling:**
- Truncate preview to 150 chars
- Show more button available
- Character counter shows max limit
- Prevent typing beyond limit in edit mode

#### Special Characters in Content
**Scenario:** Content contains HTML, scripts, or special chars

**Handling:**
- Server escapes HTML in response (Hiccup does this by default)
- Display as plain text (no XSS risk)
- Preserve user's formatting (newlines, spaces)

#### Slow Network
**Scenario:** Request takes long time

**Handling:**
- Show loading indicators immediately
- Don't timeout too quickly (allow 10-30 seconds)
- Show message if request exceeds threshold
- Offer cancel option for long operations

---

## 11. Implementation Steps

### Step 1: Set Up Component File Structure

**Action:** Create component file and namespace

**File:** `src/com/apriary/ui/summary_card.clj`

```clojure
(ns com.apriary.ui.summary-card
  (:require [com.biffweb :as biff]
            [clojure.string :as str]))
```

**Output:** Empty component namespace ready for implementation

---

### Step 2: Implement Source Badge Component

**Action:** Create function to render source badge with appropriate styling

**Code:**
```clojure
(defn source-badge [{:keys [source]}]
  (let [variants {:ai-full {:bg "bg-blue-100"
                            :text "text-blue-800"
                            :icon "robot"
                            :label "AI Generated"
                            :aria "AI Generated summary"}
                  :ai-partial {:bg "bg-amber-100"
                               :text "text-amber-800"
                               :icon "robot-pencil"
                               :label "AI Edited"
                               :aria "AI Edited summary"}
                  :manual {:bg "bg-gray-100"
                           :text "text-gray-800"
                           :icon "pencil"
                           :label "Manual"
                           :aria "Manual summary"}}
        variant (get variants (keyword source))]
    [:span {:class (str "source-badge inline-flex items-center gap-1 px-2 py-1 rounded text-sm font-medium "
                        (:bg variant) " " (:text variant))
            :aria-label (:aria variant)}
     ;; Icon SVG here
     [:span (:label variant)]]))
```

**Testing:**
- Render with each source type
- Verify correct styling applied
- Check ARIA labels with screen reader

---

### Step 3: Implement Inline Editable Field Component

**Action:** Create dual-mode component (display/edit)

**Code:**
```clojure
(defn inline-editable-field [{:keys [field-name value summary-id placeholder]}]
  (let [display-value (or value placeholder)]
    [:div {:class "editable-field group cursor-pointer hover:bg-gray-100 rounded px-2 py-1 transition-colors"
           :data-field-name field-name
           :data-original-value value}
     ;; Read-only state (default)
     [:span {:class "field-value"} display-value]
     ;; Pencil icon (visible on hover)
     [:svg {:class "inline w-4 h-4 ml-1 opacity-0 group-hover:opacity-100 transition-opacity"
            :aria-hidden "true"}
      ;; Pencil path
      ]]))

(defn inline-editable-field-edit [{:keys [field-name value summary-id]}]
  [:div {:class "editable-field editing"}
   [:input {:type "text"
            :name field-name
            :value value
            :class "border rounded px-2 py-1 focus:ring-2 focus:ring-blue-500"
            :hx-patch (str "/api/summaries/" summary-id)
            :hx-trigger "blur changed delay:100ms"
            :hx-target "closest .editable-field"
            :hx-swap "outerHTML"
            :hx-indicator (str "#spinner-" field-name)}]
   ;; Spinner
   [:svg {:id (str "spinner-" field-name)
          :class "htmx-indicator inline w-4 h-4 ml-1 animate-spin"}
    ;; Spinner path
    ]])
```

**Server-side Toggle:**
- Click handler triggers server route to return edit mode HTML
- Blur handler sends PATCH request
- Success returns display mode HTML
- Error returns edit mode with error message

**Testing:**
- Click to enter edit mode
- Type and blur to save
- Verify PATCH request sent
- Test error handling
- Test escape key cancel

---

### Step 4: Implement Content Area Component

**Action:** Create content display and edit mode

**Code:**
```clojure
(defn content-display [{:keys [content summary-id expanded?]}]
  (let [truncated? (> (count content) 150)
        display-content (if (and truncated? (not expanded?))
                          (str (subs content 0 150) "...")
                          content)]
    [:div {:class "content-area"
           :id (str "summary-content-" summary-id)
           :data-content-expanded (str expanded?)}
     [:div {:class "content-display prose prose-sm max-w-none"}
      display-content]]))

(defn content-edit-form [{:keys [content summary-id]}]
  [:div {:class "content-area editing"}
   [:form {:hx-patch (str "/api/summaries/" summary-id)
           :hx-swap "outerHTML"
           :hx-target "closest .content-area"}
    [:textarea {:name "content"
                :rows "10"
                :class "w-full border rounded p-3 focus:ring-2 focus:ring-blue-500 resize-y"
                :aria-label "Summary content"
                :minlength "50"
                :maxlength "50000"}
     content]
    [:div {:class "flex gap-2 mt-2"}
     [:button {:type "submit"
               :class "px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
               :hx-indicator "#save-spinner"}
      [:svg {:id "save-spinner"
             :class "htmx-indicator inline w-4 h-4 mr-1 animate-spin"}]
      "Save"]
     [:button {:type "button"
               :class "px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
               :hx-get (str "/api/summaries/" summary-id)
               :hx-target "closest .content-area"
               :hx-swap "outerHTML"}
      "Cancel"]]]])
```

**Testing:**
- Click edit button to enter edit mode
- Modify content and save
- Verify PATCH request with content
- Test cancel button
- Test validation (too short/long)

---

### Step 5: Implement Action Buttons Component

**Action:** Create edit, accept, delete buttons

**Code:**
```clojure
(defn action-buttons [{:keys [summary-id source accepted?]}]
  [:div {:class "action-buttons flex gap-2 items-center"}
   ;; Edit button
   [:button {:type "button"
             :class "btn-icon"
             :hx-get (str "/api/summaries/" summary-id "/edit")
             :hx-target "closest .summary-card .content-area"
             :hx-swap "outerHTML"
             :aria-label "Edit summary content"}
    ;; Pencil icon SVG
    ]

   ;; Accept button (conditional)
   (when (or (= source "ai-full") (= source "ai-partial"))
     (if accepted?
       ;; Accepted badge
       [:span {:class "accepted-badge inline-flex items-center gap-1 px-2 py-1 rounded bg-green-100 text-green-800"}
        ;; Checkmark icon
        "Accepted"]
       ;; Accept button
       [:button {:type "button"
                 :class "btn-accept px-3 py-1 bg-green-600 text-white rounded hover:bg-green-700"
                 :hx-post (str "/api/summaries/" summary-id "/accept")
                 :hx-target "closest .action-buttons"
                 :hx-swap "outerHTML"
                 :hx-confirm "Accept this summary?"}
        "Accept"]))

   ;; Delete button
   [:button {:type "button"
             :class "btn-icon text-red-600 hover:text-red-700"
             :hx-delete (str "/api/summaries/" summary-id)
             :hx-target "closest .summary-card"
             :hx-swap "outerHTML swap:1s"
             :hx-confirm "Delete this summary? This action cannot be undone."
             :aria-label "Delete summary"}
    ;; Trash icon SVG
    ]])
```

**Testing:**
- Test edit button triggers edit mode
- Test accept button (AI summaries only)
- Test delete with confirmation
- Verify accepted badge appears after acceptance

---

### Step 6: Implement Generation Metadata Component

**Action:** Display generation info for AI summaries

**Code:**
```clojure
(defn generation-metadata [{:keys [generation-id generation-date model-name]}]
  (when generation-id
    [:div {:class "generation-metadata text-sm text-gray-600"}
     [:span (str "From import on " generation-date)]
     [:span {:class "mx-2"} "•"]
     [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded bg-gray-100 text-gray-700 text-xs"}
      model-name]]))
```

**Testing:**
- Render with generation data
- Verify hidden for manual summaries
- Check date formatting

---

### Step 7: Implement Content Toggle Component

**Action:** Show more/less button

**Code:**
```clojure
(defn content-toggle [{:keys [summary-id content-length expanded?]}]
  (when (> content-length 150)
    [:button {:type "button"
              :class "content-toggle text-blue-600 hover:text-blue-700 hover:underline text-sm"
              :hx-get (str "/api/summaries/" summary-id)
              :hx-target (str "#summary-content-" summary-id)
              :hx-swap "innerHTML"
              :aria-expanded (str expanded?)
              :aria-controls (str "summary-content-" summary-id)}
     (if expanded? "Show less" "Show more")
     [:svg {:class (str "inline w-4 h-4 ml-1 transition-transform "
                        (when expanded? "rotate-180"))}
      ;; Chevron down icon
      ]]))
```

**Testing:**
- Only appears when content > 150 chars
- First click fetches full content
- Subsequent clicks toggle without API call
- Icon rotates correctly

---

### Step 8: Assemble Complete Summary Card

**Action:** Combine all components into main card

**Code:**
```clojure
(defn summary-card [{:keys [summary edit-mode?]}]
  (let [{:keys [id source hive-number observation-date special-feature
                content generation-id accepted-at]} summary]
    [:article {:class "summary-card bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow"
               :data-summary-id id
               :data-source source
               :id (str "summary-" id)}

     ;; Card Header
     [:div {:class "card-header flex items-center justify-between mb-4"}
      ;; Source badge
      [source-badge {:source source}]

      ;; Metadata section
      [:div {:class "metadata-section flex gap-4"}
       [inline-editable-field {:field-name "hive-number"
                               :value hive-number
                               :summary-id id
                               :placeholder "Hive"}]
       [inline-editable-field {:field-name "observation-date"
                               :value observation-date
                               :summary-id id
                               :placeholder "DD-MM-YYYY"}]]

      ;; Action buttons
      [action-buttons {:summary-id id
                       :source source
                       :accepted? (boolean accepted-at)}]]

     ;; Card Body
     [:div {:class "card-body"}
      ;; Special feature tag
      (when special-feature
        [:div {:class "mb-2"}
         [inline-editable-field {:field-name "special-feature"
                                 :value special-feature
                                 :summary-id id
                                 :placeholder "Special feature"}]])

      ;; Content area
      (if edit-mode?
        [content-edit-form {:content content :summary-id id}]
        [content-display {:content content :summary-id id :expanded? false}])]

     ;; Card Footer
     [:div {:class "card-footer flex justify-between items-center mt-4"}
      [generation-metadata {:generation-id generation-id
                            :generation-date "23-11-2025"  ; From generation data
                            :model-name "gpt-4-turbo"}]
      [content-toggle {:summary-id id
                       :content-length (count content)
                       :expanded? false}]]]))
```

**Testing:**
- Render complete card with sample data
- Test all interactions
- Verify responsive layout
- Check accessibility with screen reader

---

### Step 9: Create Server Routes for htmx Interactions

**Action:** Add routes to handle htmx requests

**File:** `src/com/apriary/routes/summaries.clj`

**Routes:**
```clojure
;; Edit mode toggle
(defn get-content-edit-mode [request]
  (let [summary-id (get-in request [:path-params :id])
        summary (fetch-summary summary-id)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (biff/render [content-edit-form summary])}))

;; Inline field save
(defn patch-summary-field [request]
  (let [summary-id (get-in request [:path-params :id])
        field-name (get-in request [:params :field-name])
        field-value (get-in request [:params field-name])
        updated-summary (update-summary summary-id {field-name field-value})]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (biff/render [inline-editable-field
                         {:field-name field-name
                          :value (:field-name updated-summary)
                          :summary-id summary-id}])}))

;; Routes registration
["/api/summaries/:id"
 ["/edit" {:get get-content-edit-mode}]
 ["" {:patch patch-summary-field
      :delete delete-summary}]
 ["/accept" {:post accept-summary}]]
```

**Testing:**
- Test each route independently
- Verify correct HTML returned
- Test error scenarios

---

### Step 10: Implement Validation and Error Handling

**Action:** Add validation logic and error responses

**Code:**
```clojure
(defn validate-observation-date [date-str]
  (when (and date-str (not (str/blank? date-str)))
    (if-not (re-matches #"\d{2}-\d{2}-\d{4}" date-str)
      {:valid? false :error "Invalid date format. Use DD-MM-YYYY"}
      ;; Additional date validity check
      (if (valid-date? date-str)
        {:valid? true}
        {:valid? false :error "Invalid date"}))))

(defn validate-content [content]
  (let [trimmed (str/trim content)
        length (count trimmed)]
    (cond
      (< length 50) {:valid? false :error "Content must be at least 50 characters"}
      (> length 50000) {:valid? false :error "Content must not exceed 50,000 characters"}
      :else {:valid? true})))

(defn patch-summary-with-validation [request]
  (let [params (:params request)
        validation-result (validate-params params)]
    (if (:valid? validation-result)
      ;; Process update
      (update-summary-handler request)
      ;; Return error
      {:status 400
       :body (biff/render
              [inline-editable-field-with-error
               (assoc params :error (:error validation-result))])})))
```

**Testing:**
- Test each validation function
- Verify error messages display correctly
- Test edge cases (exactly 50 chars, exactly 50000 chars)

---

### Step 11: Add Client-Side Enhancements (Optional)

**Action:** Add JavaScript for character counter and edit state management

**File:** `resources/public/js/summary-card.js`

**Code:**
```javascript
// Character counter for content textarea
document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('textarea[name="content"]').forEach(function(textarea) {
    const counter = document.createElement('div');
    counter.className = 'text-sm text-gray-600 mt-1';
    textarea.parentElement.insertBefore(counter, textarea.nextSibling);

    function updateCounter() {
      const length = textarea.value.trim().length;
      const max = 50000;

      if (length < 50) {
        counter.textContent = `Too short (${length} chars, minimum 50)`;
        counter.className = 'text-sm text-red-600 mt-1';
      } else if (length > max) {
        counter.textContent = `Too long (${length} chars, maximum ${max})`;
        counter.className = 'text-sm text-red-600 mt-1';
      } else {
        counter.textContent = `${length} / ${max} characters`;
        counter.className = 'text-sm text-gray-600 mt-1';
      }
    }

    textarea.addEventListener('input', updateCounter);
    updateCounter();
  });

  // Escape key to cancel inline edit
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
      const editField = e.target.closest('.editable-field.editing');
      if (editField) {
        // Trigger cancel action
        htmx.ajax('GET', `/api/summaries/${editField.dataset.summaryId}/field/${editField.dataset.fieldName}/cancel`, {
          target: editField,
          swap: 'outerHTML'
        });
      }
    }
  });
});
```

**Testing:**
- Character counter updates in real-time
- Escape key cancels edit
- No JavaScript errors in console

---

### Step 12: Style with Tailwind CSS

**Action:** Ensure all Tailwind classes are used correctly

**File:** `resources/tailwind.config.js`

**Custom Config (if needed):**
```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        // Custom colors if needed
      }
    }
  },
  plugins: [
    require('@tailwindcss/typography'),
  ]
}
```

**Verification:**
- All components render with correct styles
- Responsive breakpoints work (sm, md, lg)
- Hover effects function properly
- Focus states visible
- Color contrast meets WCAG AA standards

---

### Step 13: Accessibility Audit

**Action:** Test and fix accessibility issues

**Checklist:**
- [ ] All interactive elements keyboard accessible
- [ ] Tab order logical
- [ ] Focus indicators visible
- [ ] ARIA labels on icon-only buttons
- [ ] ARIA attributes (expanded, controls) correct
- [ ] Color contrast 4.5:1 minimum
- [ ] Screen reader announces changes
- [ ] Form labels associated properly

**Tools:**
- axe DevTools browser extension
- NVDA or JAWS screen reader
- Keyboard navigation testing

---

### Step 14: Write Component Tests

**Action:** Create tests for component rendering and interactions

**File:** `test/com/apriary/ui/summary_card_test.clj`

**Test Cases:**
```clojure
(deftest test-source-badge-rendering
  (testing "renders correct badge for ai-full"
    (let [html (biff/render [source-badge {:source "ai-full"}])]
      (is (str/includes? html "AI Generated"))
      (is (str/includes? html "bg-blue-100"))))

  (testing "renders correct badge for manual"
    (let [html (biff/render [source-badge {:source "manual"}])]
      (is (str/includes? html "Manual"))
      (is (str/includes? html "bg-gray-100")))))

(deftest test-inline-editable-field
  (testing "renders display mode with value"
    (let [html (biff/render [inline-editable-field
                             {:field-name "hive-number"
                              :value "A-01"
                              :summary-id "123"}])]
      (is (str/includes? html "A-01"))))

  (testing "renders placeholder when value is nil"
    (let [html (biff/render [inline-editable-field
                             {:field-name "hive-number"
                              :value nil
                              :summary-id "123"
                              :placeholder "Hive"}])]
      (is (str/includes? html "Hive")))))

(deftest test-accept-button-visibility
  (testing "shows accept button for ai-full source"
    (let [html (biff/render [action-buttons
                             {:summary-id "123"
                              :source "ai-full"
                              :accepted? false}])]
      (is (str/includes? html "Accept"))))

  (testing "hides accept button for manual source"
    (let [html (biff/render [action-buttons
                             {:summary-id "123"
                              :source "manual"
                              :accepted? false}])]
      (is (not (str/includes? html "Accept"))))))
```

**Testing:**
- Run tests: `clj -M:test`
- Achieve >80% code coverage
- Test edge cases and error scenarios

---

### Step 15: Integration Testing

**Action:** Test full card interactions with mock API

**Setup:**
1. Create test database with sample summaries
2. Start test server
3. Use htmx in browser to test interactions

**Test Scenarios:**
1. Load page with summary cards
2. Click inline field, edit, save
3. Click edit button, modify content, save
4. Click accept button, verify badge changes
5. Click delete button, confirm, verify removal
6. Click show more, verify content expands
7. Test error scenarios (network error, validation error)

**Manual Testing Checklist:**
- [ ] All buttons clickable
- [ ] Inline edit works
- [ ] Content edit works
- [ ] Accept changes button to badge
- [ ] Delete removes card
- [ ] Show more/less works
- [ ] Error messages display correctly
- [ ] Loading indicators appear
- [ ] Responsive on mobile/tablet/desktop

---

### Step 16: Performance Optimization

**Action:** Optimize rendering and interactions

**Optimizations:**
1. **Lazy load icons:** Load SVG icons from sprite sheet
2. **Debounce inline edits:** Already implemented (100ms delay)
3. **Cache expanded content:** Don't re-fetch on subsequent show/less
4. **Minimize DOM swaps:** Use targeted htmx swaps

**Code:**
```clojure
;; SVG sprite reference
(defn icon [name]
  [:svg {:class "w-4 h-4"}
   [:use {:href (str "/icons.svg#" name)}]])
```

**Testing:**
- Measure page load time
- Test with 50+ cards on page
- Verify smooth animations
- Check network tab for unnecessary requests

---

### Step 17: Documentation

**Action:** Document component usage and customization

**File:** `docs/ui/summary-card.md`

**Content:**
```markdown
# Summary Card Component

## Usage

```clojure
(require '[com.apriary.ui.summary-card :refer [summary-card]])

[summary-card {:summary summary-data
               :edit-mode? false}]
```

## Props

- `summary`: Summary DTO object (required)
- `edit-mode?`: Boolean, render in content edit mode (optional)

## Styling

All components use Tailwind CSS classes. To customize:

1. Modify Tailwind config for theme colors
2. Override classes via `:class` attribute
3. Use Tailwind's JIT mode for custom utilities

## Accessibility

- Keyboard navigation: Tab through fields, Enter/Escape to edit/cancel
- Screen reader support: All interactive elements have ARIA labels
- Focus indicators: Visible on all focusable elements

## API Integration

See API documentation for endpoint details:
- PATCH /api/summaries/{id} - Update fields
- DELETE /api/summaries/{id} - Delete summary
- POST /api/summaries/{id}/accept - Accept summary
```

**Testing:**
- Verify documentation accuracy
- Test code examples
- Ensure all props documented

---

### Step 18: Code Review and Refactoring

**Action:** Review code for quality and refactor

**Checklist:**
- [ ] Follow Clojure conventions (kebab-case, etc.)
- [ ] Remove commented code
- [ ] Extract magic numbers to constants
- [ ] Add docstrings to functions
- [ ] Consistent error handling
- [ ] No code duplication
- [ ] Proper separation of concerns

**Refactoring:**
```clojure
;; Extract constants
(def content-preview-length 150)
(def content-min-length 50)
(def content-max-length 50000)

;; Add docstrings
(defn source-badge
  "Renders a source badge indicating summary origin.

  Args:
    source: One of 'ai-full', 'ai-partial', 'manual'

  Returns:
    Hiccup span element with styled badge"
  [{:keys [source]}]
  ;; Implementation
  )
```

---

### Step 19: Deploy to Development Environment

**Action:** Deploy component to dev server for QA testing

**Steps:**
1. Build assets: `clj -M:dev build`
2. Deploy to dev server
3. Smoke test all interactions
4. Fix any environment-specific issues

**Verification:**
- [ ] Component renders correctly
- [ ] All htmx requests work
- [ ] Authentication works
- [ ] RLS enforced
- [ ] Error handling works

---

### Step 20: User Acceptance Testing

**Action:** Have stakeholders test the component

**Provide:**
- Test account credentials
- List of test scenarios
- Feedback form

**Gather Feedback:**
- Usability issues
- Missing features
- Visual design feedback
- Performance concerns

**Iterate:**
- Address critical feedback
- Plan enhancements for next iteration

---

## Summary

This implementation plan provides a comprehensive guide to building the Summary Card Component for the Apriary application. The component leverages Biff's server-side rendering with htmx for dynamic interactions, maintaining simplicity while providing rich functionality.

**Key Principles:**
- Server-rendered HTML with progressive enhancement
- htmx for AJAX interactions without client-side JavaScript frameworks
- Inline editing with auto-save for optimal UX
- Comprehensive validation at both client and server levels
- Full accessibility support (WCAG 2.1 AA compliance)
- Responsive design for mobile to desktop

**Next Steps After Implementation:**
1. Integrate into main summaries page
2. Implement generation group headers
3. Add bulk accept functionality
4. Implement CSV import UI
5. Add toast notification system

The component is designed to be maintainable, accessible, and performant, following the established tech stack and coding practices of the Apriary project.
