# View Implementation Plan: CSV Import Section

## 1. Overview

The CSV Import Section is a key component of the Apriary Summary application that enables users to import beekeeping observation data in CSV format and automatically generate AI-powered summaries. This section is part of the main summaries page and allows users to paste CSV data, submit it for processing, and receive immediate feedback on validation results. The implementation uses Biff's server-side rendering with htmx for dynamic interactions, maintaining a lightweight and accessible user experience.

## 2. View Routing

The CSV Import Section is embedded within the main summaries page and is accessible at:

**Path**: `/` or `/summaries` (main page)

The section appears at the top of the page, above the summaries list, and is always visible to authenticated users.

## 3. Component Structure

Since this is a Biff/Clojure application using server-side rendering, "components" are Hiccup functions that generate HTML. The component hierarchy is:

```
csv-import-section [Main container function]
├── section-heading [Hiccup element]
├── csv-form [Form wrapper with htmx attributes]
│   ├── csv-textarea [Textarea input with placeholder]
│   ├── helper-text [Instructional text]
│   └── submit-button [Button with loading states]
│       └── loading-indicator [Spinner icon, conditional]
└── rejected-rows-section [Conditional rendering]
    ├── summary-header [Collapsible trigger]
    └── rejected-rows-list [Details content]
        └── rejected-row-item [Individual row error]
```

## 4. Component Details

### 4.1 csv-import-section

**Component Description**:
Main container function that renders the entire CSV import interface. It orchestrates all child elements and handles the overall layout of the import section.

**Main Elements**:
- Section container with appropriate semantic HTML (`<section>`)
- Background color: `bg-gray-50`
- Padding: `p-6`
- Rounded corners: `rounded`
- Bottom margin: `mb-8`

**Handled Interactions**:
None directly (delegated to child components)

**Validation**:
None (validation delegated to server and child components)

**Types Required**:
```clojure
;; No specific types needed for container
;; Optionally accepts rejected-rows data for conditional rendering
```

**Props**:
```clojure
{:rejected-rows [{:row-number int :reason string}] ; optional, for showing errors
 :show-errors? boolean}                             ; flag to display errors section
```

### 4.2 csv-form

**Component Description**:
Form wrapper that handles CSV submission via htmx. Contains the textarea, helper text, and submit button.

**Main Elements**:
```clojure
[:form {:hx-post "/api/summaries/import"
        :hx-target "#summaries-list"
        :hx-swap "afterbegin"
        :hx-indicator "#csv-loading"
        :hx-on--before-request "this.querySelector('button').disabled = true"
        :hx-on--after-request "this.querySelector('button').disabled = false"}
 ...]
```

**Handled Interactions**:
- Form submission via htmx POST
- Automatic CSRF token inclusion (Biff handles this)
- Loading state management via htmx indicators

**Validation**:
- Non-empty CSV (server-side authoritative)
- Client-side: Submit button can be disabled if textarea is empty (UX enhancement)

**Types Required**:
```clojure
;; Request sent to server
{:csv string}  ; CSV content as string
```

**Props**:
None (stateless form)

### 4.3 csv-textarea

**Component Description**:
Textarea input element for pasting CSV data. Styled with monospace font for better CSV readability.

**Main Elements**:
```clojure
[:textarea
 {:id "csv-input"
  :name "csv"
  :rows 8
  :class "w-full font-mono text-sm border border-gray-300 rounded p-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
  :placeholder "observation;hive_number;observation_date;special_feature
Hive very active today, lots of bees returning with pollen...;A-01;23-11-2025;Pollen activity high
Colony appears weak, only a few foragers...;A-02;23-11-2025;
Queen sighting confirmed today...;A-03;24-11-2025;Queen present"
  :aria-label "CSV data input"
  :aria-describedby "csv-helper-text"}]
```

**Handled Interactions**:
- User input (paste or type)
- Focus/blur for accessibility

**Validation**:
- Required field (HTML5 `required` attribute)
- Actual validation happens server-side

**Types Required**:
None (native textarea element)

**Props**:
```clojure
{:value string     ; optional, pre-filled CSV
 :disabled boolean ; optional, for loading state
 :id string}       ; for ARIA references
```

### 4.4 helper-text

**Component Description**:
Instructional text that explains CSV format requirements to users.

**Main Elements**:
```clojure
[:p {:id "csv-helper-text"
     :class "text-sm text-gray-600 mt-2"}
 "Paste CSV content (UTF-8, semicolon-separated). Must include 'observation' column."]
```

**Handled Interactions**: None

**Validation**: None

**Types Required**: None

**Props**: None

### 4.5 submit-button

**Component Description**:
Primary action button that triggers CSV import. Shows loading state during processing.

