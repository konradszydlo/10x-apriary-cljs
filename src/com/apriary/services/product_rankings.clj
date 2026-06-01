(ns com.apriary.services.product-rankings
  "Calculate hive rankings by product type based on cumulative production."
  (:require
   [clojure.tools.logging :as log]
   [xtdb.api :as xt]))

(defn calculate-rankings
  "Calculate top/bottom N hive rankings per product type.

   Returns map of product types to ranking data:
   {:product-type {:top [...] :bottom [...]}}

   Each ranking entry: {:hive-number str :total-quantity int :metric str :count int}

   Args:
     db - XTDB database instance
     user-id - UUID of authenticated user
     n - Number of top/bottom hives to return (default 5)

   Returns:
     [:ok {:rankings {\"Honey\" {:top [...] :bottom [...]} ...}}]
     [:error {:code ... :message ...}]

   Performance Note:
     Aggregates ALL products for user without pagination. Designed for small apiary
     scale (5-50 hives, 100-500 product records). For larger datasets, consider
     adding pagination or caching in v2."
  [db user-id & {:keys [n] :or {n 5}}]
  (try
    ;; Guard clause
    (when (nil? user-id)
      (throw (IllegalArgumentException. "user-id is required")))
    (when (or (not (pos-int? n)) (> n 100))
      (throw (IllegalArgumentException. "n must be a positive integer ≤ 100")))

    (log/info "Calculating rankings"
              :user-id user-id
              :n n)

    ;; Query with aggregation: group by (hive, product, metric), sum quantity
    ;; CRITICAL: Grouping by metric prevents mixing units (e.g., kg + g)
    (let [query-params {:find '[?hive-number ?product-type ?metric (sum ?quantity) (count ?product-id)]
                        :in '[user-id]
                        :where [['?p :product/user-id 'user-id]
                                ['?p :product/id '?product-id]
                                ['?p :product/hive-number '?hive-number]
                                ['?p :product/product '?product-type]
                                ['?p :product/metric '?metric]
                                ['?p :product/quantity '?quantity]]}

          ;; Execute aggregation query
          agg-results (xt/q db query-params user-id)

          ;; Convert to maps: [hive product metric sum count] → {:hive-number ... :total-quantity ...}
          entries (mapv (fn [[hive product metric total cnt]]
                          {:hive-number hive
                           :product-type product
                           :metric metric
                           :total-quantity total
                           :count cnt})
                        agg-results)

          ;; Group by product type
          by-product (group-by :product-type entries)

          ;; For each product type, sort and take top N / bottom N
          rankings (reduce-kv
                    (fn [acc product entries-for-product]
                      (let [sorted (sort-by :total-quantity > entries-for-product)
                            actual-n (min n (count sorted))
                            top (take actual-n sorted)
                            bottom (take actual-n (reverse sorted))]
                        (assoc acc product {:top top :bottom bottom})))
                    {}
                    by-product)]

      (log/info "Rankings calculated"
                :user-id user-id
                :product-types (count rankings)
                :total-entries (count entries))

      [:ok {:rankings rankings}])

    (catch IllegalArgumentException e
      (log/error "Invalid arguments for calculate-rankings"
                 :user-id user-id
                 :error (.getMessage e))
      [:error {:code :invalid-arguments
               :message (.getMessage e)}])

    (catch Exception e
      (log/error "Failed to calculate rankings"
                 :user-id user-id
                 :error e)
      [:error {:code :calculation-failed
               :message "Unable to calculate rankings. Please try again."}])))
