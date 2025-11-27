# View Implementation Plan: Application Header, Toast Notifications, and Error Messages

## 1. Overview

This implementation plan covers the global UI components that appear on all authenticated pages of the Apriary Summary application:

1. **Application Header**: A persistent, sticky navigation bar providing access to core application functions (home navigation, new summary creation, logout)
2. **Toast Notification Container**: A non-blocking feedback system for displaying success, error, and informational messages
3. **Error Message Area**: A page-level error display component for critical errors that require user attention

These components are implemented using Biff's server-side rendering with HTMX for dynamic interactions and Tailwind CSS 4 for styling. They provide consistent navigation, feedback, and error handling across all views in the application.

## 2. View Routing

These components are global and appear on all authenticated pages. They are integrated into the main application layout template and do not have their own dedicated route.

**Integration Point**: `src/com/apriary/pages/layout.clj` or equivalent Biff layout namespace

**Visibility Rules**:
- Application Header: Visible on all authenticated pages
- Toast Notification Container: Always present in DOM, toasts appear/disappear dynamically
- Error Message Area: Conditionally rendered when page-level errors exist

## 3. Component Structure

```
Global Layout (base-html)
├── ApplicationHeader (sticky, z-50)
│   ├── AppNameLogo (link component)
│   ├── NewSummaryButton (link component)
│   └── LogoutLink (form component)
├── ToastNotificationContainer (fixed, top-right, z-50)
│   └── Toast[] (0 or more toast messages)
│       ├── ToastIcon (success/error/info indicator)
│       ├── ToastMessage (text content)
│       └── ToastCloseButton (optional, for manual dismiss)
├── ErrorMessageArea (conditional, below header)
│   ├── ErrorIcon (alert indicator)
│   ├── ErrorHeading (error type)
│   ├── ErrorMessage (detailed text)
│   ├── ErrorDetails (optional, expandable)
│   └── ErrorCloseButton (dismiss button)
└── MainContent (varies by page)
```

## 4. Component Details

### 4.1 ApplicationHeader Component

**Component Description**:
The ApplicationHeader is a persistent navigation bar that appears at the top of all authenticated pages. It provides three primary functions: navigation to home, creation of new summaries, and user logout. The header uses a sticky positioning to remain visible during scrolling.

**Main HTML Elements**:
```clojure
[:header.sticky.top-0.z-50.bg-white.shadow-sm
 [:nav.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
  [:div.flex.items-center.justify-between.h-16
   ;; Left: App Name/Logo
   [:div.flex-shrink-0
    [:a.flex.items-center {:href "/" :aria-label "Apriary Summary Home"}
     [:span.text-xl.font-bold.text-gray-900 "Apriary Summary"]]]

   ;; Right: Actions
   [:div.flex.items-center.gap-4
    ;; New Summary Button
    [:a.btn.btn-primary
     {:href "/summaries/new"
      :hx-boost "true"}
     [:span "+ New Summary"]]

    ;; Logout Link
    [:form {:method "POST" :action "/logout"}
     [:button.text-gray-700.hover:text-gray-900
      {:type "submit"
       :aria-label "Logout"}
      "Logout"]]]]]]
```

**Child Components**:
- **AppNameLogo**: Link element with application branding
- **NewSummaryButton**: Primary action button for manual summary creation
- **LogoutLink**: Form submission for ending user session

**Handled Events**:
- **Click on App Name/Logo**: Navigates to home page (`/`) using HTMX boost for SPA-like experience
- **Click on "+ New Summary"**: Navigates to new summary form (`/summaries/new`) using HTMX boost
- **Click on "Logout"**: Submits logout form, triggers Biff session termination, redirects to login page

**Validation Conditions**:
- No client-side validation required (navigation only)
- Server-side: User must be authenticated to view this component
- CSRF token automatically included in logout form by Biff

**Types Required**:
- **Session Data** (from Biff context):
  ```clojure
  {:user-id uuid
   :email string}
  ```

**Props** (from parent layout):
- `ctx` (Biff context map containing session, request, database connection)
- Current route/path (for potential active state highlighting in future)

**Accessibility**:
- Semantic `<header>` and `<nav>` elements
- `aria-label` on logo link for screen readers
- Keyboard navigable (all interactive elements focusable)
- Sufficient color contrast for all text
- Focus visible states using Tailwind's `focus-visible:` variants

**Responsive Behavior**:
- **Mobile** (< 768px): Vertical stacking if needed, smaller text sizes
- **Tablet+** (≥ 768px): Horizontal layout with comfortable spacing
- Consider hamburger menu for very small screens if more items added

---

### 4.2 ToastNotificationContainer Component

**Component Description**:
The ToastNotificationContainer is a fixed-position container that displays non-blocking feedback messages (toasts) in the top-right corner of the viewport. It supports multiple simultaneous toasts with vertical stacking, auto-dismiss for success messages, and manual dismiss for errors.

**Main HTML Elements**:
```clojure
[:div#toast-container.fixed.top-20.right-4.z-50.space-y-2
 {:aria-live "polite"
  :aria-atomic "false"
  :role "status"}
 ;; Toasts will be injected here via HTMX OOB swaps
 ]
```

**Child Components**:
Multiple **Toast** components, each structured as:
```clojure
[:div.toast.max-w-md.rounded.shadow-lg.p-4.flex.items-start.gap-3
 {:id (str "toast-" toast-id)
  :class (case toast-type
           :success "bg-green-100 border border-green-500"
           :error "bg-red-100 border border-red-500"
           :info "bg-blue-100 border border-blue-500")
  :hx-trigger (when auto-dismiss? "load delay:3s")
  :hx-swap "delete"
  :hx-target (str "#toast-" toast-id)}

 ;; Icon
 [:div.flex-shrink-0
  (case toast-type
    :success [:svg.h-6.w-6.text-green-600 {...} ;; CheckCircleIcon
    :error [:svg.h-6.w-6.text-red-600 {...} ;; XCircleIcon
    :info [:svg.h-6.w-6.text-blue-600 {...}]] ;; InformationCircleIcon

 ;; Message
 [:div.flex-1
  [:p.text-sm.font-medium {:class (case toast-type
                                     :success "text-green-800"
                                     :error "text-red-800"
                                     :info "text-blue-800")}
   message]]

 ;; Close button (for manual dismiss or errors)
 (when (or (not auto-dismiss?) (= toast-type :error))
   [:button.flex-shrink-0
    {:type "button"
     :hx-swap "delete"
     :hx-target (str "#toast-" toast-id)
     :aria-label "Close notification"}
    [:svg.h-5.w-5 {...}]])] ;; XMarkIcon
```

