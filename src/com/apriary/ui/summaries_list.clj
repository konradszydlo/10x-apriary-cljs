(ns com.apriary.ui.summaries-list
  "Summaries List Section UI components.

  This namespace contains the main components for displaying and managing
  the summaries list view, including generation groups, bulk actions, and
  individual summary cards. It implements the server-side rendering pattern
  with htmx for dynamic interactions."
  (:require [com.apriary.ui.summary-card :as summary-card]))

;; =============================================================================
;; View Model Transformations
;; =============================================================================

(defn- format-generation-date
  "Format generation created-at instant to DD-MM-YYYY string.

  Args:
    instant - java.time.Instant

  Returns:
    Date string in DD-MM-YYYY format"
  [instant]
  (when instant
    (let [formatter (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
          zone (java.time.ZoneId/systemDefault)]
      (.format (.atZone instant zone) formatter))))

(defn- all-accepted?
  "Check if all summaries in a generation have been accepted.

  Args:
    generation - Generation entity map

  Returns:
    Boolean indicating if all summaries are accepted"
  [generation]
  (let [total-accepted (+ (:generation/accepted-unedited-count generation 0)
                         (:generation/accepted-edited-count generation 0))
        total-generated (:generation/generated-count generation 0)]
    (>= total-accepted total-generated)))

(defn group-summaries-by-generation
  "Group summaries by generation-id and prepare view model data.

  This function transforms flat lists of summaries and generations into a
  hierarchical structure suitable for rendering with generation groups.

  Args:
    summaries - List of summary entities
    generations-map - Map of generation-id to generation entity

  Returns:
    Map with keys:
      :generation-groups - List of generation group view models
      :manual-summaries - List of summaries without generation-id"
  [summaries generations-map]
  (let [;; Separate AI-generated summaries from manual ones
        {ai-summaries true manual-summaries false}
        (group-by #(some? (:summary/generation-id %)) summaries)

        ;; Group AI summaries by generation-id
        grouped-by-gen (group-by :summary/generation-id ai-summaries)

        ;; Build generation group view models
        generation-groups (for [[gen-id summaries] grouped-by-gen
                                :let [generation (get generations-map gen-id)]
                                :when generation]
                           {:generation generation
                            :summaries summaries
                            :all-accepted? (all-accepted? generation)
                            :generation-date (format-generation-date
                                              (:generation/created-at generation))
                            :summaries-count (count summaries)})]

    {:generation-groups (sort-by
                          #(get-in % [:generation :generation/created-at])
                          #(compare %2 %1)  ; Descending order (newest first)
                          generation-groups)
     :manual-summaries (or manual-summaries [])}))

;; =============================================================================
;; Icons
;; =============================================================================

(defn- beehive-icon
  "Returns beehive icon SVG for empty state."
  []
  [:svg.w-16.h-16.mx-auto.text-gray-400
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :viewBox "0 0 24 24"
    :stroke "currentColor"
    :aria-hidden "true"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :stroke-width "1.5"
           :d "M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25"}]])

(defn- check-circle-icon
  "Returns check circle icon SVG for accepted status."
  []
  [:svg.w-5.h-5
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm13.36-1.814a.75.75 0 10-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 00-1.06 1.06l2.25 2.25a.75.75 0 001.14-.094l3.75-5.25z"
           :clip-rule "evenodd"}]])

