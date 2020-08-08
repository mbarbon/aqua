(ns aqua.recommend.co-occurrency
  (:require aqua.recommend.item-item-model
            aqua.misc))

(defn create-anime-co-occurrency [user-list anime-map score-threshold alpha similar-count similar-count-airing]
  (let [anime-index-map (aqua.misc/users-item-map user-list)
        compute-complete (aqua.recommend.ComputeCoOccurrencyItemItem. anime-map anime-index-map similar-count)
        compute-airing (aqua.recommend.ComputeCoOccurrencyItemItem. anime-map anime-index-map similar-count-airing)]
    (.findSimilarAnime compute-complete user-list score-threshold alpha)
    (.findSimilarAiringAnime compute-airing user-list score-threshold alpha)
    (aqua.recommend.CoOccurrency. (.simpleItemItem compute-complete)
                                  (.simpleItemItem compute-airing))))

(defn create-manga-co-occurrency [user-list manga-map score-threshold alpha similar-count]
  (let [manga-index-map (aqua.misc/users-item-map user-list)
        compute-complete (aqua.recommend.ComputeCoOccurrencyItemItem. manga-map manga-index-map similar-count)]
    (.findSimilarManga compute-complete user-list score-threshold alpha)
    (aqua.recommend.CoOccurrency. (.simpleItemItem compute-complete)
                                  nil)))

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

(defn load-manga-co-occurrency [path-complete]
  (let [complete (aqua.recommend.item-item-model/load-item-item path-complete)]
    (aqua.recommend.CoOccurrency. complete nil)))

(defn get-raw-anime-recommendations [user
                                     ^aqua.recommend.CoOccurrency model
                                     remove-known-anime]
  (aqua.recommend.item-item-model/get-raw-anime-recommendations user (.complete model) remove-known-anime))

(defn get-anime-recommendations [user
                                 ^aqua.recommend.CoOccurrency model
                                 remove-known-anime keep-airing-anime
                                 tagger]
  (let [[_ recommendations] (aqua.recommend.item-item-model/get-raw-anime-recommendations user (.complete model) remove-known-anime)
        [_ airing] (aqua.recommend.item-item-model/get-raw-anime-recommendations user (.airing model) remove-known-anime)]
    [(tagger recommendations) (tagger airing)]))

(defn get-manga-recommendations [user
                                 ^aqua.recommend.CoOccurrency model
                                 remove-known-manga
                                 tagger]
  (let [[_ recommendations] (aqua.recommend.item-item-model/get-raw-manga-recommendations user (.complete model) remove-known-manga)]
    [(tagger recommendations)]))
