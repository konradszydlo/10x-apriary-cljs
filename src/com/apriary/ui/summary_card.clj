(ns com.apriary.ui.summary-card
  "Summary card component for displaying and editing beekeeping summaries.

  This component provides a comprehensive interface for viewing and modifying
  summary entries with inline editing capabilities, content management, and
  accept/delete functionality. It uses server-side rendering with htmx for
  dynamic interactions.

  Main components:
  - source-badge: Visual indicator of summary origin (AI-generated, edited, or manual)
  - inline-editable-field: Dual-mode field for metadata editing
  - content-area: Content display and editing section
  - action-buttons: Edit, accept, and delete operations
  - generation-metadata: AI generation information
  - content-toggle: Expand/collapse for long content
  - summary-card: Complete card assembly"
  (:require [clojure.string :as str]))

;; =============================================================================
;; Constants
;; =============================================================================

(def content-preview-length 150)
(def content-min-length 50)
(def content-max-length 50000)

;; =============================================================================
;; Icons
;; Note: Some icons are defined here but used in components implemented in later steps
;; =============================================================================

(defn- robot-icon
  "Returns robot icon SVG for AI-generated summaries."
  []
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:d "M12 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm0 18a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM8 12a1 1 0 100-2 1 1 0 000 2zm8 0a1 1 0 100-2 1 1 0 000 2zM5.64 5.64a1 1 0 011.41 0l.71.71a1 1 0 01-1.42 1.42l-.7-.71a1 1 0 010-1.42zm11.31 11.31a1 1 0 011.41 0l.71.71a1 1 0 01-1.42 1.42l-.7-.71a1 1 0 010-1.42zM20 12a1 1 0 011-1h1a1 1 0 110 2h-1a1 1 0 01-1-1zM2 12a1 1 0 011-1h1a1 1 0 110 2H3a1 1 0 01-1-1zm15.36 5.64a1 1 0 011.41 0l.71.71a1 1 0 01-1.42 1.42l-.7-.71a1 1 0 010-1.42zM5.64 5.64a1 1 0 011.41 0l.71.71a1 1 0 01-1.42 1.42l-.7-.71a1 1 0 010-1.42z"}]])

(defn- pencil-icon
  "Returns pencil icon SVG for manual summaries and edit actions."
  []
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:d "M21.731 2.269a2.625 2.625 0 00-3.712 0l-1.157 1.157 3.712 3.712 1.157-1.157a2.625 2.625 0 000-3.712zM19.513 8.199l-3.712-3.712-12.15 12.15a5.25 5.25 0 00-1.32 2.214l-.8 2.685a.75.75 0 00.933.933l2.685-.8a5.25 5.25 0 002.214-1.32L19.513 8.2z"}]])

(defn- check-icon
  "Returns checkmark icon SVG for accepted status."
  []
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm13.36-1.814a.75.75 0 10-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 00-1.06 1.06l2.25 2.25a.75.75 0 001.14-.094l3.75-5.25z"
           :clip-rule "evenodd"}]])

(defn- trash-icon
  "Returns trash icon SVG for delete action."
  []
  [:svg.w-5.h-5
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M16.5 4.478v.227a48.816 48.816 0 013.878.512.75.75 0 11-.256 1.478l-.209-.035-1.005 13.07a3 3 0 01-2.991 2.77H8.084a3 3 0 01-2.991-2.77L4.087 6.66l-.209.035a.75.75 0 01-.256-1.478A48.567 48.567 0 017.5 4.705v-.227c0-1.564 1.213-2.9 2.816-2.951a52.662 52.662 0 013.369 0c1.603.051 2.815 1.387 2.815 2.951zm-6.136-1.452a51.196 51.196 0 013.273 0C14.39 3.05 15 3.684 15 4.478v.113a49.488 49.488 0 00-6 0v-.113c0-.794.609-1.428 1.364-1.452zm-.355 5.945a.75.75 0 10-1.5.058l.347 9a.75.75 0 101.499-.058l-.346-9zm5.48.058a.75.75 0 10-1.498-.058l-.347 9a.75.75 0 001.5.058l.345-9z"
           :clip-rule "evenodd"}]])