**Handled Events**:
- **Auto-dismiss timer**: Success toasts automatically remove themselves after 3-5 seconds via HTMX `hx-trigger="load delay:3s"`
- **Click on close button**: Immediately removes toast via HTMX `hx-swap="delete"`
- **HTMX OOB swap**: Server responses include toast HTML with `hx-swap-oob="afterbegin:#toast-container"`

**Validation Conditions**:
- No validation required (display only)
- Server ensures toast messages are properly escaped to prevent XSS

**Types Required**:
```clojure
;; Toast ViewModel
{:id string/uuid              ;; Unique identifier for DOM targeting
 :type keyword                ;; :success, :error, :info
 :message string              ;; Main message text
 :auto-dismiss boolean        ;; Whether to auto-dismiss
 :duration-ms number}         ;; Duration before auto-dismiss (3000-5000)
```

**Props** (from server-side generation):
- Individual toast properties as defined in ViewModel above

**Accessibility**:
- `aria-live="polite"` on container announces new toasts to screen readers
- `aria-atomic="false"` allows incremental announcements
- `role="status"` indicates informational content
- Close button has `aria-label` for screen reader users
- Color is not sole indicator (icons + text provide redundancy)
- Sufficient contrast ratios for all toast types

**Responsive Behavior**:
- **Mobile**: `right-4` positioning maintains visibility, toasts are `max-w-md` to prevent overflow
- **Tablet+**: Same positioning, comfortable spacing

**Integration Pattern**:
Toasts are added via HTMX out-of-band (OOB) swaps from server responses:
```clojure
;; In route handler response
{:status 200
 :headers {"Content-Type" "text/html"}
 :body (str
         ;; Main response content
         (main-content-html)
         ;; OOB toast swap
         (toast-oob-html {:type :success
                          :message "Summary created successfully"
                          :auto-dismiss true}))}
```

---

### 4.3 ErrorMessageArea Component

**Component Description**:
The ErrorMessageArea is a page-level error display component that appears below the header when critical errors occur. It provides detailed error information with an option to dismiss. Unlike toasts, error messages are more prominent and require user acknowledgment before dismissing.

**Main HTML Elements**:
```clojure
(when error-message
  [:div#error-message-area.mb-6.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
   [:div.bg-red-50.border-2.border-red-500.rounded.p-4
    {:role "alert"
     :aria-live "assertive"}
    [:div.flex.items-start.gap-3
     ;; Error Icon
     [:div.flex-shrink-0
      [:svg.h-6.w-6.text-red-600 {...}]] ;; XCircleIcon

     ;; Error Content
     [:div.flex-1
      [:h3.text-lg.font-semibold.text-red-800 (:heading error-message)]
      [:p.mt-1.text-sm.text-red-700 (:message error-message)]

      ;; Optional: Expandable details
      (when (:details error-message)
        [:details.mt-2
         [:summary.text-sm.text-red-600.cursor-pointer.hover:text-red-800
          "Show technical details"]
         [:pre.mt-2.text-xs.bg-red-100.p-2.rounded.overflow-x-auto
          (pr-str (:details error-message))]])]

     ;; Close Button
     [:button.flex-shrink-0
      {:type "button"
       :hx-swap "delete"
       :hx-target "#error-message-area"
       :aria-label "Dismiss error"}
      [:svg.h-5.w-5.text-red-600 {...}]]]]]) ;; XMarkIcon
```

**Child Components**:
- **ErrorIcon**: Visual indicator of error state
- **ErrorHeading**: Error type or category
- **ErrorMessage**: Detailed error description
- **ErrorDetails**: Optional expandable technical details (using HTML5 `<details>`)
- **CloseButton**: Dismisses the error message

**Handled Events**:
- **Click on close button**: Removes error message from DOM via HTMX `hx-swap="delete"`
- **Click on "Show technical details"**: Expands/collapses details (native `<details>` behavior)

**Validation Conditions**:
- No validation required (display only)
- Conditionally rendered only when error exists in context

**Types Required**:
```clojure
;; ErrorMessage ViewModel (matches API error response format)
{:error string                ;; Human-readable error message
 :code string                 ;; Error code constant (e.g., "VALIDATION_ERROR")
 :details map                 ;; Additional error details (optional)
 :timestamp string            ;; ISO timestamp
 :heading string}             ;; Error category/type (derived from code)
```

**Props** (from parent layout/page):
- `error-message` (ErrorMessage ViewModel or nil)

**Accessibility**:
- `role="alert"` indicates critical message
- `aria-live="assertive"` ensures immediate screen reader announcement
- Semantic heading hierarchy (h3 for error title)
- Close button has descriptive `aria-label`
- High contrast red color scheme
- Native `<details>` element provides accessible expand/collapse

**Responsive Behavior**:
- **Mobile**: Full-width within padding constraints, content stacks vertically if needed
- **Tablet+**: Same layout with comfortable spacing

**Display Logic**:
```clojure
;; In route handler
(defn some-page-handler [ctx]
  (let [error (get-in ctx [:flash :error])] ;; From flash or request context
    {:status 200
     :body (base-html
             ctx
             {:error-message error} ;; Passed to layout
             (page-content ctx))}))
```

---

## 5. Types

### 5.1 Session Data (from Biff)
Provided by Biff authentication middleware, available in request context.

```clojure
{:user-id uuid        ;; Unique user identifier
 :email string}       ;; User's email address
```

**Usage**: ApplicationHeader uses this to verify authentication state.

---

### 5.2 Toast ViewModel
Client-side representation of a toast notification.

```clojure
{:id string           ;; Unique identifier (UUID string) for DOM targeting
                      ;; Generated server-side: (str (random-uuid))

 :type keyword        ;; Toast type, one of:
                      ;; - :success (green, auto-dismiss)
                      ;; - :error (red, manual dismiss)
                      ;; - :info (blue, auto-dismiss)

 :message string      ;; Main message text displayed to user
                      ;; Examples: "Summary created successfully"
                      ;;           "Failed to save. Please try again."

 :auto-dismiss boolean ;; Whether toast auto-dismisses
                       ;; true for :success and :info
                       ;; false for :error

 :duration-ms number}  ;; Milliseconds before auto-dismiss
                       ;; Default: 3000 for success, 5000 for info
                       ;; Ignored if auto-dismiss is false
```