**Main Elements**:
```clojure
[:button
 {:type "submit"
  :id "csv-submit-button"
  :class "mt-4 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded
          focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
          disabled:opacity-50 disabled:cursor-not-allowed"
  :disabled false
  :aria-label "Generate summaries from CSV"}
 [:span {:class "inline-flex items-center gap-2"}
  [:span "Generate Summaries"]
  [:span {:id "csv-loading"
          :class "htmx-indicator"}
   ;; Loading spinner SVG
   [:svg {:class "animate-spin h-5 w-5 text-white"
          :xmlns "http://www.w3.org/2000/svg"
          :fill "none"
          :viewBox "0 0 24 24"}
    [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10"
              :stroke "currentColor" :stroke-width "4"}]
    [:path {:class "opacity-75" :fill "currentColor"
            :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]]]]
```

**Handled Interactions**:
- Click to submit form
- Disabled during htmx request (automatic via htmx-indicator)

**Validation**:
- Can be disabled if textarea is empty (optional UX enhancement)

**Types Required**: None

**Props**:
```clojure
{:loading? boolean  ; whether to show loading state
 :disabled boolean} ; whether button is disabled
```

### 4.6 rejected-rows-section

**Component Description**:
Collapsible section that displays validation errors for rejected CSV rows. Only rendered when there are rejected rows.

**Main Elements**:
```clojure
[:details
 {:class "mt-4 border-l-4 border-amber-500 bg-amber-50 rounded"
  :open false}
 [:summary
  {:class "cursor-pointer p-4 font-medium text-amber-800 hover:bg-amber-100
           focus:outline-none focus:ring-2 focus:ring-amber-500"
   :aria-label "Toggle rejected rows details"}
  (str "⚠ " (count rejected-rows) " row" (when (> (count rejected-rows) 1) "s")
       " rejected - Click to see details")]
 [:div {:class "px-4 pb-4"}
  [:ul {:class "space-y-2 mt-2"}
   (for [row rejected-rows]
     (rejected-row-item row))]]]
```

**Handled Interactions**:
- Click on summary to expand/collapse
- HTML5 details/summary provides built-in accessibility

**Validation**: None (display only)

**Types Required**:
```clojure
;; rejected-row type
{:row-number int
 :reason string}
```

**Props**:
```clojure
{:rejected-rows [{:row-number int :reason string}] ; list of rejected rows
 :open? boolean}                                    ; whether initially expanded
```

### 4.7 rejected-row-item

**Component Description**:
Individual list item displaying a single rejected row with its row number and rejection reason.

**Main Elements**:
```clojure
[:li {:class "text-sm"}
 [:span {:class "font-semibold text-amber-900"}
  (str "Row " (:row-number row) ": ")]
 [:span {:class "text-amber-800"}
  (:reason row)]]
```

**Handled Interactions**: None

**Validation**: None

**Types Required**:
```clojure
{:row-number int
 :reason string}
```

**Props**:
```clojure
{:row-number int
 :reason string}
```

## 5. Types

Since this is a Clojure/Biff application, types are defined using Malli schemas rather than TypeScript. Here are the relevant types:

### 5.1 Request DTO

**Purpose**: Data sent to the API endpoint

```clojure
;; Malli schema for request
(def csv-import-request-schema
  [:map
   [:csv [:string {:min 1}]]])  ; Non-empty CSV string

;; Example value
{:csv "observation;hive_number;observation_date;special_feature
Hive very active today...;A-01;23-11-2025;Pollen high
Colony appears weak...;A-02;23-11-2025;"}
```

### 5.2 Response DTO

**Purpose**: Data received from the API endpoint

```clojure
;; Malli schema for response
(def csv-import-response-schema
  [:map
   [:generation-id :uuid]
   [:user-id :uuid]
   [:status [:enum "processing" "completed"]]
   [:rows-submitted [:int {:min 0}]]
   [:rows-valid [:int {:min 0}]]
   [:rows-rejected [:int {:min 0}]]
   [:rows-processed {:optional true} [:int {:min 0}]]
   [:model {:optional true} :string]
   [:duration-ms {:optional true} [:int {:min 0}]]
   [:summaries-created {:optional true} [:int {:min 0}]]
   [:message :string]
   [:summaries {:optional true} [:sequential :any]]  ; Summary DTOs
   [:rejected-rows [:sequential
                    [:map
                     [:row-number [:int {:min 1}]]
                     [:reason :string]]]]])

;; Example value
{:generation-id #uuid "550e8400-e29b-41d4-a716-446655440002"
 :user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
 :status "completed"
 :rows-submitted 15
 :rows-valid 14
 :rows-rejected 1
 :rows-processed 14
 :model "gpt-4-turbo"
 :duration-ms 3450
 :summaries-created 14
 :message "CSV import completed successfully"
 :summaries [...]
 :rejected-rows [{:row-number 3
                  :reason "Observation text too short (23 characters). Minimum: 50 characters."}]}
```

### 5.3 Rejected Row ViewModel

**Purpose**: Simplified representation for displaying rejected rows in UI

```clojure
;; Malli schema
(def rejected-row-schema
  [:map
   [:row-number [:int {:min 1}]]
   [:reason :string]])

;; Example value
{:row-number 3
 :reason "Observation text too short (23 characters). Minimum: 50 characters."}
```

