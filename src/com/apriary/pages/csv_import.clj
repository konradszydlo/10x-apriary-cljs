(ns com.apriary.pages.csv-import
  "UI page handlers for CSV import functionality.

  This demonstrates integration with the new UI components:
  - Application header (from layout)
  - Toast notifications (for success/error feedback)
  - Error messages (for validation failures)
  - Flash messages (for post-redirect feedback)"
  (:require [com.apriary.middleware :as mid]
            [com.apriary.ui.layout :as layout]
            [com.apriary.ui.helpers :as ui-helpers]
            [com.apriary.services.csv-import :as csv-service]
            [com.apriary.services.openrouter :as openrouter-service]
            [com.apriary.services.generation :as gen-service]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]))

(defn csv-import-form
  "Renders the CSV import form page.

  Args:
    ctx - Biff context map

  Returns:
    Ring response with HTML body"
  [ctx]
  (layout/app-page
   ctx
   {:page-title "Import CSV"}
   [:div.py-6
    [:h1.text-2xl.font-bold.text-gray-900 "Import CSV Data"]
    [:p.mt-2.text-gray-600
     "Upload CSV data to generate AI summaries for your hives. "
     "The CSV should contain columns for hive number, observation date, and special features."]

    [:div.mt-8.max-w-2xl
     [:form.bg-white.shadow-sm.rounded-lg.p-6
      {:hx-post "/csv-import"
       :hx-target "#import-results"
       :hx-swap "innerHTML"}

      [:div
       [:label.block.text-sm.font-medium.text-gray-700 {:for "csv-data"}
        "CSV Data"]
       [:textarea#csv-data.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
        {:name "csv"
         :rows "10"
         :placeholder "hive_number,observation_date,special_feature\n1,2025-01-15,Strong colony with active queen\n2,2025-01-15,Needs more food"
         :required true}]]

      [:div.mt-6
       [:button.w-full.inline-flex.justify-center.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2.focus-visible:outline-blue-600
        {:type "submit"}
        "Import and Generate Summaries"]]

      [:div#import-results.mt-6]]]]))

(defn csv-import-handler
  "POST /csv-import - Import CSV and generate summaries with UI feedback.

  This handler demonstrates proper integration with the UI component system:
  - Success: Redirect with flash toast
  - Validation errors: Show error message area
  - Partial success: Show info toast with details"
  [{:keys [session biff.xtdb/node params] :as ctx}]
  (let [user-id (:uid session)
        csv-string (:csv params)]

    ;; Guard clause: CSV data required
    (if-not csv-string
      {:status 400
       :body (layout/app-page
              ctx
              {:page-title "Import CSV"
               :error-message {:error "CSV data is required"
                               :code "VALIDATION_ERROR"
                               :heading "Validation Error"
                               :timestamp (str (java.time.Instant/now))}}
              (csv-import-form ctx))}

      ;; Step 1: Parse and validate CSV
      (let [[csv-status csv-result] (csv-service/process-csv-import csv-string)]

        ;; Guard clause: CSV processing failed
        (if (= csv-status :error)
          {:status 400
           :body (layout/app-page
                  ctx
                  {:page-title "Import CSV"
                   :error-message (assoc csv-result
                                         :timestamp (str (java.time.Instant/now))
                                         :heading "CSV Validation Error")}
                  (csv-import-form ctx))}

          (let [{:keys [valid-rows rejected-rows rows-submitted
                        rows-valid rows-rejected]} csv-result]

            ;; Guard clause: no valid rows
            (if (zero? rows-valid)
              {:status 400
               :body (layout/app-page
                      ctx
                      {:page-title "Import CSV"
                       :error-message {:error "All CSV rows failed validation"
                                       :code "VALIDATION_ERROR"
                                       :heading "Validation Error"
                                       :details {:rows-submitted rows-submitted
                                                 :rows-rejected rows-rejected
                                                 :rejected-rows rejected-rows}
                                       :timestamp (str (java.time.Instant/now))}}
                      (csv-import-form ctx))}

              ;; Step 2: Generate AI summaries
              (let [[ai-status ai-result] (openrouter-service/generate-summaries-batch
                                           valid-rows)]

                ;; Guard clause: AI generation failed
                (if (= ai-status :error)
                  {:status 500
                   :body (layout/app-page
                          ctx
                          {:page-title "Import CSV"
                           :error-message (assoc ai-result
                                                 :timestamp (str (java.time.Instant/now))
                                                 :heading "AI Generation Error")}
                          (csv-import-form ctx))}

                  (let [{:keys [summaries model duration-ms]} ai-result

                        ;; Step 3: Create Generation record
                        [gen-status gen-result] (gen-service/create-generation
                                                 node user-id model
                                                 rows-valid duration-ms)]

                    ;; Guard clause: generation creation failed
                    (if (= gen-status :error)
                      {:status 500
                       :body (layout/app-page
                              ctx
                              {:page-title "Import CSV"
                               :error-message (assoc gen-result
                                                     :timestamp (str (java.time.Instant/now))
                                                     :heading "Database Error")}
                              (csv-import-form ctx))}

                      (let [generation gen-result
                            generation-id (:generation/id generation)
                            now (java.time.Instant/now)

                            ;; Step 4: Create Summary records
                            summary-entities (mapv (fn [summary]
                                                     (let [summary-id (java.util.UUID/randomUUID)]
                                                       {:xt/id summary-id
                                                        :summary/id summary-id
                                                        :summary/user-id user-id
                                                        :summary/generation-id generation-id
                                                        :summary/source :ai-full
                                                        :summary/hive-number (:hive-number summary)
                                                        :summary/observation-date (:observation-date summary)
                                                        :summary/special-feature (:special-feature summary)
                                                        :summary/content (:content summary)
                                                        :summary/created-at now
                                                        :summary/updated-at now}))
                                                   summaries)

                            tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)
                            _ (xt/submit-tx node tx-ops)]

                        (log/info "CSV import completed"
                                  :user-id user-id
                                  :generation-id generation-id
                                  :rows-submitted rows-submitted
                                  :rows-valid rows-valid
                                  :rows-rejected rows-rejected
                                  :summaries-created (count summary-entities))

                        ;; Success: Redirect with appropriate flash message
                        (if (pos? rows-rejected)
                          ;; Partial success - some rows rejected
                          {:status 303
                           :headers {"Location" "/app"}
                           :session (assoc session :flash
                                           (ui-helpers/info-flash
                                            (str (count summary-entities)
                                                 " summaries created. "
                                                 rows-rejected
                                                 " rows rejected due to validation errors.")))}
                          ;; Complete success
                          {:status 303
                           :headers {"Location" "/app"}
                           :session (assoc session :flash
                                           (ui-helpers/success-flash
                                            (str (count summary-entities)
                                                 " summaries generated successfully!")))})))))))))))))

(def module
  {:routes ["/csv-import" {:middleware [mid/wrap-signed-in]}
            ["" {:get csv-import-form
                 :post csv-import-handler}]]})
