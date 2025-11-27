# UI Architecture for Apiary Summary

## 1. UI Structure Overview

Apiary Summary uses a **server-side rendered, progressively enhanced architecture** built with the Biff framework, htmx for dynamic interactions, and Tailwind CSS 4 for styling. The UI prioritizes simplicity and efficiency through a consolidated single-page main view that combines CSV import and summary management, complemented by a separate form page for manual summary creation.

The architecture follows these core principles:

- **Server-first rendering**: Complete HTML delivered on initial page load for fast rendering and SEO compatibility
- **Progressive enhancement**: htmx adds dynamic interactions without requiring JavaScript for basic functionality
- **Inline editing patterns**: Reduce friction with click-to-edit fields and auto-save behavior
- **Clear visual hierarchy**: Color-coded badges, source indicators, and generation grouping help users quickly scan and understand their summaries
- **Accessibility by default**: Semantic HTML, ARIA attributes, keyboard navigation, and screen reader support throughout

The application serves beekeepers who need to quickly document and manage apiary work histories, with particular emphasis on automating summary generation through AI while maintaining the flexibility to create and edit entries manually.

## 2. View List

### 2.1 Main Summaries Page

**View Name**: Main Summaries Page
**View Path**: `/` or `/summaries`
**Main Purpose**: Central hub for CSV import, AI summary generation, and summary management

**Key Information to Display**:
- CSV import interface with inline validation feedback
- Complete list of user's summaries grouped by generation (for AI batches)
- Summary metadata: hive number, observation date, special features, content preview
- Source indicators (AI-generated vs manual)
- Acceptance status for AI summaries
- Generation metadata (import date, model used, summary count)
- Toast notifications for success/error feedback

**Key View Components**:

1. **Application Header** (sticky, persistent)
   - Application name/logo (left) - links to home
   - "+ New Summary" button (center-right)
   - Logout link (far right)

2. **Toast Notification Container** (fixed position, top-right)
   - Success messages (green, auto-dismiss after 3-5 seconds)
   - Error messages (red, manual dismiss)
   - Stacking support for multiple simultaneous toasts

3. **Error Message Area** (page-level)
   - Red-bordered box for general errors (authentication, file format issues)
   - Positioned below header, above content sections

4. **CSV Import Section**
   - Section heading: "Import CSV"
   - Textarea input (8 rows, monospace font)
   - Placeholder showing expected CSV format structure
   - Helper text: "Paste CSV content (UTF-8, semicolon-separated). Must include 'observation' column."
   - Submit button: "Generate Summaries" with primary blue styling
   - Loading state: button disabled, text changes to "Generating..." with spinner icon
   - Rejected rows feedback area (collapsible)
     - Summary header: "⚠ X rows were rejected - Click to see details" (amber background)
     - Expandable list with row numbers and rejection reasons

5. **Summaries List Section**
   - Section heading: "Your Summaries" (with optional count)
   - Empty state component (shown when no summaries exist):
     - Beehive/document icon
     - Heading: "No summaries yet"
     - Descriptive text explaining how to get started
     - "+ New Summary" CTA button
   - Generation group headers (for AI-generated batches):
     - Header structure: "Import from [date]" + model badge + summary count
     - "Accept All from This Import" button (or "All accepted" status text)
     - Visual grouping: blue-50 background, blue-500 left border
   - Summary cards in responsive grid:
     - 1 column on mobile (< 768px)
     - 2 columns on tablet (≥ 768px)
     - 3 columns on desktop (≥ 1024px)

6. **Summary Card Component** (detailed structure)
   - **Card Header** (flex layout)
     - Source badge (left):
       - ai-full: Blue badge with robot icon + "AI Generated"
       - ai-partial: Amber badge with robot+pencil icon + "AI Edited"
       - manual: Gray badge with pencil icon + "Manual"
     - Hive number and observation date (center) - inline editable fields
     - Action buttons (right):
       - Edit button (pencil icon) - toggles content edit mode
       - Accept/Accepted button - visible only for AI summaries
       - Delete button (trash icon) - with confirmation dialog

   - **Card Body**
     - Special feature tag/badge (purple, if present) - inline editable
     - Content area:
       - Truncated preview (first 150 characters) by default
       - Full content when expanded or in edit mode
       - Edit mode: textarea with Save/Cancel buttons

   - **Card Footer**
     - Generation metadata (for AI summaries): "From import on [date] using [model]"
     - "Show more" / "Show less" toggle button (when content exceeds 150 chars)

**UX, Accessibility, and Security Considerations**:

- **UX**:
  - Single-page layout reduces navigation overhead
  - Inline editing minimizes context switching
  - Hover effects clearly indicate editable fields
  - Visual feedback (spinners, checkmarks) confirms actions
  - Generation grouping helps users understand batch operations
  - Chronological sorting (newest first) matches mental model

- **Accessibility**:
  - Semantic HTML: `<header>`, `<main>`, `<section>` for proper document structure
  - Logical tab order: Header → CSV section → Summaries list
  - All interactive elements keyboard accessible
  - aria-labels on icon-only buttons ("Edit summary", "Delete summary", "Accept summary")
  - aria-expanded on collapsible elements (Show more/less, Rejected rows)
  - aria-live="polite" on toast container for screen reader announcements
  - Source badges include aria-label for context ("AI Generated summary", etc.)
  - Sufficient color contrast on all text elements
  - Focus visible states on all interactive elements

- **Security**:
  - Row-Level Security (RLS) enforced at middleware ensures users see only their own summaries
  - Session-based authentication (Biff default)
  - CSRF tokens included in all mutation requests (POST, PATCH, DELETE)
  - All API requests validated server-side
  - No sensitive data exposed in client-side JavaScript
  - XSS prevention through server-side HTML templating with proper escaping

**Responsive Behavior**:
- Mobile: Single column, stacked header elements, full-width inputs
- Tablet: Two-column card grid, horizontal header layout
- Desktop: Three-column card grid, comfortable spacing

**State Management**:
- Content expansion tracked via data attributes (`data-content-expanded`)
- Edit mode controlled by DOM structure changes
- Server remains source of truth for all data
- htmx OOB (out of band) swaps enable atomic multi-section updates

---

### 2.2 New Summary Form Page

**View Name**: New Summary Form Page
**View Path**: `/summaries/new`
**Main Purpose**: Manual creation of summaries without AI generation

**Key Information to Display**:
- Form for entering summary metadata and content
- Real-time character count validation
- Field-level error messages from server validation
- Navigation back to main summaries page

**Key View Components**:

1. **Application Header** (same as main page)
   - Consistent navigation experience

2. **Breadcrumb/Back Link**
   - "← Back to Summaries" link to return to main page
   - Provides clear navigation context

3. **Form Container** (centered, max-width 2xl)
   - Form heading: "Create New Summary"