**Field Details**:
- **id**: Generated using `(str (random-uuid))`, used in HTMX `hx-target` attributes
- **type**: Determines styling, icon, and auto-dismiss behavior
- **message**: User-facing text, should be concise (1-2 sentences)
- **auto-dismiss**: Controls HTMX trigger behavior
- **duration-ms**: Used in HTMX `hx-trigger` delay value

**Example**:
```clojure
{:id "toast-a1b2c3d4-e5f6-7890-abcd-ef1234567890"
 :type :success
 :message "14 summaries generated successfully"
 :auto-dismiss true
 :duration-ms 3000}
```

---

### 5.3 ErrorMessage ViewModel
Represents a page-level error message, matching the API error response format.

```clojure
{:error string        ;; Human-readable error message
                      ;; Example: "CSV import limit exceeded. Try again in 45 minutes."

 :code string         ;; Error code constant for programmatic handling
                      ;; Examples: "RATE_LIMIT_EXCEEDED", "VALIDATION_ERROR"
                      ;;           "UNAUTHORIZED", "INTERNAL_ERROR"

 :details map         ;; Additional structured error details (optional)
                      ;; Example: {:field :content, :length 23}
                      ;; Can contain any relevant debugging information

 :timestamp string    ;; ISO 8601 timestamp of when error occurred
                      ;; Example: "2025-11-23T12:00:00Z"

 :heading string}     ;; Error category/heading for display
                      ;; Derived from :code on server-side
                      ;; Examples: "Validation Error", "Rate Limit Exceeded"
```

**Field Details**:
- **error**: Main error message shown to user, should be actionable
- **code**: Machine-readable code for error categorization
- **details**: Optional map with additional context (shown in expandable section)
- **timestamp**: When error occurred, useful for debugging
- **heading**: Display-friendly error category, derived from code

**Error Code to Heading Mapping**:
```clojure
(def error-headings
  {"VALIDATION_ERROR" "Validation Error"
   "UNAUTHORIZED" "Authentication Required"
   "FORBIDDEN" "Access Denied"
   "NOT_FOUND" "Not Found"
   "RATE_LIMIT_EXCEEDED" "Rate Limit Exceeded"
   "INTERNAL_ERROR" "Server Error"
   "EXTERNAL_SERVICE_ERROR" "Service Error"})
```

**Example**:
```clojure
{:error "CSV import limit exceeded. Try again in 45 minutes."
 :code "RATE_LIMIT_EXCEEDED"
 :details {:limit 5
           :period-hours 1
           :reset-at "2025-11-23T13:00:00Z"}
 :timestamp "2025-11-23T12:15:00Z"
 :heading "Rate Limit Exceeded"}
```

---

## 6. State Management

Since this is a Biff application using server-side rendering with HTMX for dynamic behavior, state management differs significantly from client-side SPA frameworks:

### 6.1 Server-Side State

**Session State** (managed by Biff):
- Current authenticated user
- Session ID and expiration
- Flash messages for post-redirect feedback

**Request Context State**:
- Current route/path
- Error messages for current request
- Toast notifications to be displayed

**Implementation**:
```clojure
;; In route handler
(defn handler [ctx]
  (let [user (get-in ctx [:session :user])
        flash-message (get-in ctx [:flash :message])
        flash-error (get-in ctx [:flash :error])]
    {:status 200
     :body (layout/base-html
             ctx
             {:error-message flash-error}
             (page-content))}))
```

### 6.2 Client-Side State (Minimal)

**DOM-Based State**:
- Presence/absence of toast elements in `#toast-container`
- Visibility of error message area
- Expanded/collapsed state of error details (native `<details>` element)

**HTMX-Managed State**:
- Auto-dismiss timers for toasts (via `hx-trigger="load delay:3s"`)
- Navigation history (via `hx-boost`)

### 6.3 State Transitions

**Toast Lifecycle**:
1. **Creation**: Server includes toast HTML in response (OOB swap)
2. **Display**: HTMX inserts toast into `#toast-container`
3. **Auto-dismiss** (if applicable): HTMX trigger fires after delay
4. **Removal**: HTMX deletes toast element from DOM

**Error Message Lifecycle**:
1. **Creation**: Server includes error in context (flash or request-specific)
2. **Display**: Error message area rendered conditionally
3. **Dismissal**: User clicks close, HTMX deletes element
4. **Cleared**: Error not persisted (unless in flash for redirect)

### 6.4 No Custom Hooks Required

This implementation does not require custom React-style hooks. All dynamic behavior is handled through:
- HTMX attributes (`hx-trigger`, `hx-swap`, `hx-target`)
- Server-side flash messages
- Biff session management

---

## 7. API Integration

These global UI components don't directly call APIs but integrate with API responses and Biff authentication:

### 7.1 Authentication Integration

**Logout Action**:
- **Endpoint**: Biff's default logout endpoint (typically `/logout` or `/auth/signout`)
- **Method**: POST
- **Request Type**: Form submission with CSRF token (automatically included by Biff)
- **Response Type**: Redirect to login page
- **Success**: Session cleared, user redirected
- **Error**: Error toast displayed (rare, as logout typically succeeds)

**Implementation**:
```clojure
;; In ApplicationHeader
[:form {:method "POST"
        :action "/logout"}  ;; Biff handles CSRF automatically
 [:button {:type "submit"}
  "Logout"]]
```

### 7.2 Toast Integration with API Responses