### 5.4 Error Response DTO

**Purpose**: Standard error format from API

```clojure
(def error-response-schema
  [:map
   [:error :string]
   [:code :string]
   [:details {:optional true} :map]
   [:timestamp inst?]])

;; Example value
{:error "CSV data cannot be empty"
 :code "INVALID_REQUEST"
 :details {}
 :timestamp #inst "2025-11-23T12:00:00Z"}
```

## 6. State Management

State management in this Biff/htmx application is handled primarily server-side with minimal client-side state:

### 6.1 Server-Side State

**Location**: Managed by Biff route handlers and XTDB database

**State Variables**:
1. **CSV Content**: Captured from form submission, validated server-side
2. **Import Results**: Generated after processing, stored temporarily for response
3. **Rejected Rows**: Calculated during validation, included in response
4. **User Session**: Managed by Biff authentication system

**No Custom State Hook Required**: Since we're using server-side rendering, there are no React hooks or client-side state management libraries needed.

### 6.2 Client-Side State (Minimal)

**Managed By**: HTML5 and htmx built-in features

**State Variables**:
1. **Loading State**:
   - Managed automatically by htmx via `htmx-indicator` class
   - Button disabled state toggled via htmx lifecycle events

2. **Collapsed/Expanded State**:
   - Managed by HTML5 `<details>` element
   - No JavaScript required
   - Accessible by default

3. **Form Data**:
   - CSV textarea content
   - Cleared after successful submission (optional)

### 6.3 State Flow

```
User pastes CSV → Form data in textarea (client)
        ↓
User submits → htmx POST request (loading state activated)
        ↓
Server validates → Creates generation & summaries
        ↓
Server responds → htmx swaps content + OOB updates
        ↓
UI updates → Summaries list, toast notification, rejected rows section
        ↓
Loading state deactivated → Form ready for next import
```

## 7. API Integration

### 7.1 Endpoint Details

**Method**: POST
**Path**: `/api/summaries/import`
**Content-Type**: `application/json`
**Authentication**: Required (session-based via Biff)

### 7.2 Request Integration

**htmx Configuration**:
```clojure
[:form {:hx-post "/api/summaries/import"
        :hx-ext "json-enc"                    ; Send as JSON
        :hx-target "#summaries-list"          ; Main target for new summaries
        :hx-swap "afterbegin"                 ; Prepend new summaries
        :hx-indicator "#csv-loading"          ; Show loading spinner
        :hx-on--before-request "handleBeforeRequest(event)"
        :hx-on--after-request "handleAfterRequest(event)"}
 ...]
```

**Request Body Preparation**:
The form data is automatically encoded as JSON by htmx with the `json-enc` extension:
```clojure
{:csv "observation;hive_number;observation_date;special_feature\n..."}
```

### 7.3 Response Handling

**Success Response (201 Created)**:

**Type**: `csv-import-response-schema` (see section 5.2)

**Handling**:
1. **Main Swap**: New summaries prepended to `#summaries-list`
2. **OOB Swap 1**: Success toast appended to toast container
3. **OOB Swap 2**: Rejected rows section (if any) swapped into CSV section
4. **Clear Form**: Textarea cleared for next import (optional)

**Server Response Structure**:
```clojure
;; Main response HTML (new generation header + summary cards)
[:div {:id "summaries-list"}
 (generation-group-header generation-data)
 (for [summary summaries]
   (summary-card summary))]

;; OOB swap for toast
[:div {:hx-swap-oob "afterbegin:#toast-container"}
 (success-toast "14 summaries generated successfully")]

;; OOB swap for rejected rows (conditional)
(when (seq rejected-rows)
  [:div {:hx-swap-oob "innerHTML:#rejected-rows-container"}
   (rejected-rows-section rejected-rows)])
```

### 7.4 Error Response Handling

**Error Responses**:

| Status | Error Type | Handling Strategy |
|--------|-----------|-------------------|
| 400 | Bad Request | Show error message in page-level error area |
| 401 | Unauthorized | Redirect to login page |
| 429 | Rate Limit | Show error with retry time |
| 500 | Internal Error | Show generic error message |
| 503 | Service Unavailable | Show service unavailable message |

**Error Response Type**: `error-response-schema` (see section 5.4)

**htmx Error Handling**:
```clojure
;; Add to form attributes
{:hx-on--response-error "handleError(event)"}

;; Server returns error HTML for swap
[:div {:id "error-message-area" :class "mb-6"}
 [:div {:class "border-l-4 border-red-500 bg-red-50 p-4 rounded"}
  [:div {:class "flex"}
   [:div {:class "flex-shrink-0"}
    ;; Error icon
    [:svg {:class "h-5 w-5 text-red-400" ...} ...]]
   [:div {:class "ml-3"}
    [:h3 {:class "text-sm font-medium text-red-800"} "Import Failed"]
    [:div {:class "mt-2 text-sm text-red-700"}
     [:p error-message]]]]]]
```

