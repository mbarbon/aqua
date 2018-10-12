(ns aqua.cli.items-similarity
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.lfd-items
            aqua.recommend.co-occurrency
            aqua.recommend.rp-similar-anime))

(defn- print-similar [model-name anime scored-anime]
  (.sort scored-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
  (println (str "\n" model-name))
  (doseq [scored scored-anime]
    (if-let [anime (anime (.animedbId scored))]
      (println (.animedbId scored) (.title anime) (.score scored)))))

(defn -main [animedb-string]
  (println "Starting")
  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        animedb-id (Integer/valueOf animedb-string)
        _ (println "Loading models")
        lfd-items (aqua.recommend.lfd-items/load-lfd-items (aqua.paths/anime-lfd-items-model) (aqua.paths/anime-lfd-items-model-airing))
        co-occurrency (aqua.recommend.co-occurrency/load-co-occurrency (aqua.paths/anime-co-occurrency-model) (aqua.paths/anime-co-occurrency-model-airing))
        rp-model (aqua.recommend.rp-similar-anime/load-rp-similarity (aqua.paths/anime-rp-model))
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)
        scored-anime-lfd (.similarAnime (.complete lfd-items) animedb-id)
        scored-anime-co-occurrency (.similarAnime (.complete co-occurrency) animedb-id)
        scored-anime-rp (.similarAnime rp-model animedb-id)]
    (println "Anime similar to " (.title (anime animedb-id)))
    (print-similar "LFD" anime scored-anime-lfd)
    (print-similar "Co-occurrency" anime scored-anime-co-occurrency)
    (print-similar "RP" anime scored-anime-rp)))
