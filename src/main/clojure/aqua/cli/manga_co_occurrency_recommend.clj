(ns aqua.cli.manga-co-occurrency-recommend
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.co-occurrency
            aqua.misc))

(defn- run-recommender [user model anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        [recommended]
          (aqua.recommend.co-occurrency/get-manga-recommendations user model known-anime-filter known-anime-tagger)]
    (println "User" (.username user) (count (seq (.inProgressAndDropped user))))
    (println)
    (println "Completed manga")
    (doseq [scored-anime recommended]
      (if-let [anime (anime-map (.animedbId scored-anime))]
        (println (.tags scored-anime) (.itemId anime) (.title anime) (.score scored-anime))))))

(defn -main [username]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0.5 -1)
        _ (println "Loading manga")
        anime (aqua.mal-local/load-manga data-source)
        _ (println "Loading users")
        user (aqua.mal-local/load-cf-manga-user data-source anime cf-parameters username)
        model (aqua.recommend.co-occurrency/load-manga-co-occurrency (aqua.paths/manga-co-occurrency-model))]
    (println "Running recommender")
    (run-recommender user model anime)))
