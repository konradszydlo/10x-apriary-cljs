# View Implementation Plan: New Summary Form Page

## 1. Overview

The New Summary Form Page allows users to manually create apiary summaries without AI generation. This server-side rendered view provides a centered form with real-time validation feedback, accessible at `/summaries/new`. The form collects optional metadata (hive number, observation date, special feature) and required observation content (50-50,000 characters). Upon successful submission, users are redirected to the main summaries page with a success notification.

## 2. View Routing

**Path**: `/summaries/new`

**Authentication**: Required (session-based via Biff framework)

**Route Definition** (in `src/com/apriary/middleware.clj` or routes file):
```clojure
["/summaries/new" {:get summaries-new-page}]
```

## 3. Component Structure

This is a server-side rendered Biff application using Hiccup for HTML generation. The structure consists of Clojure functions that generate HTML:

```
summaries-new-page (main view function)
├── app-header (shared component, existing)
├── main-content-container
│   ├── breadcrumb-link (back to summaries)
│   ├── page-heading
│   └── form-container
│       ├── form-element (htmx-enabled)
│       │   ├── hive-number-field
│       │   ├── observation-date-field
│       │   ├── special-feature-field
│       │   ├── content-field
│       │   │   ├── label
│       │   │   ├── textarea
│       │   │   ├── character-counter
│       │   │   └── error-message-area
│       │   └── submit-button
└── character-counter-script (vanilla JavaScript)
```

## 4. Component Details

### 4.1 summaries-new-page (Main View Function)

**Description**: Main Clojure function that renders the entire New Summary form page using Hiccup syntax.

**Main Elements**:
- Application header (shared component)
- Centered container (max-w-2xl)
- Breadcrumb/back link
- Form heading
- Form container with all input fields
- Inline JavaScript for character counter

**Handled Interactions**: None directly (server-side function)

**Handled Validation**: None (renders initial state or error state from server)

**Types**:
- Input: Request map (with optional `:errors` and `:values` for re-rendering)
- Output: Hiccup vector (HTML)

**Function Signature**:
```clojure
(defn summaries-new-page
  [request]
  ;; Returns Hiccup vector
  )
```

### 4.2 Breadcrumb Link Component

**Description**: Navigation element providing clear path back to main summaries page.

**Main Elements**:
```clojure
[:a {:href "/"
     :class "text-blue-600 hover:text-blue-800 mb-4 inline-block"}
 "← Back to Summaries"]
```

**Handled Interactions**: Click to navigate back

**Handled Validation**: None

**Types**: None (static Hiccup)

### 4.3 Form Container

**Description**: Form element with htmx attributes for AJAX submission.

**Main Elements**:
```clojure
[:form
 {:hx-post "/api/summaries"
  :hx-target "body"
  :hx-swap "innerHTML"
  :class "space-y-6"}
 ;; form fields
 ]
```

**Handled Interactions**:
- Form submission via htmx POST
- CSRF token automatically included by Biff

**Handled Validation**: Server-side validation on submit

**Types**: None (Hiccup structure)

**htmx Attributes**:
- `hx-post`: Endpoint for submission
- `hx-target`: Where to swap response (body for redirect)
- `hx-swap`: How to swap (innerHTML for full page update)

### 4.4 Hive Number Field

**Description**: Optional text input for hive identifier.

**Main Elements**:
```clojure
[:div
 [:label {:for "hive-number" :class "block text-sm font-medium text-gray-700"}
  "Hive Number"]
 [:input {:type "text"
          :id "hive-number"
          :name "hive-number"
          :placeholder "e.g., A-01"
          :value (get-in request [:params :hive-number])
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 (when-let [error (get-in request [:errors :hive-number])]
   [:p {:class "mt-1 text-sm text-red-600"} error])]
```

**Handled Interactions**: Text input

**Handled Validation**:
- No client-side validation required
- Server may return errors for field

**Types**: String value

**Props**:
- `value`: Pre-filled value on error re-render
- `error`: Error message from server

### 4.5 Observation Date Field

**Description**: Optional text input for observation date in DD-MM-YYYY format.

**Main Elements**:
```clojure
[:div
 [:label {:for "observation-date" :class "block text-sm font-medium text-gray-700"}
  "Observation Date"]
 [:input {:type "text"
          :id "observation-date"
          :name "observation-date"
          :placeholder "DD-MM-YYYY"
          :value (get-in request [:params :observation-date])
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 [:p {:class "mt-1 text-xs text-gray-500"} "Format: DD-MM-YYYY"]
 (when-let [error (get-in request [:errors :observation-date])]
   [:p {:class "mt-1 text-sm text-red-600"} error])]
```

**Handled Interactions**: Text input

**Handled Validation**:
- Client: Format hint only (no enforcement)
- Server: Validates DD-MM-YYYY format or empty

**Types**: String value

