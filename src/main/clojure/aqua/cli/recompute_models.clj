(ns aqua.cli.recompute-models
  (:require aqua.mal-local
            aqua.misc
            aqua.recommend.user-sample
            aqua.recommend.lfd
            aqua.recommend.rp-similar-anime
            clojure.java.io))

(def user-count 15000)
(def all-items ["user-sample"
                "lfd-model"
                "rp-similarity"
                "rp-similarity-unfiltered"])

(def user-sample-count 100000)

; around the point where the score from find-rp-size starts stabilizing
; (i.e. multiple random projections start returning more stable results)
(def rp-projection-size 1500)
; arbitrary
(def rp-similar-item-count 30)

(def lfd-rank 25)
(def lfd-lambda 0.1)
(def lfd-iterations 20)

(defn- recompute-rp-model [users anime-map model-path]
  (let [rp-similar (aqua.recommend.rp-similar-anime/create-rp-similarity users anime-map rp-projection-size rp-similar-item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.rp-similar-anime/store-rp-similarity out rp-similar))))

(defn- recompute-lfd-model [users anime-map rank lambda iterations model-path airing-model-path user-model-path]
  (let [[lfdr lfdr-airing] (aqua.recommend.lfd/prepare-lfd-decompositor users anime-map rank lambda)]
     (dotimes [_ iterations]
       (aqua.recommend.lfd/run-anime-steps lfdr)
       (aqua.recommend.lfd/run-user-steps lfdr))
     (aqua.recommend.lfd/run-anime-steps lfdr-airing)
     (with-open [out (clojure.java.io/writer user-model-path)]
       (aqua.recommend.lfd/store-user-lfd out (.decompositionUsers lfdr)))
     (with-open [out (clojure.java.io/writer model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr)))
     (with-open [out (clojure.java.io/writer airing-model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr-airing)))))

(defn -main [& items]
  (when (some #{"user-sample"} (if (seq items) items all-items))
    (println "Recomputing user sample")
    (time (aqua.recommend.user-sample/recompute-user-sample user-sample-count "maldump/user-sample")))

  (let [data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        users (aqua.mal-local/load-filtered-cf-users "maldump" data-source cf-parameters user-count)
        anime (aqua.mal-local/load-anime data-source)]
    (doseq [item (if (seq items) items all-items)]
      (case item
        "user-sample"
          nil ; handled above
        "lfd-model"
          (do
            (println "Recomputing latent factor decomposition")
            (time (recompute-lfd-model users anime lfd-rank lfd-lambda lfd-iterations "maldump/lfd-model" "maldump/lfd-model-airing" "maldump/lfd-user-model")))
        "rp-similarity"
          (do
            (println "Recomputing random projection similarity model")
            (time (recompute-rp-model users anime "maldump/rp-model")))
        "rp-similarity-unfiltered"
          (do
            (println "Recomputing unfiltered random projection similarity model")
            (time (recompute-rp-model users {} "maldump/rp-model-unfiltered")))
        (do
          (println (str "Invalid item " item " (possible values "
                     (clojure.string/join " " all-items) ")")))))))