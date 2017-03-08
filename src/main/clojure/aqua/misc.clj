(ns aqua.misc
  )

(defn- statistics [anime-list]
  (let [anime-list-ratings (remove zero?
                             (map (fn [^aqua.mal.data.Rated r]
                               (.rating r)) anime-list))]
    (if (empty? anime-list-ratings)
      [0 1 0]
      (let [size (count anime-list-ratings)
            sum (apply + anime-list-ratings)
            sum-sq (apply + (map #(* % %) anime-list-ratings))
            mean (float (/ sum size))
            variance (- (/ sum-sq size) (* mean mean))
            stddev (if (zero? variance) 1 (Math/sqrt variance))]
        [mean stddev (apply min anime-list-ratings)]))))

(defn normalize-ratings
  ([user] (normalize-ratings user 0.5 -1))
  ([user watched-zero dropped-zero]
    (let [[mean stddev min-rating] (statistics (.completedAndDropped user))
          dropped-rating (- (+ min-rating dropped-zero) mean)]
      (doseq [^aqua.mal.data.Rated item (.completed user)]
        (if (zero? (.rating item))
          ; we assume non-rated completed items got the mean rating
          (set! (.normalizedRating item) (/ watched-zero stddev))
          (set! (.normalizedRating item) (/ (+ (- (.rating item) mean) watched-zero) stddev))))
      (doseq [^aqua.mal.data.Rated item (.dropped user)]
        (if (zero? (.rating item))
          (set! (.normalizedRating item) (/ dropped-rating stddev))
          (set! (.normalizedRating item) (/ (- (.rating item) mean) stddev)))))))

(defn normalize-all-ratings
  ([users]
    (doseq [user users]
      (normalize-ratings user)))
  ([users watched-zero dropped-zero]
    (doseq [user users]
      (normalize-ratings user watched-zero dropped-zero))))

(defn- all-but-planned [user]
  (->> (.animeList user)
       (filter #(not= (.status %) aqua.mal.data.Rated/PLANTOWATCH))
       (map #(.animedbId %))))

(defn- only-planned [user]
  (->> (.animeList user)
       (filter #(= (.status %) aqua.mal.data.Rated/PLANTOWATCH))
       (map #(.animedbId %))))

(defn- franchise-anime-ids [anime-ids anime-map]
  (set (->> anime-ids
            (map #(-> %
                      (anime-map)
                      (.franchise)))
            (remove nil?)
            (mapcat #(.anime %))
            (map #(.animedbId %)))))

(defn user-anime-ids [user anime-map]
  (let [known (all-but-planned user)
        planned (only-planned user)]
    (map set
      [known
       planned
       (franchise-anime-ids known anime-map)
       (franchise-anime-ids planned anime-map)])))

(defn- add-tags [ranked-anime-seq
                 planned-anime
                 known-franchises
                 planned-franchises]
  (for [[anime-id score] ranked-anime-seq]
    (let [is-planned (planned-anime anime-id)
          in-franchise (known-franchises anime-id)
          in-planned-franchise (planned-franchises anime-id)
          tags (cond
                 (and is-planned in-franchise) :planned-and-franchise
                 is-planned   :planned
                 in-franchise :franchise
                 in-planned-franchise :planned-franchise
                 :else        nil)]
      [anime-id score tags])))

(defn make-filter [user anime-map]
  (let [known-anime (set (all-but-planned user))
        is-known-anime (fn [^aqua.mal.data.Rated rated]
                         (known-anime (.animedbId rated)))
        remove-known-anime (partial remove is-known-anime)]
    remove-known-anime))

(defn make-airing-filter [user anime-map]
  (let [known-anime-filter (make-filter user anime-map)
        not-airing-or-old? (fn [rated]
                             (let [anime (anime-map (.animedbId rated))]
                               (or (.isCompleted anime)
                                   (.isOld anime))))]
    #(remove not-airing-or-old? (known-anime-filter %))))

(defn make-tagger [user anime-map]
  (let [[known-anime planned-anime known-franchises planned-franchises]
            (user-anime-ids user anime-map)]
    (fn [[users ranked-anime-seq]]
      [users (add-tags ranked-anime-seq planned-anime known-franchises planned-franchises)])))