**Server-Side Toast Generation Helper**:
```clojure
(ns com.apriary.ui.toast
  (:require [hiccup.core :refer [html]]))

(defn toast-html
  "Generates toast HTML for HTMX OOB swap"
  [{:keys [id type message auto-dismiss duration-ms]}]
  (let [toast-id (or id (str "toast-" (random-uuid)))
        duration (or duration-ms (if (= type :success) 3000 5000))
        auto-dismiss? (if (nil? auto-dismiss)
                        (not= type :error)
                        auto-dismiss)]
    (html
      [:div {:id toast-id
             :class (str "toast max-w-md rounded shadow-lg p-4 flex items-start gap-3 "
                         (case type
                           :success "bg-green-100 border border-green-500"
                           :error "bg-red-100 border border-red-500"
                           :info "bg-blue-100 border border-blue-500"))
             :hx-trigger (when auto-dismiss? (str "load delay:" duration "ms"))
             :hx-swap "delete"
             :hx-target (str "#" toast-id)}
       ;; Icon
       [:div.flex-shrink-0
        (case type
          :success [:svg.h-6.w-6.text-green-600 ;; ... CheckCircleIcon SVG
          :error [:svg.h-6.w-6.text-red-600 ;; ... XCircleIcon SVG
          :info [:svg.h-6.w-6.text-blue-600])] ;; ... InformationCircleIcon SVG

       ;; Message
       [:div.flex-1
        [:p {:class (str "text-sm font-medium "
                         (case type
                           :success "text-green-800"
                           :error "text-red-800"
                           :info "text-blue-800"))}
         message]]

       ;; Close button
       (when (or (not auto-dismiss?) (= type :error))
         [:button.flex-shrink-0
          {:type "button"
           :hx-swap "delete"
           :hx-target (str "#" toast-id)
           :aria-label "Close notification"}
          ;; XMarkIcon SVG
          ])])))

(defn toast-oob
  "Wraps toast HTML with OOB swap directive"
  [toast-data]
  (html
    [:div {:hx-swap-oob "afterbegin:#toast-container"}
     (toast-html toast-data)]))
```

**Usage in Route Handler**:
```clojure
(defn create-summary-handler [ctx]
  (try
    (let [summary (summary-service/create-summary ctx)]
      {:status 201
       :headers {"Content-Type" "text/html"}
       :body (str
               ;; Main response (redirect or updated view)
               (summary-view summary)
               ;; Toast notification (OOB swap)
               (toast/toast-oob
                 {:type :success
                  :message "Summary created successfully"}))})
    (catch Exception e
      {:status 400
       :body (str
               (error-view (error-data e))
               (toast/toast-oob
                 {:type :error
                  :message (.getMessage e)
                  :auto-dismiss false}))})))
```

### 7.3 Error Message Integration

**Server-Side Error Handling**:
```clojure
(defn handle-error
  "Converts exception to error message ViewModel"
  [ex]
  (let [data (ex-data ex)]
    {:error (.getMessage ex)
     :code (or (:code data) "INTERNAL_ERROR")
     :details (dissoc data :status :code)
     :timestamp (str (java.time.Instant/now))
     :heading (get error-headings (:code data) "Error")}))

;; In route handler
(defn some-handler [ctx]
  (try
    (do-something ctx)
    (catch Exception e
      {:status (or (get-in (ex-data e) [:status]) 500)
       :body (layout/base-html
               ctx
               {:error-message (handle-error e)}
               (error-page-content))})))
```

**Flash Messages for Post-Redirect Errors**:
```clojure
;; After failed operation, before redirect
{:status 303
 :headers {"Location" "/summaries"}
 :flash {:error (handle-error ex)}}

;; In target route handler
(defn summaries-list-handler [ctx]
  {:status 200
   :body (layout/base-html
           ctx
           {:error-message (get-in ctx [:flash :error])}
           (summaries-list-view ctx))})
```

---

## 8. User Interactions

### 8.1 Navigation Interactions

**Click on App Logo/Name**:
- **User Action**: User clicks "Apriary Summary" logo in header
- **Expected Behavior**: Navigate to home page (`/`)
- **Implementation**:
  - Standard `<a>` tag with `href="/"`
  - HTMX boost enabled for SPA-like navigation
  - Page updates without full reload
  - Browser history updated
- **Visual Feedback**: Logo briefly changes style on click (active state)

**Click on "+ New Summary" Button**:
- **User Action**: User clicks the "+ New Summary" button
- **Expected Behavior**: Navigate to new summary form (`/summaries/new`)
- **Implementation**:
  - Link with `href="/summaries/new"` and `hx-boost="true"`
  - HTMX loads new page content
  - URL updates in browser
- **Visual Feedback**: Button shows hover state, then loads new page

**Click on "Logout" Link**:
- **User Action**: User clicks "Logout"
- **Expected Behavior**: Session ends, user redirected to login page
- **Implementation**:
  - Form submission to Biff's logout endpoint
  - Session cleared server-side
  - Redirect response to `/login`
- **Visual Feedback**: Brief loading state, then redirect
- **Error Handling**: If logout fails (network error), display error toast

### 8.2 Toast Interactions

**Toast Auto-Dismiss (Success)**:
- **Trigger**: Success toast appears after successful operation
- **Expected Behavior**: Toast automatically disappears after 3-5 seconds
- **Implementation**:
  - HTMX trigger: `hx-trigger="load delay:3000ms"`
  - Target: `hx-target="#toast-{id}"`
  - Swap: `hx-swap="delete"`
- **Visual Feedback**: Fade-out animation (CSS transition) before removal
- **Timing**: 3 seconds for short messages, 5 seconds for longer messages

**Toast Manual Dismiss**:
- **User Action**: User clicks close button (X icon) on toast
- **Expected Behavior**: Toast immediately disappears
- **Implementation**:
  - Close button with `hx-swap="delete"` and `hx-target="#toast-{id}"`
  - HTMX removes toast element from DOM
- **Visual Feedback**: Immediate removal (or brief fade-out)
- **Accessibility**: Button has `aria-label="Close notification"`

**Multiple Toasts Stacking**:
- **Scenario**: Multiple operations complete simultaneously
- **Expected Behavior**: Toasts stack vertically in top-right corner
- **Implementation**:
  - Container uses `space-y-2` for vertical spacing
  - New toasts prepended with `hx-swap-oob="afterbegin:#toast-container"`
  - Oldest toasts at bottom, newest at top
- **Limit**: Consider limiting to max 5 visible toasts (remove oldest if exceeded)

### 8.3 Error Message Interactions

**Error Message Display**:
- **Trigger**: Page loads with error in context/flash
- **Expected Behavior**: Error message appears below header
- **Implementation**:
  - Conditional rendering: `(when error-message ...)`
  - Rendered as part of page load
  - Prominent red styling
