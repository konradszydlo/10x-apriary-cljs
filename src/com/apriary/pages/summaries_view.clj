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
            [com.apriary.ui.summaries-list :as summaries-list]
            [com.apriary.services.summary :as summary-service]
            [com.apriary.services.csv-import :as csv-service]
            [com.apriary.services.openrouter :as openrouter-service]
            [com.apriary.services.generation :as gen-service]
            [com.apriary.dto.summary :as summary-dto]
            [com.apriary.util :as util]
            [cheshire.core]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [malli.core :as m]
            [malli.error :as me]
            [rum.core :as rum]
            [xtdb.api :as xt]))

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

        ;; Fetch summaries
        [summaries-status summaries-result] (summary-service/list-summaries
                                             db user-id
                                             :sort-by sort-by
                                             :sort-order sort-order
                                             :limit 100
                                             :offset 0)

        ;; Fetch generations
        [gen-status gen-result] (gen-service/list-user-generations
                                 db user-id
                                 :sort-by "created-at"
                                 :sort-order "desc"
                                 :limit 100
                                 :offset 0)]

    (if (and (= summaries-status :ok) (= gen-status :ok))
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
           {:href "/summaries-new"}
           "+ New Summary"]]]

         ; CSV Import Section
        (csv-import/csv-import-section)

         ;; Summaries List with Generation Grouping
        [:div#summaries-list.mt-6
         (summaries-list/summaries-list-section
          {:summaries (:summaries summaries-result)
           :generations (:generations gen-result)})]])

      ;; Error case
      (layout/app-page
       ctx
       {:page-title "Summaries"
        :error-message (assoc (or summaries-result gen-result)
                              :timestamp (str (java.time.Instant/now))
                              :heading "Failed to Load Summaries")}
       [:div.py-6
        [:h1.text-2xl.font-bold.text-gray-900 "Summaries"]]))))

