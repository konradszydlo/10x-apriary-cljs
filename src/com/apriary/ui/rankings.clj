(ns com.apriary.ui.rankings
  "UI components for rankings page.")

(defn ranking-table
  "Render single ranking table (top or bottom).

   Args:
     title - String (e.g., \"Top 5 Honey Producers\")
     entries - Vector of {:hive-number :total-quantity :metric}

   Returns: Hiccup table with columns: Rank, Hive, Total"
  [title entries]
  [:div.mt-6
   [:h3.text-lg.font-semibold.text-gray-900.mb-3 title]
   (if (empty? entries)
     [:p.text-sm.text-gray-500.italic "No data available"]
     [:table.min-w-full.divide-y.divide-gray-200.border.border-gray-200.rounded-lg
      [:thead.bg-gray-50
       [:tr
        [:th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Rank"]
        [:th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Hive"]
        [:th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Total"]]]
      [:tbody.bg-white.divide-y.divide-gray-200
       (map-indexed
        (fn [idx {:keys [hive-number total-quantity metric]}]
          ^{:key (str hive-number "-" metric "-" idx)}
          [:tr.hover:bg-gray-50
           [:td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-900 (str (inc idx))]
           [:td.px-6.py-4.whitespace-nowrap.text-sm.font-medium.text-gray-900 hive-number]
           [:td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-900
            (str total-quantity " " metric)]])
        entries)]])])

(defn product-section
  "Render one product type's rankings section with top/bottom tables.

   Args:
     product-type - String (e.g., \"Honey\")
     rankings - {:top [...] :bottom [...]} from service

   Returns: Hiccup div with heading and two tables"
  [product-type {:keys [top bottom]}]
  (let [n-top (count top)
        n-bottom (count bottom)
        top-title (str "Top " n-top " " product-type " Producer" (when (not= n-top 1) "s"))
        bottom-title (str "Bottom " n-bottom " " product-type " Producer" (when (not= n-bottom 1) "s"))]
    [:section.mb-12
     [:h2.text-2xl.font-bold.text-gray-900.mb-4 product-type]
     (ranking-table top-title top)
     (ranking-table bottom-title bottom)]))

(defn rankings-page-content
  "Render full rankings page content with all product sections.

   Args:
     rankings-map - Map from service: {product-type {:top :bottom}}

   Returns: Hiccup div with page heading and product sections"
  [rankings-map]
  [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8.py-8
   [:h1.text-3xl.font-bold.text-gray-900.mb-8 "Hive Rankings"]

   (if (empty? rankings-map)
     [:div.bg-gray-50.border.border-gray-200.rounded-lg.p-8.text-center
      [:p.text-gray-700.text-lg "No products yet."]
      [:p.text-gray-500.mt-2 "Import data to see rankings."]]

     [:div
      (for [[product-type ranking-data] (sort-by key rankings-map)]
        ^{:key product-type}
        (product-section product-type ranking-data))])])
