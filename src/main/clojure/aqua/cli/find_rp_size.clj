(ns aqua.cli.find-rp-size
  (:require aqua.mal-local
            aqua.misc
            aqua.recommend.rp-similar-anime
            clojure.set))

(def iterations 5)
(def user-count 20000)

(defn- find-similar-anime [users anime-map projection-size similar-count anime-sample]
  (let [rp-similar (time (aqua.recommend.rp-similar-anime/create-rp-similarity users anime-map projection-size similar-count))]
    (into {}
      (for [anime-id anime-sample]
        [anime-id (doall (map #(.animedbId %) (.similarAnime rp-similar anime-id)))]))))

(defn- common-element-count [one two]
  (apply +
    (for [[anime-id similar-one] one]
      (let [similar-two (two anime-id)]
        (count (clojure.set/intersection (set similar-one) (set similar-two)))))))

(defn- score-intersections [one two & rest]
  (let [score (common-element-count one two)]
    (if (seq rest)
      (+ score (apply score-intersections two rest))
      score)))

(defn- total-size [result-maps]
  (apply + (for [result-map result-maps]
             (apply + (map count (vals result-map))))))

(defn- intersection-count [users anime-map projection-size similar-count anime-sample]
  (let [iterations (for [_ (range iterations)]
                     (find-similar-anime users anime-map projection-size similar-count anime-sample))]
    [(apply score-intersections iterations) (total-size iterations)]))

(defn -main [& projection-sizes]
  (let [data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        users (aqua.mal-local/load-filtered-cf-users-into "maldump" data-source cf-parameters (java.util.HashMap.) (java.util.ArrayList. (for [_ (range user-count)] nil)))
        anime (aqua.mal-local/load-anime data-source)
        similar-count 30
        anime-sample (take 1000 (shuffle (keys anime)))]
    (doseq [projection-size (map #(Integer/valueOf %) projection-sizes)]
      (let [[score total] (intersection-count users anime projection-size similar-count anime-sample)
            normalized-score (/ score total)]
        (printf "Projection %d score %.03f\n" projection-size (float normalized-score))
        (newline)))))