(defn- spinner-icon
  "Returns spinner icon SVG for loading states."
  []
  [:svg.w-5.h-5.animate-spin
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

;; =============================================================================
;; Component: Empty State
;; =============================================================================

(defn empty-state
  "Renders empty state when user has no summaries.

  Displays a friendly message with icon and call-to-action encouraging
  users to create their first summary or import CSV data.

  Returns:
    Hiccup div element with empty state content"
  []
  [:div.empty-state.text-center.py-12.px-6
   ;; Icon
   (beehive-icon)

   ;; Heading
   [:h3.text-xl.font-semibold.text-gray-900.mt-4
    "No summaries yet"]

   ;; Description
   [:p.text-gray-600.mt-2
    "Get started by importing CSV data or creating your first summary manually."]

   ;; Call-to-action
   [:div.mt-6.flex.gap-3.justify-center
    [:a.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.transition-colors
     {:href "/csv-import"}
     "Import CSV"]
    [:a.inline-flex.items-center.px-4.py-2.border.border-gray-300.text-sm.font-medium.rounded-md.text-gray-700.bg-white.hover:bg-gray-50.transition-colors
     {:href "/summaries/new"}
     "+ New Summary"]]])

;; =============================================================================
;; Component: Bulk Accept Button
;; =============================================================================

(defn bulk-accept-button
  "Renders button for bulk accepting all summaries in a generation.

  Visible only when there are unaccepted summaries. After all summaries
  are accepted, this is replaced with 'All accepted' text.

  Args:
    generation-id - UUID string of the generation
    all-accepted? - Boolean indicating if all summaries are accepted

  Returns:
    Hiccup button element or accepted status text"
  [{:keys [generation-id all-accepted?]}]
  (if all-accepted?
    ;; All accepted status
    [:span.text-green-600.text-sm.font-medium.flex.items-center.gap-1
     (check-circle-icon)
     [:span "All accepted"]]

    ;; Bulk accept button
    [:button.px-4.py-2.bg-green-600.text-white.rounded-md.hover:bg-green-700.transition-colors.text-sm.font-medium.flex.items-center.gap-2
     {:type "button"
      :hx-post (str "/api/generations/" generation-id "/accept-summaries")
      :hx-target (str "#generation-group-" generation-id)
      :hx-swap "outerHTML"
      :hx-indicator (str "#bulk-spinner-" generation-id)
      :hx-confirm "Accept all summaries from this import?"}
     [:span.htmx-indicator
      {:id (str "bulk-spinner-" generation-id)}
      (spinner-icon)]
     [:span "Accept All from This Import"]]))

;; =============================================================================
;; Component: Generation Group Header
;; =============================================================================

(defn generation-group-header
  "Renders header for a generation batch group.

  Displays import date, model used, summary count, and bulk accept button.
  Provides visual grouping and metadata for AI-generated summaries.

  Args:
    generation - Generation entity map
    generation-date - Formatted date string (DD-MM-YYYY)
    summaries-count - Number of summaries in this group
    all-accepted? - Boolean indicating if all summaries are accepted

  Returns:
    Hiccup div element with generation header"
  [{:keys [generation generation-date summaries-count all-accepted?]}]
  (let [generation-id (str (:generation/id generation))
        model (:generation/model generation)]
    [:div.generation-header.bg-blue-50.border-l-4.border-blue-500.p-4.rounded-r-md.mb-4
     [:div.flex.flex-col.md:flex-row.justify-between.items-start.md:items-center.gap-4

      ;; Left side: Metadata
      [:div.flex.flex-col.sm:flex-row.gap-2.sm:gap-4.items-start.sm:items-center.flex-wrap
       ;; Import date
       [:h3.text-lg.font-semibold.text-blue-900
        (str "Import from " generation-date)]

       ;; Model badge
       [:span.inline-flex.items-center.px-2.py-1.rounded.bg-blue-200.text-blue-800.text-xs.font-medium
        model]

       ;; Summary count
       [:span.text-sm.text-blue-700
        (str summaries-count (if (= summaries-count 1) " summary" " summaries"))]]

      ;; Right side: Bulk accept button
      [bulk-accept-button
       {:generation-id generation-id
        :all-accepted? all-accepted?}]]]))

;; =============================================================================
;; Component: Generation Group
;; =============================================================================

(defn generation-group
  "Renders a complete generation group with header and summary cards.

  Groups all summaries from a single AI generation batch together with
  a header showing metadata and bulk actions.

  Args:
    generation - Generation entity map
    summaries - List of summary entities in this generation
    generation-date - Formatted date string
    all-accepted? - Boolean indicating if all summaries are accepted
    summaries-count - Number of summaries

  Returns:
    Hiccup div element with complete generation group"
  [{:keys [generation summaries generation-date all-accepted? summaries-count]}]
  (let [generation-id (str (:generation/id generation))]
    [:div.generation-group.mb-8
     {:id (str "generation-group-" generation-id)}

     ;; Header
     [generation-group-header
      {:generation generation
       :generation-date generation-date
       :summaries-count summaries-count
       :all-accepted? all-accepted?}]

     ;; Summary cards grid
     [:div.summary-cards-grid.grid.gap-6
      {:class "grid-cols-1 md:grid-cols-2 lg:grid-cols-3"}
      (for [summary summaries]
        (let [summary-dto {:id (str (:summary/id summary))
                          :source (name (:summary/source summary))
                          :hive-number (:summary/hive-number summary)
                          :observation-date (:summary/observation-date summary)
                          :special-feature (:summary/special-feature summary)
                          :content (:summary/content summary)
                          :generation-id (str (:summary/generation-id summary))
                          :accepted-at (:summary/accepted-at summary)}]
          ^{:key (:id summary-dto)}
          [summary-card/summary-card
           {:summary summary-dto
            :generation-date generation-date
            :model-name (:generation/model generation)}]))]]))

;; =============================================================================
;; Component: Manual Summaries Section
;; =============================================================================

(defn manual-summaries-section
  "Renders section for manually created summaries (no generation-id).

  Args:
    summaries - List of manual summary entities

  Returns:
    Hiccup div element with manual summaries section"
  [summaries]
  (when (seq summaries)
    [:div.manual-summaries-section.mb-8
     ;; Section header
     [:h3.text-lg.font-semibold.text-gray-900.mb-4.flex.items-center.gap-2
      [:span "Manual Summaries"]
      [:span.inline-flex.items-center.px-2.py-1.rounded.bg-gray-200.text-gray-700.text-xs.font-medium
       (count summaries)]]

     ;; Summary cards grid
     [:div.summary-cards-grid.grid.gap-6
      {:class "grid-cols-1 md:grid-cols-2 lg:grid-cols-3"}
      (for [summary summaries]
        (let [summary-dto {:id (str (:summary/id summary))
                          :source (name (:summary/source summary))
                          :hive-number (:summary/hive-number summary)
                          :observation-date (:summary/observation-date summary)
                          :special-feature (:summary/special-feature summary)
                          :content (:summary/content summary)
                          :generation-id nil
                          :accepted-at nil}]
          ^{:key (:id summary-dto)}
          [summary-card/summary-card
           {:summary summary-dto}]))]]))

;; =============================================================================
;; Component: Summaries List Section (Main Container)
;; =============================================================================

(defn summaries-list-section
  "Main container component for the summaries list view.

  Handles overall layout and decides whether to show empty state or
  summaries content based on data availability. Groups AI-generated
  summaries by generation batch and displays manual summaries separately.

  Args:
    summaries - List of all summary entities (optional)
    generations - List of generation entities (optional)

  Returns:
    Hiccup section element with complete summaries list"
  [{:keys [summaries generations]}]
  (if (empty? summaries)
    ;; Empty state
    [:section#summaries-list-section.py-6
     [:div.flex.items-center.justify-between.mb-6
      [:h2.text-2xl.font-bold.text-gray-900 "Your Summaries"]]
     [empty-state]]

    ;; Summaries content
    (let [;; Build generations map for quick lookup
          generations-map (into {} (map (fn [gen] [(:generation/id gen) gen]) generations))

          ;; Group summaries by generation
          {:keys [generation-groups manual-summaries]}
          (group-summaries-by-generation summaries generations-map)

          total-count (count summaries)]

      [:section#summaries-list-section.py-6
       ;; Section header
       [:div.flex.items-center.justify-between.mb-6
        [:h2.text-2xl.font-bold.text-gray-900.flex.items-center.gap-2
         [:span "Your Summaries"]
         [:span.inline-flex.items-center.px-3.py-1.rounded-full.bg-blue-100.text-blue-800.text-base.font-medium
          total-count]]]

       ;; Generation groups (AI-imported summaries)
       (for [group generation-groups]
         ^{:key (str "gen-" (get-in group [:generation :generation/id]))}
         [generation-group group])

       ;; Manual summaries section
       [manual-summaries-section manual-summaries]])))
