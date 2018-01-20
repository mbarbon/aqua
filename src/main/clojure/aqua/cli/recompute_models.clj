(ns aqua.cli.recompute-models
  (:require aqua.mal-local
            aqua.misc
            aqua.recommend.user-sample
            aqua.recommend.co-occurrency
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.recommend.rp-similar-anime
            clojure.java.io))

(def user-count 15000)
(def all-items ["user-sample"
                "co-occurrency"
                "lfd-model"
                "lfd-items"
                "rp-similarity"
                "rp-similarity-unfiltered"])

(def user-sample-count 100000)

(def co-occurrency-score-threshold 0.2)
(def co-occurrency-alpha 0.3)
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

(def lfd-items-similar-item-count 30)

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
        lfd-items (aqua.recommend.lfd-items/create-model anime lfd lfd lfd-items-similar-item-count)
        lfd-items-airing (aqua.recommend.lfd-items/create-model anime lfd lfd-airing lfd-items-similar-item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.lfd-items/store-lfd-items out lfd-items))
    (with-open [out (clojure.java.io/writer airing-model-path)]
      (aqua.recommend.lfd-items/store-lfd-items out lfd-items-airing))))

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
        users (aqua.recommend.user-sample/load-filtered-cf-users "maldump/user-sample" data-source cf-parameters user-count)
        anime (aqua.mal-local/load-anime data-source)]
    (doseq [item (if (seq items) items all-items)]
      (case item
        "user-sample"
          nil ; handled above
        "co-occurrency"
          (do
            (println "Recomputing co-occurrency item-item model")
            (time (recompute-co-occurrency-model users anime "maldump/co-occurrency-model" "maldump/co-occurrency-model-airing")))
        "lfd-model"
          (do
            (println "Recomputing latent factor decomposition")
            (time (recompute-lfd-model users anime lfd-rank lfd-lambda lfd-iterations "maldump/lfd-model" "maldump/lfd-model-airing" "maldump/lfd-user-model")))
        "lfd-items"
          (do
            (println "Recomputing latent factor decomposition item similarity")
            (time (recompute-lfd-items-model anime, "maldump/lfd-model" "maldump/lfd-model-airing" "maldump/lfd-items-model" "maldump/lfd-items-model-airing")))
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
