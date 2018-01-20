(ns aqua.recommend.co-occurrency
  (:require aqua.recommend.item-item-model))

(defn create-co-occurrency [user-list anime-map score-threshold alpha similar-count similar-count-airing]
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
    (let [compute-complete (aqua.recommend.ComputeCoOccurrencyItemItem. anime-map anime-index-map similar-count)
          compute-airing (aqua.recommend.ComputeCoOccurrencyItemItem. anime-map anime-index-map similar-count-airing)]
      (.findSimilarAnime compute-complete user-list score-threshold alpha)
      (.findSimilarAiringAnime compute-airing user-list score-threshold alpha)
      (aqua.recommend.CoOccurrency. (.simpleItemItem compute-complete)
                                    (.simpleItemItem compute-airing)))))

(defn similar-anime-debug [co-occurrency anime-id anime-map]
  (let [similar-scored (.similarAnime co-occurrency anime-id)]
    (doseq [scored similar-scored]
      (let [anime (anime-map (.animedbId scored))]
        (println (.animedbId scored) (.title anime) (.score scored))))))

(defn store-co-occurrency-complete [out co-occurrency]
  (aqua.recommend.item-item-model/store-item-item out (.complete co-occurrency)))

(defn store-co-occurrency-airing [out co-occurrency]
  (aqua.recommend.item-item-model/store-item-item out (.airing co-occurrency)))

(defn load-co-occurrency [path-complete path-airing]
  (let [complete (aqua.recommend.item-item-model/load-item-item path-complete)
        airing (aqua.recommend.item-item-model/load-item-item path-airing)]
    (aqua.recommend.CoOccurrency. complete airing)))

(defn get-recommendations [user
                           ^aqua.recommend.CoOccurrency model
                           remove-known-anime]
  (aqua.recommend.item-item-model/get-recommendations user (.complete model) remove-known-anime))

(defn get-all-recommendations [user
                               ^aqua.recommend.CoOccurrency model
                               remove-known-anime keep-airing-anime
                               tagger]
  (let [[_ recommendations] (aqua.recommend.item-item-model/get-recommendations user (.complete model) remove-known-anime)
        [_ airing] (aqua.recommend.item-item-model/get-recommendations user (.airing model) remove-known-anime)]
    [(tagger recommendations) (tagger airing)]))