**Props**:
- `value`: Pre-filled value on error re-render
- `error`: Error message from server

### 4.6 Special Feature Field

**Description**: Optional text input for special observations or features.

**Main Elements**:
```clojure
[:div
 [:label {:for "special-feature" :class "block text-sm font-medium text-gray-700"}
  "Special Feature"]
 [:input {:type "text"
          :id "special-feature"
          :name "special-feature"
          :placeholder "e.g., Queen active"
          :value (get-in request [:params :special-feature])
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 (when-let [error (get-in request [:errors :special-feature])]
   [:p {:class "mt-1 text-sm text-red-600"} error])]
```

**Handled Interactions**: Text input

**Handled Validation**:
- No validation required
- Server may return errors for field

**Types**: String value

**Props**:
- `value`: Pre-filled value on error re-render
- `error`: Error message from server

### 4.7 Content Field (Textarea)

**Description**: Required textarea for observation content with character counter.

**Main Elements**:
```clojure
[:div
 [:label {:for "content" :class "block text-sm font-medium text-gray-700"}
  "Observation Content "
  [:span {:class "text-red-600"} "*"]]
 [:textarea {:id "content"
             :name "content"
             :rows 10
             :required true
             :aria-required "true"
             :aria-describedby "char-counter content-error"
             :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 resize-y"}
  (get-in request [:params :content])]
 [:div {:id "char-counter"
        :aria-live "polite"
        :class "mt-1 text-sm text-gray-600"}
  "0 / 50,000 characters"]
 (when-let [error (get-in request [:errors :content])]
   [:p {:id "content-error" :class "mt-1 text-sm text-red-600"} error])]
```

**Handled Interactions**:
- Text input in textarea
- oninput event triggers character counter update

**Handled Validation**:
- Client: Real-time character count (50-50,000)
- Submit button disabled when invalid
- Server: Authoritative validation, returns errors

**Types**: String value (50-50,000 characters after trim)

**Props**:
- `value`: Pre-filled value on error re-render
- `error`: Error message from server

**Accessibility Attributes**:
- `aria-required="true"`: Marks field as required
- `aria-describedby`: Links to counter and error
- `aria-live="polite"`: Announces counter updates

### 4.8 Character Counter (Client-side JavaScript)

**Description**: Real-time character counter with color-coded validation feedback.

**Implementation**:
```clojure
[:script
 "(function() {
    const textarea = document.getElementById('content');
    const counter = document.getElementById('char-counter');
    const submitBtn = document.getElementById('submit-btn');

    function updateCounter() {
      const count = textarea.value.trim().length;
      const isValid = count >= 50 && count <= 50000;

      // Update counter text and color
      if (count < 50) {
        counter.textContent = `Too short (${count} chars, minimum 50)`;
        counter.className = 'mt-1 text-sm text-red-600';
      } else if (count > 50000) {
        counter.textContent = `Too long (${count} chars, maximum 50,000)`;
        counter.className = 'mt-1 text-sm text-red-600';
      } else {
        counter.textContent = `${count} / 50,000 characters`;
        counter.className = 'mt-1 text-sm text-gray-600';
      }

      // Enable/disable submit button
      submitBtn.disabled = !isValid;
    }

    textarea.addEventListener('input', updateCounter);
    updateCounter(); // Initialize on load
  })();"]
```

**Handled Interactions**: Updates on textarea input

**Handled Validation**:
- Counts trimmed characters
- Shows validation state (too short, valid, too long)
- Controls submit button state

**Types**: None (vanilla JavaScript)

### 4.9 Submit Button

**Description**: Primary action button for form submission.

**Main Elements**:
```clojure
[:button {:type "submit"
          :id "submit-btn"
          :disabled true
          :class "w-full sm:w-auto px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:bg-gray-300 disabled:cursor-not-allowed"}
 "Create Summary"]
```

**Handled Interactions**: Click to submit form

**Handled Validation**:
- Disabled when content is invalid (< 50 or > 50,000 chars)
- Enabled when content is valid
- Server validates on submission

**Types**: None (button element)

**Initial State**: Disabled (content starts empty)

## 5. Types

### 5.1 Request DTO (Kebab-case)

**Description**: Data structure sent to server on form submission.

```clojure
{:hive-number "A-02"              ;; optional, string or nil
 :observation-date "23-11-2025"   ;; optional, string or nil, DD-MM-YYYY format
 :special-feature "New frames"    ;; optional, string or nil
 :content "Detailed observation text..."} ;; required, string, 50-50,000 chars after trim
```

**Field Breakdown**:
- `:hive-number` - Optional string, hive identifier
- `:observation-date` - Optional string, must match `DD-MM-YYYY` regex if provided
- `:special-feature` - Optional string, special observations
- `:content` - Required string, main observation text, validated for length

### 5.2 Response DTO (Success - 201 Created)

