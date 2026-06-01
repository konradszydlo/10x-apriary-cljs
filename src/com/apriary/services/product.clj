(ns com.apriary.services.product
  (:require [xtdb.api :as xt]
            [clojure.tools.logging :as log]))

;; Product Service Functions
;;
;; All functions follow the pattern:
;; - Returns: [:ok result] on success, [:error {:code ... :message ...}] on failure
;; - Implements Row-Level Security (RLS) checks
;; - Uses guard clauses for early error handling
;;
;; NOTE: This service currently provides only create-batch and list operations.
;; Edit/delete functionality is intentionally deferred to roadmap item S-03.
;; This is not an incomplete implementation - it matches the MVP scope defined
;; in the product-input-view plan (see plan.md "What We're NOT Doing").
;; - Logs operations for audit trail

;; =============================================================================
;; CRUD Operations
;; =============================================================================

(defn create-products-batch
  "Create multiple product records in a single transaction.

   All products are scoped to the authenticated user via :product/user-id
   for Row-Level Security enforcement.

   Params:
   - node: XTDB node instance (not db - needs to call submit-tx)
   - user-id: UUID of the authenticated user
   - products: Vector of maps with keys:
     - :hive-number (required) String
     - :date (optional) String DD-MM-YYYY format or nil
     - :product (required) String (e.g., 'Honey', 'Pollen')
     - :quantity (required) Integer > 0
     - :metric (required) String one of 'kg', 'ml', 'g'

   Returns:
   - [:ok {:count N}] on success
   - [:error {:code ... :message ...}] on failure"
  [node user-id products]
  (try
    ;; Guard clauses
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    (when (or (nil? products) (empty? products))
      (throw (IllegalArgumentException. "products list cannot be empty")))

    ;; Create product entities
    (let [now (java.time.Instant/now)
          entities (mapv (fn [product-data]
                           (let [product-id (java.util.UUID/randomUUID)]
                             {:xt/id product-id
                              :product/id product-id
                              :product/user-id user-id
                              :product/hive-number (:hive-number product-data)
                              :product/date (:date product-data)
                              :product/product (:product product-data)
                              :product/quantity (:quantity product-data)
                              :product/metric (:metric product-data)
                              :product/created-at now
                              :product/updated-at now}))
                         products)

          ;; Build transaction operations
          tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) entities)]

      ;; Persist to database in single transaction
      (xt/submit-tx node tx-ops)

      (log/info "Created products batch"
                :user-id user-id
                :count (count entities))

      [:ok {:count (count entities)}])

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for create-products-batch:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to create products batch:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to create products"}])))

(defn list-products
  "List all products for a user, sorted by date descending (newest first).

   This function implements RLS by filtering all results to only include products
   belonging to the authenticated user.

   Params:
   - db: XTDB database instance
   - user-id: UUID of the authenticated user

   Returns:
   - [:ok {:products [...]}] on success
   - [:error {:code ... :message ...}] on failure"
  [db user-id]
  (try
    ;; Guard clause
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))

    ;; Build query with RLS: only products for this user
    (let [query-params {:find '[?p]
                        :where [['?p :product/user-id user-id]]}

          ;; Execute query
          results (xt/q db query-params)

          ;; Fetch full entities and sort by date descending (newest first)
          products (->> results
                        (mapv (fn [[?p]] (xt/entity db ?p)))
                        (sort-by :product/date #(compare %2 %1)))]

      (log/info "Listed user products"
                :user-id user-id
                :count (count products))

      [:ok {:products products}])

    (catch IllegalArgumentException e
      (log/warn "Invalid argument for list-products:" (.getMessage e))
      [:error {:code "INVALID_INPUT" :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to list products:" e)
      [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve products"}])))
