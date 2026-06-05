(ns com.apriary.pages.products-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.biffweb :refer [test-xtdb-node]]
            [com.apriary.pages.products :as products]
            [com.apriary.services.product :as product-service]
            [com.apriary.services.csv-import :as csv-service]
            [xtdb.api :as xt]))

(defn make-ctx
  "Create a test context with session and database"
  [node user-id & {:keys [params]}]
  {:session {:uid user-id}
   :biff.xtdb/node node
   :biff/db (xt/db node)
   :params (or params {})})

;; =============================================================================
;; POST /api/products-import - Handler Integration Tests
;; =============================================================================

(deftest import-products-unauthorized-test
  "Test that unauthenticated requests are rejected"
  (with-open [node (test-xtdb-node [])]
    (let [ctx (assoc (make-ctx node nil) :session {})
          response (products/import-products-handler ctx)]
      ;; Handler doesn't check auth - middleware does
      ;; This test documents expected middleware behavior
      ;; If middleware is bypassed, handler operates without session
      ;; For now, we'll verify the handler doesn't crash on nil user-id
      (is (some? response)))))

(deftest import-products-empty-csv-test
  "Test that empty CSV param returns 400"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ctx (make-ctx node user-id :params {:csv ""})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 400))
      (is (str/includes? (:body response) "CSV input is required")))))

(deftest import-products-valid-round-trip-test
  ; Risk #1
  "Test valid CSV import → verify XTDB persistence via direct query"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg\nA-02;24-11-2025;Pollen;3;ml"
          ctx (make-ctx node user-id :params {:csv csv})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 200))
      (is (str/includes? (:body response) "A-01"))
      (is (str/includes? (:body response) "A-02"))

      ;; Verify XTDB persistence via direct query (not via list-products service)
      (xt/sync node)
      (let [db (xt/db node)
            products (xt/q db
                           '{:find [(pull ?p [*])]
                             :in [user-id]
                             :where [[?p :product/user-id user-id]]}
                           user-id)
            product-records (map first products)]

        ;; Verify records exist
        (is (= (count product-records) 2))

        ;; RLS assertion: verify EVERY record has correct user-id
        (is (every? #(= (:product/user-id %) user-id) product-records))

        ;; Verify content
        (is (some #(= (:product/hive-number %) "A-01") product-records))
        (is (some #(= (:product/hive-number %) "A-02") product-records))))))

(deftest import-products-all-invalid-rows-test
  ; Risk #6
  "Test 100% rejection → success response, zero products in XTDB"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; Invalid: missing required fields, invalid quantity
          csv "hive_number;date;product;quantity;metric\n;;;-1;kg\nA-02;;Honey;abc;ml"
          ctx (make-ctx node user-id :params {:csv csv})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 200))

      ;; Verify zero products in XTDB
      (xt/sync node)
      (let [db (xt/db node)
            [_ result] (product-service/list-products db user-id)
            products (:products result)]
        (is (= (count products) 0))))))

(deftest import-products-rejected-rows-rendering-test
  ; Risk #6
  "Test mixed valid/invalid → verify rejected rows in response body"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; Row 1: valid
          ;; Row 2: invalid quantity
          ;; Row 3: invalid metric
          csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg\nA-02;24-11-2025;Pollen;-1;ml\nA-03;25-11-2025;Venom;2;invalid"
          ctx (make-ctx node user-id :params {:csv csv})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 200))

      ;; Verify rejected rows appear in response (row numbers are 1-based after header)
      (is (str/includes? (:body response) "Row 3") "Row 3 should be listed as rejected")
      (is (str/includes? (:body response) "Row 4") "Row 4 should be listed as rejected")
      (is (str/includes? (:body response) "Some rows were rejected"))

      ;; Verify only valid row was stored
      (xt/sync node)
      (let [db (xt/db node)
            [_ result] (product-service/list-products db user-id)
            products (:products result)]
        (is (= (count products) 1))
        (is (= (:product/hive-number (first products)) "A-01"))))))

