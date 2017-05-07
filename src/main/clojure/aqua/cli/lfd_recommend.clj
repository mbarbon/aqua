(ns aqua.cli.lfd-recommend
  (:require aqua.mal-local aqua.recommend.lfd aqua.misc))

(defn- run-recommender [user lfd lfd-airing anime-map]
  (let [known-anime-filter (aqua.misc/make-filter user anime-map)
        known-anime-tagger (aqua.misc/make-tagger user anime-map)
        not-airing-or-old? (fn [rated]
                             (let [anime (anime-map (.animedbId rated))]
                               (or (.isCompleted anime)
                                   (.isOld anime))))
        airing-anime-filter #(remove not-airing-or-old? (known-anime-filter %))
        [completed airing] (aqua.recommend.lfd/get-all-recommendations user lfd lfd-airing known-anime-filter)
        [_ recommended] (known-anime-tagger [[] completed])
        [_ recommended-airing] (known-anime-tagger [[] airing])]
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
        lfd (with-open [in (clojure.java.io/reader "maldump/lfd-model")]
              (aqua.recommend.lfd/load-lfd in))
        lfd-airing (with-open [in (clojure.java.io/reader "maldump/lfd-model-airing")]
                     (aqua.recommend.lfd/load-lfd in))
        _ (println "Loading anime")
        anime (aqua.mal-local/load-anime data-source)]
    (println "Running recommender")
    (run-recommender user lfd lfd-airing anime)))
