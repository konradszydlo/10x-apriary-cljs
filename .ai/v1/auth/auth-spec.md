# Technical Specification: Authentication System
## Apriary Summary - User Registration, Login, and Password Recovery

**Document Version:** 1.0
**Date:** 2025-12-01
**Requirement:** US-010 - Secure Access and Authentication

---

## Executive Summary

This specification describes the architecture for implementing a complete password-based authentication system for Apriary Summary. The system builds upon existing authentication infrastructure (signup/signin functionality) and extends it with password recovery capabilities, enhanced validation, and improved user experience.

### Current State

The application currently has:
- Basic signup/signin functionality (`src/com/apriary/auth.clj`)
- Session-based authentication using `:uid` in session
- Route protection middleware (`wrap-signed-in`, `wrap-redirect-signed-in`)
- User schema with email and password-hash fields
- Login/signup pages (`src/com/apriary/pages/home.clj`)

### Scope

This specification covers:
1. **Enhanced Registration**: Password confirmation field and improved validation
2. **Password Recovery**: Complete forgot password / reset password flow
3. **Navigation Improvements**: Sign-in/sign-up buttons for unauthenticated users
4. **Email Infrastructure**: Password reset email delivery (**MVP: Console logging only, no actual email sending**)
5. **Security Enhancements**: Token-based password reset with expiration

### MVP Simplification

For the MVP release, we will **NOT** send actual emails. Instead, password reset emails will be printed to the console. This approach:
- Eliminates the need for email service integration (Postmark, SMTP, etc.)
- Removes dependency on email API keys and configuration
- Allows full testing of the password reset flow without external dependencies
- Enables developers to copy reset links directly from console output

Real email sending will be implemented in a future release.

---

## 1. USER INTERFACE ARCHITECTURE

### 1.1. Page Structure Overview

The authentication system consists of the following pages:

| Page | Route | Purpose | Access |
|------|-------|---------|--------|
| Sign Up | `/` | New user registration | Unauthenticated only |
| Sign In | `/signin` | Existing user login | Unauthenticated only |
| Forgot Password | `/forgot-password` | Request password reset | Unauthenticated only |
| Reset Password | `/reset-password` | Set new password | Token-based access |
| Password Reset Sent | `/password-reset-sent` | Confirmation page | Unauthenticated only |
| Password Reset Success | `/password-reset-success` | Success confirmation | Unauthenticated only |
| Summaries List | `/summaries` | Main app page | Authenticated only |

### 1.2. Updated Sign Up Page

**Location:** `src/com/apriary/pages/home.clj` - `home-page` function

**Changes Required:**
- Add password confirmation field
- Add client-side password matching validation (using htmx or hyperscript)
- Update error handling to show password mismatch errors

**Form Fields:**
```clojure
[:div
 [:label "Email"]
 [:input {:name "email" :type "email" :required true}]]

[:div
 [:label "Password"]
 [:input {:name "password" :type "password" :required true}]
 [:p.text-xs "Must be at least 8 characters"]]

[:div
 [:label "Confirm Password"]
 [:input {:name "password-confirm" :type "password" :required true}]]

[:button "Sign up"]
```

**Validation Cases:**
1. Email format validation (already exists)
2. Password minimum length (already exists)
3. **NEW:** Password confirmation match
4. **NEW:** Email uniqueness check (already exists in backend)

**Error Messages:**
- `"invalid-email"` → "Invalid email address. Please try again."
- `"invalid-password"` → "Password must be at least 8 characters."
- `"password-mismatch"` → "Passwords do not match. Please try again."
- `"email-exists"` → "An account with this email already exists. Please sign in."

**Navigation Elements:**
- Link to sign-in page: "Already have an account? Sign in."

### 1.3. Updated Sign In Page

**Location:** `src/com/apriary/pages/home.clj` - `signin-page` function

**Changes Required:**
- Add "Forgot password?" link below password field
- Maintain existing error handling

**Form Fields:** (no changes)
```clojure
[:div
 [:label "Email"]
 [:input {:name "email" :type "email" :required true}]]

[:div
 [:label "Password"]
 [:input {:name "password" :type "password" :required true}]
 [:a.text-sm.text-blue-600 {:href "/forgot-password"} "Forgot password?"]]

[:button "Sign in"]
```

**Navigation Elements:**
- Link to forgot password: "Forgot password?"
- Link to sign-up page: "Don't have an account yet? Sign up."

### 1.4. NEW: Forgot Password Page

**Location:** `src/com/apriary/pages/home.clj` - NEW `forgot-password-page` function

**Purpose:** Allow users to request a password reset email

**Form Structure:**
```clojure
[:h2 "Reset your password"]
[:p "Enter your email address and we'll send you a link to reset your password."]

[:div
 [:label "Email"]
 [:input {:name "email" :type "email" :required true}]]

[:button "Send reset link"]

[:a {:href "/signin"} "Back to sign in"]
```

**Validation Cases:**
1. Email format validation
2. Email exists in database (for security, always show success message)

**Error Messages:**
- `"invalid-email"` → "Invalid email address."
- Always show success for security (even if email not found)

**Success Behavior:**
- Redirect to `/password-reset-sent` regardless of whether email exists
- This prevents email enumeration attacks

### 1.5. NEW: Password Reset Sent Page

**Location:** `src/com/apriary/pages/home.clj` - NEW `password-reset-sent-page` function

**Purpose:** Confirm that password reset email was sent