- **Accessibility**: `aria-live="assertive"` announces error immediately

**Error Message Dismiss**:
- **User Action**: User clicks close button on error message
- **Expected Behavior**: Error message disappears
- **Implementation**:
  - Close button with `hx-swap="delete"` and `hx-target="#error-message-area"`
  - HTMX removes entire error message area
- **Visual Feedback**: Fade-out animation before removal
- **State**: Error not persisted after dismissal

**Error Details Expansion**:
- **User Action**: User clicks "Show technical details"
- **Expected Behavior**: Technical error details expand/collapse
- **Implementation**:
  - Native HTML5 `<details>` and `<summary>` elements
  - No JavaScript required
  - Details shown in monospace font with code formatting
- **Accessibility**: Native browser support for keyboard and screen readers

### 8.4 Keyboard Navigation

**Tab Order**:
1. App logo/name link
2. "+ New Summary" button
3. "Logout" button
4. (Other page content)
5. Toast close buttons (if visible)
6. Error message close button (if visible)

**Keyboard Shortcuts**:
- **Enter/Space** on links and buttons: Activate
- **Escape** on error details: Collapse (native `<details>` behavior)
- **Tab/Shift+Tab**: Navigate between interactive elements

---

## 9. Conditions and Validation

### 9.1 Authentication State Verification

**ApplicationHeader Visibility**:
- **Condition**: User must be authenticated
- **Verification**: Check for `(:user-id (:session ctx))`
- **Location**: Layout template or middleware
- **Effect**: Header only rendered on authenticated pages
- **Fallback**: If not authenticated, redirect to login (Biff middleware)

**Implementation**:
```clojure
(defn base-html [ctx opts & body]
  (if (get-in ctx [:session :user-id])
    ;; Authenticated: show header
    [:html
     [:body
      (application-header ctx)
      (toast-container)
      (error-message-area (:error-message opts))
      [:main body]]]
    ;; Not authenticated: redirect or show login
    (redirect-to-login)))
```

### 9.2 Toast Display Conditions

**Toast Type Validation**:
- **Condition**: Toast type must be :success, :error, or :info
- **Verification**: Server-side when generating toast
- **Effect**: Determines styling, icon, and auto-dismiss behavior
- **Fallback**: Default to :info if invalid type provided

**Auto-Dismiss Logic**:
- **Condition**: Success and info toasts auto-dismiss, errors don't
- **Verification**: Check toast type when generating HTML
- **Effect**: Adds/omits HTMX auto-dismiss trigger
- **Override**: Can be manually set via `:auto-dismiss` field

**Duration Validation**:
- **Condition**: Duration must be 1000-10000 ms
- **Verification**: Clamp to valid range server-side
- **Effect**: Determines auto-dismiss delay
- **Default**: 3000ms for success, 5000ms for info

### 9.3 Error Message Display Conditions

**Error Message Presence**:
- **Condition**: Error data must exist in context
- **Verification**: `(when error-message ...)` conditional
- **Effect**: Error message area rendered or omitted
- **Source**: Flash message or request-specific error

**Error Code Validation**:
- **Condition**: Error code should match known codes
- **Verification**: Map to heading using `error-headings` map
- **Effect**: Displays appropriate heading
- **Fallback**: "Error" if code not recognized

### 9.4 CSRF Protection

**Logout Form**:
- **Condition**: CSRF token must be valid
- **Verification**: Biff middleware automatic validation
- **Effect**: Logout succeeds or fails
- **Error Handling**: Display error toast if CSRF validation fails

### 9.5 No Client-Side Validation

Since these are display-only components, no client-side validation is required. All validation occurs server-side:
- Authentication state (Biff middleware)
- Error data structure (server-side error handling)
- Toast parameters (server-side toast generation)

---

## 10. Error Handling

### 10.1 Network Errors

**Scenario**: Network failure during navigation or logout
- **Detection**: HTMX request fails
- **User Feedback**: Display error toast with message "Connection lost. Please try again."
- **Recovery**: Allow user to retry action
- **Implementation**: Use HTMX error events
  ```clojure
  [:body {:hx-on--error "alert('Connection lost. Please try again.')"}]
  ```

### 10.2 Session Expiration

**Scenario**: User session expires during interaction
- **Detection**: Server returns 401 Unauthorized
- **User Feedback**: Redirect to login page with flash message "Your session has expired. Please log in again."
- **Recovery**: User logs in again
- **Implementation**: Biff middleware automatic redirect

**Code Pattern**:
```clojure
(defn require-auth [handler]
  (fn [ctx]
    (if (get-in ctx [:session :user-id])
      (handler ctx)
      {:status 303
       :headers {"Location" "/login"}
       :flash {:error {:error "Your session has expired. Please log in again."
                       :code "SESSION_EXPIRED"
                       :heading "Session Expired"}}})))
```

### 10.3 Logout Failure

**Scenario**: Logout request fails (rare)
- **Detection**: Exception during logout handler
- **User Feedback**: Error toast "Failed to log out. Please try again."
- **Recovery**: User can retry logout
- **Implementation**: Catch exceptions in logout handler, return error response

### 10.4 Multiple Toasts Overflow

**Scenario**: Too many toasts appear simultaneously
- **Detection**: More than 5 toasts in container
- **User Feedback**: Automatically dismiss oldest toasts
- **Recovery**: User sees most recent toasts
- **Implementation**: Client-side JavaScript or CSS max-height with overflow

**CSS Solution** (preferred for simplicity):
```css
#toast-container {
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}
```

**JavaScript Solution** (if strict limit needed):
```javascript
htmx.on('htmx:afterSwap', (evt) => {
  const container = document.getElementById('toast-container');
  const toasts = container.querySelectorAll('.toast');
  if (toasts.length > 5) {
    // Remove oldest toasts
    for (let i = 5; i < toasts.length; i++) {
      toasts[i].remove();
    }
  }
});
```

### 10.5 Error Message Rendering Errors

**Scenario**: Error message data is malformed
- **Detection**: Missing required fields in error message map
- **User Feedback**: Generic error message displayed
- **Recovery**: Display basic error without details
- **Implementation**: Use `or` to provide defaults

