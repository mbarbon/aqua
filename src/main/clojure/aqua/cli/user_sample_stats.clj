(ns aqua.cli.user-sample-stats
  (:require aqua.mal-local
            aqua.recommend.user-sample)
  (:use aqua.db-utils))

(def ^:private query-all-users
  (str "SELECT user_id"
       "    FROM user_anime_stats"
       "    WHERE completed > 5 AND"
       "          completed < 500"
       "    LIMIT ?"))

(def ^:private query-user-stats
  (str "SELECT completed FROM user_anime_stats WHERE user_id = ?"))

(defn- merge-anime-set [current-set user]
  (let [^java.util.BitSet anime-set (or current-set (java.util.BitSet.))]
    (doseq [anime-id (.completedAndDroppedIds user)]
      (if (< 0 anime-id) ; non-hentai
        (.set anime-set anime-id)))
    anime-set))

(defn- update-bucket-stats [data-source anime stats user-id]
  (let [user-completed (with-query data-source rs query-user-stats [user-id]
                         (:completed (first (resultset-seq rs))))
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        user (first (aqua.recommend.user-sample/load-filtered-cf-user-ids data-source cf-parameters [user-id] anime))
        bucket (aqua.recommend.user-sample/count-to-bucket-count user-completed)
        bucket-stats (stats bucket)]
    (assoc stats bucket {:count (+ 1 (:count bucket-stats 0))
                         :anime (merge-anime-set (:anime bucket-stats)
                                                 user)})))

(defn- print-bucket [total-count bucket-count bucket]
  (let [anime-count (.cardinality (:anime bucket))]
    (println (format "%d: users %d anime %d covering %.02f%%"
                     bucket-count (:count bucket) anime-count
                     (double (* 100 (/ anime-count total-count)))))))

(defn- print-coverage [anime-count non-hentai-count coverage]
  (let [overall (java.util.BitSet.)]
  (doseq [count (->> coverage keys sort)]
    (let [bucket (coverage count)]
      (.or overall (:anime bucket))
      (print-bucket non-hentai-count count bucket)))
  (println (format "Total %d, non-Hentai %d" anime-count non-hentai-count))
  (println (format "Covered %d, %.02f%%" (.cardinality overall)
                                           (float (* 100 (/ (.cardinality overall) non-hentai-count)))))))

(defn -main [user-count-string overall-count-string]
  (let [user-count (Integer/valueOf user-count-string)
        overall-count (Integer/valueOf overall-count-string)
        data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        anime (aqua.mal-local/load-anime data-source)
        anime-count (count anime)
        non-hentai-count (count (filter #(not (.isHentai %)) (vals anime)))
        sampled-ids (aqua.recommend.user-sample/load-user-sample "maldump/user-sample" user-count)
        all-user-ids (with-query data-source rs query-all-users [overall-count]
                       (doall (map :user_id (resultset-seq rs))))
        stats (reduce (partial update-bucket-stats data-source anime) {} sampled-ids)
        overall-stats (reduce (partial update-bucket-stats data-source anime) {} all-user-ids)]
    (println "User sample coverage by bucket")
    (print-coverage anime-count non-hentai-count stats)
    (println)
    (print-coverage anime-count non-hentai-count overall-stats)))