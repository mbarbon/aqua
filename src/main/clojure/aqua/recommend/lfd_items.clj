(ns aqua.recommend.lfd-items
  )

(defn store-lfd-items [out ^aqua.recommend.LatentFactorDecompositionItems lfd]
  (let [similar-id (.similarAnimeId lfd)
        similar-score (.similarAnimeScore lfd)
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.similarAnimeCount lfd))
      (println (count similar-id))
      (.printArray writer similar-id)
      (.printArray writer similar-score))))

(defn- load-lfd-items-v1 [in complete factors]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [similar-count (read-int)
            array-size (read-int)
            similar-id (make-array Integer/TYPE array-size)
            similar-score (make-array Float/TYPE array-size)]
        (.readArray reader similar-id)
        (.readArray reader similar-score)
        (aqua.recommend.LatentFactorDecompositionItems. complete factors
                                                        similar-count
                                                        similar-id
                                                        similar-score)))))

(defn load-lfd-items [path complete factors]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-lfd-items-v1 in complete factors)))

(defn create-model [anime lfd factors item-count]
  (let [clfd (aqua.recommend.ComputeLatentFactorDecompositionItems. anime lfd factors item-count)]
    (.findSimilarAnime clfd)
    (.lfdItems clfd)))

(defn get-recommendations [user
                           ^aqua.recommend.LatentFactorDecompositionItems lfd
                           remove-known-anime]
  (let [ranked-anime (.findSimilarAnime lfd user)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))

(defn get-all-recommendations [user
                               ^aqua.recommend.LatentFactorDecompositionItems lfd
                               ^aqua.recommend.LatentFactorDecompositionItems lfd-airing
                               remove-known-anime keep-airing-anime tagger]
  (let [ranked-anime (.findSimilarAnime lfd user)
        ranked-airing-anime (.findSimilarAnime lfd-airing user)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    (.sort ranked-airing-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [(tagger (take 100 (remove-known-anime ranked-anime)))
     (tagger (take 100 (keep-airing-anime ranked-airing-anime)))]))