**Content:**
```clojure
[:h2 "Check your email"]
[:p "If an account exists with that email address, you'll receive a password reset link shortly."]
[:p.text-sm "The link will expire in 1 hour."]
[:a {:href "/signin"} "Return to sign in"]
```

**Notes:**
- Generic message for security (no confirmation if email exists)
- Display expected expiration time
- No form, just informational content

### 1.6. NEW: Reset Password Page

**Location:** `src/com/apriary/pages/home.clj` - NEW `reset-password-page` function

**Purpose:** Allow users to set a new password using a valid token

**Route:** `/reset-password?token={token}`

**Form Structure:**
```clojure
[:h2 "Set new password"]

[:div
 [:label "New Password"]
 [:input {:name "password" :type "password" :required true}]
 [:p.text-xs "Must be at least 8 characters"]]

[:div
 [:label "Confirm New Password"]
 [:input {:name "password-confirm" :type "password" :required true}]]

[:input {:type "hidden" :name "token" :value token}]

[:button "Reset password"]
```

**Validation Cases:**
1. Token validity (not expired, exists, not used)
2. Password minimum length
3. Password confirmation match

**Error Messages:**
- `"invalid-token"` → "This password reset link is invalid or has expired. Please request a new one."
- `"token-expired"` → "This password reset link has expired. Please request a new one."
- `"token-used"` → "This password reset link has already been used. Please request a new one."
- `"invalid-password"` → "Password must be at least 8 characters."
- `"password-mismatch"` → "Passwords do not match."

**Guard Clauses:**
- If no token provided → redirect to `/forgot-password`
- If token invalid/expired → show error and link to request new reset

### 1.7. NEW: Password Reset Success Page

**Location:** `src/com/apriary/pages/home.clj` - NEW `password-reset-success-page` function

**Purpose:** Confirm successful password reset

**Content:**
```clojure
[:h2 "Password reset successful"]
[:p "Your password has been successfully reset. You can now sign in with your new password."]
[:a.btn {:href "/signin"} "Sign in"]
```

### 1.8. Navigation Components for Unauthenticated Users

**Location:** `src/com/apriary/ui/layout.clj` or `src/com/apriary/ui.clj`

**Changes Required:**

#### 1.8.1. Add Unauthenticated Header Component

Create a new component for displaying navigation when user is NOT authenticated:

```clojure
(defn unauthenticated-header
  "Header for unauthenticated users with Sign In / Sign Up buttons"
  [{:keys [session] :as _ctx}]
  (when-not (:uid session)
    [:header.sticky.top-0.z-50.bg-white.shadow-sm
     [:nav.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
      [:div.flex.items-center.justify-between.h-16
       ;; Left: App Name/Logo
       [:div.flex-shrink-0
        [:a.flex.items-center {:href "/" :aria-label "Apriary Summary Home"}
         [:span.text-xl.font-bold.text-gray-900 "Apriary Summary"]]]

       ;; Right: Auth Actions
       [:div.flex.items-center.gap-3
        [:a.text-gray-700.hover:text-gray-900 {:href "/signin"} "Sign in"]
        [:a.btn-primary {:href "/"} "Sign up"]]]]]))
```

#### 1.8.2. Update Page Layouts

Modify `ui/page` function to include the unauthenticated header:
- For unauthenticated pages (signup, signin, forgot-password), show unauthenticated-header
- For authenticated pages, continue using existing application-header from `ui/layout.clj`

### 1.9. Responsibility Separation

#### Frontend (Pages/Forms)
- Render HTML forms with appropriate fields
- Display validation errors from backend
- Provide basic client-side validation (password match)
- Handle navigation between auth pages
- Submit form data to backend endpoints

#### Backend (Biff Handlers)
- Validate all inputs (never trust client-side validation)
- Hash passwords using BCrypt
- Generate and validate reset tokens
- Send password reset emails
- Manage user sessions
- Enforce security policies (token expiration, rate limiting)
- Perform database operations

#### Integration Points
- Forms POST to `/auth/*` routes
- Backend returns 303 redirects with error query params
- Error params drive conditional rendering in pages
- Session management handled by Biff middleware
- CSRF protection via Biff anti-forgery tokens

### 1.10. User Flow Scenarios

#### Scenario A: Successful Registration
1. User visits `/` (sign-up page)
2. Fills email, password, password-confirm
3. POSTs to `/auth/signup`
4. Backend validates, creates user, creates session
5. Redirects to `/app` (which redirects to `/summaries`)
6. User sees summaries page with authenticated header

#### Scenario B: Registration with Validation Error
1. User visits `/`
2. Enters mismatched passwords
3. POSTs to `/auth/signup`
4. Backend validates, finds error
5. Redirects to `/?error=password-mismatch`
6. Page re-renders with error message and preserved email

#### Scenario C: Password Reset Flow
1. User visits `/signin`, clicks "Forgot password?"
2. Navigates to `/forgot-password`
3. Enters email, POSTs to `/auth/send-password-reset`
4. Backend generates token, prints email to console (MVP: no actual email sent)
5. Redirects to `/password-reset-sent`
6. **MVP:** Developer/user copies reset link from console output
7. Navigates to `/reset-password?token=xxx` (by pasting link from console)
8. Enters new password and confirmation
9. POSTs to `/auth/reset-password`
10. Backend validates token and password
11. Redirects to `/password-reset-success`
12. User clicks "Sign in" → navigates to `/signin`

