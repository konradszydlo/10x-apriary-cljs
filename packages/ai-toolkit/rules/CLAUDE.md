# Team Coding Conventions

This section contains Clojure/Biff-specific coding conventions for the team.

## Naming Conventions

- **Functions and variables**: Use kebab-case (`parse-uuid`, `list-summaries`)
- **Predicates**: End with `?` (`valid-email?`, `authenticated?`)
- **Private functions**: Prefix with `-` (`-validate-content`, `-build-query`)
- **Constants**: Use UPPER_SNAKE_CASE (`DEFAULT_LIMIT`, `SORT_BY_WHITELIST`)
- **Files**: Use kebab-case; test files use `_test` suffix (`summary.clj`, `summary_test.clj`)
- **Namespaces**: Mirror directory structure exactly

## Error Handling

Two legitimate error-handling patterns coexist:

**Pattern A: Error tuples** (services layer)
```clojure
;; Return [:ok value] on success, [:error {:code :message}] on failure
(defn parse-uuid [uuid-str]
  (try
    [:ok (java.util.UUID/fromString uuid-str)]
    (catch IllegalArgumentException _
      [:error {:code "INVALID_UUID" :message "Invalid UUID format"}])))
```

**Pattern B: Exceptions** (domain operations)
```clojure
;; Throw exceptions with descriptive messages
(when (nil? content)
  (throw (IllegalArgumentException. "content is required")))
```

Error tuples document their error codes (always strings, not keywords):
- `"INVALID_UUID"` - UUID format is invalid
- `"NOT_FOUND"` - Resource doesn't exist or RLS violation
- `"VALIDATION_ERROR"` - Input validation failed
- `"FORBIDDEN"` - Authorization failure
- `"INTERNAL_ERROR"` - Unexpected system error

Format: `[:error {:code "ERROR_CODE" :message "description"}]`

## Guard Clauses and Flow Control

- **Early returns**: Handle error conditions first
- **Happy path last**: Place successful execution at the end
- **Guard clauses**: Check preconditions at function start
- **Avoid unnecessary else**: Use `when` instead of `if` when no else clause needed

```clojure
(defn process [data user-id]
  ;; Guard clauses first
  (when (nil? data) (throw ...))
  (when (nil? user-id) (throw ...))
  
  ;; Business logic
  (let [result (transform data)]
    ;; Happy path last
    [:ok result]))
```

## Malli Validation

- Define all entity schemas in `schema.clj` using Malli
- Use closed maps: `[:map {:closed true} ...]`
- Validate at boundaries: API handlers, database writes
- Always validate data against Malli schemas before writing to XTDB
- Use `[:xtdb.api/put entity]` for creates/updates and `[:xtdb.api/delete id]` for deletes
- Type predicates: `inst?`, `:uuid`, `:string`, `:int`, `[:enum :a :b]`
- Optional fields: `[:field {:optional true} [:maybe :type]]`

```clojure
(def summary-schema
  [:map {:closed true}
   [:xt/id :uuid]
   [:summary/user-id :uuid]
   [:summary/content :string]
   [:summary/source [:enum :ai-full :ai-partial :manual]]])
```

## Row-Level Security (RLS)

**Critical security pattern - ALWAYS enforce:**

1. **All queries filter by user-id**:
```clojure
(let [query '{:find [?s]
              :in [user-id]
              :where [[?s :summary/user-id user-id]
                      [?s :summary/content ?content]]}]
  (xt/q db query user-id))
```

2. **Verify ownership after entity fetch**:
```clojure
(let [entity (xt/entity db summary-id)]
  (when (not= (:summary/user-id entity) user-id)
    ;; Return 404, not 403 - prevents resource enumeration
    [:error {:code "NOT_FOUND" :message "..."}]))
```

3. **Never leak resource existence**: Return 404 for unauthorized access, never 403
4. **No user data in errors**: Error messages must not reveal other users' data

## Biff Framework Patterns

**Module structure**:
```clojure
(def module
  {:routes ["/path" {:middleware [mid/wrap-signed-in]}
            ["" {:get handler}]]
   :api-routes ["/api/path" {:middleware [mid/wrap-api-auth]}
                ["" {:get api-handler}]]})
```

**303 redirects after POST**:
```clojure
(defn create-handler [ctx]
  (let [[status result] (service/create ...)]
    {:status 303
     :headers {"Location" "/success-page"}}))
```

**Transaction context**:
- Handlers: Use `biff/submit-tx ctx [...]`
- Services: Use `xt/submit-tx node [...]`

## XTDB Patterns

- Primary identifier: `:xt/id`
- Entity type: `:db/doc-type :entity-type`
- Datalog structure: `{:find [...] :in [...] :where [...]}`
- Operations: `[:xtdb.api/put entity]`, `[:xtdb.api/delete id]`

```clojure
(xt/q db
      '{:find [?entity ?field]
        :in [user-id]
        :where [[?entity :entity/user-id user-id]
                [?entity :entity/field ?field]]}
      current-user-id)
```

## Testing Conventions

- Test namespaces: Mirror source with `_test` suffix (`summary_test.clj`)
- Result tuples: Destructure with `(let [[status result] (func ...)])`
- Macros: Use `deftest`, `testing`, `is` from `clojure.test`
- Isolation: Each test has independent setup/teardown
- Coverage: Test both success paths and error conditions

```clojure
(deftest parse-uuid-test
  (testing "valid UUID"
    (let [[status result] (parse-uuid "123e4567-e89b-12d3-a456-426614174000")]
      (is (= :ok status))
      (is (instance? java.util.UUID result))))
  
  (testing "invalid UUID"
    (let [[status error] (parse-uuid "invalid")]
      (is (= :error status))
      (is (= "INVALID_UUID" (:code error))))))
```

## Security Practices

- **Passwords**: Hash with jBCrypt before storage (`:user/password-hash`)
- **Tokens**: Generate with `java.security.SecureRandom`
- **No secrets in code**: Use environment variables
- **Prevent enumeration**: Return 404 for both "not found" and "unauthorized"
- **Validate inputs**: Use Malli at API boundaries
- **No stack traces**: Don't leak internal errors in API responses

## Linting

Run clj-kondo before commit:
```bash
clj-kondo --lint src/ --config .clj-kondo/config.edn
```

Critical errors (must fix):
- `:redefined-var` - Variable defined multiple times
- `:invalid-arity` - Function called with wrong argument count

Warnings (should address):
- `:unused-namespace`, `:unused-binding`, `:unused-private-var`
- `:misplaced-docstring`, `:deprecated-var`
