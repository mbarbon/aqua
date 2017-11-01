(ns aqua.recommend.rp-similar-anime
  (:require aqua.recommend.model-files))

(defn create-rp-similarity [user-list anime-map rank similar-count]
  (let [anime-index-map (java.util.HashMap.)
        anime-rated-map (java.util.ArrayList.)]
    (doseq [user user-list]
      (doseq [rated (.animeList user)]
        (.putIfAbsent anime-index-map
                      (.animedbId rated)
                      (.size anime-index-map))))
    (dotimes [_ (.size anime-index-map)]
      (.add anime-rated-map nil))
    (doseq [entry anime-index-map]
      (.set anime-rated-map (.getValue entry) (.getKey entry)))
    (let [compute (aqua.recommend.ComputeRPSimilarAnime. anime-map anime-index-map similar-count)]
      (.findSimilarAnime compute user-list rank)
      (.rpSimilarAnime compute))))

(defn similar-anime-debug [rp-similarity anime-id anime-map]
  (let [similar-scored (.similarAnime rp-similarity anime-id)]
    (doseq [scored similar-scored]
      (let [anime (anime-map (.animedbId scored))]
        (println (.animedbId scored) (.title anime) (.score scored))))))

(defn store-rp-similarity [out rp-similarity]
  (let [anime-indices (.animeRatedMap rp-similarity)
        similar-ids (.similarAnimeId rp-similarity)
        similar-scores (.similarAnimeScore rp-similarity)
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.similarAnimeCount rp-similarity))
      (println (count similar-ids))
      (println (count anime-indices))
      (.printArray writer anime-indices)
      (.printArray writer similar-ids)
      (.printArray writer similar-scores))))

(defn- load-rp-similarity-v1 [in]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [similar-count (read-int)
            scores-count (read-int)
            anime-indices (make-array Integer/TYPE (read-int))
            similar-ids (make-array Integer/TYPE scores-count)
            similar-scores (make-array Float/TYPE scores-count)]
        (.readArray reader anime-indices)
        (.readArray reader similar-ids)
        (.readArray reader similar-scores)
        (aqua.recommend.RPSimilarAnime. anime-indices similar-count
                                        similar-ids
                                        similar-scores)))))

(defn load-rp-similarity [path]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-rp-similarity-v1 in)))

(defn get-recommendations [user
                           ^aqua.recommend.RPSimilarAnime rp
                           remove-known-anime]
  (let [ranked-anime (.findSimilarAnime rp user)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))