#_(defn summaries-list-page
    "GET /summaries - Display list of all summaries.

  Args:
    ctx - Biff context map

  Returns:
    Ring response with HTML body"
    [{:keys [session biff/db params] :as ctx}]
    (let [user-id (:uid session)
          sort-by (or (:sort-by params) "created-at")
          sort-order (or (:sort-order params) "desc")

        ;; Fetch summaries
          [summaries-status summaries-result] (summary-service/list-summaries
                                               db user-id
                                               :sort-by sort-by
                                               :sort-order sort-order
                                               :limit 100
                                               :offset 0)

        ;; Fetch generations
          [gen-status gen-result] (gen-service/list-user-generations
                                   db user-id
                                   :sort-by "created-at"
                                   :sort-order "desc"
                                   :limit 100
                                   :offset 0)]

      (if (and (= summaries-status :ok) (= gen-status :ok))
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
             {:href "/summaries-new"}
             "+ New Summary"]]]

        ;; CSV Import Section
          (csv-import/csv-import-section)

        ;; Summaries List with Generation Grouping
          [:div#summaries-list.mt-6
           (summaries-list/summaries-list-section
            {:summaries (:summaries summaries-result)
             :generations (:generations gen-result)})]])

      ;; Error case
        (layout/app-page
         ctx
         {:page-title "Summaries"
          :error-message (assoc (or summaries-result gen-result)
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
  "GET /summaries-new - Display form for creating new summary.

  Args:
    ctx - Biff context map

  Returns:
    Ring response with HTML body"
  [{:keys [params errors] :as ctx}]
  (layout/app-page
   ctx
   {:page-title "New Summary"}
   [:main.max-w-2xl.mx-auto.py-6
    ;; Breadcrumb/back link
    [:a.text-blue-600.hover:text-blue-800.mb-4.inline-block
     {:href "/summaries"}
     "← Back to Summaries"]

    ;; Page heading
    [:h1.text-2xl.font-bold.mb-6 "Create New Summary"]

    ;; Form container with htmx
    [:form.space-y-6
     {:hx-post "/api/summaries"
      :hx-ext "json-enc"
      :hx-target "body"
      :hx-swap "innerHTML"}
     ;; Hive Number Field (optional)
     [:div
      [:label.block.text-sm.font-medium.text-gray-700
       {:for "hive-number"}
       "Hive Number"]
      [:input.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
       {:type "text"
        :id "hive-number"
        :name "hive-number"
        :placeholder "e.g., A-01"
        :value (:hive-number params)}]
      (when-let [error (:hive-number errors)]
        [:p.mt-1.text-sm.text-red-600 error])]

     ;; Observation Date Field (optional)
     [:div
      [:label.block.text-sm.font-medium.text-gray-700
       {:for "observation-date"}
       "Observation Date"]
      [:input.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
       {:type "text"
        :id "observation-date"
        :name "observation-date"
        :placeholder "DD-MM-YYYY"
        :value (:observation-date params)}]
      [:p.mt-1.text-xs.text-gray-500 "Format: DD-MM-YYYY"]
      (when-let [error (:observation-date errors)]
        [:p.mt-1.text-sm.text-red-600 error])]

     ;; Special Feature Field (optional)
     [:div
      [:label.block.text-sm.font-medium.text-gray-700
       {:for "special-feature"}
       "Special Feature"]
      [:input.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500
       {:type "text"
        :id "special-feature"
        :name "special-feature"
        :placeholder "e.g., Queen active"
        :value (:special-feature params)}]
      (when-let [error (:special-feature errors)]
        [:p.mt-1.text-sm.text-red-600 error])]

     ;; Content Field (required)
     [:div
      [:label.block.text-sm.font-medium.text-gray-700
       {:for "content"}
       "Observation Content "
       [:span.text-red-600 "*"]]
      [:textarea.mt-1.block.w-full.rounded-md.border-gray-300.shadow-sm.focus:border-blue-500.focus:ring-blue-500.resize-y
       {:id "content"
        :name "content"
        :rows 10
        :required true
        :aria-required "true"
        :aria-describedby "char-counter content-error"}
       (:content params)]
      [:div#char-counter.mt-1.text-sm.text-gray-600
       {:aria-live "polite"}
       "0 / 50,000 characters"]
      (when-let [error (:content errors)]
        [:p#content-error.mt-1.text-sm.text-red-600 error])]

     ;; Submit Button
     [:div.flex.justify-end
      [:button.px-6.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.focus:outline-none.focus:ring-2.focus:ring-blue-500.focus:ring-offset-2
       {:type "submit"
        :id "submit-btn"}
       "Create Summary"]]]]))

;; =============================================================================
;; Malli Schema for Manual Summary Creation
;; =============================================================================

(def create-manual-summary-schema
  "Schema for validating manual summary creation form data.

  Field specifications:
  - :hive-number - Optional string
  - :observation-date - Optional string matching DD-MM-YYYY format
  - :special-feature - Optional string
  - :content - Required string, 50-50,000 characters after trim"
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])

;; =============================================================================
;; API Handler for Manual Summary Creation
;; =============================================================================

(defn create-manual-summary-api-handler
  "POST /api/summaries - Create new manual summary with Malli validation.

  This handler processes the htmx form submission with JSON encoding.
  On success, returns HX-Redirect header to main summaries page.
  On validation error, re-renders form with error messages and preserved values.

  Args:
    ctx - Biff context map with body-params from htmx json-enc

  Returns:
    Ring response with HX-Redirect header or re-rendered form"
  [{:keys [session biff.xtdb/node body-params] :as ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          params body-params]

      ;; Validate with Malli schema
      (if-let [validation-errors (m/explain create-manual-summary-schema params)]
        ;; Validation failed - re-render form with errors
        (let [humanized-errors (me/humanize validation-errors)]
          {:status 400
           :headers {"content-type" "text/html"}
           :body (rum/render-static-markup
                  (new-summary-page
                   (assoc ctx
                          :errors humanized-errors
                          :params params)))})

        ;; Validation passed - create summary
        (let [[status result] (summary-service/create-manual-summary
                               node user-id params)]

          (if (= status :ok)
            ;; Success: Redirect to summaries list with HX-Redirect
            {:status 201
             :headers {"HX-Redirect" "/summaries"
                       "content-type" "application/json"}
             :body (cheshire.core/generate-string
                    {:message "Summary created successfully"})}

            ;; Service error: Re-render form with error message
            {:status 500
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (new-summary-page
                     (assoc ctx
                            :errors {:general (:message result)}
                            :params params)))}))))))

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
  "POST /api/summaries-import - Import CSV and generate summaries (HTMX version).

  This handler returns HTML responses for htmx integration:
  - Success: New summary cards + success toast + rejected rows (if any)
  - Error: Error message OOB swap

  Args:
    ctx - Biff context map with body params

  Returns:
    Ring response with HTML body and OOB swaps"
  [{:keys [session biff.xtdb/node body-params] :as _ctx}]
  ;; Guard clause: authentication
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    ;; Guard clause: request body required
    (if-not (some? body-params)
      {:status 400
       :headers {"content-type" "text/html"}
       :body (rum/render-static-markup
              (csv-import/error-message-oob-html
               {:error "Request body is required"
                :code "INVALID_REQUEST"
                :heading "Invalid Request"}))}

      ;; Guard clause: csv field required
      (if-not (contains? body-params :csv)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (csv-import/error-message-oob-html
                 {:error "Missing required field: csv"
                  :code "MISSING_FIELD"
                  :heading "Validation Error"}))}

        ;; Happy path
        (let [user-id (:uid session)
              csv-string (:csv body-params)

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
                          (let [;; Format generation date
                                generation-date (let [formatter (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
                                                      zone (java.time.ZoneId/systemDefault)]
                                                  (.format (.atZone now zone) formatter))
                                success-message (str (count summary-entities)
                                                     " summaries generated successfully"
                                                     (when (pos? rows-rejected)
                                                       (str ". " rows-rejected " rows rejected.")))
                                ;; Convert entities to DTOs for rendering
                                summary-dtos (mapv summary-dto/entity->dto summary-entities)
                                main-content (map (fn [dto]
                                                    (summary-card/summary-card
                                                     {:summary dto
                                                      :generation-date generation-date
                                                      :model-name model}))
                                                  summary-dtos)
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

(defn cancel-field-edit-handler
  "GET /api/summaries/{id}/field/{field-name}/display - Return inline field in display mode (cancel edit).

  Args:
    ctx - Biff context with path-params: :id and :field-name

  Returns:
    Ring response with HTML for inline-editable-field component in display mode"
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
                      (summary-card/inline-editable-field
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
    Ring response with HTML for content-display component and updated toggle button"
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
            (let [summary-dto (summary-dto/entity->dto result)
                  content (:content summary-dto)]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      [:div
                       ;; Main target: updated content display
                       (summary-card/content-display
                        {:content content
                         :summary-id (:id summary-dto)
                         :expanded? new-expanded})

                       ;; OOB swap: updated toggle button with new state
                       [:div {:hx-swap-oob (str "outerHTML:.content-toggle[aria-controls='summary-content-" (:id summary-dto) "']")}
                        (summary-card/content-toggle
                         {:summary-id (:id summary-dto)
                          :content-length (count content)
                          :expanded? new-expanded})]])})

            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}))))))

