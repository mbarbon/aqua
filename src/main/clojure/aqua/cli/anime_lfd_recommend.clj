(ns aqua.cli.anime-lfd-recommend
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.lfd
            aqua.misc))

(defn- run-recommender [user lfd lfd-airing anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        airing-anime-filter (aqua.misc/make-airing-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        [recommended recommended-airing]
          (aqua.recommend.lfd/get-anime-recommendations user lfd lfd-airing known-anime-filter airing-anime-filter known-anime-tagger)]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
    (println)
    (println "Airing anime")
    (doseq [scored-anime (take 15 recommended-airing)]
      (if-let [anime (anime-map (.animedbId scored-anime))]
        (println (.tags scored-anime) (.animedbId anime) (.title anime) (.score scored-anime))))
    (println)
    (println "Completed anime")
    (doseq [scored-anime recommended]
      (if-let [anime (anime-map (.animedbId scored-anime))]
        (println (.tags scored-anime) (.animedbId anime) (.title anime) (.score scored-anime))))))

(defn -main [username]
  (println "Starting")
  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0.5 -1)
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)
        _ (println "Loading users")
        user (aqua.mal-local/load-cf-anime-user data-source anime cf-parameters username)
        lfd (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model) anime)
        lfd-airing (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model-airing) anime)]
    (println "Running recommender")
    (run-recommender user lfd lfd-airing anime)))
