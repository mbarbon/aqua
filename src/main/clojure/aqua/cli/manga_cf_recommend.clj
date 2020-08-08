(ns aqua.cli.manga-cf-recommend
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.cosine
            aqua.recommend.user-sample
            aqua.misc))

(def user-count 20000)

(defn- run-recommender [user users manga-map]
  (let [known-manga-filter (aqua.misc/make-filter user manga-map)
        known-manga-tagger (aqua.misc/make-tagger user manga-map)
        [recommended]
          (aqua.recommend.cosine/get-manga-recommendations user users known-manga-filter known-manga-tagger)]
    (println "User" (.username user) (count (seq (.inProgressAndDropped user))))
    (println "Completed manga")
    (doseq [scored-manga recommended]
      (if-let [manga (manga-map (.animedbId scored-manga))]
        (println (.tags scored-manga) (.mangadbId manga) (.title manga) (.score scored-manga))))))

(defn -main [username]
  (println "Starting")
  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0.5 -1)
        _ (println "Loading manga")
        manga (aqua.mal-local/load-manga data-source)
        _ (println "Loading users")
        user (aqua.mal-local/load-cf-manga-user data-source manga cf-parameters username)
        users (aqua.recommend.user-sample/load-filtered-cf-users aqua.recommend.ModelType/MANGA (aqua.paths/manga-user-sample) data-source cf-parameters user-count)]
    (println "Running recommender")
    (run-recommender user users manga)))