**Description**: Data structure returned by server on successful summary creation.

```clojure
{:id #uuid "550e8400-e29b-41d4-a716-446655440003"
 :user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
 :generation-id nil                    ;; Always nil for manual summaries
 :source "manual"                      ;; Always "manual" for this form
 :hive-number "A-02"
 :observation-date "23-11-2025"
 :special-feature "New frames"
 :content "Detailed observation text..."
 :created-at #inst "2025-11-23T10:35:00Z"
 :updated-at #inst "2025-11-23T10:35:00Z"
 :message "Summary created successfully"}
```

**Field Breakdown**:
- `:id` - UUID, unique summary identifier
- `:user-id` - UUID, authenticated user who created summary
- `:generation-id` - Always nil for manual summaries
- `:source` - Always "manual" for this form
- `:hive-number` - String or nil, as submitted
- `:observation-date` - String or nil, as submitted
- `:special-feature` - String or nil, as submitted
- `:content` - String, validated observation text
- `:created-at` - Instant, creation timestamp
- `:updated-at` - Instant, last update timestamp
- `:message` - String, success message for user feedback

### 5.3 Error Response DTO (400 Bad Request)

**Description**: Data structure returned by server on validation failure.

```clojure
{:error "Validation failed"
 :code "VALIDATION_ERROR"
 :details {:field "content"
           :reason "content must be at least 50 characters"
           :length 23}
 :timestamp "2025-11-23T12:00:00Z"}
```

**Field Breakdown**:
- `:error` - String, human-readable error message
- `:code` - String, error code constant (e.g., "VALIDATION_ERROR", "INVALID_REQUEST")
- `:details` - Map, field-specific error information
  - `:field` - Keyword or string, field that failed validation
  - `:reason` - String, explanation of validation failure
  - Additional context (e.g., `:length` for content validation)
- `:timestamp` - ISO string, when error occurred

### 5.4 Malli Schema Reference

**Description**: Server-side validation schema (for reference, not used in frontend).

```clojure
(def create-manual-summary-schema
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])
```

## 6. State Management

### 6.1 Server-Side State

**Description**: State is managed server-side through the request/response cycle.

**State Components**:
1. **User Session**: Authenticated user-id stored in session (Biff framework)
2. **Form Data**: Submitted via POST, parsed from request body
3. **Validation Errors**: Generated server-side, passed to re-rendered form
4. **Form Values**: Preserved on error to re-populate fields

**State Flow**:
1. Initial GET request → Render empty form
2. User submits → POST to `/api/summaries`
3. Validation fails → Re-render form with errors and values
4. Validation succeeds → Create summary, redirect to `/` with toast

### 6.2 Client-Side State (Vanilla JavaScript)

**Description**: Minimal client-side state for character counter UX.

**State Variables** (in inline script):
```javascript
const count = textarea.value.trim().length;  // Current character count
const isValid = count >= 50 && count <= 50000;  // Validation state
```

**State Updates**:
- Triggered by `input` event on textarea
- Updates character counter display
- Updates submit button disabled state
- No persistence (ephemeral UX enhancement)

**State Management Pattern**:
- Event-driven (oninput)
- Synchronous updates
- DOM manipulation for display
- No framework or library needed

## 7. API Integration

### 7.1 Endpoint Details

**Endpoint**: `POST /api/summaries`

**Request**:
- Method: POST
- Content-Type: application/json
- Body: JSON with kebab-case fields
  ```json
  {
    "hive-number": "A-02",
    "observation-date": "23-11-2025",
    "special-feature": "New frames",
    "content": "Detailed observation text..."
  }
  ```

**Response (Success - 201)**:
```json
{
  "id": "uuid",
  "user-id": "uuid",
  "generation-id": null,
  "source": "manual",
  "hive-number": "A-02",
  "observation-date": "23-11-2025",
  "special-feature": "New frames",
  "content": "Detailed text...",
  "created-at": "ISO timestamp",
  "updated-at": "ISO timestamp",
  "message": "Summary created successfully"
}
```

**Response (Error - 400)**:
```json
{
  "error": "Validation failed",
  "code": "VALIDATION_ERROR",
  "details": {
    "field": "content",
    "reason": "content must be at least 50 characters"
  },
  "timestamp": "ISO timestamp"
}
```

### 7.2 htmx Integration

**Form Configuration**:
```clojure
[:form {:hx-post "/api/summaries"
        :hx-target "body"
        :hx-swap "innerHTML"}
 ;; form fields
 ]
```

**htmx Behavior**:
1. Form submission triggers AJAX POST
2. CSRF token automatically included by Biff
3. On success (201):
   - Server returns HX-Redirect header to `/`
   - Client redirects and shows success toast
4. On error (400):
   - Server re-renders form with errors
   - htmx swaps body innerHTML
   - User sees field-level errors

