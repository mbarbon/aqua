(ns aqua.cli.recompute-models
  (:require aqua.mal-local
            aqua.paths
            aqua.misc
            aqua.recommend.user-sample
            aqua.recommend.co-occurrency
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.recommend.rp-similarity
            aqua.recommend.embedding
            clojure.java.io))

(def anime-user-count 80000)
(def manga-user-count 80000)
(def cf-user-count 15000)
(def all-items ["anime-user-sample"
                "manga-user-sample"
                "anime-co-occurrency"
                "manga-co-occurrency"
                "anime-lfd-model"
                "manga-lfd-model"
                "anime-lfd-items"
                "anime-embedding"
                "anime-embedding-items"
                "manga-embedding"
                "manga-embedding-items"
                "anime-rp-similarity"
                "anime-rp-similarity-unfiltered"
                "manga-rp-similarity"
                "manga-rp-similarity-unfiltered"])

(def user-sample-count 100000)

(def co-occurrency-anime-score-threshold 0.3)
(def co-occurrency-manga-score-threshold 0.05)
(def co-occurrency-alpha 0.7)
(def co-occurrency-anime-item-count 30)
(def co-occurrency-manga-item-count 10)
(def co-occurrency-item-count-airing 15)

; around the point where the score from find-rp-size starts stabilizing
; (i.e. multiple random projections start returning more stable results)
(def rp-projection-anime-size 6000)
(def rp-projection-manga-size 4500)
; arbitrary
(def rp-anime-similar-item-count 30)
(def rp-manga-similar-item-count 20)

(def anime-lfd-rank 40)
(def anime-lfd-lambda 0.1)
(def anime-lfd-iterations 24)

(def manga-lfd-rank 50)
(def manga-lfd-lambda 0.1)
(def manga-lfd-iterations 24)

(def lfd-items-item-count 30)
(def lfd-items-item-count-airing 15)

(def anime-embedding-parameters
  (aqua.recommend.embedding/make-parameters {
    "rank" 60
    "window" 15
    "negativeProportion" 5
    "frequencySmoothing" 0.75
    "samplingAlpha" 0.00001}))
(def anime-embedding-learning-rate 0.005)
(def anime-embedding-iterations 15)

(def anime-embedding-items-item-count 30)

(def manga-embedding-parameters
  (aqua.recommend.embedding/make-parameters {
    "rank" 120
    "window" 15
    "negativeProportion" 5
    "frequencySmoothing" 0.75
    "samplingAlpha" 0.00001}))
(def manga-embedding-learning-rate 0.01)
(def manga-embedding-iterations 15)

(def manga-embedding-items-item-count 20)

(defn- recompute-anime-co-occurrency-model [users anime-map model-path airing-model-path]
  (let [co-occurrency (aqua.recommend.co-occurrency/create-anime-co-occurrency users anime-map
                                                                               co-occurrency-anime-score-threshold
                                                                               co-occurrency-alpha co-occurrency-anime-item-count
                                                                               co-occurrency-item-count-airing)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.co-occurrency/store-co-occurrency-complete out co-occurrency))
    (with-open [out (clojure.java.io/writer airing-model-path)]
      (aqua.recommend.co-occurrency/store-co-occurrency-airing out co-occurrency))))

(defn- recompute-manga-co-occurrency-model [users manga-map model-path]
  (let [co-occurrency (aqua.recommend.co-occurrency/create-manga-co-occurrency users manga-map
                                                                               co-occurrency-manga-score-threshold
                                                                               co-occurrency-alpha co-occurrency-manga-item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.co-occurrency/store-co-occurrency-complete out co-occurrency))))

(defn- recompute-rp-model [users anime-map model-path]
  (let [rp-similar (aqua.recommend.rp-similarity/create-rp-similarity users anime-map rp-projection-anime-size rp-anime-similar-item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.rp-similarity/store-rp-similarity out rp-similar))))

