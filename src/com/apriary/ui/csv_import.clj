(ns com.apriary.ui.csv-import
  "CSV Import Section UI components for the Apriary Summary application.

  This module provides reusable UI components for the CSV import functionality
  that can be embedded within the main summaries page. It uses Biff's server-side
  rendering with htmx for dynamic interactions.

  Usage:

    (require '[com.apriary.ui.csv-import :as csv-import])

    ;; In summaries page
    [:div
     (csv-import/csv-import-section)
     (summaries-list)]")

;; =============================================================================
;; Section Heading Component
;; =============================================================================

(defn section-heading
  "Renders the CSV Import section heading.

  Returns:
    Hiccup vector representing the section heading"
  []
  [:h2.text-xl.font-semibold.text-gray-900.mb-4
   "Import CSV"])

;; =============================================================================
;; CSV Textarea Component
;; =============================================================================

(defn csv-textarea
  "Renders the CSV data textarea input with label and helper text.

  Args:
    opts - Optional map with keys:
      :value - Pre-filled CSV content (optional)
      :disabled - Boolean flag to disable textarea (optional)

  Returns:
    Hiccup vector representing the textarea with label and helper text"
  [{:keys [value disabled] :or {disabled false}}]
  [:div.mb-4
   [:label.block.text-sm.font-medium.text-gray-700.mb-2
    {:for "csv-input"}
    "CSV Data"]
   [:textarea.w-full.font-mono.text-sm.border.border-gray-300.rounded.p-3.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-blue-500.disabled:bg-gray-100.disabled:cursor-not-allowed
    (merge
     {:id "csv-input"
      :name "csv"
      :rows 8
      :required true
      :aria-required "true"
      :aria-describedby "csv-helper-text"
      :placeholder "observation;hive_number;observation_date;special_feature
Hive very active today, lots of bees returning with pollen...;A-01;23-11-2025;Pollen activity high
Colony appears weak, only a few foragers...;A-02;23-11-2025;
Queen sighting confirmed today...;A-03;24-11-2025;Queen present"}
     (when disabled {:disabled true})
     (when value {:value value}))]
   [:p#csv-helper-text.text-sm.text-gray-600.mt-2
    "Paste CSV content (UTF-8, semicolon-separated). Must include 'observation' column."]])

;; =============================================================================
;; Submit Button Component
;; =============================================================================

(defn- loading-spinner
  "Renders a spinning loader icon for the submit button.

  Returns:
    Hiccup vector representing the loading spinner SVG"
  []
  [:svg.animate-spin.h-5.w-5.text-white
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :aria-hidden "true"}
   [:circle.opacity-25
    {:cx "12"
     :cy "12"
     :r "10"
     :stroke "currentColor"
     :stroke-width "4"}]
   [:path.opacity-75
    {:fill "currentColor"
     :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]])

(defn submit-button
  "Renders the CSV import submit button with loading state.

  The button integrates with htmx indicator system to show a loading
  spinner during form submission.

  Returns:
    Hiccup vector representing the submit button"
  []
  [:button.bg-blue-600.hover:bg-blue-700.text-white.font-medium.py-2.px-4.rounded.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-blue-500.focus-visible:outline-offset-2.disabled:opacity-50.disabled:cursor-not-allowed.inline-flex.items-center.gap-2
   {:type "submit"
    :id "csv-submit-button"
    :aria-label "Generate summaries from CSV"}
   [:span "Generate Summaries"]
   [:span#csv-loading.htmx-indicator
    (loading-spinner)]])

;; =============================================================================
;; Rejected Rows Component
;; =============================================================================

(defn- rejected-row-item
  "Renders a single rejected row item with row number and reason.

  Args:
    row - Map with keys:
      :row-number - Integer row number
      :reason - String describing why the row was rejected

  Returns:
    Hiccup vector representing a list item for the rejected row"
  [{:keys [row-number reason]}]
  [:li.text-sm
   [:span.font-semibold.text-amber-900
    (str "Row " row-number ": ")]
   [:span.text-amber-800
    reason]])

(defn rejected-rows-section
  "Renders a collapsible section displaying rejected CSV rows.

  This component only renders when there are rejected rows. It uses
  HTML5 details/summary for native collapsible behavior with full
  accessibility support.

  Args:
    rejected-rows - Vector of maps, each with :row-number and :reason

  Returns:
    Hiccup vector representing the rejected rows section, or nil if empty"
  [rejected-rows]
  (when (seq rejected-rows)
    [:details.mt-4.border-l-4.border-amber-500.bg-amber-50.rounded
     {:open false}
     [:summary.cursor-pointer.p-4.font-medium.text-amber-800.hover:bg-amber-100.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-amber-500.rounded
      {:aria-label "Toggle rejected rows details"}
      (str "⚠ " (count rejected-rows) " row"
           (when (> (count rejected-rows) 1) "s")
           " rejected - Click to see details")]
     [:div.px-4.pb-4
      [:ul.space-y-2.mt-2
       (for [row rejected-rows]
         ^{:key (:row-number row)}
         (rejected-row-item row))]]]))

;; =============================================================================
;; CSV Form Component
;; =============================================================================

(defn csv-form
  "Renders the CSV import form with htmx integration.

  The form handles:
  - CSV data submission to /api/summaries/import
  - Loading state management via htmx indicators
  - OOB swap for rejected rows display

  Returns:
    Hiccup vector representing the CSV import form"
  []
  [:form.space-y-4
   {:hx-post "/api/summaries/import"
    :hx-ext "json-enc"
    :hx-target "#summaries-list"
    :hx-swap "afterbegin"
    :hx-indicator "#csv-loading"}
   (csv-textarea {})
   (submit-button)
   [:div#rejected-rows-container
    ;; Rejected rows will be swapped in here via OOB swap
    ]])

;; =============================================================================
;; Main CSV Import Section Container
;; =============================================================================

(defn csv-import-section
  "Renders the complete CSV import section.

  This is the main component that should be embedded in the summaries page.
  It includes:
  - Section heading
  - Error message area (for validation errors)
  - CSV import form

  Returns:
    Hiccup vector representing the complete CSV import section"
  []
  [:section.bg-gray-50.p-6.rounded.mb-8
   {:aria-labelledby "csv-import-heading"}
   [:div#error-area
    ;; Error messages will be swapped in here
    ]
   (section-heading)
   (csv-form)])

;; =============================================================================
;; OOB Swap Helpers
;; =============================================================================

(defn rejected-rows-oob-html
  "Generates rejected rows section HTML with OOB swap directive.

  This allows the rejected rows to be injected into the #rejected-rows-container
  from the server response using htmx out-of-band swaps.

  Args:
    rejected-rows - Vector of maps, each with :row-number and :reason

  Returns:
    Hiccup vector with hx-swap-oob attribute for OOB injection"
  [rejected-rows]
  [:div {:hx-swap-oob "innerHTML:#rejected-rows-container"}
   (rejected-rows-section rejected-rows)])

(defn error-message-oob-html
  "Generates error message HTML with OOB swap directive.

  This allows error messages to be displayed in the error area via OOB swap.

  Args:
    error-data - Map with keys:
      :error - Error message text
      :code - Error code
      :heading - Error heading (optional)

  Returns:
    Hiccup vector with hx-swap-oob attribute"
  [error-data]
  (let [heading (or (:heading error-data) "Error")
        message (or (:error error-data) "An error occurred")]
    [:div {:hx-swap-oob "innerHTML:#error-area"
           :class "mb-4"}
     [:div.border-l-4.border-red-500.bg-red-50.p-4.rounded
      {:role "alert"
       :aria-live "assertive"}
      [:div.flex.items-start.gap-3
       ;; Error Icon
       [:div.flex-shrink-0
        [:svg.h-5.w-5.text-red-400
         {:xmlns "http://www.w3.org/2000/svg"
          :viewBox "0 0 20 20"
          :fill "currentColor"
          :aria-hidden "true"}
         [:path {:fill-rule "evenodd"
                 :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                 :clip-rule "evenodd"}]]]
       ;; Error Content
       [:div.flex-1
        [:h3.text-sm.font-medium.text-red-800 heading]
        [:p.mt-1.text-sm.text-red-700 message]]
       ;; Close Button
       [:button.flex-shrink-0.hover:bg-red-100.rounded.p-1.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2
        {:type "button"
         :hx-swap "delete"
         :hx-target "#error-area > div"
         :aria-label "Dismiss error"}
        [:svg.h-5.w-5.text-red-600
         {:xmlns "http://www.w3.org/2000/svg"
          :viewBox "0 0 20 20"
          :fill "currentColor"
          :aria-hidden "true"}
         [:path {:d "M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z"}]]]]]]))

(defn clear-form-oob-html
  "Generates HTML to clear the CSV textarea via OOB swap.

  This can be used after successful import to reset the form.

  Returns:
    Hiccup vector with hx-swap-oob attribute to clear the textarea"
  []
  [:textarea {:hx-swap-oob "outerHTML:#csv-input"
              :id "csv-input"
              :name "csv"
              :rows 8
              :required true
              :aria-required "true"
              :aria-describedby "csv-helper-text"
              :class "w-full font-mono text-sm border border-gray-300 rounded p-3 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
              :placeholder "observation;hive_number;observation_date;special_feature
Hive very active today, lots of bees returning with pollen...;A-01;23-11-2025;Pollen activity high
Colony appears weak, only a few foragers...;A-02;23-11-2025;
Queen sighting confirmed today...;A-03;24-11-2025;Queen present"}])
