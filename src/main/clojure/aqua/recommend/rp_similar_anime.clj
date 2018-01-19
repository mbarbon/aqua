(ns aqua.recommend.rp-similar-anime
  (:require aqua.recommend.item-item-model))

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
  (aqua.recommend.item-item-model/store-item-item out rp-similarity))

(defn load-rp-similarity [path]
  (aqua.recommend.item-item-model/load-item-item path))

(defn get-recommendations [user
                           ^aqua.recommend.ItemItemModel rp
                           remove-known-anime]
  (aqua.recommend.item-item-model/get-recommendations user rp remove-known-anime))

(defn get-all-recommendations [user rp remove-known-anime keep-airing-anime tagger]
  (aqua.recommend.item-item-model/get-all-recommendations user rp remove-known-anime keep-airing-anime tagger))