**Note:** In production with real email, step 6 would be "User receives email with reset link" and step 7 would be "Clicks link in email".

#### Scenario D: Expired Reset Token
1. User clicks old reset link (> 1 hour old)
2. Navigates to `/reset-password?token=xxx`
3. Page loads, POSTs to `/auth/reset-password` on submit
4. Backend detects expired token
5. Redirects to `/reset-password?token=xxx&error=token-expired`
6. Page shows error with link to request new reset

---

## 2. BACKEND LOGIC

### 2.1. Database Schema Extensions

**Location:** `src/com/apriary/schema.clj`

#### 2.1.1. NEW: Password Reset Token Schema

Add to existing schema map:

```clojure
:password-reset-token/id :uuid
:password-reset-token [:map {:closed true}
                       [:xt/id                                :uuid]
                       [:password-reset-token/id              :uuid]
                       [:password-reset-token/user-id         :uuid]
                       [:password-reset-token/token           :string]  ;; hashed token
                       [:password-reset-token/expires-at      inst?]
                       [:password-reset-token/created-at      inst?]
                       [:password-reset-token/used-at {:optional true} [:maybe inst?]]]
```

**Field Descriptions:**
- `:xt/id` - Primary key (same as token-id)
- `:password-reset-token/id` - Token UUID
- `:password-reset-token/user-id` - Reference to user requesting reset
- `:password-reset-token/token` - Hashed token value (for security)
- `:password-reset-token/expires-at` - Expiration timestamp (created-at + 1 hour)
- `:password-reset-token/created-at` - Creation timestamp
- `:password-reset-token/used-at` - Timestamp when token was used (nil if unused)

**Security Notes:**
- Store hashed version of token (using BCrypt or SHA-256)
- Send unhashed token in email
- When validating, hash the provided token and compare with stored hash
- Tokens are single-use (mark as used after successful reset)
- Expired tokens (> 1 hour) are rejected

#### 2.1.2. User Schema - No Changes Required

Existing schema is sufficient:
```clojure
:user [:map {:closed true}
       [:xt/id              :user/id]
       [:user/id            :user/id]
       [:user/email         :string]
       [:user/password-hash :string]
       [:user/joined-at     inst?]]
```

### 2.2. Authentication Module Routes

**Location:** `src/com/apriary/auth.clj`

#### Current Routes:
```clojure
{:routes ["/auth"
          ["/signup" {:post signup}]
          ["/signin" {:post signin}]
          ["/signout" {:post signout}]]}
```

#### NEW Routes to Add:
```clojure
{:routes ["/auth"
          ["/signup" {:post signup}]
          ["/signin" {:post signin}]
          ["/signout" {:post signout}]
          ["/send-password-reset" {:post send-password-reset}]       ;; NEW
          ["/reset-password" {:post reset-password}]]}               ;; NEW
```

### 2.3. Handler Specifications

#### 2.3.1. Updated `signup` Handler

**Location:** `src/com/apriary/auth.clj:21-51`

**Required Changes:**

Add password confirmation validation:

```clojure
(defn signup [{:keys [params biff/db] :as ctx}]
  (let [email (str/trim (:email params))
        password (:password params)
        password-confirm (:password-confirm params)  ;; NEW
        existing-user (biff/lookup db :user/email email)]
    (cond
      (not (valid-email? email))
      {:status 303
       :headers {"location" "/?error=invalid-email"}}

      (not (valid-password? password))
      {:status 303
       :headers {"location" "/?error=invalid-password"}}

      ;; NEW: Password confirmation check
      (not= password password-confirm)
      {:status 303
       :headers {"location" "/?error=password-mismatch"}}

      existing-user
      {:status 303
       :headers {"location" "/?error=email-exists"}}

      :else
      ;; ... existing user creation logic ...
      )))
```

**Input Validation:**
- Email: required, valid format, unique
- Password: required, min 8 characters
- Password-confirm: required, must match password

**Error Responses:**
- Invalid email → `/?error=invalid-email`
- Invalid password → `/?error=invalid-password`
- Password mismatch → `/?error=password-mismatch`
- Email exists → `/?error=email-exists`

**Success Response:**
- Create user with hashed password
- Create session with `:uid`
- Redirect to `/app`

#### 2.3.2. `signin` Handler - No Changes

Existing implementation is sufficient.

#### 2.3.3. NEW: `send-password-reset` Handler

**Purpose:** Generate reset token and print email to console (MVP: no actual email sending)

**Input Params:**
- `email` (string) - User's email address

**Logic Flow:**
```clojure
(defn send-password-reset [{:keys [params biff/db biff.xtdb/node] :as ctx}]
  (let [email (str/trim (:email params))
        user (biff/lookup db :user/email email)]

    ;; Guard clause: invalid email format
    (if-not (valid-email? email)
      {:status 303
       :headers {"location" "/forgot-password?error=invalid-email"}}

      ;; Always redirect to success page (prevent email enumeration)
      (do
        ;; Only process if user exists
        (when user
          (let [token-id (random-uuid)
                raw-token (generate-secure-token)  ;; 32-byte random string
                hashed-token (hash-token raw-token)
                expires-at (plus-hours (now) 1)]

            ;; Create reset token record
            (biff/submit-tx
             ctx
             [{:db/op :create
               :db/doc-type :password-reset-token
               :xt/id token-id
               :password-reset-token/id token-id
               :password-reset-token/user-id (:user/id user)
               :password-reset-token/token hashed-token
               :password-reset-token/expires-at expires-at
               :password-reset-token/created-at (now)
               :password-reset-token/used-at nil}])

            ;; MVP: Print email to console (no actual sending)
            (send-password-reset-email
             ctx
             email
             (str (get-base-url ctx) "/reset-password?token=" raw-token))))

        ;; Always show success
        {:status 303
         :headers {"location" "/password-reset-sent"}}))))
```

