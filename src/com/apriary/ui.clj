(ns com.apriary.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [com.apriary.settings :as settings]
            [com.apriary.ui.header :refer [unauthenticated-header]]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]
            [rum.core :as rum]))

(defn static-path [path]
  (if-some [last-modified (some-> (io/resource (str "public" path))
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str path "?t=" last-modified)
    path))

(defn base [ctx & body]
  (apply
   biff/base-html
   (-> ctx
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description "Managing your apiary the easy way."
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (static-path "/css/main.css")}]
                                     [:script {:src (static-path "/js/main.js")}]
                                     [:script {:src "https://unpkg.com/htmx.org@2.0.7"}]
                                     [:script {:src "https://unpkg.com/htmx-ext-ws@2.0.2/ws.js"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.14"}]]
                                    head))))
   body))

(defn page [ctx & body]
  (base
   ctx
   [:body.bg-gray-50.min-h-screen
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})

    ;; Unauthenticated Header
    (unauthenticated-header ctx)

    ;; Content
    [:.flex-grow]
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     body]
    [:.flex-grow]
    [:.flex-grow]]))

(defn on-error [{:keys [status ex session] :as ctx}]
  (let [error-data (when ex
                     {:error (if (= status 404)
                               "The page you're looking for doesn't exist."
                               "An unexpected error occurred.")
                      :code (if (= status 404) "NOT_FOUND" "INTERNAL_ERROR")
                      :heading (if (= status 404) "Page Not Found" "Error")
                      :timestamp (str (java.time.Instant/now))})]
    {:status status
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            ;; Use app-page layout if user is authenticated, otherwise use simple page
            (if (:uid session)
              ((requiring-resolve 'com.apriary.ui.layout/app-page)
               ctx
               {:error-message error-data
                :page-title (if (= status 404) "Not Found" "Error")}
               [:div.py-6
                [:h1.text-2xl.font-bold.text-gray-900
                 (if (= status 404) "Page Not Found" "Something Went Wrong")]
                [:p.mt-4.text-gray-600
                 (if (= status 404)
                   "The page you're looking for doesn't exist."
                   "We're sorry, but something went wrong. Please try again.")]])
              (page
               ctx
               [:h1.text-lg.font-bold
                (if (= status 404)
                  "Page not found."
                  "Something went wrong.")])))}))
