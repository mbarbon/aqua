(ns aqua.cli.co-occurrency-recommend
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.co-occurrency
            aqua.misc))

(defn- run-recommender [user model anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        airing-anime-filter (aqua.misc/make-airing-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        [recommended recommended-airing]
          (aqua.recommend.co-occurrency/get-all-recommendations user model known-anime-filter airing-anime-filter known-anime-tagger)]
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
        user (aqua.mal-local/load-cf-user data-source anime cf-parameters username)
        model (aqua.recommend.co-occurrency/load-co-occurrency (aqua.paths/anime-co-occurrency-model) (aqua.paths/anime-co-occurrency-model-airing))]
    (println "Running recommender")
    (run-recommender user model anime)))