**Security Considerations:**
- Always return success response (even if email not found) - prevents email enumeration
- Generate cryptographically secure random tokens
- Store only hashed version of token
- Set 1-hour expiration
- Include user-id for faster lookups

**Helper Functions Required:**
- `generate-secure-token` - Generate 32-byte random token, encode as URL-safe string
- `hash-token` - Hash token using SHA-256 or BCrypt
- `send-password-reset-email` - Print email to console (MVP implementation)
- `get-base-url` - Get application base URL from config

**MVP Note:**
The `send-password-reset-email` function in MVP will print the email content and reset link to console (stdout) instead of sending actual emails. Developers can copy the reset link from console output to test the password reset flow.

#### 2.3.4. NEW: `reset-password` Handler

**Purpose:** Validate token and update user password

**Input Params:**
- `token` (string) - Reset token from email
- `password` (string) - New password
- `password-confirm` (string) - Password confirmation

**Logic Flow:**
```clojure
(defn reset-password [{:keys [params biff/db biff.xtdb/node] :as ctx}]
  (let [raw-token (:token params)
        password (:password params)
        password-confirm (:password-confirm params)
        hashed-token (hash-token raw-token)

        ;; Find token record
        token-record (biff/lookup db :password-reset-token/token hashed-token)]

    (cond
      ;; Guard clause: token not found
      (not token-record)
      {:status 303
       :headers {"location" "/reset-password?token=" raw-token "&error=invalid-token"}}

      ;; Guard clause: token expired
      (after? (now) (:password-reset-token/expires-at token-record))
      {:status 303
       :headers {"location" "/reset-password?token=" raw-token "&error=token-expired"}}

      ;; Guard clause: token already used
      (some? (:password-reset-token/used-at token-record))
      {:status 303
       :headers {"location" "/reset-password?token=" raw-token "&error=token-used"}}

      ;; Guard clause: invalid password
      (not (valid-password? password))
      {:status 303
       :headers {"location" "/reset-password?token=" raw-token "&error=invalid-password"}}

      ;; Guard clause: password mismatch
      (not= password password-confirm)
      {:status 303
       :headers {"location" "/reset-password?token=" raw-token "&error=password-mismatch"}}

      ;; Happy path: update password
      :else
      (let [user-id (:password-reset-token/user-id token-record)
            new-password-hash (hash-password password)]

        ;; Update user password and mark token as used
        (biff/submit-tx
         ctx
         [{:db/op :update
           :xt/id user-id
           :user/password-hash new-password-hash}
          {:db/op :update
           :xt/id (:xt/id token-record)
           :password-reset-token/used-at (now)}])

        {:status 303
         :headers {"location" "/password-reset-success"}}))))
```

**Validation Steps:**
1. Token exists in database
2. Token not expired (< 1 hour old)
3. Token not already used
4. Password meets requirements (min 8 chars)
5. Passwords match

**Error Responses:**
- Token not found → `/reset-password?token={token}&error=invalid-token`
- Token expired → `/reset-password?token={token}&error=token-expired`
- Token used → `/reset-password?token={token}&error=token-used`
- Invalid password → `/reset-password?token={token}&error=invalid-password`
- Password mismatch → `/reset-password?token={token}&error=password-mismatch`

**Success Response:**
- Update user password with new hash
- Mark token as used (set `used-at` timestamp)
- Redirect to `/password-reset-success`

**Security Notes:**
- Token can only be used once
- Expired tokens are rejected
- Update password atomically with marking token as used

### 2.4. Email Service

**Location:** NEW - `src/com/apriary/email.clj`

#### 2.4.1. Email Configuration

**MVP Approach:** For the MVP, we will NOT send actual emails. Instead, we will print email content to the console.

**Future:** Add to `resources/config.edn` when implementing real email:

```clojure
:biff.postmark/api-key #biff/secret "POSTMARK_API_KEY"
:com.apriary/from-email "noreply@apiary-summary.example.com"
```

#### 2.4.2. Email Template

```clojure
(ns com.apriary.email
  (:require [com.biffweb :as biff]
            [clojure.tools.logging :as log]))

(defn password-reset-email-html
  "HTML email template for password reset"
  [reset-link]
  (str
   "<h2>Reset your Apriary Summary password</h2>"
   "<p>You requested to reset your password. Click the link below to set a new password:</p>"
   "<p><a href=\"" reset-link "\" style=\"display: inline-block; padding: 12px 24px; "
   "background-color: #2563eb; color: white; text-decoration: none; "
   "border-radius: 6px;\">Reset Password</a></p>"
   "<p>This link will expire in 1 hour.</p>"
   "<p>If you didn't request this, you can safely ignore this email.</p>"
   "<p>—<br>The Apriary Summary Team</p>"))

(defn password-reset-email-text
  "Plain text email template for password reset"
  [reset-link]
  (str
   "Reset your Apriary Summary password\n\n"
   "You requested to reset your password. Click the link below to set a new password:\n\n"
   reset-link "\n\n"
   "This link will expire in 1 hour.\n\n"
   "If you didn't request this, you can safely ignore this email.\n\n"
   "—\n"
   "The Apriary Summary Team"))

(defn send-password-reset-email
  "Send password reset email (MVP: logs to console instead of actually sending)"
  [ctx to-email reset-link]
  ;; MVP: Print email to console instead of sending
  (println "\n========================================")
  (println "PASSWORD RESET EMAIL")
  (println "========================================")
  (println "To:" to-email)
  (println "Subject: Reset your Apriary Summary password")
  (println "----------------------------------------")
  (println (password-reset-email-text reset-link))
  (println "========================================\n")
  (log/info "Password reset email (console output)" :to to-email))

;; Future implementation for real email sending:
(comment
  (defn send-password-reset-email-real
    "Send password reset email via Postmark"
    [{:keys [biff.postmark/api-key com.apriary/from-email] :as ctx}
     to-email
     reset-link]
    (biff/send-email
     {:to to-email
      :from from-email
      :subject "Reset your Apriary Summary password"
      :html (password-reset-email-html reset-link)
      :text (password-reset-email-text reset-link)
      :postmark-api-key api-key})))
```

