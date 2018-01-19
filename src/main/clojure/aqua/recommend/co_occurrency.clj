(ns aqua.recommend.co-occurrency
  (:require aqua.recommend.item-item-model))

(defn create-co-occurrency [user-list anime-map score-threshold alpha similar-count]
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
    (let [compute (aqua.recommend.ComputeCoOccurrencyItemItem. anime-map anime-index-map similar-count)]
      (.findSimilarAnime compute user-list score-threshold alpha)
      (.simpleItemItem compute))))

(defn similar-anime-debug [co-occurrency anime-id anime-map]
  (let [similar-scored (.similarAnime co-occurrency anime-id)]
    (doseq [scored similar-scored]
      (let [anime (anime-map (.animedbId scored))]
        (println (.animedbId scored) (.title anime) (.score scored))))))

(defn store-co-occurrency [out co-occurrency]
  (aqua.recommend.item-item-model/store-item-item out co-occurrency))

(defn load-co-occurrency [path]
  (aqua.recommend.item-item-model/load-item-item path))

(defn get-recommendations [user
                           ^aqua.recommend.ItemItemModel model
                           remove-known-anime]
  (aqua.recommend.item-item-model/get-recommendations user model remove-known-anime))

(defn get-all-recommendations [user model remove-known-anime keep-airing-anime tagger]
  (aqua.recommend.item-item-model/get-all-recommendations user model remove-known-anime keep-airing-anime tagger))
