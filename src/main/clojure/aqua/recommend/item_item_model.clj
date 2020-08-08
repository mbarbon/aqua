(ns aqua.recommend.item-item-model
  (:require aqua.recommend.model-files))

(defn store-item-item [out item-item]
  (let [anime-indices (.animeRatedMap item-item)
        similar-ids (.similarAnimeId item-item)
        similar-scores (.similarAnimeScore item-item)
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.similarAnimeCount item-item))
      (println (count similar-ids))
      (println (count anime-indices))
      (.printArray writer anime-indices)
      (.printArray writer similar-ids)
      (.printArray writer similar-scores))))

(defn- load-item-item-v1 [in]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [similar-count (read-int)
            scores-count (read-int)
            anime-indices (int-array (read-int))
            similar-ids (int-array scores-count)
            similar-scores (float-array scores-count)]
        (.readArray reader anime-indices)
        (.readArray reader similar-ids)
        (.readArray reader similar-scores)
        (aqua.recommend.ItemItemModel. anime-indices similar-count
                                        similar-ids
                                        similar-scores)))))

(defn load-item-item [path]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-item-item-v1 in)))

(defn get-raw-anime-recommendations [user
                                     ^aqua.recommend.ItemItemModel model
                                     remove-known-anime]
  (let [ranked-anime (.findSimilarAnime model user)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))

(defn get-raw-manga-recommendations [user
                                     ^aqua.recommend.ItemItemModel model
                                     remove-known-anime]
  (let [ranked-anime (.findSimilarManga model user)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))

(defn get-anime-recommendations [user model remove-known-anime keep-airing-anime tagger]
  (let [[_ recommendations] (get-raw-anime-recommendations user model remove-known-anime)]
    [(tagger recommendations) []]))

(defn get-manga-recommendations [user model remove-known-anime keep-airing-anime tagger]
  (let [[_ recommendations] (get-raw-manga-recommendations user model remove-known-anime)]
    [(tagger recommendations) []]))