#### 2.4.3. Email Sending Strategy

**MVP (Current Implementation):**
- Print email content to console (stdout)
- This allows testing the password reset flow without email infrastructure
- Developers can copy the reset link from console output
- No external dependencies or API keys required

**Future Production Implementation:**
- Use Postmark API or similar email service
- Handle email delivery errors gracefully
- Log failed email attempts
- Monitor email delivery rates

### 2.5. Helper Functions

**Location:** `src/com/apriary/auth.clj`

#### Token Generation

```clojure
(defn generate-secure-token
  "Generate cryptographically secure random token"
  []
  (let [random-bytes (byte-array 32)
        _ (.nextBytes (java.security.SecureRandom.) random-bytes)]
    ;; Encode as URL-safe base64
    (.encodeToString (java.util.Base64/getUrlEncoder) random-bytes)))

(defn hash-token
  "Hash token using SHA-256"
  [token]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes token "UTF-8"))]
    ;; Convert to hex string
    (apply str (map #(format "%02x" %) hash-bytes))))
```

#### Time Helpers

```clojure
(defn now [] (java.util.Date.))

(defn plus-hours
  "Add hours to a date"
  [date hours]
  (java.util.Date. (+ (.getTime date) (* hours 60 60 1000))))

(defn after?
  "Check if date1 is after date2"
  [date1 date2]
  (.after date1 date2))
```

#### URL Helper

```clojure
(defn get-base-url
  "Get application base URL from config"
  [{:keys [biff/base-url]}]
  base-url)
```

### 2.6. Module Integration

**Location:** `src/com/apriary.clj`

**Note:** The email namespace (`src/com/apriary/email.clj`) will be created, but it does not need to export a Biff module since it only provides utility functions (console logging for MVP). The `send-password-reset-email` function will be called directly from the auth handlers.

No changes required to the modules list in `src/com/apriary.clj`.

### 2.7. Input Validation Summary

All handlers must validate inputs using early returns (guard clauses):

| Handler | Validations |
|---------|-------------|
| `signup` | Email format, email uniqueness, password length, password match |
| `signin` | Email format, email exists, password correct |
| `send-password-reset` | Email format |
| `reset-password` | Token exists, token not expired, token not used, password length, password match |

### 2.8. Error Handling Strategy

**Approach:** Redirect with error query parameters

**Format:** `{page-url}?error={error-code}`

**Benefits:**
- Simple implementation
- Works with form POSTs
- Preserves browser history
- Error messages controlled by frontend

**Limitations:**
- Doesn't preserve form values (except email in some cases)
- Users must re-enter data on validation failure

**Enhancement Option (Future):**
Could use flash messages in session to preserve form values:
```clojure
:session {:flash {:error "error-code" :params {...}}}
```

---

## 3. AUTHENTICATION SYSTEM ARCHITECTURE

### 3.1. Authentication Flow Overview

```
┌─────────────┐
│ Unauthenticated│
│    User      │
└──────┬───────┘
       │
       ├─── Registration Flow ──────────────────────┐
       │    1. Visit / (signup page)                │
       │    2. Submit email + password + confirm    │
       │    3. Backend validates & creates user     │
       │    4. Session created with :uid            │
       │    5. Redirect to /app → /summaries        │
       │                                             │
       ├─── Login Flow ─────────────────────────────┤
       │    1. Visit /signin                        │
       │    2. Submit email + password              │
       │    3. Backend validates credentials        │
       │    4. Session created with :uid            │
       │    5. Redirect to /app → /summaries        │
       │                                             │
       └─── Password Recovery Flow ─────────────────┤
            1. Visit /signin, click "Forgot?"       │
            2. Visit /forgot-password               │
            3. Submit email                         │
            4. Backend generates token + sends email│
            5. Redirect to /password-reset-sent     │
            6. User receives email, clicks link     │
            7. Visit /reset-password?token=xxx      │
            8. Submit new password + confirm        │
            9. Backend validates token & updates    │
           10. Redirect to /password-reset-success  │
           11. User clicks "Sign in"                │
            └────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Authenticated  │
                    │      User       │
                    └─────────────────┘
```

### 3.2. Session Management

**Mechanism:** Cookie-based sessions via Biff middleware

**Session Data Structure:**
```clojure
{:uid #uuid "user-id-here"}
```

**Session Lifecycle:**
- Created on successful signup/signin
- Stored in encrypted cookie
- Validated by `wrap-session` middleware
- Cleared on signout
- Persisted across requests

