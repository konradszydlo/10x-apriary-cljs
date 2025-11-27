(ns com.apriary.pages.generations
  (:require [com.apriary.util :as util]
            [com.apriary.services.generation :as gen-service]
            [com.apriary.dto.generation :as gen-dto]))

;; Step 5: GET /api/generations - List generations with filtering, sorting, pagination
(defn list-generations-handler
  [{:keys [session biff/db params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          sort-by-result (util/validate-sort-by (:sort_by params))
          sort-order-result (util/validate-sort-order (:sort_order params))
          limit-result (util/validate-limit (:limit params))
          offset-result (util/validate-offset (:offset params))
          model-filter (:model params)]

      (cond
        (= (first sort-by-result) :error)
        {:status 400 :body (second sort-by-result)}

        (= (first sort-order-result) :error)
        {:status 400 :body (second sort-order-result)}

        (= (first limit-result) :error)
        {:status 400 :body (second limit-result)}

        (= (first offset-result) :error)
        {:status 400 :body (second offset-result)}

        :else
        (let [sort-by (second sort-by-result)
              sort-order (second sort-order-result)
              limit (second limit-result)
              offset (second offset-result)
              [status result] (gen-service/list-user-generations
                               db user-id
                               :sort-by sort-by
                               :sort-order sort-order
                               :model model-filter
                               :limit limit
                               :offset offset)]

          (if (= status :ok)
            {:status 200
             :body (gen-dto/list->response
                    (:generations result)
                    (:total-count result)
                    limit
                    offset)}

            {:status 500 :body (second result)}))))))

;; Step 6: GET /api/generations/{id} - Get single generation by ID with RLS
(defn get-generation-handler
  [{:keys [session biff/db path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          generation-id-str (:id path-params)
          uuid-result (util/parse-uuid generation-id-str)]

      (if (= (first uuid-result) :error)
        {:status 400 :body (second uuid-result)}

        (let [generation-id (second uuid-result)
              [status result] (gen-service/get-generation-by-id
                               db generation-id user-id)]

          (if (= status :ok)
            {:status 200 :body (gen-dto/single->response result)}

            (case (:code result)
              "NOT_FOUND" {:status 404 :body result}
              "FORBIDDEN" {:status 403 :body result}
              "INVALID_INPUT" {:status 400 :body result}
              {:status 500 :body result})))))))

;; POST /api/generations/:id/accept-summaries - Bulk accept summaries for a generation
(defn bulk-accept-generation-handler
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401 :body (util/unauthorized-error)}

    (let [user-id (:uid session)
          generation-id-str (:id path-params)
          uuid-result (util/parse-uuid generation-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400 :body (second uuid-result)}

        ;; Happy path
        (let [generation-id (second uuid-result)
              [status result] (gen-service/bulk-accept-summaries-for-generation
                               node generation-id user-id)]

          (if (= status :ok)
            (let [{:keys [generation unedited-count edited-count
                          manual-count total-summaries]} result]
              {:status 200
               :body (gen-dto/bulk-accept->response
                      generation unedited-count edited-count
                      manual-count total-summaries)})

            ;; Error handling
            (case (:code result)
              "NOT_FOUND" {:status 404 :body result}
              "FORBIDDEN" {:status 403 :body result}
              "DATA_INTEGRITY_ERROR" {:status 400 :body result}
              "INVALID_INPUT" {:status 400 :body result}
              {:status 500 :body result})))))))

;; Route definitions
(def module
  {:api-routes ["/api/generations"
                {:get list-generations-handler}
                ["/:id" {:get get-generation-handler}
                 ["/accept-summaries" {:post bulk-accept-generation-handler}]]]})
