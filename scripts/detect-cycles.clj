#!/usr/bin/env bb
;; Detect circular dependencies in active hot zones using Tarjan's algorithm

(require '[cheshire.core :as json]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str]
         '[clojure.set :as set])

(defn run-clj-kondo-analysis []
  (let [result (shell/sh "clj-kondo"
                         "--lint" "src/com/apriary"
                         "--config" "{:output {:format :json :analysis {:namespace-definitions true :namespace-usages true}}}"
                         "--config-dir" ".clj-kondo")]
    (when (not= 0 (:exit result))
      (binding [*out* *err*]
        (println "clj-kondo failed:")
        (println (:err result)))
      (System/exit 1))
    (json/parse-string (:out result) true)))

(defn build-dependency-graph [analysis]
  (let [ns-usages (-> analysis :analysis :namespace-usages)
        all-ns (-> analysis :analysis :namespace-definitions)
        internal-ns (set (map :name all-ns))]

    ;; Build adjacency list: ns -> [deps]
    (reduce
     (fn [graph usage]
       (let [from (:from usage)
             to (:to usage)]
         ;; Only internal dependencies
         (if (and (internal-ns from) (internal-ns to))
           (update graph from (fnil conj []) to)
           graph)))
     {}
     ns-usages)))

(defn tarjan-scc
  "Tarjan's algorithm for finding strongly connected components (cycles)"
  [graph]
  (let [state (atom {:index 0
                     :stack []
                     :on-stack #{}
                     :indices {}
                     :lowlinks {}
                     :sccs []})

        strongconnect
        (fn strongconnect [v]
          (let [idx (:index @state)]
            (swap! state assoc-in [:indices v] idx)
            (swap! state assoc-in [:lowlinks v] idx)
            (swap! state update :index inc)
            (swap! state update :stack conj v)
            (swap! state update :on-stack conj v)

            ;; Consider successors
            (doseq [w (get graph v [])]
              (cond
                (not (contains? (:indices @state) w))
                (do
                  (strongconnect w)
                  (swap! state assoc-in [:lowlinks v]
                         (min (get-in @state [:lowlinks v])
                              (get-in @state [:lowlinks w]))))

                (contains? (:on-stack @state) w)
                (swap! state assoc-in [:lowlinks v]
                       (min (get-in @state [:lowlinks v])
                            (get-in @state [:indices w])))))

            ;; If v is a root node, pop the stack
            (when (= (get-in @state [:lowlinks v])
                     (get-in @state [:indices v]))
              (loop [scc []]
                (let [w (peek (:stack @state))]
                  (swap! state update :stack pop)
                  (swap! state update :on-stack disj w)
                  (let [new-scc (conj scc w)]
                    (if (= w v)
                      (when (> (count new-scc) 1)  ;; Only cycles (SCC > 1)
                        (swap! state update :sccs conj new-scc))
                      (recur new-scc))))))))]

    ;; Run Tarjan for each unvisited node
    (doseq [v (keys graph)]
      (when (not (contains? (:indices @state) v))
        (strongconnect v)))

    (:sccs @state)))

(defn format-cycle-markdown [cycles hot-zones]
  (if (empty? cycles)
    "# Analiza Cykli Zależności\n\n✅ **Brak cykli zależności** w aktywnych obszarach projektu.\n"
    (str/join "\n\n"
              (concat
               ["# Analiza Cykli Zależności w Aktywnych Obszarach"
                ""
                (format "**Znaleziono:** %d cykli w projekcie" (count cycles))
                (format "**Aktywne obszary z artifact-1-territory.md:**")
                (str "- 🔥 HOT: " (str/join ", " (map #(str "`" % "`") (:hot hot-zones))))
                (format "- 🟡 WARM: %s" (str/join ", " (map #(str "`" % "`") (:warm hot-zones))))
                ""
                "---"
                ""]

               (map-indexed
                (fn [idx cycle]
                  (let [cycle-ns (set cycle)
                        hot-involvement (set/intersection cycle-ns (:hot hot-zones))
                        warm-involvement (set/intersection cycle-ns (:warm hot-zones))
                        has-activity (or (seq hot-involvement) (seq warm-involvement))
                        severity (cond
                                   (seq hot-involvement) "🔥 CRITICAL"
                                   (seq warm-involvement) "🟡 MEDIUM"
                                   :else "❄️ LOW")]
                    (str "## Cykl #" (inc idx) " — " severity "\n\n"
                         "**Namespace'y w cyklu:**\n"
                         (str/join "\n" (map #(str "- `" % "`") cycle))
                         "\n\n"
                         (when has-activity
                           (str "**Związek z aktywnymi obszarami:**\n"
                                (when (seq hot-involvement)
                                  (str "- 🔥 HOT zones: " (str/join ", " (map #(str "`" % "`") hot-involvement)) "\n"))
                                (when (seq warm-involvement)
                                  (str "- 🟡 WARM zones: " (str/join ", " (map #(str "`" % "`") warm-involvement)) "\n"))
                                "\n"))
                         "**Dlaczego to utrudnia zmianę:**\n"
                         (cond
                           (seq hot-involvement)
                           (str "- Cykl obejmuje najczęściej modyfikowane pliki — każda zmiana w jednym z nich wymaga przemyślenia wpływu na pozostałe\n"
                                "- Ryzyko cascade failures — błąd w jednym namespace może propagować się po całym cyklu\n"
                                "- Trudność w jednostkowym testowaniu — trudno mockować zależności w cyklu\n")

                           (seq warm-involvement)
                           (str "- Średnia aktywność oznacza że kod jest używany ale nie krytyczny\n"
                                "- Refaktoring wymaga koordynacji między wszystkimi namespace'ami w cyklu\n")

                           :else
                           "- Niski priorytet — obszary cold zone, rzadko modyfikowane\n")
                         "\n")))
                cycles)))))

(defn -main [& args]
  (let [analysis (run-clj-kondo-analysis)
        graph (build-dependency-graph analysis)
        cycles (tarjan-scc graph)

        ;; Hot zones from territory map
        hot-zones {:hot #{(symbol "com.apriary.pages.summaries-view")
                          (symbol "com.apriary")}
                   :warm #{(symbol "com.apriary.ui.header")
                           (symbol "com.apriary.ui.summaries-list")
                           (symbol "com.apriary.schema")
                           (symbol "com.apriary.pages.app")
                           (symbol "com.apriary.ui.summary-card")
                           (symbol "com.apriary.ui")
                           (symbol "com.apriary.pages.products")
                           (symbol "com.apriary.email")}}

        output (format-cycle-markdown cycles hot-zones)]

    (println output)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
