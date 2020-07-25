(ns aqua.recommend.rp-similarity
  (:require aqua.recommend.item-item-model
            aqua.misc))

(defn create-rp-similarity [user-list anime-map rank similar-count]
  (let [anime-index-map (aqua.misc/users-item-map user-list)
        compute (aqua.recommend.ComputeRPSimilarAnime. anime-map anime-index-map similar-count)]
    (.findSimilarAnime compute user-list rank)
    (.rpSimilarAnime compute)))

(defn similar-anime-debug [rp-similarity anime-id anime-map]
  (let [similar-scored (.similarAnime rp-similarity anime-id)]
    (doseq [scored similar-scored]
      (let [anime (anime-map (.animedbId scored))]
        (println (.animedbId scored) (.title anime) (.score scored))))))

(defn store-rp-similarity [out rp-similarity]
  (aqua.recommend.item-item-model/store-item-item out rp-similarity))

(defn load-rp-similarity [path]
  (aqua.recommend.item-item-model/load-item-item path))

(defn get-raw-anime-recommendations [user
                                     ^aqua.recommend.ItemItemModel rp
                                     remove-known-anime]
  (aqua.recommend.item-item-model/get-raw-anime-recommendations user rp remove-known-anime))

(defn get-anime-recommendations [user rp remove-known-anime keep-airing-anime tagger]
  (aqua.recommend.item-item-model/get-anime-recommendations user rp remove-known-anime keep-airing-anime tagger))