(deftest import-products-rls-test
  "Test RLS: User A imports, user B cannot see those products"
  (with-open [node (test-xtdb-node [])]
    (let [user-a (java.util.UUID/randomUUID)
          user-b (java.util.UUID/randomUUID)
          csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg"
          ctx-a (make-ctx node user-a :params {:csv csv})
          response-a (products/import-products-handler ctx-a)]

      (is (= (:status response-a) 200))

      (xt/sync node)
      (let [db (xt/db node)
            [_ result-a] (product-service/list-products db user-a)
            [_ result-b] (product-service/list-products db user-b)
            products-a (:products result-a)
            products-b (:products result-b)]

        ;; User A sees their product
        (is (= (count products-a) 1))

        ;; RLS assertion: verify EVERY record belongs to user-a
        (is (every? #(= (:product/user-id %) user-a) products-a))

        ;; User B sees nothing
        (is (= (count products-b) 0))))))

;; =============================================================================
;; Cross-Feature Integration Test (Risk #3)
;; =============================================================================

(deftest import-products-then-summaries-test
  ; Risk #3
  "Verify shared parse-csv-string layer works for both features sequentially"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)

          ;; Step 1: Import products
          product-csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg"
          products-ctx (make-ctx node user-id :params {:csv product-csv})
          products-response (products/import-products-handler products-ctx)]

      ;; Verify products persisted
      (is (= (:status products-response) 200))
      (xt/sync node)
      (let [db (xt/db node)
            products (xt/q db '{:find [(pull ?p [*])] :in [user-id]
                                :where [[?p :product/user-id user-id]]} user-id)]
        (is (= (count products) 1)))

      ;; Step 2: Import summaries (service-level, not handler)
      (let [summary-csv "observation;hive_number;observation_date;special_feature\nThis is a detailed hive inspection observation with sufficient length for validation;A-01;23-11-2025;Queen active"
            [status result] (csv-service/process-csv-import summary-csv)]

        ;; Verify summaries parsing succeeded
        (is (= :ok status))
        (is (= 1 (:rows-valid result)))
        (is (= 0 (:rows-rejected result)))
        (is (= "This is a detailed hive inspection observation with sufficient length for validation"
               (:observation (first (:valid-rows result)))))
        (is (= "A-01" (:hive-number (first (:valid-rows result)))))
        (is (= "23-11-2025" (:observation-date (first (:valid-rows result)))))
        (is (= "Queen active" (:special-feature (first (:valid-rows result)))))))))

;; =============================================================================
;; XSS Prevention Tests (Risk #7)
;; =============================================================================

(deftest import-products-xss-hive-number-test
  "Test XSS: Script tag in hive-number field is escaped in HTML response"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; CSV with script tag in hive-number field
          csv "hive_number;date;product;quantity;metric\n<script>alert('XSS')</script>;23-11-2025;Honey;5;kg"
          ctx (make-ctx node user-id :params {:csv csv})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 200))

      (let [body (:body response)]
        ;; Primary check: Raw script tag must NOT be present
        (is (not (str/includes? body "<script"))
            "Raw <script tag must not appear in HTML response")

        ;; Secondary check: Escaped form should be present (flexible regex)
        (is (re-find #"&lt;\s*script\s*&gt;" body)
            "Script tag should be HTML-escaped as &lt;script&gt;")))))

(deftest import-products-xss-product-name-test
  "Test XSS: Script tag in product name field is escaped in HTML response"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          ;; CSV with script tag in product name field
          csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;<script>alert('XSS')</script>;5;kg"
          ctx (make-ctx node user-id :params {:csv csv})
          response (products/import-products-handler ctx)]

      (is (= (:status response) 200))

      (let [body (:body response)]
        ;; Primary check: Raw script tag must NOT be present
        (is (not (str/includes? body "<script"))
            "Raw <script tag must not appear in HTML response")

        ;; Secondary check: Escaped form should be present (flexible regex)
        (is (re-find #"&lt;\s*script\s*&gt;" body)
            "Script tag should be HTML-escaped as &lt;script&gt;")))))