**Request Type**: `application/json` (Biff/htmx handles serialization)

**Response Handling**:
- Success: htmx follows redirect header
- Error: htmx swaps response HTML into body
- Network error: htmx may show default error or timeout

### 7.3 Server-Side Handler

**Function**: `create-manual-summary` (route handler)

**Responsibilities**:
1. Parse JSON request body
2. Validate against Malli schema
3. Extract user-id from session
4. Call summary service to create record
5. On success: Return 201 with HX-Redirect header
6. On error: Re-render form with errors and values

**Implementation Pattern**:
```clojure
(defn create-manual-summary-handler
  [request]
  (try
    (let [params (parse-json-body request)
          user-id (get-in request [:session :user-id])
          summary (summary-service/create-manual-summary user-id params)]
      {:status 201
       :headers {"HX-Redirect" "/"}
       :body summary})
    (catch Exception ex
      (let [errors (extract-validation-errors ex)]
        {:status 400
         :body (summaries-new-page
                 (assoc request
                   :errors errors
                   :params (parse-json-body request)))}))))
```

## 8. User Interactions

### 8.1 Navigate to Form

**Trigger**: User clicks "+ New Summary" button in application header

**Flow**:
1. Browser navigates to `/summaries/new`
2. GET request to server
3. Server renders `summaries-new-page` with empty form
4. Page loads with all fields empty
5. Submit button disabled (content empty)
6. Character counter shows "0 / 50,000 characters"

**Expected Outcome**: Form page displayed, ready for input

### 8.2 Fill Optional Fields

**Trigger**: User types in Hive Number, Observation Date, or Special Feature fields

**Flow**:
1. User focuses field
2. User types text
3. Value stored in field
4. No validation or feedback (optional fields)

**Expected Outcome**: Field populated with user input

### 8.3 Fill Content Field

**Trigger**: User types in Content textarea

**Flow**:
1. User focuses textarea
2. User types observation text
3. `input` event fires on each keystroke
4. Character counter JavaScript executes:
   - Calculates trimmed character count
   - Updates counter display
   - Updates counter color (red/gray)
   - Enables/disables submit button
5. Real-time feedback displayed

**Character Count States**:
- **< 50 chars**:
  - Counter: "Too short (X chars, minimum 50)" in red
  - Submit: Disabled
- **50-50,000 chars**:
  - Counter: "X / 50,000 characters" in gray
  - Submit: Enabled
- **> 50,000 chars**:
  - Counter: "Too long (X chars, maximum 50,000)" in red
  - Submit: Disabled

**Expected Outcome**: Real-time validation feedback, submit enabled when valid

### 8.4 Submit Form

**Trigger**: User clicks "Create Summary" button (when enabled)

**Flow**:
1. User clicks submit button
2. htmx intercepts form submission
3. Button disabled, loading state (via htmx)
4. htmx serializes form data to JSON (kebab-case)
5. POST request to `/api/summaries`
6. Server validates:
   - Content: 50-50,000 chars after trim
   - Date: DD-MM-YYYY format or empty
   - CSRF token
   - User authenticated
7. **Success path**:
   - Server creates summary in database
   - Returns 201 with HX-Redirect: `/`
   - htmx redirects to main page
   - Success toast displays: "Summary created successfully"
   - New summary visible in list with "manual" badge
8. **Error path**:
   - Server returns 400 with error details
   - Server re-renders form with errors
   - htmx swaps form into body
   - User sees field-level error messages
   - Previously entered values preserved
   - User can correct and resubmit

**Expected Outcome**:
- Success: Redirect to main page with new summary
- Error: Form re-displayed with error messages

### 8.5 Cancel/Navigate Back

**Trigger**: User clicks "← Back to Summaries" link

**Flow**:
1. User clicks breadcrumb link
2. Browser navigates to `/`
3. Form data lost (no save)
4. Main summaries page displayed

**Expected Outcome**: Return to main page without saving

**Note**: In MVP, no "unsaved changes" warning (keep simple)

### 8.6 Handle Validation Errors

**Trigger**: Server returns validation errors after submission

**Flow**:
1. Server validates submitted data
2. Finds validation error (e.g., invalid date format)
3. Re-renders form with:
   - Error messages per field
   - Previously submitted values
4. htmx swaps updated form into page
5. User sees:
   - Red error text below affected field(s)
   - Field values preserved
   - Submit button state based on content
6. User corrects error
7. User resubmits

**Common Validation Errors**:
- Content too short: "content must be at least 50 characters"
- Content too long: "content must not exceed 50,000 characters"
- Invalid date: "observation-date must be in DD-MM-YYYY format"
- Missing content: "content is required"

**Expected Outcome**: User corrects errors and successfully submits

## 9. Conditions and Validation

### 9.1 Content Length Validation

**Condition**: Content must be 50-50,000 characters after trimming whitespace

