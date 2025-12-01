(ns com.apriary.pages.home
  (:require [com.biffweb :as biff]
            [com.apriary.middleware :as mid]
            [com.apriary.ui :as ui]
            [com.apriary.settings :as settings]))

(defn home-page [{:keys [params] :as ctx}]
  (ui/page
   ctx
   (biff/form
    {:action "/auth/signup"}
    [:h2.text-2xl.font-bold (str "Sign up for " settings/app-name)]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "email"} "Email"]
     [:input#email.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "email"
       :type "email"
       :autocomplete "email"
       :placeholder "Enter your email address"
       :required true}]]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "password"} "Password"]
     [:input#password.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "password"
       :type "password"
       :autocomplete "new-password"
       :placeholder "At least 8 characters"
       :required true}]
     [:.text-xs.text-gray-500.mt-1 "Must be at least 8 characters"]]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "password-confirm"} "Confirm Password"]
     [:input#password-confirm.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "password-confirm"
       :type "password"
       :autocomplete "new-password"
       :placeholder "Re-enter your password"
       :required true}]]
    [:.h-3]
    [:button.btn.w-full {:type "submit"} "Sign up"]
    (when-some [error (:error params)]
      [:<>
       [:.h-1]
       [:.text-sm.text-red-600
        (case error
          "invalid-email" "Invalid email address. Please try again."
          "invalid-password" "Password must be at least 8 characters."
          "password-mismatch" "Passwords do not match. Please try again."
          "email-exists" "An account with this email already exists. Please sign in."
          "There was an error.")]])
    [:.h-1]
    [:.text-sm "Already have an account? " [:a.link {:href "/signin"} "Sign in"] "."])))

(defn signin-page [{:keys [params] :as ctx}]
  (ui/page
   ctx
   (biff/form
    {:action "/auth/signin"}
    [:h2.text-2xl.font-bold "Sign in to " settings/app-name]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "email"} "Email"]
     [:input#email.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "email"
       :type "email"
       :autocomplete "email"
       :placeholder "Enter your email address"
       :required true}]]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "password"} "Password"]
     [:input#password.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "password"
       :type "password"
       :autocomplete "current-password"
       :placeholder "Enter your password"
       :required true}]
     [:.text-sm.text-right.mt-1
      [:a.link {:href "/forgot-password"} "Forgot password?"]]]
    [:.h-3]
    [:button.btn.w-full {:type "submit"} "Sign in"]
    (when-some [error (:error params)]
      [:<>
       [:.h-1]
       [:.text-sm.text-red-600
        (case error
          "invalid-email" "Invalid email address."
          "invalid-password" "Password is required."
          "invalid-credentials" "Invalid email or password."
          "not-signed-in" "You must be signed in to view that page."
          "There was an error.")]])
    [:.h-1]
    [:.text-sm "Don't have an account yet? " [:a.link {:href "/"} "Sign up"] "."])))

(defn forgot-password-page [{:keys [params] :as ctx}]
  (ui/page
   ctx
   (biff/form
    {:action "/auth/send-password-reset"}
    [:h2.text-2xl.font-bold "Reset your password"]
    [:.h-3]
    [:p.text-gray-600 "Enter your email address and we'll send you a link to reset your password."]
    [:.h-3]
    [:div
     [:label.block.text-sm.font-medium.mb-2 {:for "email"} "Email"]
     [:input#email.w-full.px-3.py-2.border.border-gray-300.rounded-md
      {:name "email"
       :type "email"
       :autocomplete "email"
       :placeholder "Enter your email address"
       :required true}]]
    [:.h-3]
    [:button.btn.w-full {:type "submit"} "Send reset link"]
    (when-some [error (:error params)]
      [:<>
       [:.h-1]
       [:.text-sm.text-red-600
        (case error
          "invalid-email" "Invalid email address."
          "There was an error.")]])
    [:.h-1]
    [:.text-sm [:a.link {:href "/signin"} "Back to sign in"]])))

(defn password-reset-sent-page [ctx]
  (ui/page
   ctx
   [:div
    [:h2.text-2xl.font-bold "Check your email"]
    [:.h-3]
    [:p.text-gray-600 "If an account exists with that email address, you'll receive a password reset link shortly."]
    [:.h-2]
    [:p.text-sm.text-gray-500 "The link will expire in 1 hour."]
    [:.h-3]
    [:a.btn.w-full.text-center.block {:href "/signin"} "Return to sign in"]]))

(defn reset-password-page [{:keys [params] :as ctx}]
  (let [token (:token params)]
    (if-not token
      ;; Redirect to forgot-password if no token provided
      {:status 303
       :headers {"location" "/forgot-password"}}
      (ui/page
       ctx
       (biff/form
        {:action "/auth/reset-password"}
        [:h2.text-2xl.font-bold "Set new password"]
        [:.h-3]
        [:div
         [:label.block.text-sm.font-medium.mb-2 {:for "password"} "New Password"]
         [:input#password.w-full.px-3.py-2.border.border-gray-300.rounded-md
          {:name "password"
           :type "password"
           :autocomplete "new-password"
           :placeholder "At least 8 characters"
           :required true}]
         [:.text-xs.text-gray-500.mt-1 "Must be at least 8 characters"]]
        [:.h-3]
        [:div
         [:label.block.text-sm.font-medium.mb-2 {:for "password-confirm"} "Confirm New Password"]
         [:input#password-confirm.w-full.px-3.py-2.border.border-gray-300.rounded-md
          {:name "password-confirm"
           :type "password"
           :autocomplete "new-password"
           :placeholder "Re-enter your password"
           :required true}]]
        [:input {:type "hidden" :name "token" :value token}]
        [:.h-3]
        [:button.btn.w-full {:type "submit"} "Reset password"]
        (when-some [error (:error params)]
          [:<>
           [:.h-1]
           [:.text-sm.text-red-600
            (case error
              "invalid-token" "This password reset link is invalid or has expired. Please request a new one."
              "token-expired" "This password reset link has expired. Please request a new one."
              "token-used" "This password reset link has already been used. Please request a new one."
              "invalid-password" "Password must be at least 8 characters."
              "password-mismatch" "Passwords do not match."
              "There was an error.")]
           [:.h-1]
           (when (contains? #{"invalid-token" "token-expired" "token-used"} error)
             [:.text-sm [:a.link {:href "/forgot-password"} "Request a new reset link"]])]))))))

(defn password-reset-success-page [ctx]
  (ui/page
   ctx
   [:div
    [:h2.text-2xl.font-bold "Password reset successful"]
    [:.h-3]
    [:p.text-gray-600 "Your password has been successfully reset. You can now sign in with your new password."]
    [:.h-3]
    [:a.btn.w-full.text-center.block {:href "/signin"} "Sign in"]]))

(def module
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/"                  {:get home-page}]]
            ["/signin"             {:get signin-page}]
            ["/forgot-password"    {:get forgot-password-page}]
            ["/password-reset-sent" {:get password-reset-sent-page}]
            ["/reset-password"     {:get reset-password-page}]
            ["/password-reset-success" {:get password-reset-success-page}]]})
