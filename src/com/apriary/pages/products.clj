(ns com.apriary.pages.products
  "Products page handlers for CSV import and product listing."
  (:require [com.apriary.middleware :as mid]
            [com.apriary.ui.layout :as layout]
            [com.apriary.ui.products :as products-ui]
            [com.apriary.services.csv-import :as csv-import]
            [com.apriary.services.product-csv :as product-csv]
            [com.apriary.services.product :as product-service]
            [clojure.tools.logging :as log]
            [rum.core :as rum]
            [xtdb.api :as xt]))

;; =============================================================================
;; Products Page Handler
;; =============================================================================

(defn products-page-handler
  "Render products page with CSV form and table.

  GET /products

  Returns:
    Ring response with HTML body"
  [{:keys [session biff/db] :as ctx}]
  (let [user-id (:uid session)
        [status result] (product-service/list-products db user-id)
        products (:products result [])]
    (layout/app-page
     ctx
     {:page-title "Production Tracking"}
     [:div.max-w-6xl.mx-auto.p-6
      [:h1.text-2xl.font-bold.mb-6 "Production Tracking"]
      [:div#toast-container]
      (products-ui/csv-form)
      (products-ui/rejected-rows-component [])
      (products-ui/products-table products)])))

;; =============================================================================
;; Products Import Handler
;; =============================================================================

(defn import-products-handler
  "Handle CSV import of products. Validates, stores, returns htmx response.

  POST /api/products-import

  Params:
    csv - CSV string from form

  Returns:
    Ring response with htmx fragments (table + OOB swaps)"
  [{:keys [session biff.xtdb/node params] :as ctx}]
  (let [user-id (:uid session)
        csv-input (:csv params)]

    (log/info "Products CSV import request" :user-id user-id)

    (if (or (nil? csv-input) (empty? csv-input))
      ;; Missing CSV input
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              [:div
               (products-ui/rejected-rows-oob
                [{:row-number 0 :reason "CSV input is required"}])])}

      ;; Parse CSV
      (let [[parse-status parse-result] (csv-import/parse-csv-string csv-input)]
        (if (= parse-status :error)
          ;; CSV parsing failed
          (do
            (log/warn "CSV parse error" :error parse-result)
            {:status 400
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    [:div
                     (products-ui/rejected-rows-oob
                      [{:row-number 0 :reason (:message parse-result "Invalid CSV format")}])])})

          ;; Validate product rows
          (let [[validate-status validate-result] (product-csv/process-product-csv parse-result)]
            (if (= validate-status :error)
              ;; Validation failed (e.g., missing required columns)
              (do
                (log/warn "Product CSV validation error" :error validate-result)
                {:status 400
                 :headers {"content-type" "text/html"}
                 :body (rum/render-static-markup
                        [:div
                         (products-ui/rejected-rows-oob
                          [{:row-number 0 :reason (:message validate-result "Invalid CSV structure")}])])})

              ;; Store valid products
              (let [valid-rows (:valid-rows validate-result)
                    rejected-rows (:rejected-rows validate-result)
                    [store-status store-result] (if (seq valid-rows)
                                                   (product-service/create-products-batch node user-id valid-rows)
                                                   [:ok {:count 0}])]

                (if (= store-status :error)
                  ;; Database error
                  (do
                    (log/error "Failed to store products" :error store-result)
                    {:status 500
                     :headers {"content-type" "text/html"}
                     :body (rum/render-static-markup
                            [:div
                             (products-ui/rejected-rows-oob
                              [{:row-number 0 :reason "Failed to save products to database"}])])})

                  ;; Success - fetch updated products and return htmx response
                  (let [_ (xt/sync node)
                        db (xt/db node)
                        [list-status list-result] (product-service/list-products db user-id)
                        products (:products list-result [])]

                    (log/info "Products imported successfully"
                              :user-id user-id
                              :valid-count (:count store-result)
                              :rejected-count (count rejected-rows))

                    {:status 200
                     :headers {"content-type" "text/html"}
                     :body (rum/render-static-markup
                            [:div
                             ;; Main content: refreshed products table
                             (products-ui/products-table products)
                             ;; OOB swap: success toast (only if some valid rows)
                             (when (> (:count store-result) 0)
                               (products-ui/success-toast-oob (:count store-result)))
                             ;; OOB swap: rejected rows (if any)
                             (products-ui/rejected-rows-oob rejected-rows)
                             ;; OOB swap: clear form
                             (products-ui/clear-form-oob)])}))))))))))

;; =============================================================================
;; Module Definition
;; =============================================================================

(def module
  {:routes [["/products" {:middleware [mid/wrap-signed-in]}
             ["" {:get products-page-handler}]]]
   :api-routes [["/api/products-import" {:middleware [mid/wrap-signed-in]
                                          :post import-products-handler}]]})
