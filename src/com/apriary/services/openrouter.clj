(ns com.apriary.services.openrouter
  (:require [clojure.tools.logging :as log]))

;; OpenRouter AI Service (MOCKED for MVP)
;;
;; This service is responsible for generating AI summaries from observation text.
;; For the MVP, the service is MOCKED and returns the observation text as-is.
;; Future implementation will integrate with the actual OpenRouter API.
;;
;; Mock Behavior:
;; - Returns each observation text as the generated summary
;; - Simulates a successful API call
;; - No actual HTTP request is made
;; - Model configuration is read from config but not used
;;
;; Production Implementation (Future):
;; - Will use HTTP client (clj-http or http-kit)
;; - Will call OpenRouter API endpoint
;; - Will handle rate limiting, retries, timeouts
;; - Will parse structured responses from the AI model

;; =============================================================================
;; Mock Configuration
;; =============================================================================

(def ^:private MOCK_ENABLED
  "Flag to enable/disable mocking. For MVP, always true."
  true)

(def ^:private DEFAULT_MODEL
  "Default AI model to use (for metadata only in mock mode)."
  "gpt-4-turbo")

;; =============================================================================
;; Mock Implementation
;; =============================================================================

(defn generate-summaries-batch
  "Generate AI summaries for a batch of observations.

   **MOCK IMPLEMENTATION**: For MVP, this function returns the observation
   text as-is without calling the actual OpenRouter API.

   In production, this will:
   1. Make HTTP request to OpenRouter API
   2. Send all observations in a batch request
   3. Parse AI-generated summaries from response
   4. Handle errors and retries

   Params:
   - observations: Sequence of maps with :observation key
   - model: String name of AI model to use (optional, uses config default)

   Returns:
   - [:ok {:summaries [...] :model str :duration-ms int}] on success
   - [:error {:code ... :message ...}] on failure

   Each summary in the result is a map with:
   - :content String - the generated summary text
   - :observation String - the original observation (for reference)
   - :hive-number String - from input (optional)
   - :observation-date String - from input (optional)
   - :special-feature String - from input (optional)"
  [observations & {:keys [model] :or {model DEFAULT_MODEL}}]
  (try
    (log/info "Generating summaries (MOCK)"
              :count (count observations)
              :model model
              :mock-enabled MOCK_ENABLED)

    (when MOCK_ENABLED
      ;; MOCK: Return observations as summaries
      (let [start-time (System/currentTimeMillis)

            ;; Simulate some processing time (10-50ms per observation)
            _ (Thread/sleep (+ 10 (* (count observations) 5)))

            summaries (mapv (fn [obs]
                              {:content (:observation obs)
                               :observation (:observation obs)
                               :hive-number (:hive-number obs)
                               :observation-date (:observation-date obs)
                               :special-feature (:special-feature obs)})
                            observations)

            end-time (System/currentTimeMillis)
            duration-ms (- end-time start-time)]

        (log/info "Generated summaries (MOCK)"
                  :summaries-count (count summaries)
                  :duration-ms duration-ms)

        [:ok {:summaries summaries
              :model model
              :duration-ms duration-ms}]))

    ;; Production code would go here:
    ;; (if-not MOCK_ENABLED
    ;;   (let [api-key (System/getenv "OPENROUTER_API_KEY")
    ;;         endpoint "https://openrouter.ai/api/v1/chat/completions"
    ;;         ...]
    ;;     ;; Make HTTP request
    ;;     ;; Parse response
    ;;     ;; Return summaries
    ;;     ))

    (catch InterruptedException e
      (log/warn "Summary generation interrupted:" e)
      [:error {:code "INTERRUPTED" :message "Summary generation was interrupted"}])

    (catch Exception e
      (log/error "Failed to generate summaries:" e)
      [:error {:code "EXTERNAL_SERVICE_ERROR" :message "Failed to generate summaries"}])))

;; =============================================================================
;; Helper Functions (for future production use)
;; =============================================================================

(defn- build-prompt
  "Build prompt for AI model from observation text.

   This will be used in production to structure the API request."
  [observation]
  (str "Please create a concise summary of the following beehive observation. "
       "Focus on key activities, hive condition, and any notable events:\n\n"
       observation))

(comment
  ;; Future production implementation example:

  (defn call-openrouter-api
    "Make HTTP request to OpenRouter API (PRODUCTION - NOT YET IMPLEMENTED)."
    [observations model api-key]
    (let [endpoint "https://openrouter.ai/api/v1/chat/completions"
          headers {"Authorization" (str "Bearer " api-key)
                   "Content-Type" "application/json"}
          body {:model model
                :messages (mapv (fn [obs]
                                  {:role "user"
                                   :content (build-prompt (:observation obs))})
                                observations)}]
      ;; HTTP POST request
      ;; Parse JSON response
      ;; Extract summaries
      ;; Return results
      nil)))