(defn- recompute-lfd-items-model [anime lfd-path lfd-airing-path model-path airing-model-path]
  (let [lfd (aqua.recommend.lfd/load-lfd lfd-path)
        lfd-airing (aqua.recommend.lfd/load-lfd lfd-airing-path)
        lfd-items (aqua.recommend.lfd-items/create-lfd-items anime lfd lfd-airing lfd-items-item-count lfd-items-item-count-airing)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.lfd-items/store-lfd-items-complete out lfd-items))
    (with-open [out (clojure.java.io/writer airing-model-path)]
      (aqua.recommend.lfd-items/store-lfd-items-airing out lfd-items))))

(defn- recompute-anime-lfd-model [users anime-map rank lambda iterations model-path airing-model-path user-model-path]
  (let [[lfdr lfdr-airing] (aqua.recommend.lfd/prepare-anime-lfd-decompositor users anime-map rank lambda)]
     (dotimes [_ iterations]
       (aqua.recommend.lfd/run-item-steps lfdr)
       (aqua.recommend.lfd/run-user-steps lfdr))
     (aqua.recommend.lfd/run-item-steps lfdr-airing)
     (with-open [out (clojure.java.io/writer user-model-path)]
       (aqua.recommend.lfd/store-user-lfd out (.reduceUserCount (.decompositionUsers lfdr) cf-user-count)))
     (with-open [out (clojure.java.io/writer model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr)))
     (with-open [out (clojure.java.io/writer airing-model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr-airing)))))

(defn- recompute-manga-lfd-model [users manga-map rank lambda iterations model-path user-model-path]
  (let [[lfdr] (aqua.recommend.lfd/prepare-manga-lfd-decompositor users manga-map rank lambda)]
     (dotimes [_ iterations]
       (aqua.recommend.lfd/run-item-steps lfdr)
       (aqua.recommend.lfd/run-user-steps lfdr))
     (with-open [out (clojure.java.io/writer user-model-path)]
       (aqua.recommend.lfd/store-user-lfd out (.reduceUserCount (.decompositionUsers lfdr) cf-user-count)))
     (with-open [out (clojure.java.io/writer model-path)]
       (aqua.recommend.lfd/store-lfd out (.decomposition lfdr)))))

(defn- recompute-embedding [model-type users parameters learning-rate iterations model-path]
  (let [embedding (aqua.recommend.embedding/create-embedding model-type users parameters learning-rate iterations)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.embedding/store-embedding out embedding))))

(defn- recompute-embedding-items [embedding-path item-map item-count model-path]
  (let [embedding (aqua.recommend.embedding/load-embedding embedding-path)
        embedding-items (aqua.recommend.embedding/create-embedding-items embedding item-map item-count)]
    (with-open [out (clojure.java.io/writer model-path)]
      (aqua.recommend.embedding/store-embedding-items out embedding-items))))

