(ns aqua.cli.lfd-items-recommend
  (:require aqua.mal-local
            aqua.recommend.lfd
            aqua.recommend.lfd-items
            aqua.misc))

(defn- run-recommender [user lfd-items lfd-items-airing anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        airing-anime-filter (aqua.misc/make-airing-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        [recommended recommended-airing]
          (aqua.recommend.lfd-items/get-all-recommendations user lfd-items lfd-items-airing known-anime-filter airing-anime-filter known-anime-tagger)]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
    (println)
    (println "Airing anime")
    (doseq [scored-anime (take 15 recommended-airing)]
      (let [anime (anime-map (.animedbId scored-anime))]
        (println (.tags scored-anime) (.animedbId anime) (.title anime) (.score scored-anime))))
    (println)
    (println "Completed anime")
    (doseq [scored-anime recommended]
      (let [anime (anime-map (.animedbId scored-anime))]
        (println (.tags scored-anime) (.animedbId anime) (.title anime) (.score scored-anime))))))

(defn -main [username]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        cf-parameters (aqua.misc/make-cf-parameters 0.5 -1)
        _ (println "Loading users")
        user (aqua.mal-local/load-cf-user data-source username cf-parameters)
        lfd (aqua.recommend.lfd/load-lfd "maldump/lfd-model")
        lfd-airing (aqua.recommend.lfd/load-lfd "maldump/lfd-model-airing")
        lfd-items (aqua.recommend.lfd-items/load-lfd-items "maldump/lfd-items-model" lfd lfd)
        lfd-items-airing (aqua.recommend.lfd-items/load-lfd-items "maldump/lfd-items-model-airing" lfd lfd-airing)
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)]
    (println "Running recommender")
    (run-recommender user lfd-items lfd-items-airing anime)))