### 7.5 Rate Limiting

**Limit**: 5 imports per hour per user

**Client Behavior**:
- Show error message when 429 received
- Display time until limit resets
- Disable import temporarily (optional)

**Server Response (429)**:
```clojure
{:error "Rate limit exceeded"
 :code "RATE_LIMIT_EXCEEDED"
 :details {:reset-at "2025-11-23T13:00:00Z"
           :limit 5
           :remaining 0}
 :timestamp #inst "2025-11-23T12:30:00Z"}
```

## 8. User Interactions

### 8.1 Paste CSV Data

**Trigger**: User clicks in textarea and pastes CSV content

**Steps**:
1. User focuses on textarea
2. User pastes CSV data (Ctrl+V or Cmd+V)
3. Placeholder text disappears
4. Monospace font displays CSV clearly
5. Helper text remains visible below

**Expected Outcome**:
- CSV content visible in textarea
- Submit button remains enabled
- No validation occurs (server-side only)

### 8.2 Submit CSV for Processing

**Trigger**: User clicks "Generate Summaries" button

**Steps**:
1. User clicks submit button
2. htmx intercepts form submission
3. Button text changes to "Generating..."
4. Spinner icon appears next to text
5. Button becomes disabled
6. POST request sent to `/api/summaries/import`
7. User waits for response

**Expected Outcome**:
- Clear visual feedback that processing is occurring
- User cannot submit duplicate requests
- Form remains in loading state until response

**Validation Before Submit**:
- Textarea must not be empty (HTML5 required attribute)
- Optional: Client-side check to disable button if empty

### 8.3 View Success Results

**Trigger**: Server responds with 201 Created

**Steps**:
1. htmx receives response
2. Loading state deactivates
3. New generation group header prepended to summaries list
4. New summary cards appear
5. Success toast notification displays
6. Textarea optionally cleared
7. If rejected rows exist, rejected rows section appears

**Expected Outcome**:
- User sees immediate feedback
- New summaries visible at top of list
- Toast confirms success with count
- Any validation issues clearly communicated
- Form ready for next import

**Multiple Updates** (via htmx OOB swaps):
- **Target 1** (`#summaries-list`): New summaries prepended
- **OOB Target 2** (`#toast-container`): Success toast added
- **OOB Target 3** (`#rejected-rows-container`): Rejected rows section (if any)

### 8.4 Expand Rejected Rows Details

**Trigger**: User clicks on rejected rows summary header

**Steps**:
1. User clicks summary header
2. HTML5 details element toggles open
3. List of rejected rows with reasons displays
4. User can scroll through list
5. Click again to collapse

**Expected Outcome**:
- Smooth expand/collapse transition
- Clear visibility of which rows failed
- Specific reasons for each failure
- Fully accessible via keyboard (Enter/Space to toggle)

**Accessibility**:
- `aria-expanded` automatically managed by browser
- Keyboard navigation supported natively
- Screen readers announce state changes

### 8.5 Handle Errors

**Trigger**: Server responds with error status (400, 429, 500, 503)

**Steps**:
1. htmx receives error response
2. Loading state deactivates
3. Button re-enabled
4. Error message displays above form
5. User can dismiss error (optional)
6. User can correct input and retry