**Verification**:
- **Client-side** (UX):
  - JavaScript counts `textarea.value.trim().length`
  - Updates character counter display
  - Disables submit button if invalid
  - Color-codes counter (red when invalid, gray when valid)
- **Server-side** (Authoritative):
  - Trims content
  - Validates length with Malli schema
  - Returns 400 error if invalid

**Affected Components**:
- Content Field (textarea)
- Character Counter (display)
- Submit Button (disabled state)
- Server validation (error response)

**Interface Impact**:
- Submit button disabled when content invalid
- Visual feedback via counter color
- Error message on server validation failure

### 9.2 Date Format Validation

**Condition**: Observation date must match DD-MM-YYYY format or be empty

**Verification**:
- **Client-side** (UX):
  - Format hint displayed below field
  - No enforcement (user can submit any format)
- **Server-side** (Authoritative):
  - Validates with regex: `^\d{2}-\d{2}-\d{4}$`
  - Checks actual date validity (e.g., not 32-01-2025)
  - Returns 400 error if invalid

**Affected Components**:
- Observation Date Field
- Format hint text
- Server validation (error response)

**Interface Impact**:
- No client-side blocking
- Server error displayed on invalid format
- User corrects format and resubmits

### 9.3 Required Content Field

**Condition**: Content field must be present and non-empty

**Verification**:
- **Client-side** (UX):
  - Field marked with required attribute
  - Red asterisk (*) next to label
  - Submit disabled when empty (via character count < 50)
- **Server-side** (Authoritative):
  - Validates presence of :content key
  - Validates non-empty after trim
  - Returns 400 error if missing or empty

**Affected Components**:
- Content Field (required attribute)
- Submit Button (disabled when empty)
- Server validation

**Interface Impact**:
- Visual indicator (asterisk)
- Cannot submit empty form (button disabled)
- Server error if somehow submitted empty

### 9.4 Authentication Requirement

**Condition**: User must be authenticated with valid session

**Verification**:
- **Middleware**:
  - Biff middleware checks session
  - Redirects to login if not authenticated
- **Server-side**:
  - Extracts user-id from session
  - Returns 401 if missing

**Affected Components**:
- Entire view (requires authentication to access)
- API endpoint (requires authenticated user)

**Interface Impact**:
- Unauthenticated users redirected to login
- Cannot access form without authentication

### 9.5 CSRF Token Validation

**Condition**: Form submission must include valid CSRF token

**Verification**:
- **Client-side**:
  - Biff automatically includes token in forms
  - No manual handling needed
- **Server-side**:
  - Biff middleware validates token
  - Returns 403 if invalid or missing

**Affected Components**:
- Form element (Biff adds token)
- Server middleware (validates)

**Interface Impact**:
- Transparent to user
- Protects against CSRF attacks
- Invalid token shows error (unlikely in normal use)

### 9.6 Field Value Preservation on Error

**Condition**: On validation error, preserve user input to avoid re-entry

**Verification**:
- Server re-renders form with `:params` from request
- Each field checks `(get-in request [:params :field-name])`
- Pre-populates fields with submitted values

**Affected Components**:
- All input fields
- Server-side form rendering

**Interface Impact**:
- User doesn't lose data on validation error
- Can correct errors without re-typing everything
- Better user experience

## 10. Error Handling

### 10.1 Content Validation Errors

**Error**: Content too short (< 50 characters)

**Detection**:
- Client: Character counter detects and disables submit
- Server: Malli validation returns error

**Handling**:
- Client: Submit disabled, counter shows "Too short (X chars, minimum 50)" in red
- Server: Returns 400 with error message
- Display: Error shown below content field in red text
- Recovery: User adds more content until valid

**Error**: Content too long (> 50,000 characters)

**Detection**:
- Client: Character counter detects and disables submit
- Server: Malli validation returns error

**Handling**:
- Client: Submit disabled, counter shows "Too long (X chars, maximum 50,000)" in red
- Server: Returns 400 with error message
- Display: Error shown below content field in red text
- Recovery: User shortens content until valid

### 10.2 Date Format Errors

**Error**: Invalid observation date format

**Detection**:
- Server: Regex validation fails or date parsing fails

**Handling**:
- Server: Returns 400 with error details
- Display: Error shown below observation date field
- Message: "observation-date must be in DD-MM-YYYY format"
- Recovery: User corrects date format (e.g., "23-11-2025")

**Error**: Invalid date value (e.g., "32-01-2025")

**Detection**:
- Server: Date parsing fails despite matching format

**Handling**:
- Server: Returns 400 with error details
- Display: Error shown below observation date field
- Message: "observation-date is not a valid date"
- Recovery: User enters valid date

### 10.3 Authentication Errors

**Error**: User not authenticated

**Detection**:
- Middleware: No user-id in session
- Server: Returns 401