(defn update-summary-content-handler
  "PATCH /api/summaries/{id}/content - Update summary content and return full card.

  This handler specifically handles content updates, which may trigger source
  changes (ai-full -> ai-partial). Returns the entire summary card refreshed.

  Args:
    ctx - Biff context with path-params: :id and params with :content

  Returns:
    Ring response with HTML for complete summary-card component"
  [{:keys [session biff.xtdb/node biff/db path-params params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          content (:content params)
          uuid-result (util/parse-uuid summary-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}

        (let [summary-id (second uuid-result)
              ;; Get original summary to check source change
              [get-status original] (summary-service/get-summary-by-id db summary-id user-id)]

          ;; Guard clause: summary not found
          (if (not= get-status :ok)
            {:status 404
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "Summary not found"))}

            ;; Perform update
            (let [original-source (:summary/source original)
                  [status result] (summary-service/update-summary
                                   node summary-id user-id {:content content})]

              (if (= status :ok)
                ;; Success: Return updated card + success toast
                (let [summary-dto (summary-dto/entity->dto result)
                      new-source (:summary/source result)
                      source-changed? (and (= original-source :ai-full)
                                           (= new-source :ai-partial))

                      ;; Get generation if it exists
                      generation (when-let [gen-id (:summary/generation-id result)]
                                   (xt/entity db gen-id))
                      generation-date (when generation
                                        (let [formatter (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
                                              zone (java.time.ZoneId/systemDefault)]
                                          (.format (.atZone (:generation/created-at generation) zone) formatter)))

                      toast-message (if source-changed?
                                      "Summary updated and marked as AI Edited"
                                      "Summary updated successfully")]
                  {:status 200
                   :headers {"content-type" "text/html"}
                   :body (rum/render-static-markup
                          [:div
                           ;; Main target: entire summary card
                           (summary-card/summary-card
                            {:summary summary-dto
                             :generation-date generation-date
                             :model-name (when generation (:generation/model generation))})

                           ;; OOB: Success toast
                           (ui-helpers/success-toast-oob toast-message)])})

                ;; Error: Return error toast
                (let [error-message (case (:code result)
                                      "NOT_FOUND" "Summary not found"
                                      "VALIDATION_ERROR" (:message result)
                                      "Failed to update content")]
                  {:status (case (:code result)
                             "NOT_FOUND" 404
                             "VALIDATION_ERROR" 400
                             500)
                   :headers {"content-type" "text/html"}
                   :body (rum/render-static-markup
                          [:div
                           ;; Return edit form with error
                           (summary-card/content-edit-form
                            {:content content
                             :summary-id summary-id-str
                             :error error-message})

                           ;; OOB: Error toast
                           (ui-helpers/error-toast-oob error-message)])})))))))))

