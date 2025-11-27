(ns com.apriary.pages.summaries-view
  "UI page handlers for viewing and managing summaries.

  Demonstrates integration patterns:
  - Full page loads with flash messages
  - HTMX partial updates with toast notifications
  - Error handling with error message area
  - Optimistic UI updates with rollback on error"
  (:require [com.apriary.middleware :as mid]
            [com.apriary.ui.layout :as layout]
            [com.apriary.ui.helpers :as ui-helpers]
            [com.apriary.ui.csv-import :as csv-import]
            [com.apriary.ui.summary-card :as summary-card]
            [com.apriary.services.summary :as summary-service]
            [com.apriary.services.csv-import :as csv-service]
            [com.apriary.services.openrouter :as openrouter-service]
            [com.apriary.services.generation :as gen-service]
            [com.apriary.dto.summary :as summary-dto]
            [com.apriary.util :as util]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(defn summary-card
  "Renders a single summary card.

  Args:
    summary - Summary entity map

  Returns:
    Hiccup vector for summary card"
  [summary]
  (let [summary-id (str (:summary/id summary))
        source (:summary/source summary)
        source-label (case source
                       :ai-full "AI Generated"
                       :ai-partial "AI Assisted"
                       :manual "Manual"
                       "Unknown")]
    [:div.bg-white.shadow.rounded-lg.p-6.mb-4
     {:id (str "summary-" summary-id)}

     ;; Header
     [:div.flex.items-start.justify-between
      [:div.flex-1
       [:h3.text-lg.font-semibold.text-gray-900
        "Hive #" (:summary/hive-number summary)]
       [:p.text-sm.text-gray-500
        "Date: " (str (:summary/observation-date summary))]
       [:span.inline-flex.items-center.px-2.py-1.rounded.text-xs.font-medium.mt-2
        {:class (case source
                  :ai-full "bg-green-100 text-green-800"
                  :ai-partial "bg-blue-100 text-blue-800"
                  :manual "bg-gray-100 text-gray-800"
                  "bg-gray-100 text-gray-800")}
        source-label]]

      ;; Actions
      [:div.flex.gap-2
       [:button.text-blue-600.hover:text-blue-800.text-sm
        {:hx-get (str "/summaries/" summary-id "/edit")
         :hx-target (str "#summary-" summary-id)
         :hx-swap "outerHTML"}
        "Edit"]
       [:button.text-red-600.hover:text-red-800.text-sm
        {:hx-delete (str "/summaries/" summary-id)
         :hx-confirm "Are you sure you want to delete this summary?"
         :hx-target (str "#summary-" summary-id)
         :hx-swap "outerHTML"}
        "Delete"]]]

     ;; Content
     [:div.mt-4
      (when (:summary/special-feature summary)
        [:p.text-sm.text-gray-600.mb-2
         [:span.font-medium "Special Feature: "]
         (:summary/special-feature summary)])
      [:p.text-gray-700.whitespace-pre-wrap
       (:summary/content summary)]]]))

(defn summaries-list-page
  "GET /summaries - Display list of all summaries.

  Args:
    ctx - Biff context map

  Returns:
    Ring response with HTML body"
  [{:keys [session biff/db params] :as ctx}]
  (let [user-id (:uid session)
        sort-by (or (:sort-by params) "created-at")
        sort-order (or (:sort-order params) "desc")
        [status result] (summary-service/list-summaries
                         db user-id
                         :sort-by sort-by
                         :sort-order sort-order
                         :limit 100
                         :offset 0)]

    (if (= status :ok)
      (layout/app-page
       ctx
       {:page-title "Summaries"}
       [:div.py-6
        [:div.flex.items-center.justify-between
         [:h1.text-2xl.font-bold.text-gray-900 "Summaries"]
         [:div.flex.gap-2
          [:a.inline-flex.items-center.px-3.py-2.border.border-gray-300.text-sm.font-medium.rounded-md.text-gray-700.bg-white.hover:bg-gray-50
           {:href "/csv-import"}
           "Import CSV"]
          [:a.inline-flex.items-center.px-3.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700
           {:href "/summaries/new"}
           "+ New Summary"]]]

        ;; CSV Import Section
        (csv-import/csv-import-section)

        ;; Summaries List
        [:div#summaries-list.mt-6
         (if (empty? (:summaries result))
           [:div.text-center.py-12
            [:p.text-gray-500 "No summaries yet. Import a CSV or create your first summary to get started!"]]
           (map summary-card (:summaries result)))]])

      ;; Error case
      (layout/app-page
       ctx
       {:page-title "Summaries"
        :error-message (assoc result
                              :timestamp (str (java.time.Instant/now))
                              :heading "Failed to Load Summaries")}
       [:div.py-6
        [:h1.text-2xl.font-bold.text-gray-900 "Summaries"]]))))

(defn delete-summary-handler
  "DELETE /summaries/{id} - Delete summary with toast notification.

  This demonstrates HTMX partial update with toast feedback.

  Args:
    ctx - Biff context map with path-params

  Returns:
    Ring response with empty body and toast OOB swap"
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  (let [user-id (:uid session)
        summary-id-str (:id path-params)
        uuid-result (util/parse-uuid summary-id-str)]

    ;; Guard clause: invalid UUID
    (if (= (first uuid-result) :error)
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              (ui-helpers/error-toast-oob "Invalid summary ID"))}

      (let [summary-id (second uuid-result)
            [status result] (summary-service/delete-summary
                             node summary-id user-id)]

        (if (= status :ok)
          ;; Success: Return empty content (element already removed) + success toast
          {:status 200
           :headers {"content-type" "text/html"}
           :body (rum/render-static-markup
                  (ui-helpers/success-toast-oob "Summary deleted successfully"))}

          ;; Error: Return error toast
          (let [error-message (case (:code result)
                                "NOT_FOUND" "Summary not found"
                                "Failed to delete summary")]
            {:status (if (= (:code result) "NOT_FOUND") 404 500)
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob error-message))}))))))