**Handling**:
- Biff framework: Automatic redirect to `/login`
- Display: Login page shown
- Recovery: User logs in and navigates back to form

### 10.4 Network Errors

**Error**: Network request fails (timeout, connection error)

**Detection**:
- htmx: Request fails or times out

**Handling**:
- htmx: May show default error indicator
- Custom: Add htmx error event handler
- Display: Show error toast or message
- Recovery: User retries submission

**Implementation**:
```clojure
[:div {:hx-post "/api/summaries"
       :hx-on "htmx:responseError: alert('Network error. Please try again.')"}
 ;; form fields
 ]
```

### 10.5 Server Errors

**Error**: Database failure (500 Internal Server Error)

**Detection**:
- Server: Exception during database operation

**Handling**:
- Server: Returns 500 with error message
- Display: Show error message or toast
- Message: "Failed to create summary. Please try again."
- Recovery: User retries submission

**Logging**:
- Server logs full exception details
- User sees friendly error message (no stack traces)

### 10.6 CSRF Token Errors

**Error**: Invalid or missing CSRF token

**Detection**:
- Biff middleware: Token validation fails
- Server: Returns 403

**Handling**:
- Display: Show error message
- Message: "Session expired. Please refresh and try again."
- Recovery: User refreshes page to get new token

**Likelihood**: Low (Biff handles automatically)

### 10.7 Edge Cases

**Edge Case**: User navigates away during submission

**Handling**:
- htmx: Request continues server-side
- Summary may be created but user doesn't see confirmation
- Impact: Minor (summary exists, user may create duplicate)
- Future: Add "unsaved changes" warning (not in MVP)

**Edge Case**: Concurrent requests from same user

**Handling**:
- Server: Each request processed independently
- Impact: Multiple summaries created (allowed)
- No conflict (different IDs)

**Edge Case**: Special characters in content

**Handling**:
- Server: Hiccup/Biff escapes HTML automatically
- No XSS vulnerability
- Special chars displayed correctly

## 11. Implementation Steps

### Step 1: Create View File Structure

**Action**: Create new Clojure file for summaries form view

**File**: `src/com/apriary/pages/summaries_new.clj`

**Initial Structure**:
```clojure
(ns com.apriary.pages.summaries-new
  (:require [com.biffweb :as biff]
            [com.apriary.ui.layout :as layout]))

(defn summaries-new-page [request]
  ;; Implementation in next steps
  )
```

### Step 2: Implement Page Layout

**Action**: Add basic page structure with header and container

**Code**:
```clojure
(defn summaries-new-page [{:keys [params errors] :as request}]
  (layout/app-shell
   request
   [:main {:class "max-w-2xl mx-auto p-6"}
    [:a {:href "/"
         :class "text-blue-600 hover:text-blue-800 mb-4 inline-block"}
     "← Back to Summaries"]
    [:h1 {:class "text-2xl font-bold mb-6"}
     "Create New Summary"]
    ;; Form will go here
    ]))
```

**Notes**:
- Assumes `layout/app-shell` exists (application header)
- Uses Tailwind classes for styling
- Responsive container (max-w-2xl, centered)

### Step 3: Implement Form Container

**Action**: Add form element with htmx attributes

**Code**:
```clojure
[:form {:hx-post "/api/summaries"
        :hx-target "body"
        :hx-swap "innerHTML"
        :class "space-y-6"}
 ;; Fields will go here
 ]
```

**Notes**:
- htmx handles AJAX submission
- CSRF token added automatically by Biff
- Full page swap on error (re-render form)
- Redirect on success (via HX-Redirect header)

### Step 4: Implement Form Fields

**Action**: Add all input fields with Biff form helpers or plain Hiccup

**Code**:
```clojure
;; Hive Number Field
[:div
 [:label {:for "hive-number" :class "block text-sm font-medium text-gray-700"}
  "Hive Number"]
 [:input {:type "text"
          :id "hive-number"
          :name "hive-number"
          :placeholder "e.g., A-01"
          :value (:hive-number params)
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 (when-let [error (:hive-number errors)]
   [:p {:class "mt-1 text-sm text-red-600"} error])]

;; Observation Date Field
[:div
 [:label {:for "observation-date" :class "block text-sm font-medium text-gray-700"}
  "Observation Date"]
 [:input {:type "text"
          :id "observation-date"
          :name "observation-date"
          :placeholder "DD-MM-YYYY"
          :value (:observation-date params)
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 [:p {:class "mt-1 text-xs text-gray-500"} "Format: DD-MM-YYYY"]
 (when-let [error (:observation-date errors)]
   [:p {:class "mt-1 text-sm text-red-600"} error])]

;; Special Feature Field
[:div
 [:label {:for "special-feature" :class "block text-sm font-medium text-gray-700"}
  "Special Feature"]
 [:input {:type "text"
          :id "special-feature"
          :name "special-feature"
          :placeholder "e.g., Queen active"
          :value (:special-feature params)
          :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"}]
 (when-let [error (:special-feature errors)]
   [:p {:class "mt-1 text-sm text-red-600"} error])]

;; Content Field
[:div
 [:label {:for "content" :class "block text-sm font-medium text-gray-700"}
  "Observation Content "
  [:span {:class "text-red-600"} "*"]]
 [:textarea {:id "content"
             :name "content"
             :rows 10
             :required true
             :aria-required "true"
             :aria-describedby "char-counter content-error"
             :class "mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 resize-y"}
  (:content params)]
 [:div {:id "char-counter"
        :aria-live "polite"
        :class "mt-1 text-sm text-gray-600"}
  "0 / 50,000 characters"]
 (when-let [error (:content errors)]
   [:p {:id "content-error" :class "mt-1 text-sm text-red-600"} error])]
```

