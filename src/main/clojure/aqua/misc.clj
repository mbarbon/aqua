(ns aqua.misc
  )

(defn make-cf-parameters [watched-zero dropped-zero]
  (doto (aqua.recommend.CFParameters.)
    (-> .nonRatedCompleted (set! watched-zero))
    (-> .nonRatedDropped (set! dropped-zero))))

(defn normalize-all-ratings
  ([users] (normalize-all-ratings 0.5 -1))
  ([users watched-zero dropped-zero]
    (let [cf-parameters (make-cf-parameters watched-zero dropped-zero)]
      (doseq [user users]
        (.processAfterDeserialize user cf-parameters)))))

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
        is-known-anime (fn [^aqua.recommend.CFRated rated]
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
