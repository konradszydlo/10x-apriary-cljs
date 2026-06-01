(ns com.apriary.pages.rankings
  "Rankings page handler and route."
  (:require
   [com.apriary.middleware :as mid]
   [com.apriary.services.product-rankings :as rankings-svc]
   [com.apriary.ui.layout :as layout]
   [com.apriary.ui.rankings :as rankings-ui]
   [com.biffweb :as biff]
   [clojure.tools.logging :as log]))

(defn rankings-page-handler
  "Render rankings page with top/bottom hives per product type.

   GET /rankings

   Returns: Ring response with HTML body"
  [{:keys [session biff/db] :as ctx}]
  (let [user-id (:uid session)]
    (log/info "Rendering rankings page" :user-id user-id)

    ;; Fetch rankings via service
    (let [[status result] (rankings-svc/calculate-rankings db user-id)]
      (if (= status :ok)
        ;; Render page with rankings
        (biff/render
         (layout/app-page ctx {:page-title "Rankings"}
                          (rankings-ui/rankings-page-content (:rankings result))))

        ;; Error state: render error message in page (don't return 500)
        (do
          (log/error "Rankings service error" :user-id user-id :result result)
          (biff/render
           (layout/app-page ctx {:page-title "Rankings"}
                            [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8.py-8
                             [:h1.text-3xl.font-bold.text-gray-900.mb-8 "Hive Rankings"]
                             [:div.bg-red-50.border.border-red-200.rounded-lg.p-8.text-center
                              [:p.text-red-700.text-lg "Unable to load rankings."]
                              [:p.text-red-500.mt-2 "Please try again."]]])))))))

(def module
  {:routes [["/rankings" {:middleware [mid/wrap-signed-in]}
             ["" {:get rankings-page-handler}]]]})