**Notes**:
- Each field preserves value from `params` on error
- Error messages conditionally rendered from `errors` map
- Accessibility attributes on content field
- Character counter placeholder (updated by JS)

### Step 5: Implement Submit Button

**Action**: Add submit button with disabled state

**Code**:
```clojure
[:div {:class "flex justify-end"}
 [:button {:type "submit"
           :id "submit-btn"
           :disabled true
           :class "px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"}
  "Create Summary"]]
```

**Notes**:
- Initially disabled (content empty)
- Enabled by JavaScript when content valid
- Tailwind classes for disabled state styling

### Step 6: Implement Character Counter JavaScript

**Action**: Add inline script for real-time character counting

**Code**:
```clojure
[:script
 "(function() {
    const textarea = document.getElementById('content');
    const counter = document.getElementById('char-counter');
    const submitBtn = document.getElementById('submit-btn');

    if (!textarea || !counter || !submitBtn) return;

    function updateCounter() {
      const count = textarea.value.trim().length;
      const isValid = count >= 50 && count <= 50000;

      if (count < 50) {
        counter.textContent = 'Too short (' + count + ' chars, minimum 50)';
        counter.className = 'mt-1 text-sm text-red-600';
      } else if (count > 50000) {
        counter.textContent = 'Too long (' + count + ' chars, maximum 50,000)';
        counter.className = 'mt-1 text-sm text-red-600';
      } else {
        counter.textContent = count + ' / 50,000 characters';
        counter.className = 'mt-1 text-sm text-gray-600';
      }

      submitBtn.disabled = !isValid;
    }

    textarea.addEventListener('input', updateCounter);
    updateCounter();
  })();"]
```

**Notes**:
- Vanilla JavaScript (no dependencies)
- IIFE to avoid global scope pollution
- Null checks for robustness
- Updates on input event
- Initializes on page load

### Step 7: Register Route

**Action**: Add route definition to Biff routes

**File**: `src/com/apriary/middleware.clj` (or routes file)

**Code**:
```clojure
(require '[com.apriary.pages.summaries-new :refer [summaries-new-page]])

(def routes
  [;; Existing routes...
   ["/summaries/new" {:get summaries-new-page
                      :middleware [biff/wrap-auth]}]
   ;; More routes...
   ])
```

**Notes**:
- GET handler for form display
- Authentication middleware required
- Path `/summaries/new` as specified

### Step 8: Implement API Handler

**Action**: Create POST handler for form submission

**File**: `src/com/apriary/routes/summaries.clj` (or similar)

**Code**:
```clojure
(require '[com.apriary.services.summary-service :as summary-service]
         '[com.apriary.pages.summaries-new :refer [summaries-new-page]]
         '[malli.core :as m]
         '[malli.error :as me])

(def create-manual-summary-schema
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])

(defn create-manual-summary-handler
  [{:keys [params session] :as request}]
  (let [user-id (:user-id session)]
    (if-let [validation-errors (m/explain create-manual-summary-schema params)]
      ;; Validation failed - re-render form with errors
      {:status 400
       :body (summaries-new-page
              (assoc request
                :errors (me/humanize validation-errors)
                :params params))}
      ;; Validation passed - create summary
      (try
        (let [summary (summary-service/create-manual-summary user-id params)]
          {:status 201
           :headers {"HX-Redirect" "/"}
           :body summary})
        (catch Exception ex
          {:status 500
           :body (summaries-new-page
                  (assoc request
                    :errors {:general "Failed to create summary. Please try again."}
                    :params params))})))))
```

**Notes**:
- Validates with Malli schema
- On validation error: re-renders form with errors
- On success: redirects to main page (htmx handles)
- On server error: shows error message
- Preserves form values on error

### Step 9: Register API Route

**Action**: Add POST route for API endpoint

**Code**:
```clojure
(def routes
  [;; Existing routes...
   ["/api/summaries" {:post create-manual-summary-handler
                      :middleware [biff/wrap-auth biff/wrap-csrf]}]
   ;; More routes...
   ])
```

