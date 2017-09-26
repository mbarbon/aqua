(ns aqua.recommend.user-sample
  (:require clojure.set
            aqua.misc
            aqua.mal-local))

; nothing against hentai, just there is not enough data for meaningful
; recommendations
(def select-non-hentai-anime
  (str "SELECT a.animedb_id AS animedb_id"
       "    FROM anime AS a"
       "      LEFT JOIN anime_genres AS ag"
       "        ON a.animedb_id = ag.animedb_id AND"
       "           genre_id = 12"
       "    WHERE sort_order IS NULL"))

(def select-active-users
  (str "SELECT uas.user_id"
       "    FROM user_anime_stats AS uas"
       "      INNER JOIN users AS u"
       "        ON uas.user_id = u.user_id AND"
       "           u.username <> ''"
       "    WHERE uas.completed > 5 AND"
       "          uas.completed < 500"))

(defn- count-to-bucket [c]
  (int (* 4 (- (Math/log (* 10 c)) 2))))

(defn- bucket-to-count [b]
  (Math/floor (+ 0.5 (/ (Math/exp (+ (/ b 4) 2)) 10))))

(defn- non-hentai-anime [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-non-hentai-anime)]
    (->> (resultset-seq rs)
         (map :animedb_id)
         (set))))

(defn- active-users [data-source]
  (with-open [connection (.getConnection data-source)
              statement (.createStatement connection)
              rs (.executeQuery statement select-active-users)]
    (let [all-ids (shuffle (map :user_id (resultset-seq rs)))
          cf-parameters (aqua.misc/make-cf-parameters 0 0)]
      (group-by #(->> % second alength count-to-bucket)
                (remove #(< (alength (second %)) 5)
                  (apply concat (for [batch (partition-all 1000 all-ids)]
                                  (for [cf-user (aqua.mal-local/load-cf-users-by-id data-source cf-parameters batch)]
                                    [(.userId cf-user) (.completedAndDroppedIds cf-user)]))))))))

(defn recompute-user-sample [sample-count model-path]
  (let [data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        anime-ids (non-hentai-anime data-source)
        all-users (active-users data-source)
        all-users-count (apply + (map count (vals all-users)))
        used-users (java.util.HashSet.)
        anime-counts (java.util.HashMap.)
        users (java.util.ArrayList.)]
    (while (and (< (.size users) sample-count)
                (< (.size used-users) all-users-count))
      (doseq [[bucket bucketed-users] all-users]
        (let [already-covered (java.util.HashSet.)]
          (doseq [[user-id completed-and-dropped] (remove #(.contains used-users (first %)) bucketed-users)]
            (if-not (> (count already-covered) (* (/ 2 3) (count anime-ids)))
            (let [added (clojure.set/difference
                          (clojure.set/intersection (set completed-and-dropped)
                                                    anime-ids)
                          already-covered)]
              (when (> (count added) (max 4 (* 0.03 (count completed-and-dropped))))
                (.addAll already-covered (set completed-and-dropped))
                (.add used-users user-id)
                (.add users user-id)
  ;              (doseq [completed-and-dropped-id completed-and-dropped]
  ;                (.put anime-counts (+ 1 (get anime-counts completed-and-dropped-id 0))))
                )))))))
    (with-open [out (clojure.java.io/writer model-path)]
      (binding [*out* out]
        (doseq [user-id users]
          (println user-id))))))