**Safe Rendering Pattern**:
```clojure
(defn error-message-area [error]
  (when error
    [:div#error-message-area
     [:div.error-content
      [:h3 (or (:heading error) "Error")]
      [:p (or (:error error) "An unexpected error occurred. Please try again.")]
      (when (:details error)
        [:details
         [:summary "Show details"]
         [:pre (pr-str (:details error))]])]]))
```

### 10.6 HTMX Initialization Errors

**Scenario**: HTMX library fails to load
- **Detection**: HTMX attributes don't work
- **User Feedback**: Toasts don't auto-dismiss, navigation falls back to full page loads
- **Recovery**: Basic functionality still works (server-rendered links)
- **Implementation**: Progressive enhancement approach ensures core features work without HTMX

### 10.7 Accessibility Errors

**Scenario**: Screen reader doesn't announce toasts
- **Detection**: User testing or accessibility audit
- **User Feedback**: Add redundant flash message for redirects
- **Recovery**: Ensure critical errors also shown in error message area
- **Implementation**: Dual-notification strategy for critical errors

---

## 11. Implementation Steps

### Step 1: Set Up Project Structure

1. Create namespace structure:
   ```
   src/com/apriary/
   ├── ui/
   │   ├── layout.clj       (main layout component)
   │   ├── header.clj       (application header)
   │   ├── toast.clj        (toast notification utilities)
   │   └── error.clj        (error message component)
   └── middleware.clj       (authentication middleware)
   ```

2. Verify Tailwind CSS 4 and HTMX are properly configured in project
3. Ensure Biff authentication middleware is set up

### Step 2: Implement ApplicationHeader Component

1. Create `src/com/apriary/ui/header.clj`:
   ```clojure
   (ns com.apriary.ui.header
     (:require [hiccup.core :refer [html]]))

   (defn application-header
     "Renders the sticky application header with navigation and actions"
     [ctx]
     (html
       [:header.sticky.top-0.z-50.bg-white.shadow-sm
        [:nav.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
         [:div.flex.items-center.justify-between.h-16
          ;; Logo/App Name
          [:div.flex-shrink-0
           [:a.flex.items-center
            {:href "/"
             :hx-boost "true"
             :aria-label "Apriary Summary Home"}
            [:span.text-xl.font-bold.text-gray-900 "Apriary Summary"]]]

          ;; Actions
          [:div.flex.items-center.gap-4
           ;; New Summary Button
           [:a.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2.focus-visible:outline-blue-600
            {:href "/summaries/new"
             :hx-boost "true"}
            "+ New Summary"]

           ;; Logout Form
           [:form.inline
            {:method "POST"
             :action "/auth/signout"}
            [:button.text-gray-700.hover:text-gray-900.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2.focus-visible:outline-gray-700
             {:type "submit"
              :aria-label "Logout"}
             "Logout"]]]]]]))
   ```

2. Test header rendering with authenticated session
3. Verify responsive behavior on mobile/tablet/desktop
4. Test keyboard navigation and accessibility

### Step 3: Implement Toast Notification System

1. Create `src/com/apriary/ui/toast.clj`:
   ```clojure
   (ns com.apriary.ui.toast
     (:require [hiccup.core :refer [html]]))

   (defn- toast-icon
     "Returns SVG icon based on toast type"
     [type]
     (case type
       :success [:svg.h-6.w-6.text-green-600
                 {:xmlns "http://www.w3.org/2000/svg"
                  :viewBox "0 0 24 24"
                  :fill "currentColor"}
                 [:path {:fill-rule "evenodd"
                         :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm13.36-1.814a.75.75 0 10-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 00-1.06 1.06l2.25 2.25a.75.75 0 001.14-.094l3.75-5.25z"
                         :clip-rule "evenodd"}]]

       :error [:svg.h-6.w-6.text-red-600
               {:xmlns "http://www.w3.org/2000/svg"
                :viewBox "0 0 24 24"
                :fill "currentColor"}
               [:path {:fill-rule "evenodd"
                       :d "M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25zm-1.72 6.97a.75.75 0 10-1.06 1.06L10.94 12l-1.72 1.72a.75.75 0 101.06 1.06L12 13.06l1.72 1.72a.75.75 0 101.06-1.06L13.06 12l1.72-1.72a.75.75 0 10-1.06-1.06L12 10.94l-1.72-1.72z"
                       :clip-rule "evenodd"}]]

       :info [:svg.h-6.w-6.text-blue-600
              {:xmlns "http://www.w3.org/2000/svg"
               :viewBox "0 0 24 24"
               :fill "currentColor"}
              [:path {:fill-rule "evenodd"
                      :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm8.706-1.442c1.146-.573 2.437.463 2.126 1.706l-.709 2.836.042-.02a.75.75 0 01.67 1.34l-.04.022c-1.147.573-2.438-.463-2.127-1.706l.71-2.836-.042.02a.75.75 0 11-.671-1.34l.041-.022zM12 9a.75.75 0 100-1.5.75.75 0 000 1.5z"
                      :clip-rule "evenodd"}]]))

   (defn- close-icon []
     [:svg.h-5.w-5
      {:xmlns "http://www.w3.org/2000/svg"
       :viewBox "0 0 20 20"
       :fill "currentColor"}
      [:path {:d "M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z"}]])

   (defn toast-html
     "Generates toast HTML element"
     [{:keys [id type message auto-dismiss duration-ms] :as toast-data}]
     (let [toast-id (or id (str "toast-" (random-uuid)))
           duration (or duration-ms (if (= type :success) 3000 5000))
           auto-dismiss? (if (nil? auto-dismiss)
                           (not= type :error)
                           auto-dismiss)
           bg-color (case type
                      :success "bg-green-100 border-green-500"
                      :error "bg-red-100 border-red-500"
                      :info "bg-blue-100 border-blue-500")
           text-color (case type
                        :success "text-green-800"
                        :error "text-red-800"
                        :info "text-blue-800")]
       (html
         [:div.max-w-md.rounded.shadow-lg.p-4.flex.items-start.gap-3.border.transition-all.duration-300
          {:id toast-id
           :class bg-color
           :hx-trigger (when auto-dismiss? (str "load delay:" duration "ms"))
           :hx-swap (when auto-dismiss? "delete")
           :hx-target (when auto-dismiss? (str "#" toast-id))}

          ;; Icon
          [:div.flex-shrink-0
           (toast-icon type)]

          ;; Message
          [:div.flex-1
           [:p.text-sm.font-medium {:class text-color}
            message]]

          ;; Close button
          (when (or (not auto-dismiss?) (= type :error))
            [:button.flex-shrink-0.text-gray-400.hover:text-gray-600.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2
             {:type "button"
              :hx-swap "delete"
              :hx-target (str "#" toast-id)
              :aria-label "Close notification"}
             (close-icon)])])))

   (defn toast-oob
     "Wraps toast HTML with OOB swap directive for HTMX"
     [toast-data]
     (html
       [:div {:hx-swap-oob "afterbegin:#toast-container"}
        (toast-html toast-data)]))

   (defn toast-container
     "Renders the toast notification container"
     []
     (html
       [:div#toast-container.fixed.top-20.right-4.z-50.space-y-2.max-h-[calc(100vh-100px)].overflow-y-auto
        {:aria-live "polite"
         :aria-atomic "false"
         :role "status"}]))
   ```