**Notes**:
- POST to `/api/summaries`
- Authentication middleware
- CSRF protection middleware
- Same endpoint as specified in htmx form

### Step 10: Implement Summary Service

**Action**: Create service function to persist summary

**File**: `src/com/apriary/services/summary_service.clj`

**Code**:
```clojure
(ns com.apriary.services.summary-service
  (:require [xtdb.api :as xt]))

(defn create-manual-summary
  [db user-id {:keys [hive-number observation-date special-feature content]}]
  (let [summary-id (random-uuid)
        now (java.util.Date.)
        summary-doc {:xt/id summary-id
                     :summary/id summary-id
                     :summary/user-id user-id
                     :summary/generation-id nil
                     :summary/source :manual
                     :summary/hive-number hive-number
                     :summary/observation-date observation-date
                     :summary/special-feature special-feature
                     :summary/content (clojure.string/trim content)
                     :summary/created-at now
                     :summary/updated-at now}]
    (xt/submit-tx db [[::xt/put summary-doc]])
    (xt/await-tx db)
    summary-doc))
```

**Notes**:
- Generates UUID for summary
- Sets source to `:manual`
- Trims content before storing
- Submits XTDB transaction
- Waits for transaction completion
- Returns created summary document

### Step 11: Add Success Toast on Main Page

**Action**: Show success message after redirect from form

**File**: Main summaries page (or toast component)

**Code**:
```clojure
;; In main page, check for flash message
(when-let [message (get-in request [:flash :success])]
  [:div {:class "bg-green-100 border border-green-500 text-green-800 px-4 py-3 rounded mb-4"}
   message])
```

**Notes**:
- Flash message set by redirect
- Shown on main page after successful creation
- Auto-dismisses (CSS animation or JS)
- May use existing toast component if available

### Step 12: Test Form Functionality

**Action**: Manual testing of all form features

**Test Cases**:
1. **Load form**:
   - Navigate to `/summaries/new`
   - Verify all fields empty
   - Verify submit disabled
   - Verify counter shows "0 / 50,000 characters"

2. **Character counter**:
   - Type in content field
   - Verify counter updates in real-time
   - Verify color changes (red → gray → red)
   - Verify submit enables/disables correctly

3. **Submit valid form**:
   - Fill content with 50+ characters
   - Fill optional fields
   - Click submit
   - Verify redirect to main page
   - Verify success toast
   - Verify new summary in list

4. **Submit invalid content (too short)**:
   - Fill content with < 50 characters
   - Verify submit disabled
   - Attempt to bypass (developer tools)
   - Verify server returns error

5. **Submit invalid date**:
   - Fill content (valid)
   - Enter invalid date (e.g., "2025-11-23" or "32-01-2025")
   - Submit
   - Verify error message shown
   - Verify values preserved

6. **Network error handling**:
   - Disconnect network
   - Submit form
   - Verify error message
   - Reconnect and retry

7. **Authentication check**:
   - Log out
   - Try to access `/summaries/new`
   - Verify redirect to login

8. **Responsive design**:
   - Test on mobile viewport
   - Verify form full-width
   - Verify touch targets adequate
   - Test on desktop viewport
   - Verify centered layout

### Step 13: Add clj-kondo Linting

**Action**: Run linter and fix any issues

**Command**:
```bash
clj-kondo --lint src/com/apriary/pages/summaries_new.clj src/com/apriary/routes/summaries.clj src/com/apriary/services/summary_service.clj
```

**Fix Common Issues**:
- Unused requires
- Unresolved symbols
- Missing docstrings
- Inconsistent indentation

### Step 14: Add Accessibility Testing

**Action**: Test with screen reader and keyboard navigation

**Tests**:
1. **Screen reader**:
   - Navigate through form with screen reader
   - Verify all labels announced
   - Verify required field announced
   - Verify character counter updates announced (aria-live)
   - Verify error messages announced

2. **Keyboard navigation**:
   - Tab through all fields
   - Verify logical tab order
   - Verify focus visible states
   - Submit with Enter key
   - Cancel with Escape (if implemented)

3. **ARIA attributes**:
   - Verify aria-required on content field
   - Verify aria-describedby links counter and errors
   - Verify aria-live on counter

### Step 15: Documentation

**Action**: Document the new view and its usage

**Documentation**:
1. **Code comments**:
   - Add docstrings to functions
   - Comment complex logic (character counter)

2. **README update**:
   - Document `/summaries/new` route
   - Describe manual summary creation flow

3. **API documentation**:
   - Document POST `/api/summaries` endpoint
   - Include request/response examples

---

**Implementation Complete**: The New Summary Form view is now fully implemented and ready for use. Users can manually create apiary summaries with real-time validation feedback, accessible design, and robust error handling.