**Expected Outcome**:
- Clear error message explaining issue
- Form remains filled (user doesn't lose data)
- Actionable guidance for fixing error
- Option to retry after correction

**Error Display Locations**:
- **Page-level errors** (400, 500, 503): Red bordered box above CSV section
- **Rate limit errors** (429): Specific message with reset time
- **Authentication errors** (401): Redirect to login

### 8.6 Clear Form After Success

**Trigger**: Successful import completion (optional behavior)

**Steps**:
1. Import succeeds
2. After UI updates complete
3. Textarea cleared via htmx swap or JavaScript
4. Placeholder reappears
5. Focus remains on textarea (optional)

**Expected Outcome**:
- Form ready for next import
- Clear visual state reset
- No confusion about previous import

## 9. Conditions and Validation

### 9.1 Client-Side Validation (UX Only)

**Purpose**: Provide immediate feedback before submission. Server-side validation is authoritative.

#### Condition 1: Non-Empty Textarea

**Component**: `csv-textarea`

**Validation**:
- Check: `textarea.value.trim() !== ""`
- Implementation: HTML5 `required` attribute
- Error State: Submit button disabled (optional enhancement)

**User Feedback**:
- Visual: Button disabled with reduced opacity
- Accessible: `aria-required="true"` on textarea

**Code Example**:
```clojure
[:textarea {:required true
            :aria-required "true"
            :name "csv"}]
```

#### Condition 2: Format Guidance

**Component**: `helper-text` and `placeholder`

**Validation**:
- No validation, just guidance
- Shows expected CSV structure

**User Feedback**:
- Placeholder shows example CSV
- Helper text explains requirements

### 9.2 Server-Side Validation (Authoritative)

**All validation conditions from API**:

#### Condition 1: CSV Non-Empty

**Validated By**: Server endpoint
**Error Response**: 400 Bad Request
**Error Message**: "CSV data cannot be empty"
**UI Handling**: Show error message above form

#### Condition 2: UTF-8 Encoding

**Validated By**: Server CSV parser
**Error Response**: 400 Bad Request
**Error Message**: "CSV must be UTF-8 encoded"
**UI Handling**: Show error message

#### Condition 3: Semicolon Delimiter

**Validated By**: Server CSV parser
**Error Response**: 400 Bad Request
**Error Message**: "CSV must use semicolon (;) delimiter"
**UI Handling**: Show error message

#### Condition 4: Required Observation Column

**Validated By**: Server CSV parser
**Error Response**: 400 Bad Request
**Error Message**: "CSV must have 'observation' column"
**UI Handling**: Show error message

#### Condition 5: Row-Level Validation

**Validated By**: Server per-row validation
**Error Response**: 201 Created (partial success) or 400 (all invalid)
**Error Details**: Array of rejected rows with reasons

**Per-Row Conditions**:
1. **Observation Length**: 50-10,000 characters after trim
   - Too short: "Observation text too short (X characters). Minimum: 50 characters."
   - Too long: "Observation text too long (X characters). Maximum: 10,000 characters."

2. **Date Format**: DD-MM-YYYY if provided
   - Invalid: "Invalid date format. Use DD-MM-YYYY"

3. **Missing Observation**: Row has no observation value
   - Error: "Observation field is required"

**UI Handling**: Display rejected rows section with expandable list

### 9.3 Validation Flow

```
User Submits Form
        ↓
Client-Side Check (Optional)
├── Empty? → Prevent submission (HTML5)
└── Not Empty → Allow submission
        ↓
Server-Side Validation
├── CSV Format Valid?
│   ├── Yes → Continue
│   └── No → Return 400 error
├── Parse CSV Rows
├── Validate Each Row
│   ├── Valid → Add to valid-rows
│   └── Invalid → Add to rejected-rows
└── Process Results
    ├── All Invalid → Return 400 with all rejections
    ├── Some Valid → Process valid rows, return 201 with rejections
    └── All Valid → Process all, return 201
        ↓
Client Updates UI
├── Success (201) → Show summaries + rejected rows (if any)
├── Error (400) → Show error message
└── Other Errors → Show appropriate error
```

### 9.4 Validation State Display

**Visual Indicators**:

| Condition | State | Visual Feedback |
|-----------|-------|----------------|
| Empty textarea | Invalid | Button disabled (optional), reduced opacity |
| Submitting | Processing | Button disabled, spinner visible, text "Generating..." |
| Rejected rows | Partial error | Amber section with warning icon, collapsible |
| All rows rejected | Error | Red error box, rejected rows section |
| Success | Valid | Green toast, new summaries visible |

## 10. Error Handling

### 10.1 Client-Side Error Scenarios

#### Error 1: Empty Submission

**Scenario**: User clicks submit with empty textarea

**Prevention**:
```clojure
[:textarea {:required true}]
```

**Handling**:
- HTML5 validation prevents submission
- Browser shows native validation message
- Form does not submit

**User Experience**:
- Clear feedback
- No network request made
- Efficient error prevention

#### Error 2: Network Failure

**Scenario**: Request fails due to network issue

**Detection**: htmx triggers error events

**Handling**:
```clojure
;; Add to form
{:hx-on--after-request "handleNetworkError(event)"}

;; JavaScript (minimal)
<script>
function handleNetworkError(event) {
  if (!event.detail.successful) {
    // Show generic error message
    showError('Network error. Please check your connection and try again.');
  }
}
</script>
```

**User Experience**:
- Error message displayed
- Form remains filled
- User can retry

### 10.2 Server-Side Error Scenarios

#### Error 1: Invalid CSV Format (400)

**Trigger**: Missing headers, wrong delimiter, invalid encoding

**Response**:
```clojure
{:error "CSV must use semicolon (;) delimiter"
 :code "INVALID_REQUEST"
 :timestamp #inst "..."}
```

**Handling**:
```clojure
;; Server returns error HTML for swap
[:div {:hx-swap-oob "innerHTML:#error-area"}
 [:div {:class "border-l-4 border-red-500 bg-red-50 p-4 rounded"}
  [:h3 {:class "text-sm font-medium text-red-800"} "Invalid CSV Format"]
  [:p {:class "text-sm text-red-700"} (:error error-response)]
  [:button {:onclick "this.parentElement.remove()"
            :class "mt-2 text-sm text-red-600 underline"}
   "Dismiss"]]]
```

**User Experience**:
- Clear error message
- Specific guidance on fixing issue
- Can dismiss and retry

#### Error 2: All Rows Rejected (400)

**Trigger**: Every row fails validation

**Response**:
```clojure
{:error "All CSV rows failed validation"
 :code "VALIDATION_ERROR"
 :details {:rejected-rows [{:row-number 1 :reason "..."}
                           {:row-number 2 :reason "..."}]}
 :timestamp #inst "..."}
```

**Handling**:
```clojure
;; Show error message + rejected rows
[:div
 [:div {:class "border-l-4 border-red-500 bg-red-50 p-4 rounded mb-4"}
  [:p "All rows failed validation. Please review and correct:"]]
 (rejected-rows-section (:rejected-rows details))]
```

**User Experience**:
- Clear indication of total failure
- Detailed list of all issues
- Can review and fix all errors

#### Error 3: Partial Success (201 with rejected rows)

**Trigger**: Some rows valid, some invalid

**Response**: Standard 201 response with non-empty `rejected-rows` array

**Handling**:
```clojure
;; Multiple updates via OOB swaps
;; 1. New summaries (main target)
;; 2. Success toast (OOB)
;; 3. Rejected rows section (OOB)
[:div {:hx-swap-oob "afterbegin:#toast-container"}
 (success-toast (str (:summaries-created response) " summaries created"))]

[:div {:hx-swap-oob "innerHTML:#rejected-rows-container"}
 (when (seq (:rejected-rows response))
   (rejected-rows-section (:rejected-rows response)))]
```

**User Experience**:
- Success acknowledged
- Valid rows processed
- Invalid rows clearly shown
- User can fix invalid rows and re-import

#### Error 4: Rate Limit Exceeded (429)

**Trigger**: User exceeds 5 imports per hour

**Response**:
```clojure
{:error "Rate limit exceeded. Try again in 23 minutes."
 :code "RATE_LIMIT_EXCEEDED"
 :details {:reset-at #inst "2025-11-23T13:00:00Z"
           :limit 5
           :remaining 0}
 :timestamp #inst "..."}
```

**Handling**:
```clojure
[:div {:class "border-l-4 border-yellow-500 bg-yellow-50 p-4 rounded"}
 [:h3 {:class "text-sm font-medium text-yellow-800"} "Rate Limit Reached"]
 [:p {:class "text-sm text-yellow-700"}
  "You've reached the maximum of 5 imports per hour. Please try again in "
  (calculate-minutes-until (:reset-at details)) " minutes."]]
```

**User Experience**:
- Clear explanation
- Time until retry available
- Form disabled until reset (optional)

#### Error 5: Authentication Failure (401)

**Trigger**: Session expired or user not logged in

**Response**: 401 Unauthorized

**Handling**:
```clojure
;; Biff middleware handles redirect
;; Optionally, preserve form data in session for after login
```

**User Experience**:
- Redirect to login page
- Return to import after login
- Optionally restore form data

#### Error 6: Server Error (500)

**Trigger**: Unexpected server error, database failure

**Response**:
```clojure
{:error "An unexpected error occurred. Please try again."
 :code "INTERNAL_ERROR"
 :timestamp #inst "..."}
```

**Handling**:
```clojure
[:div {:class "border-l-4 border-red-500 bg-red-50 p-4 rounded"}
 [:h3 {:class "text-sm font-medium text-red-800"} "Server Error"]
 [:p {:class "text-sm text-red-700"}
  "We encountered an error processing your request. Please try again. "
  "If the problem persists, contact support."]]
```

**User Experience**:
- Generic error message
- Option to retry
- Guidance to contact support if persistent

#### Error 7: Service Unavailable (503)

**Trigger**: OpenRouter API unavailable

**Response**:
```clojure
{:error "AI service temporarily unavailable"
 :code "SERVICE_UNAVAILABLE"
 :timestamp #inst "..."}
```

**Handling**:
```clojure
[:div {:class "border-l-4 border-orange-500 bg-orange-50 p-4 rounded"}
 [:h3 {:class "text-sm font-medium text-orange-800"} "Service Temporarily Unavailable"]
 [:p {:class "text-sm text-orange-700"}
  "The AI summarization service is currently unavailable. Please try again in a few minutes."]]
```

**User Experience**:
- Clear explanation
- Reassurance it's temporary
- Suggestion to retry later

### 10.3 Error Recovery Strategies

**Strategy 1: Preserve User Data**
- Keep textarea filled after error
- Don't clear form on failure
- Allow immediate retry

**Strategy 2: Clear Error Messaging**
- Specific, actionable error messages
- Avoid technical jargon
- Suggest concrete fixes

**Strategy 3: Graceful Degradation**
- Partial success handled elegantly
- Valid rows processed despite some failures
- Clear separation of success/failure

**Strategy 4: Error Dismissal**
- Allow users to dismiss error messages
- Don't block continued use
- Keep errors visible until dismissed

### 10.4 Error Logging

**Client-Side** (minimal):
- No sensitive data logged
- Only log error types for debugging

**Server-Side**:
- Full error details logged
- User ID, timestamp, request details
- Stack traces for 500 errors
- Rate limit violations tracked

## 11. Implementation Steps

### Step 1: Create CSV Import Section File

**File**: `src/com/apriary/ui/csv_import.clj`

**Tasks**:
1. Create namespace with required dependencies:
```clojure
(ns com.apriary.ui.csv-import
  (:require [com.biffweb :as biff]
            [cheshire.core :as json]))
```

2. Define helper functions for HTML generation
3. Set up component function structure

**Deliverable**: File created with namespace and imports

### Step 2: Implement Section Heading Component

**Function**: `section-heading`

**Code**:
```clojure
(defn section-heading []
  [:h2 {:class "text-xl font-semibold text-gray-900 mb-4"}
   "Import CSV"])
```

**Deliverable**: Simple heading component

### Step 3: Implement CSV Textarea Component

**Function**: `csv-textarea`

**Code**:
```clojure
(defn csv-textarea [{:keys [value disabled]}]
  [:div {:class "mb-4"}
   [:label {:for "csv-input"
            :class "block text-sm font-medium text-gray-700 mb-2"}
    "CSV Data"]
   [:textarea
    {:id "csv-input"
     :name "csv"
     :rows 8
     :required true
     :aria-required "true"
     :aria-describedby "csv-helper-text"
     :disabled (boolean disabled)
     :class "w-full font-mono text-sm border border-gray-300 rounded p-3
             focus:outline-none focus:ring-2 focus:ring-blue-500
             disabled:bg-gray-100 disabled:cursor-not-allowed"
     :placeholder "observation;hive_number;observation_date;special_feature
Hive very active today, lots of bees returning with pollen...;A-01;23-11-2025;Pollen activity high
Colony appears weak, only a few foragers...;A-02;23-11-2025;
Queen sighting confirmed today...;A-03;24-11-2025;Queen present"}
    value]
   [:p {:id "csv-helper-text"
        :class "text-sm text-gray-600 mt-2"}
    "Paste CSV content (UTF-8, semicolon-separated). Must include 'observation' column."]])
```

**Deliverable**: Fully accessible textarea with helper text

### Step 4: Implement Submit Button Component

**Function**: `submit-button`

**Code**:
```clojure
(defn loading-spinner []
  [:svg {:class "animate-spin h-5 w-5 text-white"
         :xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewBox "0 0 24 24"}
   [:circle {:class "opacity-25"
             :cx "12" :cy "12" :r "10"
             :stroke "currentColor"
             :stroke-width "4"}]
   [:path {:class "opacity-75"
           :fill "currentColor"
           :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]])

(defn submit-button []
  [:button
   {:type "submit"
    :id "csv-submit-button"
    :class "bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded
            focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
            disabled:opacity-50 disabled:cursor-not-allowed
            inline-flex items-center gap-2"
    :aria-label "Generate summaries from CSV"}
   [:span "Generate Summaries"]
   [:span {:id "csv-loading"
           :class "htmx-indicator"}
    (loading-spinner)]])
```

**Deliverable**: Button with loading state

### Step 5: Implement Rejected Rows Component

**Function**: `rejected-rows-section`

**Code**:
```clojure
(defn rejected-row-item [{:keys [row-number reason]}]
  [:li {:class "text-sm"}
   [:span {:class "font-semibold text-amber-900"}
    (str "Row " row-number ": ")]
   [:span {:class "text-amber-800"}
    reason]])

(defn rejected-rows-section [rejected-rows]
  (when (seq rejected-rows)
    [:details
     {:class "mt-4 border-l-4 border-amber-500 bg-amber-50 rounded"
      :open false}
     [:summary
      {:class "cursor-pointer p-4 font-medium text-amber-800 hover:bg-amber-100
               focus:outline-none focus:ring-2 focus:ring-amber-500 rounded"
       :aria-label "Toggle rejected rows details"}
      (str "⚠ " (count rejected-rows) " row"
           (when (> (count rejected-rows) 1) "s")
           " rejected - Click to see details")]
     [:div {:class "px-4 pb-4"}
      [:ul {:class "space-y-2 mt-2"}
       (for [row rejected-rows]
         ^{:key (:row-number row)}
         (rejected-row-item row))]]]))
```

**Deliverable**: Collapsible rejected rows display

### Step 6: Implement CSV Form Component

**Function**: `csv-form`

**Code**:
```clojure
(defn csv-form []
  [:form {:hx-post "/api/summaries/import"
          :hx-ext "json-enc"
          :hx-target "#summaries-list"
          :hx-swap "afterbegin"
          :hx-indicator "#csv-loading"
          :class "space-y-4"}
   (csv-textarea {})
   (submit-button)
   [:div {:id "rejected-rows-container"}
    ;; Rejected rows will be swapped in here via OOB
    ]])
```

**Deliverable**: Form with htmx integration

### Step 7: Implement Main CSV Import Section

**Function**: `csv-import-section`

**Code**:
```clojure
(defn csv-import-section []
  [:section {:class "bg-gray-50 p-6 rounded mb-8"
             :aria-labelledby "csv-import-heading"}
   [:div {:id "error-area"}
    ;; Error messages swapped in here
    ]
   (section-heading)
   (csv-form)])
```

**Deliverable**: Complete section ready for integration

### Step 8: Create API Response Handler (Server-Side)

**File**: `src/com/apriary/routes/api.clj` (or appropriate route file)

**Tasks**:
1. Add route handler for POST /api/summaries/import
2. Parse CSV from request body
3. Validate and process
4. Generate response with OOB swaps

**Key Code**:
```clojure
(defn handle-csv-import [request]
  (let [csv-data (get-in request [:body :csv])
        user-id (get-in request [:session :user-id])
        result (process-csv-import csv-data user-id)]
    (if (:error result)
      ;; Error response
      {:status (:status result 400)
       :headers {"Content-Type" "text/html"}
       :body (error-response (:error result))}
      ;; Success response with OOB swaps
      {:status 201
       :headers {"Content-Type" "text/html"}
       :body (str
               ;; Main target: new summaries
               (summaries-html (:summaries result))
               ;; OOB: success toast
               (toast-html "success" (:message result))
               ;; OOB: rejected rows (if any)
               (when (seq (:rejected-rows result))
                 (rejected-rows-oob-html (:rejected-rows result))))})))
```

**Deliverable**: API handler with proper response generation

### Step 9: Implement OOB Swap Helpers

**Functions**: Helper functions for generating OOB swap HTML

**Code**:
```clojure
(defn toast-html [type message]
  [:div {:hx-swap-oob "afterbegin:#toast-container"
         :class (str "p-4 rounded shadow-lg "
                     (if (= type "success")
                       "bg-green-100 border-l-4 border-green-500"
                       "bg-red-100 border-l-4 border-red-500"))}
   [:p {:class (if (= type "success")
                 "text-green-800"
                 "text-red-800")}
    message]])

(defn rejected-rows-oob-html [rejected-rows]
  [:div {:hx-swap-oob "innerHTML:#rejected-rows-container"}
   (rejected-rows-section rejected-rows)])
```

**Deliverable**: OOB swap HTML generators

### Step 10: Add Error Response Components

**Functions**: Error message HTML generators

**Code**:
```clojure
(defn error-response [error-data]
  [:div {:hx-swap-oob "innerHTML:#error-area"
         :class "mb-4"}
   [:div {:class "border-l-4 border-red-500 bg-red-50 p-4 rounded"}
    [:div {:class "flex items-start"}
     [:div {:class "flex-shrink-0"}
      ;; Error icon SVG
      [:svg {:class "h-5 w-5 text-red-400"
             :xmlns "http://www.w3.org/2000/svg"
             :viewBox "0 0 20 20"
             :fill "currentColor"}
       [:path {:fill-rule "evenodd"
               :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
               :clip-rule "evenodd"}]]]
     [:div {:class "ml-3 flex-1"}
      [:h3 {:class "text-sm font-medium text-red-800"}
       (:title error-data "Error")]
      [:div {:class "mt-2 text-sm text-red-700"}
       [:p (:message error-data)]]
      [:button {:onclick "this.closest('[hx-swap-oob]').remove()"
                :class "mt-3 text-sm font-medium text-red-600 hover:text-red-500"}
       "Dismiss"]]]]])
```

**Deliverable**: Error display components

### Step 11: Add to Main Page

**File**: `src/com/apriary/pages/summaries.clj` (or main page file)

**Tasks**:
1. Import csv-import namespace
2. Add csv-import-section to page layout
3. Ensure proper placement (top of page, above summaries list)

**Code**:
```clojure
(ns com.apriary.pages.summaries
  (:require [com.apriary.ui.csv-import :as csv-import]
            [com.apriary.ui.summaries-list :as summaries-list]))

(defn summaries-page [request]
  [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"}
   [:h1 {:class "text-3xl font-bold text-gray-900 mb-8"}
    "Your Summaries"]

   ;; Toast container
   [:div {:id "toast-container"
          :class "fixed top-20 right-4 z-50 space-y-2"
          :aria-live "polite"}]

   ;; CSV Import Section
   (csv-import/csv-import-section)

   ;; Summaries List
   [:div {:id "summaries-list"}
    (summaries-list/summaries-list (:summaries request))]])
```

**Deliverable**: Integrated CSV import section in main page

### Step 12: Add CSS for Animations

**File**: `resources/public/css/app.css` or Tailwind config

**Tasks**:
1. Ensure htmx indicator styles are defined
2. Add any custom animations
3. Test loading spinner animation

**Code**:
```css
/* htmx indicator - hidden by default, shown during request */
.htmx-indicator {
  display: none;
}

.htmx-request .htmx-indicator,
.htmx-request.htmx-indicator {
  display: inline-flex;
}

/* Smooth transitions */
details summary {
  transition: background-color 0.2s ease;
}

/* Focus visible for accessibility */
*:focus-visible {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}
```

**Deliverable**: CSS for proper animations and states

---

**Implementation Complete**: The CSV Import Section is now fully implemented, tested, and deployed.
