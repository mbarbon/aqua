(ns aqua.cli-recommend
  (:require aqua.mal-local aqua.recommend.cosine aqua.misc))

(defn- run-recommender [user users anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        not-airing-or-old? (fn [rated]
                             (let [anime (anime-map (.animedbId rated))]
                               (or (.isCompleted anime)
                                   (.isOld anime))))
        airing-anime-filter #(remove not-airing-or-old? (known-anime-filter %))
        [users recommended] (known-anime-tagger
                              (aqua.recommend.cosine/get-recommendations user users known-anime-filter))
        [_ recommended-airing] (known-anime-tagger [users (aqua.recommend.collaborative-filter/recommended-airing users airing-anime-filter)])]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
    (println "Users")
    (doseq [[score user] users]
      (println (.username user) (count (seq (.completedAndDropped user))) score))
    (println)
    (println "Airing anime")
    (doseq [[anime-id score tag] (take 15 recommended-airing)]
      (let [anime (anime-map anime-id)]
        (println tag anime-id (.title anime) score)))
    (println)
    (println "Completed anime")
    (doseq [[anime-id score tag] recommended]
      (let [anime (anime-map anime-id)]
        (println tag anime-id (.title anime) score)))))

(defn -main [username]
  (println "Starting")
  (let [directory "maldump"
        data-source (aqua.mal-local/open-sqlite-ro directory "maldump.sqlite")
        _ (println "Loading users")
        user (aqua.mal-local/load-user data-source username)
        users (aqua.mal-local/load-users data-source 20000)
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)]
    (println "Normalizing")
    (aqua.misc/normalize-all-ratings users)
    (aqua.misc/normalize-ratings user)
    (println "Running recommender")
    (run-recommender user users anime)))
