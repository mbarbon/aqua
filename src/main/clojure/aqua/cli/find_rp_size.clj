(ns aqua.cli.find-rp-size
  (:require aqua.mal-local
            aqua.paths
            aqua.misc
            aqua.recommend.rp-similarity
            aqua.recommend.user-sample
            clojure.set))

(def iterations 5)
(def user-count 20000)

(defn- find-similar-items [users item-map projection-size similar-count item-sample]
  (let [rp-similar (time (aqua.recommend.rp-similarity/create-rp-similarity users item-map projection-size similar-count))]
    (into {}
      (for [item-id item-sample]
        [item-id (doall (map #(.animedbId %) (.similarAnime rp-similar item-id)))]))))

(defn- common-element-count [one two]
  (apply +
    (for [[item-id similar-one] one]
      (let [similar-two (two item-id)]
        (count (clojure.set/intersection (set similar-one) (set similar-two)))))))

(defn- score-intersections [one two & rest]
  (let [score (common-element-count one two)]
    (if (seq rest)
      (+ score (apply score-intersections two rest))
      score)))

(defn- total-size [result-maps]
  (apply + (for [result-map result-maps]
             (apply + (map count (vals result-map))))))

(defn- intersection-count [users item-map projection-size similar-count item-sample]
  (let [iterations (for [_ (range iterations)]
                     (find-similar-items users item-map projection-size similar-count item-sample))]
    [(apply score-intersections iterations) (total-size iterations)]))

(defn -main [kind-string projection-sizes]
  (let [data-source (aqua.mal-local/open-sqlite-ro (aqua.paths/mal-db))
        kind (aqua.recommend.ModelType/fromString kind-string)
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        users (if (.isAnime kind)
                (aqua.recommend.user-sample/load-filtered-cf-users kind (aqua.paths/anime-user-sample) data-source cf-parameters user-count)
                (aqua.recommend.user-sample/load-filtered-cf-users kind (aqua.paths/manga-user-sample) data-source cf-parameters user-count))
        items (if (.isAnime kind)
                (aqua.mal-local/load-anime data-source)
                (aqua.mal-local/load-manga data-source))
        similar-count 30
        item-sample (take 1000 (shuffle (keys items)))]
    (doseq [projection-size (map #(Integer/valueOf %) (clojure.string/split projection-sizes #","))]
      (let [[score total] (intersection-count users items projection-size similar-count item-sample)
            normalized-score (/ score total)]
        (printf "Projection %d score %.03f\n" projection-size (float normalized-score))
        (newline)))))