4. **Form Fields** (labeled, accessible)
   - **Hive Number** (text input, optional)
     - Label: "Hive Number"
     - Placeholder: "e.g., A-01"
     - Field-level error display area

   - **Observation Date** (text input, optional)
     - Label: "Observation Date"
     - Placeholder: "DD-MM-YYYY"
     - Format hint below input
     - Field-level error display area

   - **Special Feature** (text input, optional)
     - Label: "Special Feature"
     - Placeholder: "e.g., Queen active"
     - Field-level error display area

   - **Content** (textarea, required)
     - Label: "Observation Content"
     - Textarea: 10 rows, vertically resizable
     - Character counter below textarea:
       - Red if < 50 chars: "Too short (X chars, minimum 50)"
       - Gray if 50-50,000: "X / 50,000 characters"
       - Red if > 50,000: "Too long (X chars, maximum 50,000)"
     - Character count updates on input using vanilla JavaScript
     - Field-level error display area

5. **Form Actions**
   - Submit button: "Create Summary"
     - Primary blue styling
     - Disabled when content is outside valid range (client-side UX)
     - Server still validates to ensure security
   - Cancel/Back link (optional)

**UX, Accessibility, and Security Considerations**:

- **UX**:
  - Centered form layout focuses attention
  - Live character counter provides immediate validation feedback
  - Color-coded counter helps users understand state at a glance
  - Optional fields clearly marked
  - Validation happens on both client (UX) and server (security)
  - After successful creation, redirects to main page with success toast

- **Accessibility**:
  - All form fields have associated labels (using Biff form helpers)
  - Required fields marked with aria-required
  - Character counter uses aria-live for screen reader updates
  - Error messages associated with fields via aria-describedby
  - Keyboard navigation flows logically through form
  - Submit button clearly indicates disabled state
  - Clear focus indicators on all form elements

- **Security**:
  - Server-side validation authoritative (client-side for UX only)
  - CSRF token included in form submission (Biff default)
  - Content length validation: 50-50,000 characters
  - Date format validation: DD-MM-YYYY or empty
  - Row-Level Security ensures summary created under authenticated user
  - HTML escaping prevents XSS attacks

**Responsive Behavior**:
- Mobile: Full-width form, larger touch targets
- Desktop: max-w-2xl centered container, comfortable spacing

**Post-Submission Flow**:
1. Form submits via htmx POST to `/api/summaries`
2. Server validates and creates summary with source: "manual"
3. On success:
   - htmx redirects to main page (`/`)
   - Success toast displays: "Summary created successfully"
   - New summary appears in list (no generation group, manual badge)
4. On validation error:
   - Form re-renders with field-level error messages
   - User remains on form page to correct errors

---

### 2.3 Authentication Pages (Existing)

**View Names**: Login Page, Register Page
**View Paths**: `/login`, `/register` (Biff default routes)
**Status**: Already implemented via Biff framework, no modifications planned for MVP

**Key Information**:
- These pages use Biff's default authentication UI
- Session-based authentication with secure cookies
- After successful login, user redirects to main summaries page (`/`)
- RLS ensures each user sees only their own data

---

## 3. User Journey Map

### Journey 1: CSV Import to Bulk Accept (Primary Workflow)

**User Goal**: Generate multiple AI summaries from beekeeping notes and accept them in bulk

**Steps**:

1. **Starting Point**: User lands on main summaries page after login
   - Sees CSV import section at top
   - May see existing summaries below (if any)

2. **CSV Preparation**: User prepares CSV content in external editor
   - Format: UTF-8, semicolon-separated
   - Columns: observation, hive_number, observation_date, special_feature
   - Copies CSV content to clipboard

3. **CSV Import**: User pastes CSV into textarea on main page
   - Textarea shows placeholder with expected format
   - Helper text reminds of requirements

4. **Submission**: User clicks "Generate Summaries" button
   - Button shows loading state: "Generating..." with spinner icon
   - Button disabled during processing
   - User cannot submit multiple imports simultaneously

5. **Server Processing** (transparent to user):
   - CSV parsed and validated
   - Invalid rows rejected with reasons
   - Valid rows sent to OpenRouter API
   - AI generates summaries
   - Generation record created
   - Summary records created in database

6. **Results Display**: Page updates via htmx OOB swaps
   - Success toast appears: "14 summaries generated successfully"
   - Rejected rows section shows (if any rejections): "⚠ 1 row was rejected - Click to see details"
   - New generation group header prepended to summaries list:
     - "Import from 23-11-2025"
     - "gpt-4-turbo" model badge
     - "14 summaries"
     - "Accept All from This Import" button
   - 14 new summary cards appear below header
   - All cards show "ai-full" source badge (blue)
   - CSV textarea clears (or remains for next import)

7. **Review Phase**: User reviews generated summaries
   - Clicks "Show more" on individual summaries to see full content
   - Optionally edits metadata inline (hive number, date, special feature)
   - Optionally edits content (changes source to "ai-partial", amber badge)

8. **Bulk Acceptance**: User clicks "Accept All from This Import" button
   - htmx POST to `/api/summaries/generation/accept`
   - All summary cards update to show "Accepted" badge (green)
   - Button changes to "All accepted ✓" text in green
   - Success toast: "14 summaries accepted successfully"
   - Generation counters updated server-side

9. **Completion**: User's summaries are now documented and accepted
   - Ready for next import or manual entry
   - Can edit or delete individual summaries as needed

**Alternative Paths**:
- **Validation Errors**: If all rows rejected, error message appears, no summaries created
- **Partial Success**: Some rows rejected, others succeed - user sees both feedback
- **Network Error**: Error toast appears, user can retry CSV import
- **Individual Accept**: User can accept summaries individually instead of bulk

---

### Journey 2: Manual Summary Creation

**User Goal**: Manually create a single summary without using AI

**Steps**:

1. **Starting Point**: User on main summaries page
   - Decides to create summary manually (not enough content for CSV, or prefers control)

2. **Navigation**: User clicks "+ New Summary" button in header
   - htmx navigation to `/summaries/new`
   - Form page loads with empty fields

3. **Form Completion**: User fills in form fields
   - Hive Number: "A-03" (optional, user enters)
   - Observation Date: "24-11-2025" (optional, user enters)
   - Special Feature: "First inspection after winter" (optional, user enters)
   - Content: User types observation notes
     - Character counter updates in real-time
     - Shows "X / 50,000 characters" when valid
     - Turns red if too short or too long

4. **Validation Feedback**: Character counter guides user
   - If < 50 chars: "Too short (45 chars, minimum 50)" in red
   - Submit button disabled
   - User adds more content until valid

5. **Submission**: User clicks "Create Summary" button (now enabled)
   - htmx POST to `/api/summaries`
   - Server validates all fields

