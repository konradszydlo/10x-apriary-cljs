(ns com.apriary.pages.app
  (:require [com.apriary.middleware :as mid]
            [com.apriary.ui.layout :as layout]
            [xtdb.api :as xt]))

(defn app [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (layout/app-page
     ctx
     {:page-title "Dashboard"}
     [:div.py-6
      [:h1.text-2xl.font-bold.text-gray-900 "Welcome to Apiary Summary"]
      [:div.mt-4.text-gray-600 "Signed in as " [:span.font-semibold email] "."]
      [:div.mt-6.text-gray-500 "Nothing here yet. Use the \"+ New Summary\" button to get started."]])))

(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]]})