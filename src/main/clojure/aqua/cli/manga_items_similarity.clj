(ns aqua.cli.manga-items-similarity
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.lfd-items
            aqua.recommend.co-occurrency
            aqua.recommend.embedding
            aqua.recommend.rp-similarity))

(defn- print-similar [model-name anime scored-anime]
  (.sort scored-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
  (println (str "\n" model-name))
  (doseq [scored scored-anime]
    (if-let [anime (anime (.animedbId scored))]
      (println (.animedbId scored) (.title anime) (.score scored)))))

(defn -main [animedb-string]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        animedb-id (Integer/valueOf animedb-string)
        _ (println "Loading models")
        co-occurrency (aqua.recommend.co-occurrency/load-manga-co-occurrency (aqua.paths/manga-co-occurrency-model))
        embedding (aqua.recommend.embedding/load-embedding-items (aqua.paths/manga-embedding-items-model))
        _ (println "Loading manga")
        anime (aqua.mal-local/load-manga data-source)
        scored-anime-co-occurrency (.similarAnime (.complete co-occurrency) animedb-id)
        scored-anime-embedding (.similarAnime (.complete embedding) animedb-id)]
    (println "manga similar to " (.title (anime animedb-id)))
    (print-similar "Co-occurrency" anime scored-anime-co-occurrency)
    (print-similar "Embedding" anime scored-anime-embedding)))