6. **Success Response**: Form submission succeeds
   - htmx redirect to main page (`/`)
   - Success toast appears: "Summary created successfully"
   - New summary visible in list
   - Shows "manual" source badge (gray with pencil icon)
   - No "Accept" button (manual summaries don't need acceptance)

7. **Completion**: Summary successfully added to user's collection
   - User can edit, delete, or create more summaries

**Alternative Paths**:
- **Validation Error**: Server returns 400 with field errors
  - Form re-renders with error messages below affected fields
  - User corrects errors and resubmits
- **Cancel**: User clicks back link, returns to main page without saving
- **Date Format Error**: User enters invalid date, server validation catches it

---

### Journey 3: Inline Metadata Editing

**User Goal**: Quickly update missing or incorrect metadata (hive number, date, special feature)

**Steps**:

1. **Starting Point**: User viewing summaries list
   - Notices a summary is missing hive number
   - Or date is incorrect
   - Or wants to add special feature tag

2. **Discovery**: User hovers over editable field
   - Field shows hover effect:
     - Light gray background (bg-gray-100)
     - Small pencil icon appears
     - Subtle underline or border
   - Indicates field is editable

3. **Activation**: User clicks on the field
   - Field transforms from read-only text to input element
   - Current value pre-populated in input
   - Input receives focus automatically
   - Other card elements remain visible but non-interactive

4. **Editing**: User modifies the value
   - Types new hive number: "B-12"
   - Or corrects date: "25-11-2025"
   - Or adds special feature: "Honey harvest"

5. **Auto-save**: User tabs away or clicks outside field (blur event)
   - htmx triggers PATCH request to `/api/summaries/{id}`
   - Spinner icon appears next to field during save
   - Input remains visible during save

6. **Success Feedback**: Server responds with updated field HTML
   - htmx swaps input back to read-only view with new value
   - Green checkmark appears briefly (1-2 seconds)
   - Field shows updated value
   - updated_at timestamp updated server-side
   - Source field unchanged (metadata edits don't affect AI source status)

7. **Completion**: Field successfully updated
   - User can continue editing other fields
   - Changes immediately reflected in UI

**Alternative Paths**:
- **Validation Error**: Date format invalid
  - Field stays in edit mode
  - Red error message appears below: "Invalid date format. Use DD-MM-YYYY"
  - User corrects and tries again
- **Network Error**: Save fails
  - Field reverts to original value
  - Error toast appears: "Failed to update. Please try again."
- **Cancel**: User presses Escape key
  - Field reverts to original value without saving
  - No API call made

---

### Journey 4: Content Editing

**User Goal**: Modify the AI-generated summary content or edit manual summary text

**Steps**:

1. **Starting Point**: User viewing summary in list
   - Wants to refine AI-generated content
   - Or expand on manual summary

2. **Enter Edit Mode**: User clicks "Edit" button in card header
   - Content area replaced with textarea (Biff form)
   - Textarea pre-populated with current full content
   - Textarea vertically resizable, max-height set
   - "Edit" button changes to "Cancel" button
   - "Save" and "Cancel" buttons appear below textarea
   - Other card elements visible but dimmed/non-interactive

3. **Content Modification**: User edits text in textarea
   - Can rewrite, add details, or correct AI output
   - Textarea expands/contracts as needed
   - No character counter shown (assumes user knows content is valid)

4. **Decision Point**: User chooses to save or cancel

   **Path A - Save**:
   - User clicks "Save" button
   - htmx PATCH to `/api/summaries/{id}` with new content
   - Loading indicator on Save button
   - Server validates (50-50,000 characters)
   - If summary was "ai-full", server changes source to "ai-partial"
   - Response returns updated summary HTML

   - **Success**:
     - Textarea swaps back to read-only content view
     - Updated content displays
     - If source changed: badge updates from blue "AI Generated" to amber "AI Edited"
     - Success feedback (checkmark or subtle highlight)
     - Toast notification: "Summary updated" (with note if source changed)

   - **Validation Error**:
     - Textarea remains in edit mode
     - Error message appears: "Content must be between 50 and 50,000 characters"
     - User can correct and retry

   **Path B - Cancel**:
   - User clicks "Cancel" button
   - Textarea swaps back to read-only view with original content
   - No API call made
   - No changes persisted
   - "Edit" button reappears

5. **Completion**: Content update complete
   - User can continue browsing or editing other summaries

**Alternative Paths**:
- **Network Error During Save**: Save fails
  - Textarea remains in edit mode
  - Error toast: "Failed to save. Please try again."
  - User can retry save or cancel
- **Concurrent Edit Warning**: Another edit in progress (future enhancement)
  - Save disabled until other operation completes

---

### Journey 5: Individual Summary Acceptance

**User Goal**: Accept a single AI-generated summary to confirm quality

**Steps**:

1. **Starting Point**: User reviewing AI-generated summary
   - Summary shows "ai-full" or "ai-partial" badge
   - "Accept" button visible in card header

2. **Acceptance**: User clicks "Accept" button
   - htmx POST to `/api/summaries/{id}/accept`
   - Button shows loading state briefly

3. **Server Processing**:
   - Validates summary is AI-generated (not manual)
   - Increments appropriate generation counter:
     - If ai-full: `accepted-unedited-count++`
     - If ai-partial: `accepted-edited-count++`
   - Updates generation updated_at timestamp

4. **Success Response**:
   - "Accept" button replaced with "Accepted" badge (green with checkmark)
   - Badge is non-interactive (cannot un-accept in MVP)
   - Success toast: "Summary accepted"
   - Generation header may update if all summaries now accepted

5. **Completion**: Summary marked as accepted
   - Contributes to success metrics
   - User can continue reviewing other summaries

**Alternative Paths**:
- **Already Accepted**: User clicks Accept again (shouldn't happen if UI correct)
  - Server returns 409 Conflict
  - Error toast: "Summary already accepted"
- **Manual Summary**: Accept button not visible (UI prevents this)
  - If somehow triggered, server returns 400: "Cannot accept manual summaries"

---

### Journey 6: Summary Deletion

**User Goal**: Permanently remove an unwanted summary

**Steps**:

1. **Starting Point**: User viewing summary in list
   - Decides summary is no longer needed

2. **Deletion Initiation**: User clicks delete button (trash icon)
   - Browser native confirmation dialog appears via htmx hx-confirm
   - Dialog: "Delete this summary?"
   - User confirms or cancels

3. **Confirmation**: User clicks "OK" in dialog
   - htmx DELETE to `/api/summaries/{id}`
   - Summary card shows loading state (optional fade or spinner)

4. **Server Processing**:
   - Validates user owns summary (RLS)
   - Permanently deletes from database (hard delete)
   - Returns 200 or 204 success

5. **Success Response**:
   - htmx targets "closest .summary-card"
   - Card fades out with CSS transition
   - Card removed from DOM
   - Success toast: "Summary deleted"
   - If was last summary in generation group, group header may also remove

6. **Completion**: Summary removed from user's collection
   - Cannot be recovered (no soft delete in MVP)
   - User continues managing remaining summaries

**Alternative Paths**:
- **Cancel Confirmation**: User clicks "Cancel" in dialog
  - No API call made
  - Summary remains unchanged
- **Network Error**: Delete fails
  - Card remains in UI
  - Error toast: "Failed to delete. Please try again."
- **Already Deleted**: Summary deleted by another session (edge case)
  - Server returns 404
  - Card removes from UI
  - Error toast: "Summary not found"

---

### Journey 7: Content Expansion (Secondary Interaction)

**User Goal**: View full summary content when preview is truncated

**Steps**:

1. **Starting Point**: User viewing summary card
   - Content truncated to 150 characters with "... Show more" button

2. **Expansion**: User clicks "Show more" button
   - htmx GET to `/api/summaries/{id}`
   - htmx target: `#summary-content-{id}`
   - Loading indicator (optional subtle spinner)

3. **Server Response**:
   - Returns full summary content HTML
   - htmx swaps into content area with innerHTML

4. **Display Update**:
   - Full content displays
   - Button text changes to "Show less"
   - data attribute updated: `data-content-expanded="true"`

5. **User Reads**: User reviews full content
   - Can now edit, accept, or perform other actions with full context

6. **Collapse** (optional): User clicks "Show less" button
   - No API call (full content already loaded)
   - htmx swaps back to truncated view
   - Button text changes to "Show more"
   - data attribute updated: `data-content-expanded="false"`

7. **Completion**: User has viewed full content
   - Can continue browsing other summaries

**Alternative Paths**:
- **Network Error**: Fetch fails
  - "Show more" button remains
  - Error toast: "Failed to load content. Please try again."
- **Already Expanded**: Button shows "Show less"
  - Toggle collapses without API call

---

## 4. Layout and Navigation Structure

### 4.1 Page Layout Hierarchy

**Global Layout Pattern** (applied to all views):

```
┌─────────────────────────────────────────────────────────────┐
│ Application Header (sticky top-0, z-50)                     │
│ ┌───────────┬─────────────────────────┬─────────────────┐  │
│ │ App Name  │                         │ + New Summary   │  │
│ │ (logo)    │                         │ Logout          │  │
│ └───────────┴─────────────────────────┴─────────────────┘  │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ Toast Notification Container (fixed top-20 right-4, z-50)   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ✓ Success message (auto-dismiss)                        │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ ✗ Error message (manual dismiss)                        │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ Main Content Area (varies by view)                          │
│                                                              │
│ [View-specific content here]                                │
│                                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Main Summaries Page Layout

```
┌─────────────────────────────────────────────────────────────┐
│ Header (sticky)                                              │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ Error Message Area (conditional, page-level errors)         │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ CSV Import Section (bg-gray-50, p-6, rounded, mb-8)         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Import CSV (heading)                                     │ │
│ │                                                          │ │
│ │ [Textarea - 8 rows, monospace font, placeholder]        │ │
│ │                                                          │ │
│ │ Helper text: "Paste CSV content (UTF-8, semicolon...)   │ │
│ │                                                          │ │
│ │ [Generate Summaries Button] (loading state on submit)   │ │
│ │                                                          │ │
│ │ ┌──────────────────────────────────────────────────────┐│ │
│ │ │ ⚠ Rejected Rows Feedback (collapsible, conditional) ││ │
│ │ │ "X rows were rejected - Click to see details"        ││ │
│ │ │ [Expandable list of row numbers + reasons]           ││ │
│ │ └──────────────────────────────────────────────────────┘│ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ Summaries List Section                                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Your Summaries (heading, with count)                    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ [Empty State - shown when no summaries]                     │
│ OR                                                           │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Generation Group Header (bg-blue-50, border-l-4)        │ │
│ │ "Import from [date]" | Model badge | "14 summaries"    │ │
│ │                           [Accept All from This Import] │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌───────────────┬───────────────┬───────────────┐          │
│ │ Summary Card  │ Summary Card  │ Summary Card  │ (grid)   │
│ │               │               │               │          │
│ └───────────────┴───────────────┴───────────────┘          │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Generation Group Header (next import batch)             │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌───────────────┬───────────────┬───────────────┐          │
│ │ Summary Card  │ Summary Card  │ Summary Card  │          │
│ └───────────────┴───────────────┴───────────────┘          │
│                                                              │
│ Manual Summaries (no group header)                          │
│ ┌───────────────┬───────────────┬───────────────┐          │
│ │ Summary Card  │ Summary Card  │ Summary Card  │          │
│ │ (manual)      │ (manual)      │ (manual)      │          │
│ └───────────────┴───────────────┴───────────────┘          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 Summary Card Detailed Layout

```
┌───────────────────────────────────────────────────────────┐
│ CARD HEADER (flex, items-center, justify-between)         │
│ ┌──────────────┬─────────────────────┬─────────────────┐ │
│ │ [AI Badge]   │ Hive: A-01          │ [Edit] [Accept] │ │
│ │ Blue/Amber   │ Date: 23-11-2025    │ [Delete]        │ │
│ │ (with icon)  │ (inline editable)   │ (icons)         │ │
│ └──────────────┴─────────────────────┴─────────────────┘ │
├───────────────────────────────────────────────────────────┤
│ CARD BODY (p-4)                                            │
│                                                            │
│ [Special Feature Tag] (purple badge, if present)          │
│ (inline editable)                                          │
│                                                            │
│ Content Preview / Full Content:                           │
│ "Hive A-01 is showing high activity with strong pollen   │
│  foraging. Monitor closely for swarming behavior in..."   │
│                                                            │
│ OR (in edit mode):                                        │
│ ┌────────────────────────────────────────────────────┐   │
│ │ [Textarea - editable content]                       │   │
│ │                                                     │   │
│ │                                                     │   │
│ └────────────────────────────────────────────────────┘   │
│ [Save] [Cancel]                                           │
│                                                            │
├───────────────────────────────────────────────────────────┤
│ CARD FOOTER (text-sm, text-gray-600)                      │
│                                                            │
│ From import on 23-11-2025 using gpt-4-turbo              │
│                                                            │
│ [Show more] / [Show less] (if content > 150 chars)       │
│                                                            │
└───────────────────────────────────────────────────────────┘
```

### 4.4 New Summary Form Page Layout

```
┌─────────────────────────────────────────────────────────────┐
│ Header (sticky)                                              │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ Main Content (max-w-2xl mx-auto, p-6)                       │
│                                                              │
│ [← Back to Summaries]                                       │
│                                                              │
│ Create New Summary (heading)                                │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Form Container                                           │ │
│ │                                                          │ │
│ │ Hive Number (label)                                      │ │
│ │ [Text Input - optional]                                  │ │
│ │ [Field-level error message area]                         │ │
│ │                                                          │ │
│ │ Observation Date (label)                                 │ │
│ │ [Text Input - DD-MM-YYYY placeholder]                    │ │
│ │ Format: DD-MM-YYYY (helper text)                         │ │
│ │ [Field-level error message area]                         │ │
│ │                                                          │ │
│ │ Special Feature (label)                                  │ │
│ │ [Text Input - optional]                                  │ │
│ │ [Field-level error message area]                         │ │
│ │                                                          │ │
│ │ Observation Content * (label, required indicator)        │ │
│ │ ┌──────────────────────────────────────────────────┐    │ │
│ │ │ [Textarea - 10 rows, vertically resizable]       │    │ │
│ │ │                                                   │    │ │
│ │ │                                                   │    │ │
│ │ └──────────────────────────────────────────────────┘    │ │
│ │ Character counter: X / 50,000 characters                 │ │
│ │ (color-coded: gray when valid, red when invalid)         │ │
│ │ [Field-level error message area]                         │ │
│ │                                                          │ │
│ │ [Create Summary Button] (disabled if content invalid)    │ │
│ │                                                          │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.5 Navigation Flow Diagram

```
┌──────────────┐
│    Login     │
│    Page      │
└──────┬───────┘
       │
       │ (successful login)
       ▼
┌──────────────────────────────────────────────┐
│         Main Summaries Page                  │
│         (/ or /summaries)                    │
│                                              │
│  ┌────────────────┐  ┌──────────────────┐  │
│  │ CSV Import     │  │ Summaries List    │  │
│  │ Section        │  │ with Cards        │  │
│  └────────────────┘  └──────────────────┘  │
└────┬─────────────────────────────┬──────────┘
     │                             │
     │ (+ New Summary button)      │ (inline actions)
     ▼                             │
┌──────────────────┐              │
│ New Summary Form │              │ • Edit (inline)
│ Page             │              │ • Accept
│ (/summaries/new) │              │ • Delete
└────┬─────────────┘              │ • Show more/less
     │                             │
     │ (Create Summary)            │
     ▼                             │
┌──────────────────────────────────▼──────────┐
│         Main Summaries Page                  │
│         (returns after creation)             │
│         + success toast                      │
└──────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│  Logout Link (in header, any page)         │
│  → redirects to Login Page                 │
└────────────────────────────────────────────┘
```

### 4.6 Navigation Mechanisms

**Primary Navigation** (Application Header):
- **App Name/Logo** (left): Links to home (`/` or `/summaries`)
  - Always visible, provides consistent way to return to main view
- **"+ New Summary" Button** (center-right): Links to `/summaries/new`
  - Primary action for manual summary creation
  - Available from all authenticated pages
- **Logout Link** (far right): Triggers logout, redirects to login
  - Ends session, clears authentication

**Secondary Navigation**:
- **Breadcrumb/Back Links**: On form page, "← Back to Summaries"
  - Provides clear path back to main view
  - htmx navigation for SPA-like experience
- **htmx History**: Enabled for route changes
  - Browser back/forward buttons work as expected
  - `/summaries` ↔ `/summaries/new` transitions tracked

**In-Page Navigation**:
- **Anchor Links**: Not used in MVP (simple enough without)
- **Scroll Behavior**: Smooth scrolling, sticky header remains visible
- **Focus Management**: After htmx swaps, focus moves logically
  - After CSV import: focus to first new summary
  - After inline edit save: focus remains on field or moves to next editable

**Navigation Characteristics**:
- **Shallow hierarchy**: Only 2 levels (main page, new summary form)
- **Context retention**: htmx preserves scroll position on back navigation
- **No dead ends**: Every page has clear path back or forward
- **Consistent header**: Same navigation available regardless of view

---

## 5. Key Components

### 5.1 Reusable UI Components

These components are used across multiple views and interactions:

#### 5.1.1 Source Badge Component

**Purpose**: Visually indicate the origin of a summary (AI-generated vs manual, edited vs unedited)

**Variants**:
- **ai-full**: "AI Generated"
  - Color: Blue background (blue-100), blue text (blue-800)
  - Icon: Robot/CPU icon (SparklesIcon or CpuChipIcon)
  - Indicates: Unedited AI-generated summary
  - aria-label: "AI Generated summary"

- **ai-partial**: "AI Edited"
  - Color: Amber background (amber-100), amber text (amber-800)
  - Icon: Robot + Pencil icon combo
  - Indicates: AI-generated summary that user has edited
  - aria-label: "AI Edited summary"

- **manual**: "Manual"
  - Color: Gray background (gray-100), gray text (gray-800)
  - Icon: Pencil icon (PencilIcon)
  - Indicates: User-created summary without AI
  - aria-label: "Manual summary"

**Structure**:
- Inline badge: `px-2 py-1 rounded text-sm font-medium`
- Icon + text layout: `flex items-center gap-1`
- Consistent sizing: 16px icons
- Always visible in summary card header

**Accessibility**:
- aria-label provides context for screen readers
- Color is not the only indicator (icons + text labels)
- Sufficient contrast ratios

---

#### 5.1.2 Inline Editable Field Component

**Purpose**: Enable quick editing of metadata fields with auto-save behavior

**Use Cases**:
- Hive number
- Observation date
- Special feature tag

**States**:

1. **Read-only State** (default)
   - Display: Plain text with value or placeholder
   - Hover: Light background (bg-gray-100), pencil icon, cursor pointer
   - Click: Transitions to edit state

2. **Edit State**
   - Display: Input element (text or date) with current value
   - Focus: Automatically focused on activation
   - Blur: Triggers auto-save
   - Escape key: Cancels, reverts to read-only

3. **Saving State**
   - Display: Input remains, spinner icon appears
   - Interaction: Input disabled during save

4. **Success State**
   - Display: Returns to read-only with updated value
   - Feedback: Green checkmark appears briefly (1-2 seconds)

5. **Error State**
   - Display: Remains in edit state
   - Feedback: Red error message below field
   - Interaction: User can correct and retry

**htmx Pattern**:
```html
<div class="editable-field">
  <input
    hx-trigger="blur changed delay:100ms"
    hx-patch="/api/summaries/{id}"
    hx-target="closest .editable-field"
    hx-swap="outerHTML"
    hx-indicator="#spinner-{field-id}"
  />
</div>
```

**Accessibility**:
- Keyboard accessible (Tab, Enter, Escape)
- aria-label describes field purpose
- Focus visible states
- Error messages associated via aria-describedby

---

#### 5.1.3 Action Button Component

**Purpose**: Consistent interactive buttons for summary card actions

**Variants**:

1. **Edit Button**
   - Icon: PencilIcon (outline)
   - Label: "Edit" (visible or aria-label if icon-only)
   - Action: Toggles content edit mode
   - Style: Secondary button (gray/outline)

2. **Accept Button**
   - Label: "Accept"
   - Icon: CheckCircleIcon (optional)
   - Action: Marks summary as accepted
   - Style: Primary button (blue/green)
   - Visibility: Only for AI summaries (ai-full, ai-partial)

3. **Accepted Badge** (post-acceptance)
   - Label: "Accepted"
   - Icon: CheckCircleIcon (solid, green)
   - Style: Badge (green-100 background, green-800 text)
   - Non-interactive (no click action)

4. **Delete Button**
   - Icon: TrashIcon (outline)
   - Label: "Delete" (aria-label if icon-only)
   - Action: Deletes summary with confirmation
   - Style: Danger button (red/outline)
   - Confirmation: Browser native confirm dialog

5. **Show More/Less Button**
   - Label: "Show more" or "Show less"
   - Icon: ChevronDownIcon / ChevronUpIcon
   - Action: Expands/collapses content
   - Style: Text link (underlined on hover)
   - aria-expanded: "true" / "false"

**Accessibility**:
- Minimum 44x44px touch target on mobile
- Clear focus indicators
- aria-labels for icon-only buttons
- Disabled states visually distinct and announced

---

#### 5.1.4 Toast Notification Component

**Purpose**: Provide non-blocking feedback for user actions

**Types**:

1. **Success Toast**
   - Color: Green background (green-100), green border (green-500)
   - Icon: CheckCircleIcon (solid, green)
   - Auto-dismiss: 3-5 seconds
   - Example: "14 summaries generated successfully"

2. **Error Toast**
   - Color: Red background (red-100), red border (red-500)
   - Icon: XCircleIcon (solid, red)
   - Auto-dismiss: Manual (user closes) or 10 seconds
   - Example: "Failed to save. Please try again."

3. **Info Toast** (optional)
   - Color: Blue background (blue-100), blue border (blue-500)
   - Icon: InformationCircleIcon
   - Auto-dismiss: 5 seconds
   - Example: "Summary source changed to 'AI Edited'"

**Structure**:
- Fixed position: `fixed top-20 right-4 z-50`
- Stacking: `space-y-2` for multiple toasts
- Width: `max-w-md`
- Padding: `p-4 rounded shadow-lg`
- Close button: X icon (optional, for manual dismiss)

**Behavior**:
- Slide-in animation on appearance
- Fade-out on auto-dismiss
- ARIA live region: `aria-live="polite"` for screen reader announcements
- Focus management: Does not steal focus

**htmx Integration**:
- Delivered via OOB (out of band) swap
- Server includes toast HTML in response with `hx-swap-oob="afterbegin:#toast-container"`
- Auto-dismiss via htmx trigger: `hx-trigger="load delay:3s"` + `hx-swap="delete"`

---

#### 5.1.5 Generation Group Header Component

**Purpose**: Visually group AI-generated summaries by import batch

**Structure**:
```
┌─────────────────────────────────────────────────────────┐
│ Import from 23-11-2025 | [gpt-4-turbo] | 14 summaries  │
│                          [Accept All from This Import]  │
└─────────────────────────────────────────────────────────┘
```

**Elements**:
- **Title**: "Import from [date]" (date from generation/created-at)
- **Model Badge**: Small badge showing AI model used (e.g., "gpt-4-turbo")
- **Summary Count**: "14 summaries" (from generation/generated-count)
- **Bulk Accept Button**: "Accept All from This Import"
  - Visible when: `accepted-unedited-count + accepted-edited-count < generated-count`
  - Replaced with: "All accepted ✓" (green text) when all accepted

**Styling**:
- Background: `bg-blue-50`
- Left border: `border-l-4 border-blue-500` (visual grouping indicator)
- Padding: `p-4`
- Flex layout: Title/metadata left, button right
- Margin: `mt-6` first group, `mt-8` subsequent groups

**Responsive**:
- Mobile: Stack title/metadata and button vertically
- Desktop: Horizontal layout

**Accessibility**:
- Semantic heading (h3) for "Import from [date]"
- Button has clear label
- Group relationship indicated by visual proximity

---

#### 5.1.6 Empty State Component

**Purpose**: Guide users when no summaries exist

**Structure**:
```
┌─────────────────────────────────────────────┐
│            [Beehive/Document Icon]          │
│                                             │
│           No summaries yet                  │
│                                             │
│  Get started by importing CSV data or       │
│  creating your first summary manually.      │
│                                             │
│          [+ New Summary Button]             │
│                                             │
└─────────────────────────────────────────────┘
```

**Elements**:
- **Icon**: Large (64px-96px) beehive or document illustration
- **Heading**: "No summaries yet" (text-xl, font-semibold)
- **Descriptive Text**: Explains how to get started (text-gray-600)
- **CTA Button**: "+ New Summary" (primary blue button)
  - Links to `/summaries/new`

**Styling**:
- Centered text alignment
- Vertical spacing: `space-y-4`
- Container: `py-12 px-6`
- Background: Light gray or white

**Visibility**:
- Shown only when user has zero summaries
- Hidden once first summary created

---

#### 5.1.7 Character Counter Component

**Purpose**: Provide real-time validation feedback for content fields

**Use Case**: New summary form content textarea

**States**:

1. **Too Short** (< 50 characters)
   - Color: Red (text-red-600)
   - Message: "Too short (X chars, minimum 50)"

2. **Valid** (50-50,000 characters)
   - Color: Gray (text-gray-600)
   - Message: "X / 50,000 characters"

3. **Too Long** (> 50,000 characters)
   - Color: Red (text-red-600)
   - Message: "Too long (X chars, maximum 50,000)"

**Implementation**:
- Vanilla JavaScript `oninput` event listener
- Updates character count in real-time as user types
- Conditionally enables/disables submit button based on validity
- No network requests (client-side only for UX)

**Accessibility**:
- aria-live region announces count changes to screen readers
- aria-live="polite" to avoid interrupting typing
- Color not sole indicator (text explains state)

**Styling**:
- Position: Below textarea, right-aligned
- Font size: `text-sm`
- Updates smoothly without layout shift

---

#### 5.1.8 Loading Indicator Component

**Purpose**: Provide visual feedback during asynchronous operations

**Variants**:

1. **Button Loading State**
   - Icon: ArrowPathIcon (spinning animation)
   - Text: Changes to action in progress (e.g., "Generating...")
   - State: Button disabled during loading
   - Example: CSV import submit button

2. **Inline Spinner**
   - Icon: Small spinner (16px-20px)
   - Position: Next to saving field (inline edit)
   - Style: Gray, subtle animation

3. **Card Loading State** (optional)
   - Skeleton or fade effect on card during update
   - Used for delete operation

**Animation**:
- CSS: `animate-spin` utility (Tailwind)
- Smooth rotation, no jank
- Stops when operation completes

**Accessibility**:
- aria-busy="true" on loading elements
- Screen reader announces loading state
- Loading indicator disappears when complete

---

### 5.2 Component Interaction Patterns

#### 5.2.1 Auto-save Pattern (Inline Editable Fields)

**Trigger**: User edits field and blurs (clicks away or tabs)

**Flow**:
1. User clicks field → transforms to input
2. User edits value
3. User tabs away (blur event)
4. htmx triggers PATCH request with `delay:100ms` (debounce)
5. Spinner appears next to field
6. Server validates and updates
7. Response contains updated field HTML
8. htmx swaps input back to read-only view
9. Green checkmark appears briefly
10. Checkmark fades out after 1-2 seconds

**Error Handling**:
- If validation fails: field stays in edit mode, error message appears
- If network fails: field reverts to original value, error toast appears

---

#### 5.2.2 Out of Band (OOB) Swap Pattern

**Use Case**: CSV import updates multiple page sections simultaneously

**Flow**:
1. User submits CSV import
2. Server processes CSV
3. Response includes multiple HTML fragments:
   - Main target: New generation header + summary cards (prepended to list)
   - OOB target 1: Success toast (appended to toast container)
   - OOB target 2: Rejected rows section (swapped into CSV section)
4. htmx performs all swaps atomically
5. Page updates in multiple places from single request

**HTML Structure**:
```html
<!-- Main response target -->
<div id="summaries-list">
  <!-- New generation header + cards -->
</div>

<!-- OOB swap for toast -->
<div hx-swap-oob="afterbegin:#toast-container">
  <!-- Success toast HTML -->
</div>

<!-- OOB swap for rejected rows -->
<div hx-swap-oob="innerHTML:#rejected-rows">
  <!-- Rejected rows feedback HTML -->
</div>
```

---

#### 5.2.3 Lazy Loading Pattern (Content Expansion)

**Use Case**: "Show more" button fetches full content

**Flow**:
1. Card initially renders with truncated content (first 150 chars)
2. User clicks "Show more" button
3. htmx GET request to `/api/summaries/{id}`
4. Response contains full content HTML
5. htmx swaps into `#summary-content-{id}` with `innerHTML`
6. Button text changes to "Show less"
7. data attribute updated: `data-content-expanded="true"`
8. Future "Show less" clicks toggle visibility without API call

**Optimization**:
- Full content fetched once, then cached in DOM
- Hidden div pattern for show/less toggle after initial fetch
- No repeated API calls for same summary

---

#### 5.2.4 Optimistic vs Pessimistic Updates

**Pessimistic Updates** (used in MVP):
- All mutations wait for server confirmation before updating UI
- More reliable, simpler error handling
- Used for: inline edits, deletions, acceptance, CSV import

**Flow**:
1. User action triggers request
2. Loading indicator appears
3. Server processes
4. Response updates UI
5. Loading indicator disappears

**No Optimistic Updates in MVP**:
- Avoids complexity of rollback on error
- Ensures UI always reflects server state
- Acceptable latency for beekeeping use case (not real-time critical)

---

### 5.3 Error Handling Components

#### 5.3.1 Field-Level Error Messages

**Use Case**: Form validation errors (New Summary form, inline edits)

**Display**:
- Red text below affected field
- Icon: XCircleIcon (optional)
- Message: Clear, actionable explanation
- Example: "Invalid date format. Use DD-MM-YYYY"

**Accessibility**:
- aria-describedby links error to field
- aria-invalid="true" on field when error present
- Error announced to screen readers

---

#### 5.3.2 Page-Level Error Messages

**Use Case**: General errors affecting entire operation

**Display**:
- Red-bordered box at top of page (below header)
- Icon: XCircleIcon
- Heading: Error type (e.g., "Import Failed")
- Message: Error description and next steps
- Close button: Manual dismiss

**Styling**:
- Background: red-50
- Border: red-500, 2px
- Padding: p-4
- Margin: mb-6

**Visibility**:
- Appears after failed operations (CSV import, authentication)
- Manually dismissible
- Does not auto-dismiss (requires user acknowledgment)

---

#### 5.3.3 Rejected Rows Feedback Component

**Use Case**: CSV import validation errors

**Display**:
- Collapsible section within CSV import area
- Summary header: "⚠ X rows were rejected - Click to see details"
- Amber background (amber-100)
- Expandable list:
  - Row number
  - Rejection reason (e.g., "Observation text too short (23 characters)")

**Interaction**:
- Initially collapsed if rejections exist
- Click header to expand/collapse
- HTML5 `<details>` and `<summary>` elements (progressive enhancement)

**Accessibility**:
- Semantic HTML provides native keyboard and screen reader support
- aria-expanded managed by browser

---

## 6. Responsive Design Strategy

### 6.1 Breakpoints

- **Mobile**: Default (< 768px)
- **Tablet**: `md:` (≥ 768px)
- **Desktop**: `lg:` (≥ 1024px)

### 6.2 Responsive Adaptations by Component

**Summary Cards Grid**:
- Mobile: 1 column (grid-cols-1)
- Tablet: 2 columns (md:grid-cols-2)
- Desktop: 3 columns (lg:grid-cols-3)

**Card Header**:
- Mobile: Vertical stack (flex-col)
- Tablet+: Horizontal (flex-row)

**Generation Group Header**:
- Mobile: Title/metadata and button stacked vertically
- Tablet+: Horizontal layout with button aligned right

**CSV Section**:
- Textarea: Always full-width
- Button: Full-width on mobile, auto-width on tablet+

**Application Header**:
- Mobile: May stack elements or use compact layout
- Desktop: Horizontal with ample spacing

**Form Inputs**:
- Mobile: Full-width with larger touch targets (min 44x44px)
- Desktop: Comfortable width with standard sizing

### 6.3 Touch Considerations (Mobile)

- Minimum 44x44px touch targets for all interactive elements
- Increased spacing between clickable items
- Larger buttons on mobile breakpoint
- Inline edit fields activate on tap (no hover required)
- Swipe gestures: Not implemented in MVP (use buttons)

---

## 7. Accessibility Compliance Summary

### 7.1 WCAG 2.1 AA Compliance Targets

**Perceivable**:
- Color contrast: All text meets 4.5:1 ratio (body text), 3:1 (large text)
- Text alternatives: All icons have aria-labels or visible text labels
- Distinguishable: Color is not the only visual indicator (icons + text)

**Operable**:
- Keyboard accessible: All functionality available via keyboard
- Logical tab order: Follows visual flow
- Focus visible: Clear focus indicators on all interactive elements
- No keyboard traps: Users can navigate away from all components

**Understandable**:
- Clear labels: All form fields have associated labels
- Error messages: Clear, actionable, announced to screen readers
- Predictable navigation: Consistent header across all pages

**Robust**:
- Semantic HTML: Proper use of headings, landmarks, lists
- ARIA attributes: Used appropriately where semantic HTML insufficient
- Valid markup: No errors that interfere with assistive technology

### 7.2 Screen Reader Considerations

- Main regions identified: `<header>`, `<main>`, `<section>`
- Heading hierarchy: Logical h1 → h2 → h3 structure
- Form labels: All inputs associated via `<label>` or aria-labelledby
- Live regions: Toast container uses aria-live="polite"
- Hidden content: Decorative elements use aria-hidden="true"
- Dynamic updates: ARIA live regions announce changes without interruption

### 7.3 Keyboard Navigation

**Tab Order**:
1. Skip to main content (optional)
2. Application header: App name → New Summary → Logout
3. CSV import section: Textarea → Generate button
4. Summaries list: First summary card → actions → next card
5. Footer (if present)

**Keyboard Shortcuts** (standard browser behavior):
- Tab: Move forward
- Shift+Tab: Move backward
- Enter: Activate buttons/links
- Space: Activate buttons, toggle checkboxes
- Escape: Cancel inline edit, close modals (future)

**Inline Edit Keyboard Behavior**:
- Click or Enter: Activate edit mode
- Type: Edit value
- Tab or Enter: Save and move to next field
- Escape: Cancel and revert to original value

---

## 8. Security Considerations in UI

### 8.1 Authentication and Authorization

**Session-Based Authentication**:
- All pages except login/register require authentication
- Unauthenticated requests redirect to login page
- Session cookies: httpOnly, secure, sameSite flags

**Row-Level Security (RLS)**:
- Enforced at middleware/API layer, not in UI
- UI assumes data is already filtered by user-id
- No client-side filtering or access control logic

**CSRF Protection**:
- All forms include CSRF tokens (Biff default)
- htmx reads CSRF token from meta tag automatically
- Tokens validated server-side on all mutation requests

### 8.2 Input Validation and Sanitization

**Client-Side Validation** (UX only):
- Character counter enforces length limits
- Date format hints guide correct input
- Submit buttons disabled when invalid (can be bypassed)

**Server-Side Validation** (authoritative):
- All inputs validated server-side regardless of client state
- HTML escaping prevents XSS attacks
- Server-side templating (Hiccup) escapes by default

**No Sensitive Data in Client**:
- No API keys, tokens, or secrets in JavaScript
- No user data in localStorage or sessionStorage
- All sensitive operations happen server-side

### 8.3 Rate Limiting (Server-Side)

**CSV Import**:
- Maximum 5 imports per hour per user
- Rate limit enforced server-side
- 429 error returned when exceeded
- UI shows error toast with retry guidance

**API Endpoints**:
- 100 requests per minute per user (general limit)
- Rate limit headers in responses
- UI does not implement client-side throttling (trusts server)

### 8.4 Content Security Policy (Server-Side)

**Headers** (configured server-side):
- CSP: Restricts script sources to same-origin
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- Strict-Transport-Security: Force HTTPS

**UI Impact**:
- No inline scripts (htmx attributes only)
- No eval() or dangerous JavaScript patterns
- All JavaScript loaded from same origin

---

## 9. Performance Considerations

### 9.1 Initial Page Load

**Server-Side Rendering**:
- Complete HTML delivered on first request
- No loading spinners or blank screens
- Fast first contentful paint (FCP)

**CSS**:
- Tailwind CSS purged in production
- Critical CSS inlined (optional)
- Minimal file size

**JavaScript**:
- htmx library only (~14KB gzipped)
- Character counter: minimal vanilla JS
- No heavy frameworks (React, Vue, etc.)

### 9.2 Lazy Loading and Code Splitting

**Lazy Loading**:
- Full summary content fetched on-demand ("Show more")
- Initial list shows truncated previews only

**No Code Splitting Needed**:
- Single htmx library, no dynamic imports
- All JavaScript minimal enough to load upfront

### 9.3 API Efficiency

**Batch Operations**:
- CSV import processes multiple rows in single request
- Bulk accept updates all summaries in generation at once

**Pagination** (not in MVP, but considered):
- API supports limit/offset for summary list
- Default limit: 50 summaries
- Future: Implement infinite scroll or pagination UI

**Response Size**:
- Summary list returns truncated content (150 chars)
- Full content fetched separately when needed

### 9.4 Caching Strategy

**Browser Caching**:
- Static assets cached with far-future expiration
- HTML not cached (session-dependent)

**No Application Caching**:
- No client-side caching of API responses
- Server remains source of truth
- Future: Consider HTTP caching headers for read-only data

### 9.5 Rendering Performance

**CSS Transitions**:
- Smooth animations via `transition-all duration-300`
- Fade-in for new cards, fade-out for deletions
- GPU-accelerated transforms where possible

**DOM Updates**:
- htmx swaps are efficient (targeted, minimal re-renders)
- OOB swaps update multiple sections without full page reload
- No unnecessary DOM thrashing

**Perceived Performance**:
- Immediate loading indicators (spinners, disabled buttons)
- Optimistic UI feedback (checkmarks, fade effects)
- Toast notifications confirm success without interrupting flow

---

## 10. Future Considerations (Out of MVP Scope)

### 10.1 Features Not Included in MVP

- **Filtering and Search**: Add filters for source type, date range, hive number
  - UI would add filter controls to summaries list header
- **Sorting Options**: Beyond chronological (by hive, by date, by acceptance)
  - UI would add sort dropdown or column headers
- **Pagination**: For users with hundreds of summaries
  - UI would add pagination controls or infinite scroll
- **Batch Delete**: Delete multiple summaries at once
  - UI would add checkboxes and bulk actions toolbar
- **Summary Sharing**: Share summaries with other users
  - UI would add share button and permissions UI
- **Mobile App**: Native iOS/Android apps
  - Responsive design foundation supports PWA conversion
- **Dark Mode**: Theme toggle and dark color scheme
  - Tailwind supports via `dark:` variant, not enabled in MVP

### 10.2 Architectural Extensions

- **Real-Time Updates**: WebSocket notifications for async CSV processing
- **Offline Support**: Service worker for offline data access
- **Advanced Error Handling**: Retry mechanisms, exponential backoff
- **Analytics Dashboard**: Visualizations of summary metrics
- **Export Functionality**: Download summaries as PDF, DOCX
- **Version History**: Track changes to summaries over time

---

## 11. Requirements Mapping

### 11.1 Functional Requirements to UI Elements

| Requirement | UI Element(s) | View/Component |
|-------------|---------------|----------------|
| RF-001: Authentication & RLS | Login/register pages (existing), Session management | Login, Register (Biff default) |
| RF-002: CSV Import | Textarea, submit button, helper text | Main page - CSV Import Section |
| RF-003: Batch Processing | Generation group headers, bulk accept button | Main page - Summaries List |
| RF-004: Input Validation | Character counter, rejected rows feedback | New Summary Form, CSV Import Section |
| RF-005: AI Integration | Source badges, generation metadata | Summary cards |
| RF-006: Summary Format | Summary card structure (header, body, footer) | Summary cards |
| RF-007: Missing Data Handling | Inline editable fields, optional form inputs | Summary cards, New Summary Form |
| RF-008: CRUD Operations | Create (form), Read (list), Update (inline edit), Delete (button) | All views |
| RF-009: Accept Functionality | Accept button, Accepted badge, bulk accept | Summary cards, Generation headers |
| RF-010: Event Tracking | Transparent (server-side), no UI element | N/A |

### 11.2 User Stories to User Journeys

| User Story | User Journey | UI Flow |
|------------|--------------|---------|
| US-001: Registration & Login | (Existing Biff flow) | Login → Main page |
| US-002: CSV Import | Journey 1: CSV Import to Bulk Accept | CSV section → Results → Bulk accept |
| US-003: Validation Feedback | Journey 1 (rejected rows) | CSV import → Rejected rows section |
| US-004: Browse Summaries | Main page initial view | Login → Summaries list |
| US-005: Edit Metadata | Journey 3: Inline Metadata Editing | Click field → Edit → Auto-save |
| US-006: Edit Content | Journey 4: Content Editing | Edit button → Textarea → Save/Cancel |
| US-007: Accept Summaries | Journey 5: Individual Acceptance | Accept button → Accepted badge |
| US-008: Delete Summary | Journey 6: Summary Deletion | Delete button → Confirm → Remove |
| US-009: Manual Creation | Journey 2: Manual Summary Creation | New Summary button → Form → Create |

### 11.3 Success Metrics to UI Support

| Metric | UI Elements Supporting Metric | How UI Helps |
|--------|-------------------------------|--------------|
| M1: Accept Rate (≥75%) | Accept button, Bulk accept, Source badges | Makes acceptance easy and visible, encourages quality review |
| M2: AI Adoption (≥75%) | CSV import prominence, Generation grouping | Positions AI workflow as primary path, makes it efficient and rewarding |

---

## 12. Conclusion

This UI architecture provides a comprehensive foundation for the Apiary Summary MVP, balancing simplicity with functionality. The design prioritizes:

- **Efficiency**: Single-page main view reduces navigation overhead
- **Clarity**: Clear visual indicators (badges, grouping) help users understand their data
- **Accessibility**: Keyboard navigation, screen reader support, semantic HTML throughout
- **Performance**: Server-side rendering, lazy loading, minimal JavaScript
- **Security**: Row-Level Security, CSRF protection, server-side validation
- **Scalability**: Modular components, clean separation of concerns, extensible patterns

The architecture maps directly to the PRD requirements, API endpoints, and user stories, ensuring all MVP features are accounted for. Future enhancements can build on this solid foundation without requiring architectural rewrites.
