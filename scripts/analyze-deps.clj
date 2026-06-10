#!/usr/bin/env bb
;; Namespace dependency analyzer using clj-kondo analysis
;; Usage: bb scripts/analyze-deps.clj [output-format]
;; Formats: dot (default), json, text, metrics

(require '[cheshire.core :as json]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str]
         '[clojure.set :as set])

(defn run-clj-kondo-analysis []
  (let [result (shell/sh "clj-kondo"
                         "--lint" "src"
                         "--config" "{:output {:format :json :analysis {:namespace-definitions true :namespace-usages true}}}"
                         "--config-dir" ".clj-kondo")]
    (when (not= 0 (:exit result))
      (binding [*out* *err*]
        (println "clj-kondo failed:")
        (println (:err result)))
      (System/exit 1))
    (json/parse-string (:out result) true)))

(defn extract-namespace-graph [analysis]
  (let [ns-defs (-> analysis :analysis :namespace-definitions)
        ns-usages (-> analysis :analysis :namespace-usages)

        ;; Build set of all defined namespaces
        all-ns (set (map :name ns-defs))

        ;; Build dependency map: ns -> #{required-ns}
        deps (reduce
              (fn [acc usage]
                (let [from (:from usage)
                      to (:to usage)]
                  ;; Only track internal dependencies
                  (if (and (all-ns from) (all-ns to))
                    (update acc from (fnil conj #{}) to)
                    acc)))
              {}
              ns-usages)]
    {:namespaces all-ns
     :dependencies deps}))

(defn calculate-metrics [graph]
  (let [{:keys [namespaces dependencies]} graph

        ;; Afferent coupling: who depends on this ns
        afferent (reduce-kv
                  (fn [acc from deps]
                    (reduce (fn [a dep]
                              (update a dep (fnil inc 0)))
                            acc
                            deps))
                  {}
                  dependencies)

        ;; Efferent coupling: who does this ns depend on
        efferent (into {} (map (fn [ns] [ns (count (get dependencies ns #{}))]) namespaces))

        ;; Instability: I = Ce / (Ca + Ce)
        ;; 0 = maximally stable, 1 = maximally unstable
        instability (into {}
                          (map (fn [ns]
                                 (let [ca (get afferent ns 0)
                                       ce (get efferent ns 0)
                                       total (+ ca ce)
                                       i (if (zero? total) 0.0 (/ ce total))]
                                   [ns {:ca ca :ce ce :instability (double i)}]))
                               namespaces))]
    instability))

(defn format-dot [graph metrics]
  (let [{:keys [namespaces dependencies]} graph
        ns-list (sort namespaces)]
    (str/join "\n"
              (concat
               ["digraph dependencies {"
                "  rankdir=LR;"
                "  node [shape=box, style=rounded];"
                ""]

               ;; Nodes with color based on instability
               (map (fn [ns]
                      (let [{:keys [instability]} (get metrics ns)
                            color (cond
                                    (< instability 0.3) "lightgreen"
                                    (< instability 0.7) "lightyellow"
                                    :else "lightcoral")
                            label (str ns "\\nI=" (format "%.2f" instability))]
                        (format "  \"%s\" [label=\"%s\", fillcolor=\"%s\", style=\"filled,rounded\"];"
                                ns label color)))
                    ns-list)

               [""]

               ;; Edges
               (mapcat (fn [from]
                         (map (fn [to]
                                (format "  \"%s\" -> \"%s\";" from to))
                              (sort (get dependencies from #{}))))
                       (sort (keys dependencies)))

               [""]
               ["  // Legend"
                "  subgraph cluster_legend {"
                "    label=\"Instability (I)\";"
                "    node [shape=plaintext];"
                "    legend [label=<"
                "      <table border=\"0\" cellpadding=\"2\" cellspacing=\"0\">"
                "        <tr><td bgcolor=\"lightgreen\">Stable (I &lt; 0.3)</td></tr>"
                "        <tr><td bgcolor=\"lightyellow\">Medium (0.3 ≤ I &lt; 0.7)</td></tr>"
                "        <tr><td bgcolor=\"lightcoral\">Unstable (I ≥ 0.7)</td></tr>"
                "      </table>"
                "    >];"
                "  }"
                "}"]))))

(defn format-text [graph metrics]
  (let [{:keys [namespaces dependencies]} graph]
    (str/join "\n"
              (concat
               ["# Namespace Dependencies"
                ""]
               (map (fn [ns]
                      (let [deps (sort (get dependencies ns #{}))
                            {:keys [ca ce instability]} (get metrics ns)]
                        (format "%s\n  Depends on: %s\n  Metrics: Ca=%d Ce=%d I=%.2f"
                                ns
                                (if (empty? deps) "(none)" (str/join ", " deps))
                                ca ce instability)))
                    (sort namespaces))))))

(defn format-metrics [graph metrics]
  (let [{:keys [namespaces]} graph
        sorted-by-instability (sort-by (comp :instability metrics) namespaces)]
    (str/join "\n"
              (concat
               ["# Namespace Stability Metrics"
                ""
                "Format: namespace | Ca (afferent) | Ce (efferent) | I (instability)"
                ""]
               (map (fn [ns]
                      (let [{:keys [ca ce instability]} (get metrics ns)]
                        (format "%-50s | Ca=%2d | Ce=%2d | I=%.2f | %s"
                                ns ca ce instability
                                (cond
                                  (< instability 0.3) "STABLE"
                                  (< instability 0.7) "medium"
                                  :else "unstable"))))
                    sorted-by-instability)
               [""]
               ["# Interpretation:"
                "Ca (afferent coupling)  = # of namespaces that depend on this one"
                "Ce (efferent coupling)  = # of namespaces this one depends on"
                "I  (instability)        = Ce / (Ca + Ce)"
                ""
                "Stable namespaces (I < 0.3): hard to change, many dependents"
                "Unstable namespaces (I ≥ 0.7): easy to change, few dependents"]))))

(defn format-json [graph metrics]
  (json/generate-string
   {:namespaces (vec (:namespaces graph))
    :dependencies (into {} (map (fn [[k v]] [k (vec v)]) (:dependencies graph)))
    :metrics metrics}
   {:pretty true}))

(defn -main [& args]
  (let [format (or (first args) "dot")
        analysis (run-clj-kondo-analysis)
        graph (extract-namespace-graph analysis)
        metrics (calculate-metrics graph)

        output (case format
                 "dot" (format-dot graph metrics)
                 "json" (format-json graph metrics)
                 "text" (format-text graph metrics)
                 "metrics" (format-metrics graph metrics)
                 (do
                   (binding [*out* *err*]
                     (println "Unknown format:" format)
                     (println "Available formats: dot, json, text, metrics"))
                   (System/exit 1)))]

    (println output)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