(defn -main [& item-args]
  (let [items (if (seq item-args) item-args all-items)
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))]
    (if (some #{"anime-user-sample"} items)
      (let [anime (aqua.mal-local/load-anime data-source)
            completed-anime (set (->> (vals anime)
                                      (filter #(.isCompleted %))
                                      (map #(.animedbId %))))]
        (println "Recomputing anime user sample")
        (time (aqua.recommend.user-sample/recompute-user-sample aqua.recommend.ModelType/ANIME data-source user-sample-count completed-anime (aqua.paths/anime-user-sample)))))
    (if (some #{"manga-user-sample"} items)
      (let [manga (aqua.mal-local/load-manga data-source)]
        (println "Recomputing manga user sample")
        (time (aqua.recommend.user-sample/recompute-user-sample aqua.recommend.ModelType/MANGA data-source user-sample-count (.keySet manga) (aqua.paths/manga-user-sample))))))

  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        anime (aqua.mal-local/load-anime data-source)
        manga (aqua.mal-local/load-manga data-source)
        anime-users (do
                      (println "Loading anime users")
                      (time (aqua.recommend.user-sample/load-filtered-cf-users aqua.recommend.ModelType/ANIME (aqua.paths/anime-user-sample)
                                                                               data-source cf-parameters anime-user-count anime)))
        manga-users (do
                      (println "Loading manga users")
                      (time (aqua.recommend.user-sample/load-filtered-cf-users aqua.recommend.ModelType/MANGA (aqua.paths/manga-user-sample)
                                                                               data-source cf-parameters manga-user-count manga)))]
    (doseq [item (if (seq item-args) item-args all-items)]
      (case item
        "anime-user-sample"
          nil ; handled above
        "manga-user-sample"
          nil ; handled above
        "anime-co-occurrency"
          (do
            (println "Recomputing anime co-occurrency item-item model")
            (time (recompute-anime-co-occurrency-model anime-users anime (aqua.paths/anime-co-occurrency-model) (aqua.paths/anime-co-occurrency-model-airing))))
        "manga-co-occurrency"
          (do
            (println "Recomputing manga co-occurrency item-item model")
            (time (recompute-manga-co-occurrency-model manga-users manga (aqua.paths/manga-co-occurrency-model))))
        "anime-lfd-model"
          (do
            (println "Recomputing anime latent factor decomposition")
            (time (recompute-anime-lfd-model anime-users
                                             anime
                                             anime-lfd-rank
                                             anime-lfd-lambda
                                             anime-lfd-iterations
                                             (aqua.paths/anime-lfd-model)
                                             (aqua.paths/anime-lfd-model-airing)
                                             (aqua.paths/anime-lfd-user-model))))
        "manga-lfd-model"
          (do
            (println "Recomputing manga latent factor decomposition")
            (time (recompute-manga-lfd-model manga-users
                                             manga
                                             manga-lfd-rank
                                             manga-lfd-lambda
                                             manga-lfd-iterations
                                             (aqua.paths/manga-lfd-model)
                                             (aqua.paths/manga-lfd-user-model))))
        "anime-lfd-items"
          (do
            (println "Recomputing anime latent factor decomposition item similarity")
            (time (recompute-lfd-items-model anime (aqua.paths/anime-lfd-model)
                                                   (aqua.paths/anime-lfd-model-airing)
                                                   (aqua.paths/anime-lfd-items-model)
                                                   (aqua.paths/anime-lfd-items-model-airing))))
        "anime-embedding"
          (do
            (println "Recomputing anime embedding similarity")
            (time (recompute-embedding aqua.recommend.ModelType/COMPLETED_ANIME
                                       anime-users
                                       anime-embedding-parameters
                                       anime-embedding-learning-rate
                                       anime-embedding-iterations
                                       (aqua.paths/anime-embedding))))
        "manga-embedding"
          (do
            (println "Recomputing manga embedding similarity")
            (time (recompute-embedding aqua.recommend.ModelType/MANGA
                                       manga-users
                                       manga-embedding-parameters
                                       manga-embedding-learning-rate
                                       manga-embedding-iterations
                                       (aqua.paths/manga-embedding))))
        "anime-embedding-items"
          (do
            (println "Recomputing anime embedding item similarity")
            (time (recompute-embedding-items (aqua.paths/anime-embedding)
                                             anime
                                             anime-embedding-items-item-count
                                             (aqua.paths/anime-embedding-items-model))))
        "manga-embedding-items"
          (do
            (println "Recomputing manga embedding item similarity")
            (time (recompute-embedding-items (aqua.paths/manga-embedding)
                                             manga
                                             manga-embedding-items-item-count
                                             (aqua.paths/manga-embedding-items-model))))
        "anime-rp-similarity"
          (do
            (println "Recomputing anime random projection similarity model")
            (time (recompute-rp-model anime-users anime (aqua.paths/anime-rp-model))))
        "anime-rp-similarity-unfiltered"
          (do
            (println "Recomputing anime unfiltered random projection similarity model")
            (time (recompute-rp-model anime-users {} (aqua.paths/anime-rp-model-unfiltered))))
        "manga-rp-similarity"
          (do
            (println "Recomputing manga random projection similarity model")
            (time (recompute-rp-model manga-users manga (aqua.paths/manga-rp-model))))
        "manga-rp-similarity-unfiltered"
          (do
            (println "Recomputing manga unfiltered random projection similarity model")
            (time (recompute-rp-model manga-users {} (aqua.paths/manga-rp-model-unfiltered))))
        (do
          (println (str "Invalid item " item " (possible values "
                     (clojure.string/join " " all-items) ")")))))))
