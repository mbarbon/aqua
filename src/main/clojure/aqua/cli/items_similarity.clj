(ns aqua.cli.items-similarity
  (:require aqua.mal-local
            aqua.recommend.lfd-items
            aqua.recommend.rp-similar-anime))

(defn -main [animedb-string]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        animedb-id (Integer/valueOf animedb-string)
        _ (println "Loading models")
        lfd-items (aqua.recommend.lfd-items/load-lfd-items "maldump/lfd-items-model" "maldump/lfd-items-model-airing")
        rp-model (aqua.recommend.rp-similar-anime/load-rp-similarity "maldump/rp-model")
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)
        scored-anime-lfd (.similarAnime (.complete lfd-items) animedb-id)
        scored-anime-rp (.similarAnime rp-model animedb-id)]
    (.sort scored-anime-lfd aqua.recommend.ScoredAnimeId/SORT_SCORE)
    (.sort scored-anime-rp aqua.recommend.ScoredAnimeId/SORT_SCORE)
    (println "Anime similar to " (.title (anime animedb-id)))
    (println "\nLFD")
    (doseq [scored scored-anime-lfd]
      (println (.animedbId scored) (.title (anime (.animedbId scored))) (.score scored)))
    (println "\nRP")
    (doseq [scored scored-anime-rp]
      (println (.animedbId scored) (.title (anime (.animedbId scored))) (.score scored)))))