**Security:**
- Cookie secret configured in `resources/config.edn`
- HTTPS-only in production (`:biff.middleware/secure true`)
- SameSite cookie attribute
- CSRF protection via anti-forgery tokens

### 3.3. Password Security

**Hashing Algorithm:** BCrypt

**Implementation:**
```clojure
;; Using jbcrypt library (already in project)
(defn hash-password [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn verify-password [password hash]
  (BCrypt/checkpw password hash))
```

**Security Features:**
- Automatic salt generation
- Configurable work factor (default: 10)
- Resistant to rainbow table attacks
- Slow by design (prevents brute force)

**Password Requirements:**
- Minimum length: 8 characters
- No maximum length limit
- No complexity requirements (for MVP)
- Can be enhanced later with:
  - Special character requirements
  - Uppercase/lowercase requirements
  - Number requirements
  - Password strength meter

### 3.4. Reset Token Security

**Token Format:**
- 32-byte random value
- URL-safe base64 encoding
- Example: `a7BcD3FgH9JkL2MnP5QrS8TuV1WxY4Z6`

**Token Storage:**
- Store hashed version only (SHA-256)
- Never store plaintext token
- Single-use tokens (marked as used after reset)

**Token Lifecycle:**
1. **Generation:**
   - Create random 32-byte value
   - Hash with SHA-256 for storage
   - Store hash, user-id, expiration
   - Send plaintext token in email

2. **Validation:**
   - Receive token from user
   - Hash received token
   - Look up by hash in database
   - Check expiration and used status

3. **Consumption:**
   - Mark as used (set `used-at` timestamp)
   - Update user password
   - Atomic transaction

**Expiration:**
- Default: 1 hour from creation
- Configurable in auth module
- Expired tokens rejected

**Security Benefits:**
- Tokens can't be reused
- Database breach doesn't expose valid tokens
- Time-limited attack window
- No token in database after use

### 3.5. Route Protection

**Middleware:** Already implemented in `src/com/apriary/middleware.clj`

**`wrap-signed-in` Middleware:**
```clojure
(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (some? (:uid session))
      (handler ctx)
      {:status 303
       :headers {"location" "/signin?error=not-signed-in"}})))
```

**Usage:**
```clojure
;; Protected routes
["/summaries" {:middleware [mid/wrap-signed-in]}
 ["" {:get summaries-list-page}]]

;; Public routes (no middleware)
["/" {:get home-page}]
["/signin" {:get signin-page}]
```

**`wrap-redirect-signed-in` Middleware:**
- Prevents authenticated users from accessing auth pages
- Redirects to `/app` if already signed in
- Used on signup/signin pages

### 3.6. CSRF Protection

**Implementation:** Biff's anti-forgery middleware

**How It Works:**
- Middleware generates CSRF token per session
- Token included in all forms via `biff/form` helper
- Token validated on POST requests
- Invalid token → 403 Forbidden

**Integration:**
```clojure
;; In forms (automatic via biff/form)
(biff/form {:action "/auth/signup"} ...)

;; In HTMX requests (automatic via hx-headers)
{:hx-headers (cheshire/generate-string
              {:x-csrf-token csrf/*anti-forgery-token*})}
```

### 3.7. Security Best Practices Implementation

#### 3.7.1. Email Enumeration Prevention
- Always return success on password reset request
- Don't reveal if email exists or not
- Same response time regardless of email existence

#### 3.7.2. Timing Attack Prevention
- Use constant-time comparison for password verification (BCrypt handles this)
- Use constant-time comparison for token validation (via hash comparison)

#### 3.7.3. Rate Limiting (Future Enhancement)
Not in MVP scope, but should be added:
- Limit signup attempts per IP
- Limit password reset requests per email/IP
- Limit signin attempts per email/IP
- Use Redis or in-memory store for rate limiting

Recommended approach:
```clojure
;; Pseudocode for future implementation
(defn rate-limit-middleware [handler limit-key max-attempts window-seconds]
  (fn [ctx]
    (let [attempts (get-attempts limit-key)]
      (if (> attempts max-attempts)
        {:status 429 :body "Too many requests"}
        (do
          (increment-attempts limit-key window-seconds)
          (handler ctx))))))
```

#### 3.7.4. SQL Injection Prevention
- Not applicable (using XTDB with Datalog)
- Datalog queries are parameterized by design
- No string concatenation in queries

#### 3.7.5. XSS Prevention
- Biff/Rum automatically escapes HTML
- No raw HTML injection in user inputs
- Content-Security-Policy headers (future)

#### 3.7.6. Session Security
- Secure cookies in production
- HttpOnly cookies (prevent JavaScript access)
- SameSite cookies (CSRF protection)
- Session timeout (future enhancement)

### 3.8. Error Logging and Monitoring

**Logging Strategy:**

```clojure
(ns com.apriary.auth
  (:require [clojure.tools.logging :as log]))

;; Log security events
(log/info "User signup" :email email)
(log/info "User signin" :email email :user-id user-id)
(log/warn "Failed signin attempt" :email email :reason "invalid-password")
(log/info "Password reset requested" :email email)
(log/info "Password reset completed" :user-id user-id)
(log/warn "Invalid reset token" :token-hash (subs hashed-token 0 8))
```

**What to Log:**
- Successful authentications (email, user-id)
- Failed authentication attempts (email, reason)
- Password reset requests (email)
- Password reset completions (user-id)
- Invalid/expired token usage
- Account creations

