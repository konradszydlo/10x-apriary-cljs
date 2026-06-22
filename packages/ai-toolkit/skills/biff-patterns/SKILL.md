---
name: biff-patterns
description: Document Biff framework patterns used in this project. Use when implementing new Biff modules, debugging middleware, or understanding XTDB queries.
allowed-tools:
  - Read
  - Grep
  - Bash
---

# Biff Patterns Reference

Reference guide for Biff v1.9.0 framework patterns used in the Apriary project.

## When to Use

Invoke this skill when:
- Implementing new Biff modules
- Debugging middleware issues
- Understanding routing patterns
- Working with XTDB in Biff context
- Creating new pages or handlers

## Pattern 1: Module Structure

**The standard Biff module pattern** (from `src/com/apriary/pages/app.clj:8-10`):

```clojure
(def module
  {:routes ["/path" {:middleware [mid/wrap-signed-in]}
            ["" {:get handler-fn
                 :post post-handler-fn}]]})
```

### Core Structure

A module is a map with:
- `:routes` - Web routes (HTML pages)
- `:api-routes` - API endpoints (JSON responses)
- Optional middleware at route group level

### Module Variations

**Variation 1: Routes + API routes**
```clojure
(def module
  {:routes ["/summaries" {:middleware [mid/wrap-signed-in]}
            ["" {:get list-page}]
            ["/:id" {:get detail-page}]]
   :api-routes ["/api/summaries" {:middleware [mid/wrap-api-auth]}
                ["" {:get list-api
                     :post create-api}]
                ["/:id" {:get get-api
                         :put update-api
                         :delete delete-api}]]})
```

**Variation 2: Schema-only module** (from `schema.clj:62`)
```clojure
;; No routes - just schema registration
(def module
  {:schema schema-definitions})
```

**Variation 3: Empty module with delegated routes** (from `summaries.clj:387`)
```clojure
;; Routes defined in views, module just provides namespace hook
(def module {})
```

**Variation 4: API-only module** (from `generations.clj:119`)
```clojure
;; No web routes - API endpoints only
(def module
  {:api-routes ["/api/generations" {:middleware [mid/wrap-api-auth]}
                ["" {:get list-generations-api}]]})
```

### Middleware Application

Middleware applies at the route group level:

```clojure
{:routes ["/protected" {:middleware [mid/wrap-signed-in
                                      mid/wrap-csrf]}
          ["" {:get protected-handler}]]}
```

## Pattern 2: Row-Level Security (RLS)

**All user-owned data queries MUST filter by `:user-id`** (from `src/com/apriary/services/summary.clj:88-117`):

### Query-time RLS

```clojure
(defn list-summaries [db user-id opts]
  (let [query '{:find [?s]
                :in [user-id]
                :where [[?s :summary/user-id user-id]
                        [?s :summary/content ?content]]}]
    (xt/q db query user-id)))
```

### Entity-fetch RLS

After `xt/entity`, verify ownership (from `summary.clj:155-171`):

```clojure
(defn get-summary-by-id [db summary-id user-id]
  (let [entity (xt/entity db summary-id)]
    (cond
      (nil? entity)
      [:error {:code "NOT_FOUND" :message "..."}]
      
      ;; RLS check
      (not= (:summary/user-id entity) user-id)
      ;; Return 404, not 403 - prevents enumeration
      [:error {:code "NOT_FOUND" :message "..."}]
      
      :else
      [:ok entity])))
```

### Key RLS Rules

1. **Filter queries**: Always include `[?entity :entity/user-id user-id]` in `:where`
2. **Check ownership**: After entity fetch, verify `(:entity/user-id entity) = user-id`
3. **404 not 403**: Return NOT_FOUND for unauthorized access (prevents resource enumeration)
4. **No data leakage**: Error messages must not reveal existence of resources

## Pattern 3: Transaction Handling

**Two contexts for transactions**:

### Handler Context: `biff/submit-tx`

From `src/com/apriary/auth.clj:82-92`:

```clojure
(defn signup-handler [{:keys [biff/db params] :as ctx}]
  ;; In handler - use biff/submit-tx
  (biff/submit-tx ctx
    [{:db/doc-type :user
      :user/email email
      :user/password-hash (hash-password password)}])
  
  {:status 303
   :headers {"Location" "/app"}})
```

### Service Context: `xt/submit-tx`

Services don't have full `ctx`, use node directly:

```clojure
(defn create-manual-summary [node user-id summary-data]
  (let [entity {:xt/id (java.util.UUID/randomUUID)
                :summary/user-id user-id
                :summary/content content}]
    ;; In service - use xt/submit-tx with node
    (xt/submit-tx node [[:xtdb.api/put entity]])
    [:ok entity]))
```

### Transaction Patterns

**Multiple operations in one transaction:**
```clojure
(xt/submit-tx node
  [[:xtdb.api/put entity-1]
   [:xtdb.api/put entity-2]
   [:xtdb.api/delete old-entity-id]])
```

**Entity updates:**
```clojure
(let [existing (xt/entity db id)
      updated (assoc existing 
                     :field/new-value value
                     :entity/updated-at (java.time.Instant/now))]
  (xt/submit-tx node [[:xtdb.api/put updated]]))
```