2. Test toast creation with different types
3. Verify auto-dismiss timing
4. Test manual dismiss functionality
5. Test multiple toasts stacking

### Step 4: Implement Error Message Component

1. Create `src/com/apriary/ui/error.clj`:
   ```clojure
   (ns com.apriary.ui.error
     (:require [hiccup.core :refer [html]]
               [clojure.pprint :refer [pprint]]))

   (def error-headings
     {"VALIDATION_ERROR" "Validation Error"
      "UNAUTHORIZED" "Authentication Required"
      "FORBIDDEN" "Access Denied"
      "NOT_FOUND" "Not Found"
      "RATE_LIMIT_EXCEEDED" "Rate Limit Exceeded"
      "INTERNAL_ERROR" "Server Error"
      "EXTERNAL_SERVICE_ERROR" "Service Error"
      "SERVICE_UNAVAILABLE" "Service Unavailable"
      "CONFLICT" "Conflict"
      "INVALID_REQUEST" "Invalid Request"})

   (defn- error-icon []
     [:svg.h-6.w-6.text-red-600
      {:xmlns "http://www.w3.org/2000/svg"
       :viewBox "0 0 24 24"
       :fill "currentColor"}
      [:path {:fill-rule "evenodd"
              :d "M9.401 3.003c1.155-2 4.043-2 5.197 0l7.355 12.748c1.154 2-.29 4.5-2.599 4.5H4.645c-2.309 0-3.752-2.5-2.598-4.5L9.4 3.003zM12 8.25a.75.75 0 01.75.75v3.75a.75.75 0 01-1.5 0V9a.75.75 0 01.75-.75zm0 8.25a.75.75 0 100-1.5.75.75 0 000 1.5z"
              :clip-rule "evenodd"}]])

   (defn- close-icon []
     [:svg.h-5.w-5.text-red-600
      {:xmlns "http://www.w3.org/2000/svg"
       :viewBox "0 0 20 20"
       :fill "currentColor"}
      [:path {:d "M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z"}]])

   (defn error-message-area
     "Renders page-level error message area"
     [error-data]
     (when error-data
       (let [heading (or (:heading error-data)
                         (get error-headings (:code error-data))
                         "Error")
             message (or (:error error-data)
                         "An unexpected error occurred. Please try again.")]
         (html
           [:div#error-message-area.mb-6.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
            [:div.bg-red-50.border-2.border-red-500.rounded.p-4
             {:role "alert"
              :aria-live "assertive"}
             [:div.flex.items-start.gap-3
              ;; Error Icon
              [:div.flex-shrink-0
               (error-icon)]

              ;; Error Content
              [:div.flex-1
               [:h3.text-lg.font-semibold.text-red-800 heading]
               [:p.mt-1.text-sm.text-red-700 message]

               ;; Optional: Expandable details
               (when (:details error-data)
                 [:details.mt-2
                  [:summary.text-sm.text-red-600.cursor-pointer.hover:text-red-800.focus-visible:outline.focus-visible:outline-2
                   "Show technical details"]
                  [:pre.mt-2.text-xs.bg-red-100.p-2.rounded.overflow-x-auto.text-red-900
                   (with-out-str (pprint (:details error-data)))]])]

              ;; Close Button
              [:button.flex-shrink-0.hover:bg-red-100.rounded.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2
               {:type "button"
                :hx-swap "delete"
                :hx-target "#error-message-area"
                :aria-label "Dismiss error"}
               (close-icon)]]]]))))
   ```

2. Test error message rendering with various error types
3. Test expandable details functionality
4. Test dismiss functionality
5. Verify accessibility with screen readers

### Step 5: Create Main Layout Component

1. Create `src/com/apriary/ui/layout.clj`:
   ```clojure
   (ns com.apriary.ui.layout
     (:require [hiccup.page :refer [html5 include-css]]
               [com.apriary.ui.header :refer [application-header]]
               [com.apriary.ui.toast :refer [toast-container]]
               [com.apriary.ui.error :refer [error-message-area]]))

   (defn base-html
     "Base HTML layout for all authenticated pages"
     [ctx {:keys [error-message page-title]} & body]
     (html5
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title (str (or page-title "Apriary Summary") " - Apriary Summary")]

        ;; Tailwind CSS
        (include-css "/css/tailwind.css")

        ;; HTMX
        [:script {:src "https://unpkg.com/htmx.org@1.9.10"
                  :integrity "sha384-D1Kt99CQMDuVetoL1lrYwg5t+9QdHe7NLX/SoJYkXDFfX37iInKRy5xLSi8nO7UC"
                  :crossorigin "anonymous"}]]

       [:body.bg-gray-50.min-h-screen
        ;; Application Header
        (when (get-in ctx [:session :user-id])
          (application-header ctx))

        ;; Toast Notification Container
        (toast-container)

        ;; Error Message Area
        (error-message-area error-message)

        ;; Main Content
        [:main.pb-12
         body]]))
   ```

2. Update existing pages to use new layout
3. Test layout on all pages
4. Verify header only appears on authenticated pages

### Step 6: Integrate with Route Handlers

