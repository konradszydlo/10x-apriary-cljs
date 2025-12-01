(ns repl
  (:require [com.apriary :as main]
            [com.biffweb :as biff :refer [q]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; REPL-driven development
;; ----------------------------------------------------------------------------------------
;; If you're new to REPL-driven development, Biff makes it easy to get started: whenever
;; you save a file, your changes will be evaluated. Biff is structured so that in most
;; cases, that's all you'll need to do for your changes to take effect. (See main/refresh
;; below for more details.)
;;
;; The `clj -M:dev dev` command also starts an nREPL server on port 7888, so if you're
;; already familiar with REPL-driven development, you can connect to that with your editor.
;;
;; If you're used to jacking in with your editor first and then starting your app via the
;; REPL, you will need to instead connect your editor to the nREPL server that `clj -M:dev
;; dev` starts. e.g. if you use emacs, instead of running `cider-jack-in`, you would run
;; `cider-connect`. See "Connecting to a Running nREPL Server:"
;; https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server
;; ----------------------------------------------------------------------------------------

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn add-fixtures []
  (biff/submit-tx (get-context)
    (-> (io/resource "fixtures.edn")
        slurp
        edn/read-string)))

(defn check-config []
  (let [prod-config (biff/use-aero-config {:biff.config/profile "prod"})
        dev-config  (biff/use-aero-config {:biff.config/profile "dev"})
        ;; Add keys for any other secrets you've added to resources/config.edn
        secret-keys [:biff.middleware/cookie-secret
                     :biff/jwt-secret
                     ; ...
                     ]
        get-secrets (fn [{:keys [biff/secret] :as config}]
                      (into {}
                            (map (fn [k]
                                   [k (secret k)]))
                            secret-keys))]
    {:prod-config prod-config
     :dev-config dev-config
     :prod-secrets (get-secrets prod-config)
     :dev-secrets (get-secrets dev-config)}))

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, config.env, or deps.edn.
  (main/refresh)

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [biff/db] :as ctx} (get-context)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))

  ;; Update an existing user's email address
  (let [{:keys [biff/db] :as ctx} (get-context)
        user-id (biff/lookup-id db :user/email "hello@example.com")]
    (biff/submit-tx ctx
      [{:db/doc-type :user
        :xt/id user-id
        :db/op :update
        :user/email "new.address@example.com"}]))

  (sort (keys (get-context)))

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"}))

  ;; Test token generation and hashing functions
  (require '[com.apriary.auth :as auth])

  ;; Test 1: Generate tokens are unique
  (let [token1 (auth/generate-secure-token)
        token2 (auth/generate-secure-token)]
    {:token1 token1
     :token2 token2
     :unique? (not= token1 token2)})

  ;; Test 2: Hash token is consistent
  (let [token (auth/generate-secure-token)
        hash1 (auth/hash-token token)
        hash2 (auth/hash-token token)]
    {:token token
     :hash hash1
     :consistent? (= hash1 hash2)})

  ;; Test 3: Different tokens produce different hashes
  (let [token1 (auth/generate-secure-token)
        token2 (auth/generate-secure-token)
        hash1 (auth/hash-token token1)
        hash2 (auth/hash-token token2)]
    {:different-hashes? (not= hash1 hash2)})

  ;; Test 4: Time helper functions
  (let [current-time (auth/now)
        future-time (auth/plus-hours current-time 1)
        is-after? (auth/after? future-time current-time)]
    {:current current-time
     :future future-time
     :is-after? is-after?})

  ;; Test 5: Email templates and console output
  (require '[com.apriary.email :as email])

  ;; Test password reset email output to console
  (let [test-token (auth/generate-secure-token)
        reset-link (str "http://localhost:8080/reset-password?token=" test-token)]
    (email/send-password-reset-email (get-context) "test@example.com" reset-link)
    {:test-completed true :reset-link reset-link})

  ;; Test 6: End-to-end password reset flow
  (let [ctx (get-context)
        {:keys [biff/db]} ctx
        test-email "password-reset-test@example.com"
        original-password "original123"
        new-password "newpassword456"]

    ;; Step 1: Create test user
    (let [user-id (random-uuid)]
      (biff/submit-tx ctx
                      [{:db/op :create
                        :db/doc-type :user
                        :xt/id user-id
                        :user/id user-id
                        :user/email test-email
                        :user/password-hash (auth/hash-password original-password)
                        :user/joined-at (java.util.Date.)}]))

    ;; Step 2: Request password reset (simulating the handler)
    (let [user (biff/lookup db :user/email test-email)
          token-id (random-uuid)
          raw-token (auth/generate-secure-token)
          hashed-token (auth/hash-token raw-token)
          expires-at (auth/plus-hours (auth/now) 1)]

      ;; Create reset token
      (biff/submit-tx ctx
                      [{:db/op :create
                        :db/doc-type :password-reset-token
                        :xt/id token-id
                        :password-reset-token/id token-id
                        :password-reset-token/user-id (:user/id user)
                        :password-reset-token/token hashed-token
                        :password-reset-token/expires-at expires-at
                        :password-reset-token/created-at (auth/now)
                        :password-reset-token/used-at nil}])

      ;; Print email to console
      (email/send-password-reset-email
       ctx
       test-email
       (str "http://localhost:8080/reset-password?token=" raw-token))

      ;; Step 3: Verify token was created
      (let [token-record (biff/lookup db :password-reset-token/token hashed-token)]
        (println "\n=== PASSWORD RESET FLOW TEST ===")
        (println "Step 1: User created:" test-email)
        (println "Step 2: Token created and email printed to console")
        (println "Step 3: Token found in DB:" (some? token-record))
        (println "Token expires at:" (:password-reset-token/expires-at token-record))

        ;; Step 4: Use the token to reset password (simulating the handler)
        (when token-record
          (biff/submit-tx ctx
                          [{:db/op :update
                            :db/doc-type :user
                            :xt/id (:user/id user)
                            :user/password-hash (auth/hash-password new-password)}
                           {:db/op :update
                            :db/doc-type :password-reset-token
                            :xt/id (:xt/id token-record)
                            :password-reset-token/used-at (auth/now)}])

          ;; Step 5: Verify new password works
          (let [updated-user (biff/lookup db :user/email test-email)
                old-password-works (auth/verify-password original-password (:user/password-hash updated-user))
                new-password-works (auth/verify-password new-password (:user/password-hash updated-user))]
            (println "Step 4: Password updated")
            (println "Step 5: Old password works:" old-password-works)
            (println "Step 5: New password works:" new-password-works)
            (println "Step 6: Token marked as used:" (some? (:password-reset-token/used-at (biff/lookup db :password-reset-token/token hashed-token))))
            (println "=== TEST COMPLETE ===\n")

            {:test-completed true
             :old-password-fails (not old-password-works)
             :new-password-works new-password-works
             :raw-token raw-token}))))))

  ;; ====================
  ;; SECURITY TESTS
  ;; ====================

  ;; Test 7: CSRF Protection - Verify CSRF token is required
  (comment
    "CSRF protection test - manual verification required:
     1. Forms created with biff/form automatically include CSRF token
     2. POST requests without valid CSRF token are rejected by middleware
     3. All auth forms use biff/form helper

     To test manually:
     - Try submitting signup/signin form with invalid or missing CSRF token
     - Should receive 403 Forbidden error

     Forms protected:
     - /auth/signup (signup form)
     - /auth/signin (signin form)
     - /auth/signout (logout form)
     - /auth/send-password-reset (forgot password form)
     - /auth/reset-password (reset password form)")

  ;; Test 8: Route Protection - Authenticated routes
  (require '[com.apriary.middleware :as mid])

  ;; Test that wrap-signed-in redirects unauthenticated users
  (let [unauthenticated-ctx {:session {}}  ; No :uid in session
        handler (mid/wrap-signed-in (fn [ctx] {:status 200 :body "Protected content"}))
        response (handler unauthenticated-ctx)]
    (println "\n=== ROUTE PROTECTION TEST - Authenticated Routes ===")
    (println "Test: Unauthenticated user accessing protected route")
    (println "Expected: 303 redirect to /signin")
    (println "Result:")
    (println "  Status:" (:status response))
    (println "  Location:" (get-in response [:headers "location"]))
    (println "  Success:" (and (= 303 (:status response))
                                (= "/signin?error=not-signed-in" (get-in response [:headers "location"]))))
    (println "=================================\n")
    response)

  ;; Test that wrap-signed-in allows authenticated users
  (let [authenticated-ctx {:session {:uid (random-uuid)}}
        handler (mid/wrap-signed-in (fn [ctx] {:status 200 :body "Protected content"}))
        response (handler authenticated-ctx)]
    (println "\n=== ROUTE PROTECTION TEST - Authenticated User ===")
    (println "Test: Authenticated user accessing protected route")
    (println "Expected: 200 OK with content")
    (println "Result:")
    (println "  Status:" (:status response))
    (println "  Body:" (:body response))
    (println "  Success:" (= 200 (:status response)))
    (println "=================================\n")
    response)

  ;; Test 9: Route Protection - Unauthenticated routes (redirect if signed in)
  (let [authenticated-ctx {:session {:uid (random-uuid)}}
        handler (mid/wrap-redirect-signed-in (fn [ctx] {:status 200 :body "Signup page"}))
        response (handler authenticated-ctx)]
    (println "\n=== ROUTE PROTECTION TEST - Signup Page When Authenticated ===")
    (println "Test: Authenticated user accessing signup page")
    (println "Expected: 303 redirect to /app")
    (println "Result:")
    (println "  Status:" (:status response))
    (println "  Location:" (get-in response [:headers "location"]))
    (println "  Success:" (and (= 303 (:status response))
                                (= "/app" (get-in response [:headers "location"]))))
    (println "=================================\n")
    response)

  ;; Test that wrap-redirect-signed-in allows unauthenticated users
  (let [unauthenticated-ctx {:session {}}
        handler (mid/wrap-redirect-signed-in (fn [ctx] {:status 200 :body "Signup page"}))
        response (handler unauthenticated-ctx)]
    (println "\n=== ROUTE PROTECTION TEST - Signup Page When Unauthenticated ===")
    (println "Test: Unauthenticated user accessing signup page")
    (println "Expected: 200 OK with signup page")
    (println "Result:")
    (println "  Status:" (:status response))
    (println "  Body:" (:body response))
    (println "  Success:" (= 200 (:status response)))
    (println "=================================\n")
    response)

  ;; Test 10: Password Security - Verify BCrypt behavior
  (let [password "testpassword123"
        hash1 (auth/hash-password password)
        hash2 (auth/hash-password password)]
    (println "\n=== PASSWORD SECURITY TEST ===")
    (println "Test: BCrypt generates unique hashes for same password")
    (println "Password:" password)
    (println "Hash 1:" (subs hash1 0 20) "...")
    (println "Hash 2:" (subs hash2 0 20) "...")
    (println "Hashes different:" (not= hash1 hash2))
    (println "Both verify correctly:"
             (and (auth/verify-password password hash1)
                  (auth/verify-password password hash2)))
    (println "Wrong password rejected:" (not (auth/verify-password "wrongpassword" hash1)))
    (println "=================================\n")
    {:unique-hashes (not= hash1 hash2)
     :both-verify (and (auth/verify-password password hash1)
                       (auth/verify-password password hash2))
     :rejects-wrong (not (auth/verify-password "wrongpassword" hash1))})

  ;; Test 11: Token Security - Verify token uniqueness and hashing
  (let [token1 (auth/generate-secure-token)
        token2 (auth/generate-secure-token)
        hash1 (auth/hash-token token1)
        hash2-same (auth/hash-token token1)
        hash2-diff (auth/hash-token token2)]
    (println "\n=== TOKEN SECURITY TEST ===")
    (println "Test: Secure token generation and hashing")
    (println "Token 1 length:" (count token1) "chars")
    (println "Token 2 length:" (count token2) "chars")
    (println "Tokens are unique:" (not= token1 token2))
    (println "Hash is consistent:" (= hash1 hash2-same))
    (println "Different tokens → different hashes:" (not= hash1 hash2-diff))
    (println "Hash length (SHA-256 hex):" (count hash1) "chars (expected: 64)")
    (println "=================================\n")
    {:tokens-unique (not= token1 token2)
     :hash-consistent (= hash1 hash2-same)
     :different-hashes (not= hash1 hash2-diff)
     :hash-length-correct (= 64 (count hash1))})

  ;; Test 12: Email Enumeration Prevention
  (comment
    "Email enumeration prevention test - manual verification required:

     Test procedure:
     1. Request password reset for existing email
     2. Request password reset for non-existing email
     3. Compare responses and timing

     Expected behavior:
     - Both requests return 303 redirect to /password-reset-sent
     - Both show same generic message
     - Response time should be similar (no timing attacks)
     - No indication whether email exists or not

     Security benefit:
     - Attackers cannot determine which emails are registered
     - Prevents user enumeration attacks"))
