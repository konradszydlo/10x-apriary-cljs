(ns com.apriary.ui.header
  "Application header component with navigation and actions."
  (:require [com.biffweb :as biff]))

(defn application-header
  "Renders the sticky application header with navigation and actions.

  Displays:
  - App name/logo (links to /)
  - New Summary button (links to /summaries-new)
  - Logout button (form submission to /auth/signout)

  Args:
    ctx - Biff context map containing session, request, database connection"
  [{:keys [session] :as _ctx}]
  (when (:uid session)
    [:header.sticky.top-0.z-50.bg-white.shadow-sm
     [:nav.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
      [:div.flex.items-center.justify-between.h-16
       ;; Left: App Name/Logo
       [:div.flex-shrink-0
        [:a.flex.items-center
         {:href "/"
          :hx-boost "true"
          :aria-label "Apriary Summary Home"}
         [:span.text-xl.font-bold.text-gray-900 "Apriary Summary"]]]

       ;; Right: Actions
       [:div.flex.items-center.gap-4
        ;; New Summary Button
        [:a.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2.focus-visible:outline-blue-600
         {:href "/summaries-new"
          :hx-boost "true"}
         "+ New Summary"]

        ;; Logout Form
        (biff/form
         {:action "/auth/signout"
          :class "inline"}
         [:button.text-gray-700.hover:text-gray-900.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2.focus-visible:outline-gray-700
          {:type "submit"
           :aria-label "Logout"}
          "Logout"])]]]]))