(defn- spinner-icon
  "Returns spinner icon SVG for loading states."
  []
  [:svg.w-4.h-4.animate-spin
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

(defn- chevron-down-icon
  "Returns chevron down icon SVG for content toggle."
  []
  [:svg.w-4.h-4
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 24 24"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M12.53 16.28a.75.75 0 01-1.06 0l-7.5-7.5a.75.75 0 011.06-1.06L12 14.69l6.97-6.97a.75.75 0 111.06 1.06l-7.5 7.5z"
           :clip-rule "evenodd"}]])

;; =============================================================================
;; Component: Source Badge
;; =============================================================================

(defn source-badge
  "Renders a source badge indicating summary origin.

  The badge uses color-coding and icons to visually distinguish between:
  - AI-generated (unedited): Blue badge with robot icon
  - AI-edited: Amber badge with robot and pencil icons
  - Manual: Gray badge with pencil icon

  Args:
    source - One of \"ai-full\", \"ai-partial\", or \"manual\"

  Returns:
    Hiccup span element with styled badge"
  [{:keys [source]}]
  (let [variants {"ai-full" {:bg "bg-blue-100"
                             :text "text-blue-800"
                             :icon (robot-icon)
                             :label "AI Generated"
                             :aria "AI Generated summary"}
                  "ai-partial" {:bg "bg-amber-100"
                                :text "text-amber-800"
                                :icon (robot-icon)
                                :label "AI Edited"
                                :aria "AI Edited summary"}
                  "manual" {:bg "bg-gray-100"
                            :text "text-gray-800"
                            :icon (pencil-icon)
                            :label "Manual"
                            :aria "Manual summary"}}
        variant (get variants source)]
    [:span.source-badge.inline-flex.items-center.gap-1.px-2.py-1.rounded.text-sm.font-medium
     {:class [(:bg variant) (:text variant)]
      :aria-label (:aria variant)}
     (:icon variant)
     [:span (:label variant)]]))

;; =============================================================================
;; Component: Inline Editable Field
;; =============================================================================

