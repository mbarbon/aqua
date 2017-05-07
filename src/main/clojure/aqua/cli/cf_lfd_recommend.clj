(ns aqua.cli.cf-lfd-recommend
  (:require aqua.mal-local
            aqua.recommend.lfd-cf
            aqua.misc))

(defn- run-recommender [user lfd-users anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        not-airing-or-old? (fn [rated]
                             (let [anime (anime-map (.animedbId rated))]
                               (or (.isCompleted anime)
                                   (.isOld anime))))
        airing-anime-filter #(remove not-airing-or-old? (known-anime-filter %))
        [users recommended] (known-anime-tagger
                              (aqua.recommend.lfd-cf/get-recommendations user lfd-users known-anime-filter))
        [_ recommended-airing] (known-anime-tagger [users (aqua.recommend.collaborative-filter/recommended-airing users airing-anime-filter)])]
    (println "User" (.username user) (count (seq (.completedAndDropped user))))
    (println "Users")
    (doseq [scored-user users]
      (println (.username (.user scored-user)) (count (seq (.completedAndDropped user))) (.score scored-user)))
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
        users (aqua.mal-local/load-filtered-cf-users-into directory data-source cf-parameters (java.util.HashMap.) (java.util.ArrayList. (for [_ (range 20000)] nil)))
        lfd (with-open [in (clojure.java.io/reader "maldump/lfd-model")]
              (aqua.recommend.lfd/load-lfd in))
        lfd-users (with-open [in (clojure.java.io/reader "maldump/lfd-user-model")]
                    (aqua.recommend.lfd/load-user-lfd in lfd users))
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)]
    (println "Running recommender")
    (run-recommender user lfd-users anime)))
