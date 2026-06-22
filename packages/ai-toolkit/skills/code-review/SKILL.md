---
name: code-review
description: Review Clojure/Biff code changes against team engineering conventions, testing standards, and security expectations. Use when asked to review code, check a PR, or audit code quality.
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---

# Code Review Skill

Review code changes against Clojure/Biff team conventions and best practices.

## When to Use

Invoke this skill when you hear:
- "review code"
- "check this PR"
- "review my changes"
- "code review"
- "audit code quality"

## Review Categories

### 1. Naming Conventions

**Check for:**
- Kebab-case for functions, vars, and namespaces (`parse-uuid`, `list-summaries`)
- Predicates end with `?` (`valid?`, `authenticated?`)
- Private helpers prefixed with `-` (`-validate-content`, `-build-query`)
- Constants in UPPER_SNAKE_CASE (`SORT_BY_WHITELIST`, `DEFAULT_LIMIT`)

**Examples:**
```clojure
;; Good
(defn parse-uuid [uuid-str] ...)
(defn valid-email? [email] ...)
(defn- -build-error-response [...] ...)

;; Bad
(defn parseUUID [uuid-str] ...)  ; camelCase
(defn is_valid [x] ...)          ; snake_case
```

### 2. Error Handling

**Two legitimate patterns coexist in this codebase:**

**Pattern A: Error tuples** (services layer - `util.clj`, `generation.clj`, `summary.clj`)
```clojure
;; Return [:ok value] or [:error {:code :message}]
(defn parse-uuid [uuid-str]
  (try
    [:ok (java.util.UUID/fromString uuid-str)]
    (catch IllegalArgumentException _
      [:error {:code "INVALID_UUID" :message "Invalid UUID format"}])))
```

**Pattern B: Exceptions** (domain operations - `product_rankings.clj`, `csv_import.clj`, `product.clj`)
```clojure
;; Throw exceptions for domain errors
(when (nil? content)
  (throw (IllegalArgumentException. "content is required")))
```

**Check for:**
- Services layer uses error tuples consistently
- Domain operations throw exceptions with descriptive messages
- No empty catch blocks
- Error messages include context (field name, expected format)

### 3. Guards & Flow Control

**Check for:**
- Early returns for error conditions
- Happy path last in function
- Guard clauses at function start
- No unnecessary `else` clauses (use `when` instead of `if` when no else needed)

**Example:**
```clojure
;; Good: early returns, happy path last
(defn get-summary-by-id [db summary-id user-id]
  ;; Guard clauses first
  (when (nil? summary-id)
    (throw (IllegalArgumentException. "summary-id is required")))
  (when (nil? user-id)
    (throw (IllegalArgumentException. "user-id is required")))
  
  ;; Happy path last
  (let [entity (xt/entity db summary-id)]
    (cond
      (nil? entity) [:error {...}]
      (not= (:summary/user-id entity) user-id) [:error {...}]
      :else [:ok entity])))
```

### 4. Validation

**Check for:**
- Malli schemas for all entities in `schema.clj`
- Closed maps (`:closed true`)
- Type predicates at function boundaries
- Validation happens before business logic

**Example:**
```clojure
;; schema.clj
(def summary-schema
  [:map {:closed true}
   [:xt/id :uuid]
   [:summary/id :uuid]
   [:summary/user-id :uuid]
   [:summary/content :string]
   ...])
```

### 5. Row-Level Security (RLS)

**Critical security pattern - ALWAYS enforce:**

**Check for:**
- All XTDB queries filter by `:user-id`
- Ownership verification after `xt/entity` lookups
- 404 responses for unauthorized access (never 403 - prevents enumeration)
- No user data leakage in error messages

**Example:**
```clojure
;; Good: RLS enforced
(defn list-summaries [db user-id opts]
  (let [query '{:find [?s]
                :in [user-id]
                :where [[?s :summary/user-id user-id]
                        [?s :summary/content ?content]]}]
    (xt/q db query user-id)))

;; Good: Ownership check after entity fetch
(let [entity (xt/entity db summary-id)]
  (when (not= (:summary/user-id entity) user-id)
    ;; Return 404, not 403 - don't leak existence
    [:error {:code "NOT_FOUND" :message "..."}]))
```

### 6. Biff Framework Patterns