## Pattern 4: Middleware Stacks

From `src/com/apriary/middleware.clj:34-64`:

### Three Default Stacks

**Base middleware** (minimal):
```clojure
(def base-defaults
  [mid/wrap-anti-forgery-websockets
   mid/wrap-session-csrf-token])
```

**Site middleware** (web pages):
```clojure
(def site-defaults
  (concat base-defaults
          [mid/wrap-signed-in-redirect
           mid/wrap-ensure-authenticated]))
```

**API middleware** (JSON endpoints):
```clojure
(def api-defaults
  (concat base-defaults
          [mid/wrap-api-auth
           mid/wrap-json-body
           mid/wrap-json-response]))
```

### Custom Middleware Composition

```clojure
;; Apply site middleware to route group
{:routes ["/admin" {:middleware site-defaults}
          ["" {:get admin-dashboard}]]}

;; Override with custom stack
{:api-routes ["/api/public" {:middleware [mid/wrap-json-response]}
              ["" {:get public-endpoint}]]}
```

## Pattern 5: Malli Schemas

From `src/com/apriary/schema.clj:5-37`:

### Entity Schema Pattern

```clojure
(def summary-schema
  [:map {:closed true}
   [:xt/id :uuid]
   [:summary/id :uuid]
   [:summary/user-id :uuid]
   [:summary/content :string]
   [:summary/source [:enum :ai-full :ai-partial :manual]]
   [:summary/created-at inst?]
   [:summary/updated-at inst?]
   ;; Optional fields
   [:summary/hive-number {:optional true} [:maybe :string]]
   [:summary/observation-date {:optional true} [:maybe :string]]])
```

### Key Schema Rules

1. **Closed maps**: `{:closed true}` prevents extra keys
2. **Type predicates**: Use `inst?`, `:uuid`, `:string`, `:int`
3. **Enums**: `[:enum :value1 :value2]` for fixed options
4. **Optional fields**: `{:optional true}` with `[:maybe type]`
5. **Doc type**: Include `:db/doc-type` for entity discrimination

### Validation at Boundaries

```clojure
(require '[malli.core :as m])

(defn validate-summary [data]
  (if (m/validate summary-schema data)
    [:ok data]
    [:error {:code "VALIDATION_ERROR" 
             :message (m/explain summary-schema data)}]))
```

## Pattern 6: Hiccup Pages

From `src/com/apriary/pages/home.clj:9-41`:

### Page Function Structure

```clojure
(defn home-page [ctx]
  ;; Returns Hiccup vector
  [:html
   [:head
    [:title "Apriary"]
    [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]]
   [:body
    [:div.container.mx-auto.px-4
     [:h1.text-3xl.font-bold "Welcome"]
     (biff/form {:action "/summaries" :method "post"}
       [:input.border.px-4.py-2 {:type "text" :name "content"}]
       [:button.bg-blue-500.text-white.px-4.py-2 
        {:type "submit"} 
        "Create"])]]])
```

### Biff Helpers

**Forms with CSRF:**
```clojure
(biff/form {:action "/endpoint" :method "post"}
  ;; CSRF token auto-injected
  [:input {:type "text" :name "field"}]
  [:button {:type "submit"} "Submit"])
```

**Conditional rendering:**
```clojure
(when (:session/user-id ctx)
  [:div "Authenticated content"])

(if (empty? summaries)
  [:p "No summaries yet"]
  [:ul (for [s summaries]
         [:li (:summary/content s)])])
```

### Tailwind Classes

Apply Tailwind utility classes directly:

```clojure
[:div.flex.flex-col.gap-4.p-6
 [:h2.text-2xl.font-semibold.text-gray-800 "Title"]
 [:button.bg-green-500.hover:bg-green-600.text-white.rounded.px-4.py-2
  "Action"]]
```

## Common Patterns Summary

| Task | Pattern | File Reference |
|------|---------|----------------|
| Create module | `{:routes [...] :api-routes [...]}` | `app.clj:8-10` |
| Query with RLS | Filter by `:user-id` in `:where` | `summary.clj:88-117` |
| Verify ownership | Check after `xt/entity` | `summary.clj:155-171` |
| Submit transaction (handler) | `biff/submit-tx ctx [...]` | `auth.clj:82-92` |
| Submit transaction (service) | `xt/submit-tx node [...]` | `summary.clj:236` |
| Define schema | `[:map {:closed true} ...]` | `schema.clj:5-37` |
| Build page | Return Hiccup vector | `home.clj:9-41` |
| Apply middleware | Route group `:middleware [...]` | `middleware.clj:34-64` |

## Debugging Tips

**Check middleware order:**
```bash
# Middleware runs top-to-bottom in stack
# Auth should come before content handlers
```

**Verify RLS:**
```bash
# Search for queries without user-id filter
grep -r ":where" src/ | grep -v "user-id"
```

**Test transaction:**
```clojure
;; In REPL
(xt/submit-tx node [[:xtdb.api/put test-entity]])
(xt/entity (xt/db node) (:xt/id test-entity))
```