(defn new-summary-page
  "GET /summaries/new - Display form for creating new summary.

  Args:
    ctx - Biff context map

  Returns:
    Ring response with HTML body"
  [ctx]
  (layout/app-page
   ctx
   {:page-title "New Summary"}
   [:div.py-6
    [:h1.text-2xl.font-bold.text-gray-900 "Create New Summary"]

    [:form.mt-6.max-w-2xl.bg-white.shadow-sm.rounded-lg.p-6
     {:hx-post "/summaries"
      :hx-target "#form-container"
      :hx-swap "outerHTML"}

     [:div#form-container
      [:div
       [:label.block.text-sm.font-medium.text-gray-700 {:for "hive-number"}
        "Hive Number"]
       [:input#hive-number.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
        {:type "number"
         :name "hive-number"
         :required true
         :min "1"}]]

      [:div.mt-4
       [:label.block.text-sm.font-medium.text-gray-700 {:for "observation-date"}
        "Observation Date"]
       [:input#observation-date.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
        {:type "date"
         :name "observation-date"
         :required true}]]

      [:div.mt-4
       [:label.block.text-sm.font-medium.text-gray-700 {:for "special-feature"}
        "Special Feature (Optional)"]
       [:input#special-feature.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
        {:type "text"
         :name "special-feature"}]]

      [:div.mt-4
       [:label.block.text-sm.font-medium.text-gray-700 {:for "content"}
        "Summary Content"]
       [:textarea#content.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
        {:name "content"
         :rows "6"
         :required true}]]

      [:div.mt-6.flex.gap-3
       [:button.inline-flex.justify-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus-visible:outline.focus-visible:outline-2
        {:type "submit"}
        "Create Summary"]
       [:a.inline-flex.justify-center.px-4.py-2.border.border-gray-300.text-sm.font-medium.rounded-md.text-gray-700.bg-white.hover:bg-gray-50
        {:href "/summaries"}
        "Cancel"]]]]]))

