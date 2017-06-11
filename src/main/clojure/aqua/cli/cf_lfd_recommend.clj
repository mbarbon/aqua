(ns aqua.cli.cf-lfd-recommend
  (:require aqua.mal-local
            aqua.recommend.lfd-cf
            aqua.misc))

(def user-count 20000)

(defn- run-recommender [user lfd-users anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        airing-anime-filter (aqua.misc/make-airing-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        [recommended recommended-airing]
          (aqua.recommend.lfd-cf/get-all-recommendations user lfd-users known-anime-filter airing-anime-filter known-anime-tagger)]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
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
        users (aqua.mal-local/load-filtered-cf-users directory data-source cf-parameters user-count)
        lfd (with-open [in (clojure.java.io/reader "maldump/lfd-model")]
              (aqua.recommend.lfd/load-lfd in))
        lfd-users (with-open [in (clojure.java.io/reader "maldump/lfd-user-model")]
                    (aqua.recommend.lfd/load-user-lfd in lfd users))
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)]
    (println "Running recommender")
    (run-recommender user lfd-users anime)))
