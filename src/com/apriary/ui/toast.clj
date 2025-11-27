(ns com.apriary.ui.toast
  "Toast notification utilities for Apriary Summary application.

  Usage:

  In route handlers, include toast in response using toast-oob:

    (require '[com.apriary.ui.toast :as toast])

    {:status 200
     :body (str
             (main-content)
             (toast/toast-oob
               {:type :success
                :message \"Operation completed successfully\"}))}

  For flash messages after redirects:

    {:status 303
     :headers {\"Location\" \"/\"}
     :flash {:message {:type :success
                       :text \"Summary created\"}}}")

(defn- toast-icon
  "Returns SVG icon based on toast type.

  Args:
    type - One of :success, :error, or :info"
  [type]
  (case type
    :success
    [:svg.h-6.w-6.text-green-600
     {:xmlns "http://www.w3.org/2000/svg"
      :viewBox "0 0 24 24"
      :fill "currentColor"
      :aria-hidden "true"}
     [:path {:fill-rule "evenodd"
             :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm13.36-1.814a.75.75 0 10-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 00-1.06 1.06l2.25 2.25a.75.75 0 001.14-.094l3.75-5.25z"
             :clip-rule "evenodd"}]]

    :error
    [:svg.h-6.w-6.text-red-600
     {:xmlns "http://www.w3.org/2000/svg"
      :viewBox "0 0 24 24"
      :fill "currentColor"
      :aria-hidden "true"}
     [:path {:fill-rule "evenodd"
             :d "M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25zm-1.72 6.97a.75.75 0 10-1.06 1.06L10.94 12l-1.72 1.72a.75.75 0 101.06 1.06L12 13.06l1.72 1.72a.75.75 0 101.06-1.06L13.06 12l1.72-1.72a.75.75 0 10-1.06-1.06L12 10.94l-1.72-1.72z"
             :clip-rule "evenodd"}]]

    :info
    [:svg.h-6.w-6.text-blue-600
     {:xmlns "http://www.w3.org/2000/svg"
      :viewBox "0 0 24 24"
      :fill "currentColor"
      :aria-hidden "true"}
     [:path {:fill-rule "evenodd"
             :d "M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm8.706-1.442c1.146-.573 2.437.463 2.126 1.706l-.709 2.836.042-.02a.75.75 0 01.67 1.34l-.04.022c-1.147.573-2.438-.463-2.127-1.706l.71-2.836-.042.02a.75.75 0 11-.671-1.34l.041-.022zM12 9a.75.75 0 100-1.5.75.75 0 000 1.5z"
             :clip-rule "evenodd"}]]))

(defn- close-icon
  "Returns close (X) icon SVG."
  []
  [:svg.h-5.w-5
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 20 20"
    :fill "currentColor"
    :aria-hidden "true"}
   [:path {:d "M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z"}]])

(defn toast-html
  "Generates toast HTML element.

  Args:
    toast-data - Map with keys:
      :id (optional) - Unique identifier (defaults to random UUID)
      :type - Toast type (:success, :error, :info)
      :message - Main message text
      :auto-dismiss (optional) - Whether to auto-dismiss (defaults based on type)
      :duration-ms (optional) - Duration before auto-dismiss (defaults: 3000 for success, 5000 for info)

  Returns:
    Hiccup vector representing the toast element"
  [{:keys [id type message auto-dismiss duration-ms]}]
  (let [toast-id (or id (str "toast-" (random-uuid)))
        duration (or duration-ms (if (= type :success) 3000 5000))
        auto-dismiss? (if (nil? auto-dismiss)
                        (not= type :error)
                        auto-dismiss)
        bg-color (case type
                   :success "bg-green-100 border-green-500"
                   :error "bg-red-100 border-red-500"
                   :info "bg-blue-100 border-blue-500")
        text-color (case type
                     :success "text-green-800"
                     :error "text-red-800"
                     :info "text-blue-800")]
    [:div.max-w-md.rounded.shadow-lg.p-4.flex.items-start.gap-3.border.transition-all.duration-300
     (merge
      {:id toast-id
       :class bg-color}
      (when auto-dismiss?
        {:hx-trigger (str "load delay:" duration "ms")
         :hx-swap "delete"
         :hx-target (str "#" toast-id)}))

     ;; Icon
     [:div.flex-shrink-0
      (toast-icon type)]

     ;; Message
     [:div.flex-1
      [:p.text-sm.font-medium {:class text-color}
       message]]

     ;; Close button
     (when (or (not auto-dismiss?) (= type :error))
       [:button.flex-shrink-0.text-gray-400.hover:text-gray-600.focus-visible:outline.focus-visible:outline-2.focus-visible:outline-offset-2
        {:type "button"
         :hx-swap "delete"
         :hx-target (str "#" toast-id)
         :aria-label "Close notification"}
        (close-icon)])]))

(defn toast-oob
  "Wraps toast HTML with OOB swap directive for HTMX.

  This allows toasts to be injected into the toast-container from any server response.

  Args:
    toast-data - Map with toast data (see toast-html for keys)

  Returns:
    Hiccup vector with hx-swap-oob attribute"
  [toast-data]
  [:div {:hx-swap-oob "afterbegin:#toast-container"}
   (toast-html toast-data)])

(defn toast-container
  "Renders the toast notification container.

  This is a fixed-position container in the top-right corner that holds all toast notifications.
  Toasts are injected into this container via HTMX OOB swaps from server responses.

  Returns:
    Hiccup vector representing the toast container"
  []
  [:div#toast-container.fixed.top-20.right-4.z-50.space-y-2
   {:aria-live "polite"
    :aria-atomic "false"
    :role "status"
    :style {:max-height "calc(100vh - 100px)"
            :overflow-y "auto"}}])