(defn update-summary-field-handler
  "PATCH /api/summaries/{id} - Update a single summary field with inline edit.

  This handler supports updating individual fields (hive-number, observation-date,
  special-feature) and returns the updated field in display mode.

  Args:
    ctx - Biff context with path-params: :id and params with field data

  Returns:
    Ring response with HTML for inline-editable-field component in display mode"
  [{:keys [session biff.xtdb/node path-params params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

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
              ;; Extract field update from params
              field-name (cond
                           (contains? params :hive-number) "hive-number"
                           (contains? params :observation-date) "observation-date"
                           (contains? params :special-feature) "special-feature"
                           (contains? params :content) "content"
                           :else nil)
              field-value (when field-name (get params (keyword field-name)))
              updates (when field-name {(keyword field-name) field-value})]

          ;; Guard clause: no valid field provided
          (if-not updates
            {:status 400
             :headers {"content-type" "text/html"}
             :body (rum/render-static-markup
                    (ui-helpers/error-toast-oob "No valid field to update"))}

            ;; Perform update
            (let [[status result] (summary-service/update-summary
                                   node summary-id user-id updates)]

              (if (= status :ok)
                ;; Success: Return updated field in display mode + success toast
                (let [summary-dto (summary-dto/entity->dto result)
                      updated-value (get summary-dto (keyword field-name))
                      placeholder (case field-name
                                    "hive-number" "e.g., A-01"
                                    "observation-date" "DD-MM-YYYY"
                                    "special-feature" "e.g., Queen active"
                                    "")]
                  {:status 200
                   :headers {"content-type" "text/html"}
                   :body (rum/render-static-markup
                          [:div
                           ;; Main target: updated field in display mode
                           (summary-card/inline-editable-field
                            {:field-name field-name
                             :value updated-value
                             :summary-id (:id summary-dto)
                             :placeholder placeholder})

                           ;; OOB: Success toast
                           (ui-helpers/success-toast-oob
                            (str (str/capitalize (str/replace field-name "-" " ")) " updated"))])})

                ;; Error: Return error field + error toast
                (let [error-message (case (:code result)
                                      "NOT_FOUND" "Summary not found"
                                      "VALIDATION_ERROR" (:message result)
                                      "Failed to update field")]
                  {:status (case (:code result)
                             "NOT_FOUND" 404
                             "VALIDATION_ERROR" 400
                             500)
                   :headers {"content-type" "text/html"}
                   :body (rum/render-static-markup
                          [:div
                           ;; Main target: field with error
                           (summary-card/inline-editable-field-edit
                            {:field-name field-name
                             :value field-value
                             :summary-id summary-id-str
                             :placeholder ""
                             :error error-message})

                           ;; OOB: Error toast
                           (ui-helpers/error-toast-oob error-message)])})))))))))

(defn accept-summary-handler
  "POST /api/summaries/{id}/accept - Accept an AI-generated summary.

  This handler accepts a single summary and updates generation counters.
  Returns updated action buttons with 'Accepted' badge.

  Args:
    ctx - Biff context with path-params: :id

  Returns:
    Ring response with HTML for action-buttons component"
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

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
              [status result] (summary-service/accept-summary node summary-id user-id)]

          (if (= status :ok)
            ;; Success: Return updated action buttons + success toast
            (let [{:keys [summary generation]} result
                  summary-dto (summary-dto/entity->dto summary)
                  generation-id (str (:generation/id generation))

                  ;; Check if all summaries in generation are now accepted
                  all-accepted? (>= (+ (:generation/accepted-unedited-count generation 0)
                                       (:generation/accepted-edited-count generation 0))
                                    (:generation/generated-count generation 0))]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      [:div
                       ;; Main target: updated action buttons
                       (summary-card/action-buttons
                        {:summary-id (:id summary-dto)
                         :source (:source summary-dto)
                         :accepted? true})

                       ;; OOB: Update generation header if all accepted
                       (when all-accepted?
                         (let [generation-date (let [formatter (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
                                                     zone (java.time.ZoneId/systemDefault)]
                                                 (.format (.atZone (:generation/created-at generation) zone) formatter))
                               summaries-count (:generation/generated-count generation)]
                           [:div {:hx-swap-oob (str "outerHTML:#generation-group-" generation-id " .generation-header")}
                            ((requiring-resolve 'com.apriary.ui.summaries-list/generation-group-header)
                             {:generation generation
                              :generation-date generation-date
                              :summaries-count summaries-count
                              :all-accepted? true})]))

                       ;; OOB: Success toast
                       (ui-helpers/success-toast-oob "Summary accepted")])})

            ;; Error: Return error toast
            (let [error-message (case (:code result)
                                  "NOT_FOUND" "Summary not found"
                                  "INVALID_OPERATION" (:message result)
                                  "CONFLICT" (:message result)
                                  "Failed to accept summary")]
              {:status (case (:code result)
                         "NOT_FOUND" 404
                         "INVALID_OPERATION" 400
                         "CONFLICT" 409
                         500)
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (ui-helpers/error-toast-oob error-message))})))))))