**What NOT to Log:**
- Passwords (plaintext or hashed)
- Full reset tokens
- Session tokens
- Email content

### 3.9. Configuration

**Location:** `resources/config.edn`

**Required Configuration:**
```clojure
{;; Existing config
 :biff/base-url #profile {:prod #join ["https://" #biff/env DOMAIN]
                          :default #join ["http://localhost:" #ref [:biff/port]]}
 :biff.middleware/cookie-secret #biff/secret COOKIE_SECRET
 :biff/jwt-secret #biff/secret JWT_SECRET

 ;; NEW: Auth configuration
 :com.apriary.auth/reset-token-expiry-hours 1
 :com.apriary.auth/min-password-length 8}
```

**Environment Variables Required:**
```bash
# Existing
COOKIE_SECRET=xxx
JWT_SECRET=xxx
```

**Email Configuration (Future - Not needed for MVP):**
```clojure
;; To be added when implementing real email sending:
:biff.postmark/api-key #biff/secret "POSTMARK_API_KEY"
:com.apriary/from-email "noreply@apiary-summary.example.com"
```

**Additional Environment Variables (Future):**
```bash
# Not needed for MVP - only when implementing real email
POSTMARK_API_KEY=xxx
```

### 3.10. Testing Considerations

**Unit Tests Required:**

1. **Validation Functions:**
   - `valid-email?` with various email formats
   - `valid-password?` with edge cases
   - Token generation uniqueness
   - Token hashing consistency

2. **Password Hashing:**
   - Hash generation creates different hashes for same password
   - Verify works with correct password
   - Verify fails with incorrect password

3. **Token Management:**
   - Token generation creates unique tokens
   - Token hashing is deterministic
   - Token expiration logic

**Integration Tests Required:**

1. **Signup Flow:**
   - Successful signup with valid data
   - Rejection of invalid email
   - Rejection of short password
   - Rejection of mismatched passwords
   - Rejection of duplicate email

2. **Signin Flow:**
   - Successful signin with valid credentials
   - Rejection of invalid email
   - Rejection of invalid password
   - Session creation

3. **Password Reset Flow:**
   - Token generation and email sending
   - Token validation
   - Expired token rejection
   - Used token rejection
   - Password update

4. **Security Tests:**
   - CSRF token validation
   - Route protection (authenticated vs unauthenticated)
   - Email enumeration prevention

**Test Data Setup:**
```clojure
;; Test fixtures
(def test-user
  {:user/id #uuid "test-user-id"
   :user/email "test@example.com"
   :user/password-hash (hash-password "password123")
   :user/joined-at (java.util.Date.)})

(def test-reset-token
  {:password-reset-token/id #uuid "test-token-id"
   :password-reset-token/user-id #uuid "test-user-id"
   :password-reset-token/token (hash-token "test-token")
   :password-reset-token/expires-at (plus-hours (now) 1)
   :password-reset-token/created-at (now)})
```

---

## 4. IMPLEMENTATION CHECKLIST

### Phase 1: Enhanced Registration
- [ ] Update signup form with password confirmation field
- [ ] Update `signup` handler with password match validation
- [ ] Update error messages on signup page
- [ ] Test signup flow with all validation cases

### Phase 2: Password Reset Schema & Tokens
- [ ] Add password reset token schema to `schema.clj`
- [ ] Implement `generate-secure-token` function
- [ ] Implement `hash-token` function
- [ ] Implement time helper functions
- [ ] Test token generation and hashing

### Phase 3: Email Infrastructure (MVP: Console Logging)
- [ ] Create `email.clj` namespace
- [ ] Implement email templates (HTML & text)
- [ ] Implement `send-password-reset-email` function (console output for MVP)
- [ ] Test email output to console
- [ ] Verify reset links can be copied from console and work correctly

### Phase 4: Password Reset Pages
- [ ] Create forgot-password page
- [ ] Create password-reset-sent page
- [ ] Create reset-password page
- [ ] Create password-reset-success page
- [ ] Test page rendering and navigation

### Phase 5: Password Reset Handlers
- [ ] Implement `send-password-reset` handler
- [ ] Implement `reset-password` handler
- [ ] Add routes to auth module
- [ ] Test password reset flow end-to-end

### Phase 6: Navigation Updates
- [ ] Create unauthenticated-header component
- [ ] Update sign-in page with "Forgot password?" link
- [ ] Update layout to show appropriate header based on auth state
- [ ] Test navigation for authenticated/unauthenticated users

### Phase 7: Testing & Security
- [ ] Write unit tests for validation functions
- [ ] Write integration tests for all auth flows
- [ ] Security review (token handling, password hashing)
- [ ] Test CSRF protection
- [ ] Test route protection middleware

---

## 6. DEPENDENCIES

### Existing Dependencies (Already in Project)
- `org.mindrot/jbcrypt` - Password hashing
- `com.biffweb/biff` - Framework (sessions, CSRF, routing)
- `xtdb` - Database
- `rum` - HTML rendering
- `ring` - Web server

### New Dependencies Required
None - all required functionality available in existing dependencies

### Configuration Dependencies
- Environment variables for secrets (COOKIE_SECRET, JWT_SECRET)

### Future Dependencies (Not needed for MVP)
- Email service (Postmark or similar) - Only when implementing actual email sending
- Email service API key - Only for production email delivery

---

## 7. RISK ASSESSMENT

