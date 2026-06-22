---
name: clojure-style
description: Enforce Clojure formatting and linting rules. Use when checking style compliance or integrating clj-kondo.
allowed-tools:
  - Bash
  - Read
---

# Clojure Style Guide

Enforce Clojure-specific formatting, linting, and style conventions for the Apriary project.

## When to Use

Invoke this skill when:
- Checking code style compliance
- Running clj-kondo linter
- Formatting Clojure code
- Reviewing naming conventions
- Writing or reviewing documentation

## Naming Conventions

### Files and Namespaces

**File naming:**
- Use kebab-case: `summary_service.clj` → `summary-service.clj`
- Test files: Add `_test` suffix: `summary_test.clj`
- Namespaces mirror directory structure

**Examples:**
```
src/com/apriary/services/summary.clj        → com.apriary.services.summary
src/com/apriary/services/summary_test.clj   → com.apriary.services.summary-test
```

### Functions and Variables

**Kebab-case for all identifiers:**
```clojure
;; Good
(defn parse-uuid [uuid-str] ...)
(defn list-summaries [db user-id opts] ...)
(def default-limit 50)

;; Bad
(defn parseUUID [uuidStr] ...)     ; camelCase
(defn list_summaries [db uid] ...) ; snake_case
```

### Predicates

**End with `?`:**
```clojure
(defn valid-email? [email] ...)
(defn authenticated? [ctx] ...)
(defn empty-string? [s] ...)
```

### Private Functions

**Prefix with `-`:**
```clojure
(defn- -validate-content [content]
  ;; Private helper
  ...)

(defn- -build-error-response [status code message]
  ;; Private implementation detail
  ...)
```

### Constants

**UPPER_SNAKE_CASE:**
```clojure
(def SORT_BY_WHITELIST
  #{"created_at" "model" "generated_count"})

(def DEFAULT_PAGE_SIZE 50)
(def MAX_CONTENT_LENGTH 50000)
```

## clj-kondo Integration

### Running the Linter

**Basic usage:**
```bash
# Lint entire source directory
clj-kondo --lint src/

# Lint specific file
clj-kondo --lint src/com/apriary/services/summary.clj

# Use project config
clj-kondo --lint src/ --config .clj-kondo/config.edn
```

**CI integration:**
```bash
# Exit with non-zero on errors
clj-kondo --lint src/ --fail-level error
```

### Configuration

From `.clj-kondo/config.edn:1-18`:

```clojure
{:linters {:unresolved-symbol {:level :warning
                              :exclude [(com.biffweb/test-xtdb-node)
                                        (clojure.test/deftest)
                                        (clojure.test/is)
                                        (clojure.test/testing)]}
            :unused-namespace {:level :warning}
            :unused-binding {:level :warning}
            :unused-private-var {:level :warning}
            :redefined-var {:level :error}      ; Critical
            :misplaced-docstring {:level :warning}
            :invalid-arity {:level :error       ; Critical
                            :skip-args [com.biffweb/test-xtdb-node]}
            :not-empty? {:level :warning}
            :deprecated-var {:level :warning}}
 :lint-as {com.biffweb/test-xtdb-node clojure.core/let}
 :output {:exclude-files ["target/"
                          ".cpcache/"
                          "resources/public/"]}}
```

### Error Levels

**`:error` - Must fix before commit:**
- `:redefined-var` - Variable defined multiple times
- `:invalid-arity` - Function called with wrong number of args

**`:warning` - Should address:**
- `:unused-namespace` - Imported but never used
- `:unused-binding` - `let` binding not referenced
- `:unused-private-var` - Private function not called
- `:misplaced-docstring` - Docstring in wrong position

### Interpreting Output

**Example warnings:**
```
src/com/apriary/services/summary.clj:12:1: warning: unused namespace clojure.string
src/com/apriary/util.clj:45:7: warning: unused binding response
```

**Example errors:**
```
src/com/apriary/handlers/api.clj:23:1: error: redefined var parse-params
src/com/apriary/services/generation.clj:67:3: error: invalid arity calling update-summary (expected 3, got 2)
```

## Documentation Standards

### Docstrings

**Function docstrings:**
```clojure
(defn parse-uuid
  "Parse a UUID string and return the UUID object or an error tuple.
   
   Params:
   - uuid-str: String representation of UUID
   
   Returns:
   - [:ok uuid-object] on success
   - [:error {:code :message}] on failure"
  [uuid-str]
  ...)
```

**Multi-arity functions:**
```clojure
(defn list-summaries
  "Query summaries for an authenticated user with filtering.
   
   Two-arity version uses default options.
   Three-arity version accepts options map:
   - :sort-by - Field to sort by
   - :limit - Max results (1-100)
   - :offset - Skip results"
  ([db user-id]
   (list-summaries db user-id {}))
  ([db user-id opts]
   ...))
```

### Error Tuple Documentation

**Document return tuples:**
```clojure
;; Returns:
;; - [:ok result] on success
;; - [:error {:code "ERROR_CODE" :message "..."}] on failure

;; Error codes:
;; - INVALID_UUID: UUID format is invalid
;; - NOT_FOUND: Resource doesn't exist or RLS violation
;; - VALIDATION_ERROR: Input validation failed
```

