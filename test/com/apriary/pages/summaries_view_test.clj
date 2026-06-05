(ns com.apriary.pages.summaries-view-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.pages.summaries-view :as summaries-view]
            [xtdb.api :as xt]))

(defn make-ctx
  "Create a test context with session and database"
  [node user-id & {:keys [body-params]}]
  {:session {:uid user-id}
   :biff.xtdb/node node
   :biff/db (xt/db node)
   :body-params (or body-params {})})

;; =============================================================================
;; POST /api/summaries-import - HTMX Handler Integration Tests
;; =============================================================================

(deftest import-csv-xss-observation-field-test
  "Test XSS: Script tag in observation field is escaped in HTML response"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (random-uuid)
          ;; CSV with script tag in observation field (padded to meet 50-char minimum)
          csv "observation;hive_number;observation_date;special_feature\n<script>alert('XSS')</script> padded with extra text to meet fifty char minimum;A-01;23-11-2025;Queen active"
          ctx (make-ctx node user-id :body-params {:csv csv})
          response (summaries-view/import-csv-htmx-handler ctx)]

      (is (= (:status response) 201))

      (let [body (:body response)]
        ;; Primary check: Raw script tag must NOT be present
        (is (not (str/includes? body "<script"))
            "Raw <script tag must not appear in HTML response")

        ;; Secondary check: Escaped form should be present (flexible regex)
        (is (re-find #"&lt;\s*script\s*&gt;" body)
            "Script tag should be HTML-escaped as &lt;script&gt;"))

      ;; Database-level verification: prove malicious content stored verbatim
      ;; (Escaping happens at render time via Rum, not during CSV parsing)
      (xt/sync node)
      (let [db (xt/db node)
            summaries (xt/q db
                            '{:find [(pull ?s [:summary/content])]
                              :in [user-id]
                              :where [[?s :summary/user-id user-id]]}
                            user-id)
            summary-content (:summary/content (ffirst summaries))]
        ;; Verify raw content in DB contains the script tag (not escaped)
        (is (str/includes? summary-content "<script>alert('XSS')</script>")
            "XTDB should store raw content with script tags - escaping happens at render time")
        ;; Verify full content preservation (not just malicious fragment)
        (is (str/includes? summary-content "padded with extra text to meet fifty char minimum")
            "Full observation content should be preserved in XTDB")))))
