(ns com.apriary.ui.layout
  "Main layout component integrating header, toast notifications, and error messages.

  This namespace provides the base layout for all authenticated pages in the application.
  It integrates:
  - ApplicationHeader (sticky navigation)
  - ToastNotificationContainer (for success/error/info messages)
  - ErrorMessageArea (for page-level errors)

  Usage:

    (require '[com.apriary.ui.layout :as layout])

    (defn my-page-handler [ctx]
      {:status 200
       :body (layout/app-page
               ctx
               {:page-title \"My Page\"}
               [:div \"Page content here\"])})"
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [com.apriary.settings :as settings]
            [com.apriary.ui.header :refer [application-header]]
            [com.apriary.ui.toast :as toast]
            [com.apriary.ui.error :refer [error-message-area]]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]))

(defn- static-path
  "Generates cache-busted path for static resources.

  Args:
    path - Resource path (e.g., '/css/main.css')

  Returns:
    Path with timestamp query parameter if resource exists"
  [path]
  (if-some [last-modified (some-> (io/resource (str "public" path))
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str path "?t=" last-modified)
    path))

(defn base-html
  "Base HTML layout for all pages.

  This is the foundation layout that includes all necessary scripts and styles.
  Use app-page for authenticated pages with header/toast/error components.

  Args:
    ctx - Biff context map
    opts - Options map with keys:
      :page-title - Page title (defaults to app name)
      :recaptcha - Whether to include reCAPTCHA (default false)
    body - Page content (hiccup vectors)

  Returns:
    Complete HTML document as hiccup vector"
  [{:keys [::recaptcha] :as ctx} opts & body]
  (apply
   biff/base-html
   (-> ctx
       (merge #:base{:title (or (:page-title opts) settings/app-name)
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description "Managing your apiary the easy way."
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (static-path "/css/main.css")}]
                                     [:script {:src (static-path "/js/main.js")}]
                                     [:script {:src "https://unpkg.com/htmx.org@2.0.7"}]
                                     [:script {:src "https://unpkg.com/htmx-ext-ws@2.0.2/ws.js"}]
                                     [:script {:src "https://unpkg.com/htmx.org@2.0.7/dist/ext/json-enc.js"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.14"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                    head))))
   body))

(defn app-page
  "Main layout for authenticated application pages.

  This layout includes:
  - Application header (sticky navigation)
  - Toast notification container
  - Error message area (if error present)
  - Flash message support (toasts and errors from redirects)
  - Main content area

  Args:
    ctx - Biff context map containing session, flash, etc.
    opts - Options map with keys:
      :page-title - Page title (appended to app name)
      :error-message - Error message data to display
    body - Page content (hiccup vectors)

  Returns:
    Complete HTML document with integrated UI components"
  [ctx opts & body]
  (let [flash-message (get-in ctx [:session :flash :message])
        flash-error (get-in ctx [:session :flash :error])
        error-message (or (:error-message opts) flash-error)]
    (base-html
     ctx
     opts
     [:body.bg-gray-50.min-h-screen
      (when (bound? #'csrf/*anti-forgery-token*)
        {:hx-headers (cheshire/generate-string
                      {:x-csrf-token csrf/*anti-forgery-token*})})

      ;; Application Header (only for authenticated users)
      (application-header ctx)

      ;; Toast Notification Container
      (toast/toast-container)

      ;; Flash toast (rendered once on page load from redirect)
      (when flash-message
        (toast/toast-html
         {:type (:type flash-message)
          :message (:text flash-message)
          :auto-dismiss true}))

      ;; Error Message Area
      (error-message-area error-message)

      ;; Main Content
      [:main.pb-12
       [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
        body]]])))

(defn page
  "Simple page layout without header (for unauthenticated pages).

  This is compatible with the existing ui/page function for login/signup pages.

  Args:
    ctx - Biff context map
    body - Page content (hiccup vectors)

  Returns:
    Complete HTML document"
  [ctx & body]
  (base-html
   ctx
   {}
   [:.flex-grow]
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})
    body]
   [:.flex-grow]
   [:.flex-grow]))