(defn inline-editable-field
  "Renders an inline editable field in display (read-only) mode.

  The field shows the current value or placeholder text. On hover, it displays
  a pencil icon to indicate editability. Clicking the field will trigger a
  server-side conversion to edit mode.

  Args:
    field-name - Field identifier (\"hive-number\", \"observation-date\", or \"special-feature\")
    value - Current field value (string or nil)
    summary-id - UUID string of the summary
    placeholder - Placeholder text when value is empty

  Returns:
    Hiccup div element with editable field in display mode"
  [{:keys [field-name value summary-id placeholder]}]
  (let [display-value (or value placeholder)
        empty? (nil? value)]
    [:div.editable-field.group.cursor-pointer.rounded.px-2.py-1.transition-colors
     {:class (if empty? "text-gray-400" "hover:bg-gray-100")
      :data-field-name field-name
      :data-original-value value
      :hx-get (str "/api/summaries/" summary-id "/field/" field-name "/edit")
      :hx-target "closest .editable-field"
      :hx-swap "outerHTML"}
     [:span.field-value display-value]
     [:span.inline.ml-1.opacity-0.group-hover:opacity-100.transition-opacity
      (pencil-icon)]]))

(defn inline-editable-field-edit
  "Renders an inline editable field in edit mode with auto-save.

  The field is rendered as an input with htmx attributes for automatic saving
  on blur+change events. A loading spinner appears during save operations.
  Pressing Escape cancels edit and reverts to original value.

  Args:
    field-name - Field identifier
    value - Current field value
    summary-id - UUID string of the summary
    placeholder - Placeholder text for input
    error - Optional error message to display

  Returns:
    Hiccup div element with editable field in edit mode"
  [{:keys [field-name value summary-id placeholder error]}]
  (let [input-type (if (= field-name "observation-date") "text" "text")
        pattern (when (= field-name "observation-date") "\\d{2}-\\d{2}-\\d{4}")]
    [:div.editable-field.editing
     {:data-field-name field-name
      :data-original-value (or value "")}
     [:input.border.rounded.px-2.py-1.focus:ring-2.focus:ring-blue-500
      (merge
       {:type input-type
        :name field-name
        :value (or value "")
        :placeholder placeholder
        :class (when error "border-red-500")
        :hx-patch (str "/api/summaries/" summary-id)
        :hx-trigger "blur changed delay:100ms"
        :hx-target "closest .editable-field"
        :hx-swap "outerHTML"
        :hx-indicator (str "#spinner-" field-name)
        :aria-label (str "Edit " field-name)
        :_ (str "on keydown[key=='Escape'] "
                "halt the event "
                "then fetch /api/summaries/" summary-id "/field/" field-name "/display "
                "then put the result into the closest .editable-field")}
       (when pattern
         {:pattern pattern}))]
     [:span.htmx-indicator.inline.ml-1
      {:id (str "spinner-" field-name)}
      (spinner-icon)]
     (when error
       [:p.text-red-600.text-sm.mt-1 error])]))

;; =============================================================================
;; Component: Special Feature Tag
;; =============================================================================

(defn special-feature-tag
  "Renders special feature as an editable purple badge.

  Only rendered when special-feature has a value. Displays as a purple badge
  with inline editing capability.

  Args:
    special-feature - Feature text (string or nil)
    summary-id - UUID string of the summary

  Returns:
    Hiccup div element with special feature badge, or nil if no feature"
  [{:keys [special-feature summary-id]}]
  (when (seq special-feature)
    [:div.mb-2
     [:div.inline-flex.items-center.gap-2.px-3.py-1.rounded-full.bg-purple-100.text-purple-800.text-sm.font-medium.group.cursor-pointer.hover:bg-purple-200.transition-colors
      {:hx-get (str "/api/summaries/" summary-id "/field/special-feature/edit")
       :hx-target "closest div"
       :hx-swap "outerHTML"}
      [:span special-feature]
      [:span.opacity-0.group-hover:opacity-100.transition-opacity
       (pencil-icon)]]]))

;; =============================================================================
;; Component: Content Area
;; =============================================================================

(defn content-display
  "Renders summary content in display mode with optional truncation.

  Content longer than 150 characters is truncated with an ellipsis. The full
  content can be revealed via the content-toggle component.

  Args:
    content - Summary content text
    summary-id - UUID string of the summary
    expanded? - Boolean indicating if content is fully expanded (default: false)

  Returns:
    Hiccup div element with content display"
  [{:keys [content summary-id expanded?]}]
  (let [truncated? (> (count content) content-preview-length)
        display-content (if (and truncated? (not expanded?))
                          (str (subs content 0 content-preview-length) "...")
                          content)]
    [:div.content-area
     {:id (str "summary-content-" summary-id)
      :data-content-expanded (str (boolean expanded?))}
     [:div.content-display.prose.prose-sm.max-w-none
      display-content]]))

(defn content-edit-form
  "Renders summary content in edit mode with textarea and save/cancel buttons.

  The form includes client-side validation hints (minlength/maxlength) and
  submits via htmx PATCH on save. Cancel button fetches original content.

  Args:
    content - Current summary content
    summary-id - UUID string of the summary
    error - Optional validation error message

  Returns:
    Hiccup div element with content edit form"
  [{:keys [content summary-id error]}]
  [:div.content-area.editing
   [:form
    {:hx-patch (str "/api/summaries/" summary-id "/content")
     :hx-swap "outerHTML"
     :hx-target "closest .summary-card"}

    (when error
      [:p.text-red-600.text-sm.mb-2 error])

    [:textarea.w-full.border.rounded.p-3.resize-y
     {:name "content"
      :rows "10"
      :id (str "content-textarea-" summary-id)
      :class (if error "border-red-500 focus:ring-red-500" "focus:ring-2 focus:ring-blue-500")
      :aria-label "Summary content"
      :minlength content-min-length
      :maxlength content-max-length
      :oninput "updateCharCount(this)"}
     content]

    ;; Character counter
    [:div.char-counter.text-sm.text-gray-600.mt-1
     {:id (str "char-counter-" summary-id)}
     (str (count (str/trim content)) " / " content-max-length " characters")]

    [:div.flex.gap-2.mt-2
     [:button.px-4.py-2.bg-blue-600.text-white.rounded.hover:bg-blue-700.disabled:opacity-50.flex.items-center.gap-2
      {:type "submit"
       :id (str "save-btn-" summary-id)
       :hx-indicator "#save-spinner"}
      [:span.htmx-indicator
       {:id "save-spinner"}
       (spinner-icon)]
      [:span "Save"]]

     [:button.px-4.py-2.bg-gray-200.text-gray-800.rounded.hover:bg-gray-300
      {:type "button"
       :hx-get (str "/api/summaries/" summary-id "/cancel-edit")
       :hx-target "closest .content-area"
       :hx-swap "outerHTML"}
      "Cancel"]]]])

;; =============================================================================
;; Component: Action Buttons
;; =============================================================================

(defn action-buttons
  "Renders action buttons for summary operations (edit, accept, delete).

  The accept button only appears for AI-generated summaries and is replaced
  with an 'Accepted' badge after acceptance. Delete and accept operations
  include confirmation dialogs.

  Args:
    summary-id - UUID string of the summary
    source - Summary source (\"ai-full\", \"ai-partial\", or \"manual\")
    accepted? - Boolean indicating if summary has been accepted

  Returns:
    Hiccup div element with action buttons"
  [{:keys [summary-id source accepted?]}]
  [:div.action-buttons.flex.gap-2.items-center

   ;; Edit button
   [:button.btn-icon.p-2.text-gray-600.hover:text-gray-800.hover:bg-gray-100.rounded.transition-colors
    {:type "button"
     :hx-get (str "/api/summaries/" summary-id "/edit")
     :hx-target "closest .summary-card .content-area"
     :hx-swap "outerHTML"
     :aria-label "Edit summary content"}
    (pencil-icon)]

   ;; Accept button (conditional: only for AI summaries)
   (when (or (= source "ai-full") (= source "ai-partial"))
     (if accepted?
       ;; Accepted badge
       [:span.accepted-badge.inline-flex.items-center.gap-1.px-2.py-1.rounded.bg-green-100.text-green-800.text-sm.font-medium
        (check-icon)
        [:span "Accepted"]]
       ;; Accept button
       [:button.btn-accept.px-3.py-1.bg-green-600.text-white.rounded.hover:bg-green-700.transition-colors.text-sm.font-medium
        {:type "button"
         :hx-post (str "/api/summaries/" summary-id "/accept")
         :hx-target "closest .action-buttons"
         :hx-swap "outerHTML"
         :hx-confirm "Accept this summary?"}
        "Accept"]))

   ;; Delete button
   [:button.btn-icon.p-2.text-red-600.hover:text-red-700.hover:bg-red-50.rounded.transition-colors
    {:type "button"
     :hx-delete (str "/api/summaries/" summary-id)
     :hx-target "closest .summary-card"
     :hx-swap "outerHTML swap:1s"
     :hx-confirm "Delete this summary? This action cannot be undone."
     :aria-label "Delete summary"}
    (trash-icon)]])

;; =============================================================================
;; Component: Generation Metadata
;; =============================================================================

(defn generation-metadata
  "Renders generation metadata for AI-generated summaries.

  Displays the import date and AI model name. Only rendered when generation-id
  is present (i.e., for AI-generated summaries, not manual ones).

  Args:
    generation-id - UUID string of generation batch (nil for manual summaries)
    generation-date - Date string in DD-MM-YYYY format
    model-name - AI model identifier (e.g., \"gpt-4-turbo\")

  Returns:
    Hiccup div element with generation metadata, or nil if no generation-id"
  [{:keys [generation-id generation-date model-name]}]
  (when generation-id
    [:div.generation-metadata.text-sm.text-gray-600.flex.items-center.gap-2
     [:span (str "From import on " generation-date)]
     [:span.text-gray-400 "•"]
     [:span.inline-flex.items-center.gap-1.px-2.py-0.5.rounded.bg-gray-100.text-gray-700.text-xs.font-medium
      model-name]]))

;; =============================================================================
;; Component: Content Toggle
;; =============================================================================

(defn content-toggle
  "Renders a show more/less toggle button for long content.

  Only rendered when content exceeds 150 characters. The button toggles between
  truncated and full content views, with an animated chevron icon indicating
  the current state.

  Args:
    summary-id - UUID string of the summary
    content-length - Length of the full content
    expanded? - Boolean indicating current expansion state

  Returns:
    Hiccup button element with content toggle, or nil if content is short"
  [{:keys [summary-id content-length expanded?]}]
  (when (> content-length content-preview-length)
    [:button.content-toggle.text-blue-600.hover:text-blue-700.hover:underline.text-sm.flex.items-center.gap-1.transition-colors
     {:type "button"
      :hx-get (str "/api/summaries/" summary-id "/toggle-content?expanded=" (boolean expanded?))
      :hx-target (str "#summary-content-" summary-id)
      :hx-swap "outerHTML"
      :aria-expanded (str (boolean expanded?))
      :aria-controls (str "summary-content-" summary-id)}
     [:span (if expanded? "Show less" "Show more")]
     [:span.transition-transform.duration-200
      {:class (when expanded? "rotate-180")}
      (chevron-down-icon)]]))

;; =============================================================================
;; Component: Summary Card (Main Assembly)
;; =============================================================================

(defn summary-card
  "Renders the complete summary card component.

  This is the main component that assembles all sub-components into a cohesive
  card interface. It handles both display and edit modes based on the edit-mode?
  parameter.

  Args:
    summary - Summary DTO map with keys:
      :id - UUID string
      :source - \"ai-full\", \"ai-partial\", or \"manual\"
      :hive-number - Optional hive identifier
      :observation-date - Optional date in DD-MM-YYYY format
      :special-feature - Optional feature tag
      :content - Summary text
      :generation-id - Optional generation batch UUID
      :accepted-at - Optional acceptance timestamp
    edit-mode? - Boolean to render content in edit mode (default: false)
    expanded? - Boolean to render content expanded (default: false)
    generation-date - Optional generation date for metadata display
    model-name - Optional AI model name for metadata display

  Returns:
    Hiccup article element with complete summary card"
  [{:keys [summary edit-mode? expanded? generation-date model-name]}]
  (let [{:keys [id source hive-number observation-date special-feature
                content generation-id accepted-at]} summary
        accepted? (some? accepted-at)]

    [:article.summary-card.bg-white.rounded-lg.shadow-md.p-6.hover:shadow-lg.transition-shadow
     {:data-summary-id id
      :data-source source
      :id (str "summary-" id)}

     ;; =======================================================================
     ;; Card Header
     ;; =======================================================================
     [:div.card-header.flex.items-start.justify-between.mb-4

      ;; Left side: Source badge and metadata
      [:div.flex.flex-col.gap-2.flex-1
       ;; Source badge
       (source-badge {:source source})

       ;; Metadata fields
       [:div.metadata-section.flex.flex-wrap.gap-3.items-center
        ;; Hive number
        (inline-editable-field
         {:field-name "hive-number"
          :value hive-number
          :summary-id id
          :placeholder "e.g., A-01"})

        ;; Observation date
        (inline-editable-field
         {:field-name "observation-date"
          :value observation-date
          :summary-id id
          :placeholder "DD-MM-YYYY"})]]

      ;; Right side: Action buttons
      (action-buttons
       {:summary-id id
        :source source
        :accepted? accepted?})]

     ;; =======================================================================
     ;; Card Body
     ;; =======================================================================
     [:div.card-body

      ;; Special feature tag (if present)
      (when (seq special-feature)
        (special-feature-tag
         {:special-feature special-feature
          :summary-id id}))

      ;; Content area (display or edit mode)
      (if edit-mode?
        (content-edit-form
         {:content content
          :summary-id id})
        (content-display
         {:content content
          :summary-id id
          :expanded? expanded?}))]

     ;; =======================================================================
     ;; Card Footer
     ;; =======================================================================
     [:div.card-footer.flex.justify-between.items-center.mt-4

      ;; Generation metadata (AI summaries only)
      (generation-metadata
       {:generation-id generation-id
        :generation-date generation-date
        :model-name model-name})

      ;; Content toggle (long content only)
      (content-toggle
       {:summary-id id
        :content-length (count content)
        :expanded? expanded?})]]))
