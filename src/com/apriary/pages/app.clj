(ns com.apriary.pages.app
  (:require [com.apriary.middleware :as mid]))

(defn app [_ctx]
  {:status 303
   :headers {"Location" "/summaries"}})

(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]]})