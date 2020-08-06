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

; those accessor-like functions ar there to avoid the memory overhead
; of Method objects created by reflection
(defn- scored-animedb-id [^aqua.recommend.RecommendationItem scored] (.animedbId scored))
(defn- rated-animedb-id [^aqua.recommend.CFRated rated] (.animedbId rated))
(defn- rated-status [^aqua.recommend.CFRated rated] (.status rated))
(defn- franchise-items [^aqua.mal.data.Franchise franchise] (.items franchise))
(defn- item-itemdb-id [^aqua.mal.data.Item item] (.itemId item))
(defn- item-franchise [^aqua.mal.data.Item item] (.franchise item))

(defn- all-but-planned [user]
  (->> (.allButPlanToWatch user)
       (map rated-animedb-id)))

(defn- only-planned [user]
  (->> (.planToWatch user)
       (map rated-animedb-id)))

(defn- franchise-item-ids [item-ids item-map]
  (set (->> item-ids
            (map #(some-> %
                          (item-map)
                          item-franchise))
            (remove nil?)
            (mapcat franchise-items)
            (map item-itemdb-id))))

(defn user-item-ids [user item-map]
  (let [known (all-but-planned user)
        planned (only-planned user)]
    (map set
      [known
       planned
       (franchise-item-ids known item-map)
       (franchise-item-ids planned item-map)])))

(defn users-item-map [users]
  (let [item-index-map (java.util.HashMap.)]
    (doseq [^aqua.recommend.CFUser user users]
      (doseq [^aqua.recommend.CFRated rated (.itemList user)]
        (.putIfAbsent item-index-map
                      (.itemId rated)
                      (.size item-index-map))))
    item-index-map))

(defn- add-tags [ranked-anime-seq
                 planned-anime
                 known-franchises
                 planned-franchises
                 anime-map]
  (let [seen-franchises (java.util.HashSet.)]
    (doseq [^aqua.recommend.ScoredAnime scored-anime ranked-anime-seq]
      (let [anime-id (.animedbId scored-anime)
            franchise-id (if-let [^aqua.mal.data.Item anime (.get anime-map anime-id)]
                           (if-let [franchise (.franchise anime)]
                             (.franchiseId franchise)))
            is-planned (planned-anime anime-id)
            in-franchise (known-franchises anime-id)
            in-planned-franchise (planned-franchises anime-id)
            same-franchise (.contains seen-franchises franchise-id)
            tags (cond
                   (and is-planned in-franchise) :planned-and-franchise
                   is-planned   :planned
                   in-franchise :franchise
                   in-planned-franchise :planned-franchise
                   same-franchise :same-franchise
                   :else        nil)]
        (if franchise-id
          (.add seen-franchises franchise-id))
        (.setTags scored-anime tags))))
  ranked-anime-seq)

(defn make-filter [user anime-map]
  (let [known-anime (set (all-but-planned user))
        is-known-or-bad-anime (fn [^aqua.recommend.RecommendationItem rated]
                                (or (known-anime (scored-animedb-id rated))
                                    (.isHentai rated)))
        remove-anime (partial remove is-known-or-bad-anime)]
    remove-anime))

(defn make-airing-filter [user anime-map]
  (let [known-anime-filter (make-filter user anime-map)
        not-airing-or-old? (fn [rated]
                             (if-let [^aqua.mal.data.Anime anime (anime-map (scored-animedb-id rated))]
                               (or (.isCompleted anime)
                                   (.isOld anime))))]
    #(remove not-airing-or-old? (known-anime-filter %))))

(defn make-tagger [user anime-map]
  (let [[known-anime planned-anime known-franchises planned-franchises]
            (user-item-ids user anime-map)]
    (fn [ranked-anime-seq]
      (add-tags ranked-anime-seq planned-anime known-franchises planned-franchises anime-map))))

; this needs to be called "early" in order to route java.util.logging through SLF4J
(defn capture-java-util-logging []
  (let [path (-> aqua.recommend.TmpFile
                (.getClassLoader)
                (.getResource "logging.properties")
                (.getFile))];
    (System/setProperty "java.util.logging.config.file" path)))
