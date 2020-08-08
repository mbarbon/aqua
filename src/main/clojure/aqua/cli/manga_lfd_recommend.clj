(ns aqua.cli.manga-lfd-recommend
  (:require aqua.mal-local
            aqua.paths
            aqua.recommend.lfd
            aqua.misc))

(defn- run-recommender [user lfd manga-map]
  (let [known-manga-filter (aqua.misc/make-filter user manga-map)
        known-manga-tagger (aqua.misc/make-tagger user manga-map)
        [recommended]
          (aqua.recommend.lfd/get-manga-recommendations user lfd known-manga-filter known-manga-tagger)]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
    (println)
    (println "Manga")
    (doseq [scored-manga recommended]
      (if-let [manga (manga-map (.animedbId scored-manga))]
        (println (.tags scored-manga) (.mangadbId manga) (.title manga) (.score scored-manga))))))

(defn -main [username]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        cf-parameters (aqua.misc/make-cf-parameters 0.5 -1)
        _ (println "Loading manga")
        manga (aqua.mal-local/load-manga data-source)
        _ (println "Loading users")
        user (aqua.mal-local/load-cf-manga-user data-source manga cf-parameters username)
        lfd (aqua.recommend.lfd/load-lfd (aqua.paths/manga-lfd-model) manga)]
    (println "Running recommender")
    (run-recommender user lfd manga)))
