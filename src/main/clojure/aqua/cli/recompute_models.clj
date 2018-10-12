(ns aqua.cli.recompute-models
  (:require aqua.mal-local
            aqua.paths
            aqua.misc
            aqua.recommend.user-sample
            aqua.recommend.co-occurrency
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.recommend.rp-similar-anime
            clojure.java.io))

(def user-count 60000)
(def cf-user-count 15000)
(def all-items ["anime-user-sample"
                "anime-co-occurrency"
                "anime-lfd-model"
                "anime-lfd-items"
                "anime-rp-similarity"
                "anime-rp-similarity-unfiltered"])

(def user-sample-count 100000)

(def co-occurrency-score-threshold 0.3)
(def co-occurrency-alpha 0.7)
(def co-occurrency-item-count 30)
(def co-occurrency-item-count-airing 15)

; around the point where the score from find-rp-size starts stabilizing
; (i.e. multiple random projections start returning more stable results)
(def rp-projection-size 7000)
; arbitrary
(def rp-similar-item-count 30)

(def lfd-rank 40)
(def lfd-lambda 0.1)
(def lfd-iterations 24)

(def lfd-items-item-count 30)
(def lfd-items-item-count-airing 15)

(defn- recompute-co-occurrency-model [users anime-map model-path airing-model-path]
  (let [co-occurrency (aqua.recommend.co-occurrency/create-co-occurrency users anime-map
                                                                         co-occurrency-score-threshold
                                                                         co-occurrency-alpha co-occurrency-item-count
                                                                         co-occurrency-item-count-airing)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.co-occurrency/store-co-occurrency-complete out co-occurrency))
    (with-open [out (clojure.java.io/writer airing-model-path)]
      (aqua.recommend.co-occurrency/store-co-occurrency-airing out co-occurrency))))

(defn- recompute-rp-model [users anime-map model-path]
  (let [rp-similar (aqua.recommend.rp-similar-anime/create-rp-similarity users anime-map rp-projection-size rp-similar-item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.rp-similar-anime/store-rp-similarity out rp-similar))))

(defn- recompute-lfd-items-model [anime lfd-path lfd-airing-path model-path airing-model-path]
  (let [lfd (aqua.recommend.lfd/load-lfd lfd-path)
        lfd-airing (aqua.recommend.lfd/load-lfd lfd-airing-path)
        lfd-items (aqua.recommend.lfd-items/create-lfd-items anime lfd lfd-airing lfd-items-item-count lfd-items-item-count-airing)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.lfd-items/store-lfd-items-complete out lfd-items))
    (with-open [out (clojure.java.io/writer airing-model-path)]
      (aqua.recommend.lfd-items/store-lfd-items-airing out lfd-items))))

(defn- recompute-lfd-model [users anime-map rank lambda iterations model-path airing-model-path user-model-path]
  (let [[lfdr lfdr-airing] (aqua.recommend.lfd/prepare-lfd-decompositor users anime-map rank lambda)]
     (dotimes [_ iterations]
       (aqua.recommend.lfd/run-anime-steps lfdr)
       (aqua.recommend.lfd/run-user-steps lfdr))
     (aqua.recommend.lfd/run-anime-steps lfdr-airing)
     (with-open [out (clojure.java.io/writer user-model-path)]
       (aqua.recommend.lfd/store-user-lfd out (.reduceUserCount (.decompositionUsers lfdr) cf-user-count)))
     (with-open [out (clojure.java.io/writer model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr)))
     (with-open [out (clojure.java.io/writer airing-model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr-airing)))))

(defn -main [& items]
  (when (some #{"anime-user-sample"} (if (seq items) items all-items))
    (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
          anime (aqua.mal-local/load-anime data-source)]
      (println "Recomputing user sample")
      (time (aqua.recommend.user-sample/recompute-user-sample data-source user-sample-count (.keySet anime) (aqua.paths/anime-user-sample)))))

  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        anime (aqua.mal-local/load-anime data-source)
        users (aqua.recommend.user-sample/load-filtered-cf-users (aqua.paths/anime-user-sample) data-source cf-parameters user-count anime)]
    (doseq [item (if (seq items) items all-items)]
      (case item
        "anime-user-sample"
          nil ; handled above
        "anime-co-occurrency"
          (do
            (println "Recomputing co-occurrency item-item model")
            (time (recompute-co-occurrency-model users anime (aqua.paths/anime-co-occurrency-model) (aqua.paths/anime-co-occurrency-model-airing))))
        "anime-lfd-model"
          (do
            (println "Recomputing latent factor decomposition")
            (time (recompute-lfd-model users anime lfd-rank lfd-lambda lfd-iterations (aqua.paths/anime-lfd-model) (aqua.paths/anime-lfd-model-airing) (aqua.paths/anime-lfd-user-model))))
        "anime-lfd-items"
          (do
            (println "Recomputing latent factor decomposition item similarity")
            (time (recompute-lfd-items-model anime (aqua.paths/anime-lfd-model)
                                                   (aqua.paths/anime-lfd-model-airing)
                                                   (aqua.paths/anime-lfd-items-model)
                                                   (aqua.paths/anime-lfd-items-model-airing))))
        "anime-rp-similarity"
          (do
            (println "Recomputing random projection similarity model")
            (time (recompute-rp-model users anime (aqua.paths/anime-rp-model))))
        "anime-rp-similarity-unfiltered"
          (do
            (println "Recomputing unfiltered random projection similarity model")
            (time (recompute-rp-model users {} (aqua.paths/anime-rp-model-unfiltered))))
        (do
          (println (str "Invalid item " item " (possible values "
                     (clojure.string/join " " all-items) ")")))))))
