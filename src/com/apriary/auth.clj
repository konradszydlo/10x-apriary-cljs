(ns com.apriary.auth
  (:require [com.biffweb :as biff]
            [clojure.string :as str]
            [com.apriary.email :as email])
  (:import [org.mindrot.jbcrypt BCrypt]))

(defn hash-password [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn verify-password [password hash]
  (BCrypt/checkpw password hash))

(defn valid-email? [email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)))

(defn valid-password? [password]
  (and (string? password)
       (>= (count password) 8)))

;; Token generation and hashing for password reset

(defn generate-secure-token
  "Generate cryptographically secure random token"
  []
  (let [random-bytes (byte-array 32)
        _ (.nextBytes (java.security.SecureRandom.) random-bytes)]
    ;; Encode as URL-safe base64
    (.encodeToString (java.util.Base64/getUrlEncoder) random-bytes)))

(defn hash-token
  "Hash token using SHA-256"
  [token]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes token "UTF-8"))]
    ;; Convert to hex string
    (apply str (map #(format "%02x" %) hash-bytes))))

;; Time helper functions

(defn now [] (java.util.Date.))

(defn plus-hours
  "Add hours to a date"
  [date hours]
  (java.util.Date. (+ (.getTime date) (* hours 60 60 1000))))

(defn after?
  "Check if date1 is after date2"
  [date1 date2]
  (.after date1 date2))

(defn get-base-url
  "Get application base URL from config"
  [{:keys [biff/base-url]}]
  base-url)

(defn signup [{:keys [params biff/db] :as ctx}]
  (let [email (str/trim (:email params))
        password (:password params)
        password-confirm (:password-confirm params)
        existing-user (biff/lookup db :user/email email)]
    (cond
      (not (valid-email? email))
      {:status 303
       :headers {"location" "/?error=invalid-email"}}

      (not (valid-password? password))
      {:status 303
       :headers {"location" "/?error=invalid-password"}}

      (not= password password-confirm)
      {:status 303
       :headers {"location" "/?error=password-mismatch"}}

      existing-user
      {:status 303
       :headers {"location" "/?error=email-exists"}}

      :else
      (let [user-id (random-uuid)
            password-hash (hash-password password)]
        (biff/submit-tx ctx
                        [{:db/op :create
                          :db/doc-type :user
                          :xt/id user-id
                          :user/id user-id
                          :user/email email
                          :user/password-hash password-hash
                          :user/joined-at (java.util.Date.)}])
        {:status 303
         :headers {"location" "/app"}
         :session {:uid user-id}}))))

(defn signin [{:keys [params biff/db]}]
  (let [email (str/trim (:email params))
        password (:password params)
        user (biff/lookup db :user/email email)]
    (cond
      (not (valid-email? email))
      {:status 303
       :headers {"location" "/signin?error=invalid-email"}}

      (not password)
      {:status 303
       :headers {"location" "/signin?error=invalid-password"}}

      (not user)
      {:status 303
       :headers {"location" "/signin?error=invalid-credentials"}}

      (not (verify-password password (:user/password-hash user)))
      {:status 303
       :headers {"location" "/signin?error=invalid-credentials"}}

      :else
      {:status 303
       :headers {"location" "/app"}
       :session {:uid (:xt/id user)}})))

(defn signout [_ctx]
  {:status 303
   :headers {"location" "/signin"}
   :session nil})

(defn send-password-reset [{:keys [params biff/db] :as ctx}]
  (let [email (str/trim (:email params))
        user (biff/lookup db :user/email email)]

    ;; Guard clause: invalid email format
    (if-not (valid-email? email)
      {:status 303
       :headers {"location" "/forgot-password?error=invalid-email"}}

      ;; Always redirect to success page (prevent email enumeration)
      (do
        ;; Only process if user exists
        (when user
          (let [token-id (random-uuid)
                raw-token (generate-secure-token)
                hashed-token (hash-token raw-token)
                expires-at (plus-hours (now) 1)]

            ;; Create reset token record
            (biff/submit-tx
             ctx
             [{:db/op :create
               :db/doc-type :password-reset-token
               :xt/id token-id
               :password-reset-token/id token-id
               :password-reset-token/user-id (:user/id user)
               :password-reset-token/token hashed-token
               :password-reset-token/expires-at expires-at
               :password-reset-token/created-at (now)
               :password-reset-token/used-at nil}])

            ;; MVP: Print email to console (no actual sending)
            (email/send-password-reset-email
             ctx
             email
             (str (get-base-url ctx) "/reset-password?token=" raw-token))))

        ;; Always show success
        {:status 303
         :headers {"location" "/password-reset-sent"}}))))

(defn reset-password [{:keys [params biff/db] :as ctx}]
  (let [raw-token (:token params)
        password (:password params)
        password-confirm (:password-confirm params)
        hashed-token (hash-token raw-token)

        ;; Find token record
        token-record (biff/lookup db :password-reset-token/token hashed-token)]

    (cond
      ;; Guard clause: token not found
      (not token-record)
      {:status 303
       :headers {"location" (str "/reset-password?token=" raw-token "&error=invalid-token")}}

      ;; Guard clause: token expired
      (after? (now) (:password-reset-token/expires-at token-record))
      {:status 303
       :headers {"location" (str "/reset-password?token=" raw-token "&error=token-expired")}}

      ;; Guard clause: token already used
      (some? (:password-reset-token/used-at token-record))
      {:status 303
       :headers {"location" (str "/reset-password?token=" raw-token "&error=token-used")}}

      ;; Guard clause: invalid password
      (not (valid-password? password))
      {:status 303
       :headers {"location" (str "/reset-password?token=" raw-token "&error=invalid-password")}}

      ;; Guard clause: password mismatch
      (not= password password-confirm)
      {:status 303
       :headers {"location" (str "/reset-password?token=" raw-token "&error=password-mismatch")}}

      ;; Happy path: update password
      :else
      (let [user-id (:password-reset-token/user-id token-record)
            new-password-hash (hash-password password)]

        ;; Update user password and mark token as used
        (biff/submit-tx
         ctx
         [{:db/op :update
           :db/doc-type :user
           :xt/id user-id
           :user/password-hash new-password-hash}
          {:db/op :update
           :db/doc-type :password-reset-token
           :xt/id (:xt/id token-record)
           :password-reset-token/used-at (now)}])

        {:status 303
         :headers {"location" "/password-reset-success"}}))))

(def module
  {:routes ["/auth"
            ["/signup" {:post signup}]
            ["/signin" {:post signin}]
            ["/signout" {:post signout}]
            ["/send-password-reset" {:post send-password-reset}]
            ["/reset-password" {:post reset-password}]]})