(defn create-summary-handler
  "POST /summaries - Create new summary with validation.

  Args:
    ctx - Biff context map with body params

  Returns:
    Ring response with redirect or validation errors"
  [{:keys [session biff.xtdb/node params] :as ctx}]
  (let [user-id (:uid session)
        body {:content (:content params)
              :hive-number (when-let [hn (:hive-number params)]
                             (parse-long hn))
              :observation-date (:observation-date params)
              :special-feature (:special-feature params)}]

    ;; Guard clause: validation
    (cond
      (str/blank? (:content body))
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              [:div
               (ui-helpers/error-toast-oob "Summary content is required")])}

      (nil? (:hive-number body))
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              (ui-helpers/error-toast-oob "Hive number is required"))}

      ;; Happy path
      :else
      (let [[status result] (summary-service/create-manual-summary
                             node user-id body)]

        (if (= status :ok)
          ;; Success: Redirect with flash message
          {:status 303
           :headers {"Location" "/summaries"}
           :session (assoc session :flash
                           (ui-helpers/success-flash "Summary created successfully!"))}

          ;; Error: Show error message
          {:status 400
           :body (layout/app-page
                  ctx
                  {:page-title "New Summary"
                   :error-message (assoc result
                                         :timestamp (str (java.time.Instant/now))
                                         :heading "Failed to Create Summary")}
                  (new-summary-page ctx))})))))

(defn import-csv-htmx-handler
  "POST /api/summaries/import - Import CSV and generate summaries (HTMX version).

  This handler returns HTML responses for htmx integration:
  - Success: New summary cards + success toast + rejected rows (if any)
  - Error: Error message OOB swap

  Args:
    ctx - Biff context map with body params

  Returns:
    Ring response with HTML body and OOB swaps"
  [{:keys [session biff.xtdb/node body] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    ;; Guard clause: request body required
    (if-not (some? body)
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              (csv-import/error-message-oob-html
               {:error "Request body is required"
                :code "INVALID_REQUEST"
                :heading "Invalid Request"}))}

      ;; Guard clause: csv field required
      (if-not (contains? body :csv)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (csv-import/error-message-oob-html
                 {:error "Missing required field: csv"
                  :code "MISSING_FIELD"
                  :heading "Validation Error"}))}

        ;; Happy path
        (let [user-id (:uid session)
              csv-string (:csv body)

              ;; Step 1: Parse and validate CSV
              [csv-status csv-result] (csv-service/process-csv-import csv-string)]

          ;; Guard clause: CSV processing failed
          (if (= csv-status :error)
            {:status 400
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (csv-import/error-message-oob-html csv-result))}

            (let [{:keys [valid-rows rejected-rows rows-submitted
                          rows-valid rows-rejected]} csv-result]

              ;; Guard clause: no valid rows
              (if (zero? rows-valid)
                {:status 400
                 :headers {"content-type" "text/html"}
                 :body (rum/render-static-markup
                        [:div
                         (csv-import/error-message-oob-html
                          {:error "All CSV rows failed validation"
                           :code "VALIDATION_ERROR"
                           :heading "Validation Error"})
                         (csv-import/rejected-rows-oob-html rejected-rows)])}

                ;; Step 2: Generate AI summaries
                (let [[ai-status ai-result] (openrouter-service/generate-summaries-batch
                                             valid-rows)]

                  ;; Guard clause: AI generation failed
                  (if (= ai-status :error)
                    {:status 500
                     :headers {"content-type" "text/html"}
                     :body (rum/render-static-markup
                            (csv-import/error-message-oob-html
                             (assoc ai-result :heading "AI Generation Error")))}

                    (let [{:keys [summaries model duration-ms]} ai-result

                          ;; Step 3: Create Generation record
                          [gen-status gen-result] (gen-service/create-generation
                                                   node user-id model
                                                   rows-valid duration-ms)]

                      ;; Guard clause: generation creation failed
                      (if (= gen-status :error)
                        {:status 500
                         :headers {"content-type" "text/html"}
                         :body (rum/render-static-markup
                                (csv-import/error-message-oob-html
                                 (assoc gen-result :heading "Database Error")))}

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

                          (log/info "CSV import completed (htmx)"
                                    :user-id user-id
                                    :generation-id generation-id
                                    :rows-submitted rows-submitted
                                    :rows-valid rows-valid
                                    :rows-rejected rows-rejected
                                    :summaries-created (count summary-entities))

                          ;; Step 5: Build HTML response with OOB swaps
                          (let [success-message (str (count summary-entities)
                                                     " summaries generated successfully"
                                                     (when (pos? rows-rejected)
                                                       (str ". " rows-rejected " rows rejected.")))
                                main-content (map summary-card summary-entities)
                                toast (ui-helpers/success-toast-oob success-message)
                                rejected-rows-html (when (seq rejected-rows)
                                                     (csv-import/rejected-rows-oob-html rejected-rows))
                                clear-form (csv-import/clear-form-oob-html)]

                            {:status 201
                             :headers {"content-type" "text/html"}
                             :body (rum/render-static-markup
                                    [:div
                                     ;; Main target: new summary cards
                                     main-content
                                     ;; OOB: success toast
                                     toast
                                     ;; OOB: rejected rows (if any)
                                     rejected-rows-html
                                     ;; OOB: clear form
                                     clear-form])}))))))))))))))