1. Update route handlers to include toast notifications:
   ```clojure
   (ns com.apriary.routes.summaries
     (:require [com.apriary.ui.toast :as toast]))

   (defn create-summary-handler [ctx]
     (try
       (let [summary (create-summary-logic ctx)]
         {:status 201
          :headers {"Content-Type" "text/html"
                    "HX-Redirect" "/"}
          :body (toast/toast-oob
                  {:type :success
                   :message "Summary created successfully"})})
       (catch Exception e
         {:status 400
          :body (str
                  (error-page ctx e)
                  (toast/toast-oob
                    {:type :error
                     :message (.getMessage e)
                     :auto-dismiss false}))})))
   ```

2. Update error handling middleware:
   ```clojure
   (defn wrap-error-handler [handler]
     (fn [ctx]
       (try
         (handler ctx)
         (catch Exception e
           (let [error-data (error/handle-error e)]
             {:status (or (:status (ex-data e)) 500)
              :body (layout/base-html
                      ctx
                      {:error-message error-data}
                      (error-page-content))})))))
   ```

3. Test integration with various API operations
4. Verify toasts appear after successful operations
5. Verify errors display correctly

### Step 7: Add Flash Message Support

1. Update route handlers to set flash messages for redirects:
   ```clojure
   (defn import-csv-handler [ctx]
     (try
       (let [result (import-csv-logic ctx)]
         {:status 303
          :headers {"Location" "/"}
          :flash {:message {:type :success
                            :text (str (:summaries-created result)
                                      " summaries generated successfully")}}})
       (catch Exception e
         {:status 303
          :headers {"Location" "/"}
          :flash {:error (error/handle-error e)}})))
   ```

2. Update layout to display flash messages:
   ```clojure
   (defn base-html [ctx opts & body]
     (let [flash-message (get-in ctx [:flash :message])
           flash-error (get-in ctx [:flash :error])]
       (html5
         [:head ...]
         [:body
          (application-header ctx)
          (toast-container)

          ;; Flash toast (rendered once on page load)
          (when flash-message
            (toast/toast-html
              {:type (:type flash-message)
               :message (:text flash-message)
               :auto-dismiss true}))

          ;; Flash error
          (error-message-area (or (:error-message opts) flash-error))

          [:main body]])))
   ```

3. Test flash messages after redirects
4. Verify messages appear only once

### Step 8: Implement Accessibility Features

1. Add ARIA attributes:
   - `aria-live="polite"` on toast container
   - `aria-live="assertive"` on error message area
   - `aria-label` on icon-only buttons
   - `role="alert"` on error messages
   - `role="status"` on toast container

2. Ensure keyboard navigation:
   - Test tab order
   - Verify all interactive elements are keyboard accessible
   - Test dismiss functionality with keyboard

3. Test with screen reader:
   - Verify toast announcements
   - Verify error message announcements
   - Test navigation with NVDA/JAWS/VoiceOver

4. Verify color contrast:
   - Use browser devtools contrast checker
   - Ensure all text meets WCAG AA standards

### Step 9: Add Responsive Styles

1. Test header on mobile devices:
   ```clojure
   ;; Update header with responsive classes
   [:header.sticky.top-0.z-50.bg-white.shadow-sm
    [:nav.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
     [:div.flex.flex-col.sm:flex-row.items-start.sm:items-center.justify-between.py-3.sm:py-0.sm:h-16.gap-2.sm:gap-4
      ;; Responsive layout adjustments
      ...]]]
   ```

2. Test toast positioning on mobile:
   - Verify toasts don't obscure content
   - Adjust positioning if needed

3. Test error message on mobile:
   - Ensure readable on small screens
   - Verify technical details are scrollable

4. Test on various screen sizes:
   - Mobile: 375px, 414px
   - Tablet: 768px, 1024px
   - Desktop: 1280px, 1920px

### Step 10: Performance Optimization

1. Minimize toast container overhead:
   - Ensure empty container has minimal DOM impact
   - Test with many toasts (5-10 simultaneous)

2. Optimize HTMX attributes:
   - Use efficient selectors
   - Minimize OOB swaps

3. Test page load performance:
   - Verify layout doesn't cause layout shift
   - Ensure header renders quickly

### Step 11: Write Tests

1. Create unit tests for toast generation:
   ```clojure
   (ns com.apriary.ui.toast-test
     (:require [clojure.test :refer :all]
               [com.apriary.ui.toast :as toast]))

   (deftest toast-html-test
     (testing "Success toast with auto-dismiss"
       (let [html (toast/toast-html
                    {:type :success
                     :message "Operation successful"
                     :auto-dismiss true
                     :duration-ms 3000})]
         (is (clojure.string/includes? html "bg-green-100"))
         (is (clojure.string/includes? html "hx-trigger"))
         (is (clojure.string/includes? html "delay:3000ms"))))

     (testing "Error toast without auto-dismiss"
       (let [html (toast/toast-html
                    {:type :error
                     :message "Operation failed"
                     :auto-dismiss false})]
         (is (clojure.string/includes? html "bg-red-100"))
         (is (not (clojure.string/includes? html "hx-trigger"))))))
   ```

2. Create integration tests:
   - Test full request/response with toasts
   - Test error handling flow
   - Test flash message flow

3. Create accessibility tests:
   - Automated ARIA validation
   - Keyboard navigation tests

### Step 12: Documentation

1. Document toast usage:
   ```clojure
   ;; In toast.clj namespace docstring
   "Toast notification utilities for Apriary Summary application.

    Usage:

    In route handlers, include toast in response using toast-oob:

      (require '[com.apriary.ui.toast :as toast])

      {:status 200
       :body (str
               (main-content)
               (toast/toast-oob
                 {:type :success
                  :message \"Operation completed successfully\"}))}

    For flash messages after redirects:

      {:status 303
       :headers {\"Location\" \"/\"}
       :flash {:message {:type :success
                         :text \"Summary created\"}}}
    "
   ```

2. Document error handling:
   - How to structure error data
   - When to use toasts vs error message area
   - Flash message patterns

3. Create usage examples:
   - Success scenarios
   - Error scenarios
   - Edge cases

---

## Notes

- This implementation uses server-side rendering with HTMX for progressive enhancement
- No client-side state management frameworks (React, Vue) are needed
- Authentication is handled by Biff's built-in middleware
- CSRF protection is automatic via Biff
- All dynamic behavior uses HTMX attributes for declarative interactivity
- Accessibility is a first-class concern throughout the implementation
