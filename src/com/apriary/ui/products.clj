(ns com.apriary.ui.products
  "Products UI components for CSV import and table display.")

;; =============================================================================
;; Rejected Rows Component
;; =============================================================================

(defn rejected-rows-component
  "Render rejected CSV rows with validation errors.

  Args:
    rejected-rows - Vector of maps with :row-number and :reason

  Returns:
    Hiccup div with rejected rows or empty div"
  [rejected-rows]
  [:div#rejected-rows.mt-4
   (when (seq rejected-rows)
     [:div.bg-red-50.border.border-red-200.rounded.p-4
      {:role "alert"}
      [:h3.text-red-800.font-semibold "Some rows were rejected:"]
      [:ul.mt-2.space-y-1
       (for [{:keys [row-number reason]} rejected-rows]
         ^{:key row-number}
         [:li.text-sm.text-red-700
          (str "Row " row-number ": " reason)])]])])

;; =============================================================================
;; CSV Form Component
;; =============================================================================

(defn csv-form
  "Render CSV import form with htmx submission.

  Returns:
    Hiccup form element"
  []
  [:div#csv-form.mb-6
   [:form {:hx-post "/api/products-import"
           :hx-target "#products-table"
           :hx-swap "outerHTML"
           :hx-indicator "#csv-loading"}
    [:label.block.text-sm.font-medium.mb-2 {:for "csv-input"}
     "Paste CSV data (hive_number;date;product;quantity;metric)"]
    [:textarea#csv-input.w-full.font-mono.text-sm.border.rounded.p-3.resize-y
     {:name "csv"
      :rows 8
      :required true
      :placeholder "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg\nA-02;24-11-2025;Pollen;2;kg"}]
    [:button.mt-3.bg-blue-600.text-white.px-4.py-2.rounded.hover:bg-blue-700
     {:type "submit"}
     "Import Products"]
    [:div#csv-loading.htmx-indicator.ml-3.inline-block
     "Importing..."]]])

;; =============================================================================
;; Products Table Component
;; =============================================================================

(defn products-table
  "Render products as HTML table, sorted by date descending (newest first).

  Args:
    products - Vector of product entities

  Returns:
    Hiccup div with table or empty state message"
  [products]
  [:div#products-table.mt-6
   (if (seq products)
     [:table.min-w-full.border.border-gray-300
      [:thead.bg-gray-100
       [:tr
        [:th.border.px-4.py-2.text-left "Hive Number"]
        [:th.border.px-4.py-2.text-left "Date"]
        [:th.border.px-4.py-2.text-left "Product"]
        [:th.border.px-4.py-2.text-right "Quantity"]
        [:th.border.px-4.py-2.text-left "Metric"]]]
      [:tbody
       (for [product (sort-by :product/date #(compare %2 %1) products)]
         ^{:key (:product/id product)}
         [:tr.hover:bg-gray-50
          [:td.border.px-4.py-2 (:product/hive-number product)]
          [:td.border.px-4.py-2 (or (:product/date product) "-")]
          [:td.border.px-4.py-2 (:product/product product)]
          [:td.border.px-4.py-2.text-right (:product/quantity product)]
          [:td.border.px-4.py-2 (:product/metric product)]])]]
     [:p.text-gray-500.italic "No products yet. Import CSV data above to get started."])])

;; =============================================================================
;; OOB Swap Helpers
;; =============================================================================

(defn rejected-rows-oob
  "Generate rejected rows with OOB swap directive.

  Args:
    rejected-rows - Vector of maps with :row-number and :reason

  Returns:
    Hiccup div with hx-swap-oob attribute"
  [rejected-rows]
  [:div {:hx-swap-oob "innerHTML:#rejected-rows"}
   (when (seq rejected-rows)
     [:div.bg-red-50.border.border-red-200.rounded.p-4
      {:role "alert"}
      [:h3.text-red-800.font-semibold "Some rows were rejected:"]
      [:ul.mt-2.space-y-1
       (for [{:keys [row-number reason]} rejected-rows]
         ^{:key row-number}
         [:li.text-sm.text-red-700
          (str "Row " row-number ": " reason)])]])])

(defn success-toast-oob
  "Generate success toast with OOB swap directive.

  Args:
    count - Number of products imported

  Returns:
    Hiccup div with hx-swap-oob attribute"
  [count]
  [:div {:hx-swap-oob "afterbegin:#toast-container"}
   [:div.bg-green-50.border.border-green-200.rounded.p-4.mb-2
    {:role "alert"}
    [:p.text-green-800
     (str "Successfully imported " count " product" (when (> count 1) "s") ".")]]])

(defn clear-form-oob
  "Generate HTML to clear CSV textarea via OOB swap.

  Returns:
    Hiccup div with hx-swap-oob attribute to replace form"
  []
  [:div {:hx-swap-oob "innerHTML:#csv-form"}
   [:form {:hx-post "/api/products-import"
           :hx-target "#products-table"
           :hx-swap "outerHTML"
           :hx-indicator "#csv-loading"}
    [:label.block.text-sm.font-medium.mb-2 {:for "csv-input"}
     "Paste CSV data (hive_number;date;product;quantity;metric)"]
    [:textarea#csv-input.w-full.font-mono.text-sm.border.rounded.p-3.resize-y
     {:name "csv"
      :rows 8
      :required true
      :placeholder "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg\nA-02;24-11-2025;Pollen;2;kg"}]
    [:button.mt-3.bg-blue-600.text-white.px-4.py-2.rounded.hover:bg-blue-700
     {:type "submit"}
     "Import Products"]
    [:div#csv-loading.htmx-indicator.ml-3.inline-block
     "Importing..."]]])