;; =============================================================================
;; HTMX Route Handlers for Summary Card Interactions
;; =============================================================================

(defn get-field-edit-mode-handler
  "GET /api/summaries/{id}/field/{field-name}/edit - Return inline field in edit mode.

  Args:
    ctx - Biff context with path-params: :id and :field-name

  Returns:
    Ring response with HTML for inline-editable-field-edit component"
  [{:keys [session biff/db path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          field-name (:field-name path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}

        (let [summary-id (second uuid-result)
              [status result] (summary-service/get-summary-by-id db summary-id user-id)]

          (if (= status :ok)
            (let [summary-dto (summary-dto/entity->dto result)
                  field-value (get summary-dto (keyword field-name))
                  placeholder (case field-name
                                "hive-number" "e.g., A-01"
                                "observation-date" "DD-MM-YYYY"
                                "special-feature" "e.g., Queen active"
                                "")]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (summary-card/inline-editable-field-edit
                       {:field-name field-name
                        :value field-value
                        :summary-id (:id summary-dto)
                        :placeholder placeholder}))})

            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}))))))

(defn get-content-edit-mode-handler
  "GET /api/summaries/{id}/edit - Return content area in edit mode.

  Args:
    ctx - Biff context with path-params: :id

  Returns:
    Ring response with HTML for content-edit-form component"
  [{:keys [session biff/db path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}

        (let [summary-id (second uuid-result)
              [status result] (summary-service/get-summary-by-id db summary-id user-id)]

          (if (= status :ok)
            (let [summary-dto (summary-dto/entity->dto result)]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (summary-card/content-edit-form
                       {:content (:content summary-dto)
                        :summary-id (:id summary-dto)}))})

            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}))))))

(defn cancel-content-edit-handler
  "GET /api/summaries/{id}/cancel-edit - Return content area in display mode.

  Args:
    ctx - Biff context with path-params: :id

  Returns:
    Ring response with HTML for content-display component"
  [{:keys [session biff/db path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]

      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}

        (let [summary-id (second uuid-result)
              [status result] (summary-service/get-summary-by-id db summary-id user-id)]

          (if (= status :ok)
            (let [summary-dto (summary-dto/entity->dto result)]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (summary-card/content-display
                       {:content (:content summary-dto)
                        :summary-id (:id summary-dto)
                        :expanded? false}))})

            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}))))))

(defn toggle-content-handler
  "GET /api/summaries/{id}/toggle-content - Toggle content expansion state.

  Args:
    ctx - Biff context with path-params: :id and query-params: :expanded

  Returns:
    Ring response with HTML for content-display component"
  [{:keys [session biff/db path-params params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          current-expanded (= "true" (:expanded params))
          new-expanded (not current-expanded)
          uuid-result (util/parse-uuid summary-id-str)]

      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}

        (let [summary-id (second uuid-result)
              [status result] (summary-service/get-summary-by-id db summary-id user-id)]

          (if (= status :ok)
            (let [summary-dto (summary-dto/entity->dto result)]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (summary-card/content-display
                       {:content (:content summary-dto)
                        :summary-id (:id summary-dto)
                        :expanded? new-expanded}))})

            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}))))))

(def module
  {:routes ["/summaries" {:middleware [mid/wrap-signed-in]}
            ["" {:get summaries-list-page
                 :post create-summary-handler}]
            ["/new" {:get new-summary-page}]
            ["/:id" {:delete delete-summary-handler}]]
   :api-routes [["/api/summaries/import" {:post import-csv-htmx-handler}]
                ["/api/summaries/:id"
                 ["/field/:field-name/edit" {:get get-field-edit-mode-handler}]
                 ["/edit" {:get get-content-edit-mode-handler}]
                 ["/cancel-edit" {:get cancel-content-edit-handler}]
                 ["/toggle-content" {:get toggle-content-handler}]]]})