### Namespace Comments

**Top-level namespace documentation:**
```clojure
(ns com.apriary.services.summary
  "Service functions for Summary entity CRUD operations.
   
   All functions follow the pattern:
   - Returns [:ok result] or [:error map]
   - Implements Row-Level Security (RLS) checks
   - Uses guard clauses for early error handling
   - Logs operations for audit trail"
  (:require [xtdb.api :as xt]
            [clojure.tools.logging :as log]))
```

## Code Formatting

### Indentation

**2-space indentation:**
```clojure
(defn example [x]
  (if (valid? x)
    (let [result (process x)]
      (log/info "Processed" result)
      [:ok result])
    [:error {:code "INVALID"}]))
```

### Threading Macros

**Use `->` and `->>` for deeply nested calls (>2 levels):**

```clojure
;; Without threading (hard to read)
(assoc (update (select-keys entity [:a :b :c]) :b inc) :d 10)

;; With threading (clearer)
(-> entity
    (select-keys [:a :b :c])
    (update :b inc)
    (assoc :d 10))

;; Collection threading
(->> summaries
     (filter #(= (:source %) :ai-full))
     (map :content)
     (take 10))
```

### Map and Vector Formatting

**Vertical alignment for readability:**
```clojure
;; Good: Multi-line map
(def entity
  {:xt/id summary-id
   :summary/user-id user-id
   :summary/content content
   :summary/source :manual
   :summary/created-at now
   :summary/updated-at now})

;; Good: Inline for short maps
{:status 200 :body result}

;; Good: Vectors
(let [fields [:name :email :created-at]
      filters [[:user-id user-id]
               [:active true]]]
  ...)
```

### Conditional Formatting

**Use `cond` for multiple conditions:**
```clojure
;; Good: cond for multiple branches
(cond
  (nil? entity) [:error {:code "NOT_FOUND"}]
  (not= owner user-id) [:error {:code "FORBIDDEN"}]
  (invalid? entity) [:error {:code "INVALID"}]
  :else [:ok entity])

;; Good: when for single condition
(when (authenticated? ctx)
  (process-request ctx))

;; Avoid: nested if
(if (valid? x)
  (if (authorized? x)
    (if (available? x)
      (process x)
      error-3)
    error-2)
  error-1)
```

## Common Style Issues

### 1. Namespace Organization

**Order of declarations:**
```clojure
(ns com.apriary.services.summary
  (:require [xtdb.api :as xt]           ; External deps first
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.apriary.schema :as schema])) ; Internal deps last

;; Constants
(def DEFAULT_LIMIT 50)

;; Private helpers
(defn- -validate-content [content] ...)

;; Public API
(defn create-summary [...] ...)
(defn list-summaries [...] ...)
```

### 2. Avoid Redundant Code

**DRY principle:**
```clojure
;; Bad: Repeated validation
(defn create-summary [data]
  (when (nil? (:content data))
    (throw ...))
  (when (< (count (:content data)) 50)
    (throw ...))
  ...)

;; Good: Extract validation
(defn- -validate-content [content]
  (when (nil? content) (throw ...))
  (when (< (count content) 50) (throw ...))
  [:ok content])

(defn create-summary [data]
  (let [[status validated] (-validate-content (:content data))]
    ...))
```

### 3. Meaningful Names

**Descriptive over concise:**
```clojure
;; Bad
(defn proc [x] ...)
(defn get [id] ...)
(defn upd [id data] ...)

;; Good
(defn process-summary [summary] ...)
(defn get-summary-by-id [summary-id] ...)
(defn update-summary-content [summary-id content] ...)
```

## Editor Integration

### VSCode + Calva

Add to `.vscode/settings.json`:
```json
{
  "calva.lintOnSave": true,
  "calva.format.enable": true,
  "clojure-lsp.enable": true
}
```

### Emacs + CIDER

Add to `.dir-locals.el`:
```elisp
((clojure-mode . ((cider-clojure-cli-global-options . "-A:dev")
                  (cider-preferred-build-tool . clojure-cli)
                  (clojure-enable-linters . t))))
```

## Pre-commit Checks

**Recommended git hook:**
```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running clj-kondo..."
clj-kondo --lint src/ --fail-level error

if [ $? -ne 0 ]; then
  echo "clj-kondo found errors. Fix them before committing."
  exit 1
fi

echo "Linting passed!"
```

## Quick Reference

| Category | Convention | Example |
|----------|-----------|---------|
| Function | kebab-case | `parse-uuid` |
| Predicate | kebab-case + `?` | `valid-email?` |
| Private | `-` prefix | `-validate-content` |
| Constant | UPPER_SNAKE_CASE | `DEFAULT_LIMIT` |
| File | kebab-case | `summary-service.clj` |
| Test file | `_test` suffix | `summary_test.clj` |
| Indentation | 2 spaces | N/A |
| Threading | Use for >2 nesting | `->`, `->>` |
| Docstring | Above params | Multi-line |

## Resources

- **clj-kondo docs**: https://github.com/clj-kondo/clj-kondo
- **Clojure style guide**: https://guide.clojure.style/
- **Biff docs**: https://biffweb.com/docs/