### Low Risk
- Password confirmation validation (simple addition)
- Page creation (following existing patterns)
- Email template creation
- Console logging for password reset emails (MVP approach)

### Medium Risk
- Token generation and security (requires careful implementation)
- Time-based token expiration (edge cases)

### High Risk
- Security vulnerabilities if token handling incorrect
- Race conditions in token usage

### Mitigation Strategies
- Follow security best practices for token handling
- Use well-tested libraries (BCrypt, SecureRandom)
- Implement comprehensive tests
- Security review before production deployment
- Use single-use tokens with atomic updates

### MVP-Specific Risk Reduction
- **Email deliverability**: Not applicable for MVP - using console output eliminates this risk
- **External service dependencies**: Not applicable for MVP - no external email service required
- **API key management**: Not applicable for MVP - no API keys needed

---

## 8. SUCCESS CRITERIA

The authentication system is considered complete when:

1. **User Registration**
   - ✓ Users can sign up with email, password, and confirmation
   - ✓ Password confirmation is validated
   - ✓ Appropriate error messages shown for all validation failures
   - ✓ Successful registration creates session and redirects to app

2. **User Login**
   - ✓ Users can sign in with email and password
   - ✓ Invalid credentials show appropriate error
   - ✓ Successful login creates session and redirects to app
   - ✓ "Forgot password?" link visible and functional

3. **Password Recovery**
   - ✓ Users can request password reset from signin page
   - ✓ Reset email content printed to console (MVP: no actual email sending)
   - ✓ Reset link can be copied from console and works correctly
   - ✓ Reset link loads reset password page
   - ✓ Users can set new password with confirmation
   - ✓ Expired/invalid tokens show appropriate errors
   - ✓ Successful reset allows signin with new password

4. **Navigation**
   - ✓ Unauthenticated users see Sign In / Sign Up buttons
   - ✓ Authenticated users see Logout button
   - ✓ Protected routes redirect to signin if not authenticated
   - ✓ Auth pages redirect to app if already authenticated

5. **Security**
   - ✓ Passwords stored as BCrypt hashes
   - ✓ Reset tokens stored as hashes
   - ✓ Tokens expire after 1 hour
   - ✓ Tokens single-use only
   - ✓ CSRF protection active on all forms
   - ✓ No email enumeration vulnerability

6. **Testing**
   - ✓ All validation logic unit tested
   - ✓ All auth flows integration tested
   - ✓ Security features verified

---

## 9. APPENDIX

### A. URL Mapping

| URL | Purpose | Access | HTTP Method |
|-----|---------|--------|-------------|
| `/` | Sign up page | Unauthenticated | GET |
| `/signin` | Sign in page | Unauthenticated | GET |
| `/forgot-password` | Forgot password page | Unauthenticated | GET |
| `/reset-password?token=xxx` | Reset password page | Token-based | GET |
| `/password-reset-sent` | Confirmation page | Unauthenticated | GET |
| `/password-reset-success` | Success page | Unauthenticated | GET |
| `/auth/signup` | Process signup | Unauthenticated | POST |
| `/auth/signin` | Process signin | Unauthenticated | POST |
| `/auth/signout` | Process signout | Authenticated | POST |
| `/auth/send-password-reset` | Request reset email | Unauthenticated | POST |
| `/auth/reset-password` | Process password reset | Token-based | POST |
| `/app` | App entry (redirects to /summaries) | Authenticated | GET |
| `/summaries` | Main app page | Authenticated | GET |

### B. Database Queries

**Find user by email:**
```clojure
(biff/lookup db :user/email email)
```

**Find reset token by hash:**
```clojure
(biff/lookup db :password-reset-token/token hashed-token)
```

**Find user's active reset tokens:**
```clojure
(xt/q db
  {:find '[?token]
   :where [['?token :password-reset-token/user-id user-id]
           ['?token :password-reset-token/used-at nil]]})
```

### C. Error Code Reference

| Code | Message | Page |
|------|---------|------|
| `invalid-email` | Invalid email address | Signup, Signin, Forgot |
| `invalid-password` | Password must be at least 8 characters | Signup, Reset |
| `password-mismatch` | Passwords do not match | Signup, Reset |
| `email-exists` | Account already exists | Signup |
| `invalid-credentials` | Invalid email or password | Signin |
| `not-signed-in` | Must be signed in | Protected routes |
| `invalid-token` | Invalid/expired reset link | Reset Password |
| `token-expired` | Reset link expired | Reset Password |
| `token-used` | Reset link already used | Reset Password |

### D. File Structure

```
src/com/apriary/
├── apriary.clj              (main, modules list)
├── auth.clj                 (auth handlers - UPDATED)
├── email.clj                (email service - NEW)
├── schema.clj               (database schema - UPDATED)
├── middleware.clj           (auth middleware - no changes)
├── ui.clj                   (base UI - minor updates)
├── ui/
│   ├── layout.clj           (layouts - UPDATED)
│   └── header.clj           (header component - UPDATED)
└── pages/
    ├── home.clj             (auth pages - UPDATED + NEW pages)
    ├── app.clj              (no changes)
    └── summaries_view.clj   (no changes)
```

### E. Session Data Structure

```clojure
;; Unauthenticated session
{}

;; Authenticated session
{:uid #uuid "user-uuid-here"}

;; Session with flash message (future enhancement)
{:uid #uuid "user-uuid-here"
 :flash {:message "Password reset successful"
         :type :success}}
```

---

**End of Specification**
