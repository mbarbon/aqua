(ns aqua.recommend.embedding
  (:require aqua.misc
            aqua.recommend.model-files))

(declare store-embedding)

(defn make-parameters [args]
  (let [p (aqua.recommend.ComputeEmbedding$Parameters.)]
    (set! (.rank p) (args "rank"))
    (set! (.window p) (args "window"))
    (set! (.negativeProportion p) (args "negativeProportion"))
    (set! (.frequencySmoothing p) (args "frequencySmoothing"))
    (set! (.samplingAlpha p) (args "samplingAlpha"))
    p))

(defn create-embedding [model-type user-list parameters learning-rate iterations]
  (let [item-index-map (aqua.misc/users-item-map user-list)
        compute-complete (aqua.recommend.ComputeEmbedding. model-type item-index-map parameters)]
    (.computeFrequencies compute-complete user-list)
    (with-open [out (clojure.java.io/writer (aqua.paths/manga-embedding))]
      (store-embedding out compute-complete))
    (dotimes [i iterations]
      (let [adjusted-lr (* learning-rate (/ (- iterations i) iterations))]
        (.trainEpoch compute-complete user-list adjusted-lr))
      (with-open [out (clojure.java.io/writer (aqua.paths/manga-embedding))]
        (store-embedding out compute-complete)))
    compute-complete))

(defn store-embedding [out embedding]
  (let [item-indices (.itemMap embedding)
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.rank embedding))
      (println (count item-indices))
      (.printArray writer item-indices)
      (.printArray writer (.embedding embedding)))))

(defn- load-embedding-v1 [in]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [rank (read-int)
            item-indices (int-array (read-int))
            embedding (float-array (* rank (count item-indices)))]
        (.readArray reader item-indices)
        (.readArray reader embedding)
        (aqua.recommend.Embedding. rank item-indices embedding)))))

(defn load-embedding [path]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-embedding-v1 in)))

(defn create-embedding-items [embedding item-map item-count]
  (aqua.recommend.EmbeddingItemItem. (.simpleItemItem embedding item-map item-count)))

(defn store-embedding-items [out embedding-item]
  (aqua.recommend.item-item-model/store-item-item out (.complete embedding-item)))

(defn load-embedding-items [path-complete]
  (let [complete (aqua.recommend.item-item-model/load-item-item path-complete)]
    (aqua.recommend.EmbeddingItemItem. complete)))

(defn get-manga-recommendations [user
                                 ^aqua.recommend.EmbeddingItemItem model
                                 remove-known-manga
                                 tagger]
  (let [[_ recommendations] (aqua.recommend.item-item-model/get-raw-manga-recommendations user (.complete model) remove-known-manga)]
    [(tagger recommendations)]))
