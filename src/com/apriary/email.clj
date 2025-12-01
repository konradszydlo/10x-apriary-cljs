(ns com.apriary.email
  (:require [clojure.tools.logging :as log]))

(defn password-reset-email-html
  "HTML email template for password reset"
  [reset-link]
  (str
   "<h2>Reset your Apriary Summary password</h2>"
   "<p>You requested to reset your password. Click the link below to set a new password:</p>"
   "<p><a href=\"" reset-link "\" style=\"display: inline-block; padding: 12px 24px; "
   "background-color: #2563eb; color: white; text-decoration: none; "
   "border-radius: 6px;\">Reset Password</a></p>"
   "<p>This link will expire in 1 hour.</p>"
   "<p>If you didn't request this, you can safely ignore this email.</p>"
   "<p>—<br>The Apriary Summary Team</p>"))

(defn password-reset-email-text
  "Plain text email template for password reset"
  [reset-link]
  (str
   "Reset your Apriary Summary password\n\n"
   "You requested to reset your password. Click the link below to set a new password:\n\n"
   reset-link "\n\n"
   "This link will expire in 1 hour.\n\n"
   "If you didn't request this, you can safely ignore this email.\n\n"
   "—\n"
   "The Apriary Summary Team"))

(defn send-password-reset-email
  "Send password reset email (MVP: logs to console instead of actually sending)"
  [ctx to-email reset-link]
  ;; MVP: Print email to console instead of sending
  (println "\n========================================")
  (println "PASSWORD RESET EMAIL")
  (println "========================================")
  (println "To:" to-email)
  (println "Subject: Reset your Apriary Summary password")
  (println "----------------------------------------")
  (println (password-reset-email-text reset-link))
  (println "========================================\n")
  (log/info "Password reset email (console output)" :to to-email))

;; Future implementation for real email sending:
(comment
  (defn send-password-reset-email-real
    "Send password reset email via Postmark"
    [{:keys [biff.postmark/api-key com.apriary/from-email] :as ctx}
     to-email
     reset-link]
    (biff/send-email
     {:to to-email
      :from from-email
      :subject "Reset your Apriary Summary password"
      :html (password-reset-email-html reset-link)
      :text (password-reset-email-text reset-link)
      :postmark-api-key api-key})))