(defn bulk-accept-generation-handler
  "POST /api/generations/{id}/accept-summaries - Bulk accept all summaries in generation.

  This handler accepts all summaries in a generation batch and refreshes
  the entire generation group with updated state.

  Args:
    ctx - Biff context with path-params: :id

  Returns:
    Ring response with HTML for complete generation-group component"
  [{:keys [session biff.xtdb/node biff/db path-params] :as _ctx}]
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}

    (let [user-id (:uid session)
          generation-id-str (:id path-params)
          uuid-result (util/parse-uuid generation-id-str)]

      ;; Guard clause: invalid UUID
      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid generation ID"))}

        (let [generation-id (second uuid-result)
              [status result] (gen-service/bulk-accept-summaries-for-generation
                               node generation-id user-id)]

          (if (= status :ok)
            ;; Success: Re-render entire generation group
            (let [{:keys [generation total-summaries]} result

                  ;; Fetch all summaries for this generation
                  summaries-query {:find '[?s]
                                   :where [['?s :summary/generation-id generation-id]
                                           ['?s :summary/user-id user-id]]}
                  summary-ids (xt/q db summaries-query)
                  summaries (mapv (fn [[?s]] (xt/entity db ?s)) summary-ids)

                  ;; Format generation date
                  generation-date (let [formatter (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
                                        zone (java.time.ZoneId/systemDefault)]
                                    (.format (.atZone (:generation/created-at generation) zone) formatter))]

              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      [:div
                       ;; Main target: entire generation group
                       ((requiring-resolve 'com.apriary.ui.summaries-list/generation-group)
                        {:generation generation
                         :summaries summaries
                         :generation-date generation-date
                         :all-accepted? true
                         :summaries-count (count summaries)})

                       ;; OOB: Success toast
                       (ui-helpers/success-toast-oob
                        (str total-summaries " " (if (= total-summaries 1) "summary" "summaries") " accepted"))])})

            ;; Error: Return error toast
            (let [error-message (case (:code result)
                                  "NOT_FOUND" "Generation not found"
                                  "FORBIDDEN" "Access denied"
                                  "Failed to accept summaries")]
              {:status (case (:code result)
                         "NOT_FOUND" 404
                         "FORBIDDEN" 403
                         500)
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (ui-helpers/error-toast-oob error-message))})))))))

(def module
  {:routes [["/summaries" {:middleware [mid/wrap-signed-in]}
             ["" {:get summaries-list-page
                  :post create-summary-handler}]
             ["/:id" {:delete delete-summary-handler}]]
            ["/summaries-new" {:middleware [mid/wrap-signed-in]
                               :get new-summary-page}]]
   :api-routes [["/api/summaries" {:middleware [mid/wrap-signed-in]}
                 ["" {:post create-manual-summary-api-handler}]
                 ["/:id"
                  ["" {:patch update-summary-field-handler
                       :delete delete-summary-handler}]
                  ["/content" {:patch update-summary-content-handler}]
                  ["/accept" {:post accept-summary-handler}]
                  ["/field/:field-name/edit" {:get get-field-edit-mode-handler}]
                  ["/field/:field-name/display" {:get cancel-field-edit-handler}]
                  ["/edit" {:get get-content-edit-mode-handler}]
                  ["/cancel-edit" {:get cancel-content-edit-handler}]
                  ["/toggle-content" {:get toggle-content-handler}]]]
                ["/api/summaries-import" {:post import-csv-htmx-handler
                                          :middleware [mid/wrap-signed-in]}]
                ["/api/generations/:id/accept-summaries" {:post bulk-accept-generation-handler
                                                          :middleware [mid/wrap-signed-in]}]]})