**Check for:**
- Module maps with `:routes` and optional `:api-routes`
- Middleware applied via module pattern
- 303 redirects after POST operations
- Context destructuring in handlers
- `biff/submit-tx` in handlers, `xt/submit-tx` in services

**Example:**
```clojure
;; Good: Biff module pattern
(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app-handler
                 :post app-post}]]
   :api-routes ["/api/summaries" {:middleware [mid/wrap-api-auth]}
                ["" {:get list-summaries-api}]]})

;; Good: 303 redirect after POST
(defn create-summary-handler [ctx]
  (let [[status result] (summary/create-manual-summary ...)]
    (if (= status :ok)
      {:status 303
       :headers {"Location" "/summaries"}}
      {:status 400
       :body (build-error-response ...)})))
```

### 7. XTDB Query Patterns

**Check for:**
- `:xt/id` as primary identifier
- `:db/doc-type` for entity type discrimination
- Datalog queries use proper `:find`, `:where`, `:in` structure
- Operations use `:db/op` (`:delete`, `:update`)

**Example:**
```clojure
;; Good: Proper XTDB query structure
(xt/q db
      '{:find [?s ?content]
        :in [user-id]
        :where [[?s :summary/user-id user-id]
                [?s :summary/content ?content]
                [?s :xt/id ?id]]}
      current-user-id)

;; Good: Transaction operations
(xt/submit-tx node [[:xtdb.api/put entity]
                    [:xtdb.api/delete old-id]])
```

### 8. Testing Patterns

**Check for:**
- Test namespaces mirror source namespaces with `_test` suffix
- Result tuple destructuring: `(let [[status result] (func ...)])`
- `clojure.test` macros: `deftest`, `testing`, `is`
- Isolated setup/teardown per test
- Tests cover both success and error paths

**Example:**
```clojure
(ns com.apriary.services.summary-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.apriary.services.summary :as summary]))

(deftest parse-uuid-test
  (testing "valid UUID"
    (let [[status result] (summary/parse-uuid "123e4567-e89b-12d3-a456-426614174000")]
      (is (= :ok status))
      (is (instance? java.util.UUID result))))
  
  (testing "invalid UUID"
    (let [[status error] (summary/parse-uuid "invalid")]
      (is (= :error status))
      (is (= "INVALID_UUID" (:code error))))))
```

### 9. Linting Compliance

**Check for clj-kondo errors:**
- Redefined vars (`:level :error`)
- Invalid arity (`:level :error`)
- Unused namespaces (`:level :warning`)
- Unused bindings (`:level :warning`)
- Misplaced docstrings (`:level :warning`)

**Run:**
```bash
clj-kondo --lint src/ --config .clj-kondo/config.edn
```

## Output Format

### Finding Structure

For each issue found:

```
[SEVERITY] Category: Issue Description
File: path/to/file.clj:line
Why: Explanation of the problem
Fix: Suggested correction
```

### Severity Levels

- **CRITICAL**: Security issues, RLS violations, data loss risks
- **WARNING**: Convention violations, potential bugs, code smells
- **OBSERVATION**: Style suggestions, optimization opportunities

### Final Recommendation

After listing all findings:

```
RECOMMENDATION: [APPROVE | REQUEST CHANGES | NEEDS DISCUSSION]

Summary:
- X critical issues (must fix before merge)
- Y warnings (should address)
- Z observations (optional improvements)
```

**Decision criteria:**
- APPROVE: No critical issues, warnings are minor
- REQUEST CHANGES: Critical issues present OR significant warnings
- NEEDS DISCUSSION: Architectural concerns, pattern inconsistencies

## Example Review

```
[CRITICAL] RLS/Security: Query missing user-id filter
File: src/com/example/service.clj:45
Why: Query returns all products without user ownership check
Fix: Add [:product/user-id user-id] to :where clause

[WARNING] Error Handling: Empty catch block
File: src/com/example/handler.clj:78
Why: Swallowed exception prevents debugging
Fix: Log error or return [:error {...}] tuple

[OBSERVATION] Naming: Function could be more descriptive
File: src/com/apriary/util.clj:120
Why: `process` doesn't convey intent
Fix: Consider `validate-and-parse-params`

RECOMMENDATION: REQUEST CHANGES

Summary:
- 1 critical issue (RLS violation - security risk)
- 1 warning (error handling)
- 1 observation (naming)

The RLS violation must be fixed before merge to prevent unauthorized data access.
```
