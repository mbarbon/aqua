(ns aqua.recommend.lfd
  )

(defn prepare-lfd-decompositor [user-list anime-map rank lambda]
  (let [anime-index-map (java.util.HashMap.)
        anime-rated-map (java.util.ArrayList.)
        airing-anime-index-map (java.util.HashMap.)
        airing-anime-rated-map (java.util.ArrayList.)]
    (doseq [anime (vals anime-map)]
      (cond
        (.isAiring anime) (.put airing-anime-index-map
                                (.animedbId anime)
                                (.size airing-anime-index-map))
        (.isCompleted anime) (.put anime-index-map
                                   (.animedbId anime)
                                   (.size anime-index-map))))
    (dotimes [_ (.size anime-index-map)]
      (.add anime-rated-map nil))
    (dotimes [_ (.size airing-anime-index-map)]
      (.add airing-anime-rated-map nil))
    (doseq [entry anime-index-map]
      (.set anime-rated-map (.getValue entry) (.getKey entry)))
    (doseq [entry airing-anime-index-map]
      (.set airing-anime-rated-map (.getValue entry) (.getKey entry)))
    (let [lfdr (aqua.recommend.ComputeLatentFactorDecomposition. anime-index-map
                                                                 (into-array Integer/TYPE anime-rated-map)
                                                                 (.size user-list)
                                                                 rank
                                                                 lambda)
          ; shares user weights
          lfdr-airing (.forAiring lfdr
                                  airing-anime-index-map
                                  (into-array Integer/TYPE airing-anime-rated-map))]
      (dotimes [i (.size user-list)]
        (.addCompletedRatings lfdr i (.get user-list i))
        (.addAiringRatings lfdr-airing i (.get user-list i)))
      (.initializeIteration lfdr)
      ; initializes user weights twice, but it's OK
      (.initializeIteration lfdr-airing)
      [lfdr lfdr-airing])))

(defn run-user-steps
  ([lfdr] (run-user-steps lfdr 0 (.userCount lfdr)))
  ([lfdr current-index steps]
    (dotimes [i steps]
      (.userStep lfdr (+ current-index i)))))

(defn run-anime-steps
  ([lfdr] (run-anime-steps lfdr 0 (.animeCount lfdr)))
  ([lfdr current-index steps]
    (dotimes [i steps]
      (.animeStep lfdr (+ current-index i)))))

(defn store-lfd [out lfd]
  (let [anime-indices (.animeRatedMap lfd)
        anime-factors (.animeFactors lfd)
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.lambda lfd))
      (println (count anime-indices))
      (println (.numColumns anime-factors))
      (println (.numRows anime-factors))
      (.printArray writer anime-indices)
      (.printArray writer (.getData anime-factors)))))

(defn load-lfd [in]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        read-double (fn [] (Double/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [lambda (read-double)
            anime-indices (make-array Integer/TYPE (read-int))
            cols (read-int)
            rows (read-int)
            factors-data (make-array Double/TYPE (* rows cols))]
        (.readArray reader anime-indices)
        (.readArray reader factors-data)
        (aqua.recommend.LatentFactorDecomposition. lambda
                                                   anime-indices
                                                   (no.uib.cipr.matrix.DenseMatrix. rows cols factors-data false))))))

(defn store-user-lfd [out user-lfd]
  (let [user-map (map #(.userId %) (.userMap user-lfd))
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.rank user-lfd))
      (println (count (.userFactors user-lfd)))
      (println (count user-map))
      (.printArray writer (into-array Integer/TYPE user-map))
      (.printArray writer (into-array Double/TYPE (.userFactors user-lfd))))))

(defn load-user-lfd [in lfd users]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)
        user-map (into {} (for [user users] [(.userId user) user]))]
    (binding [*in* in]
      (let [rank (read-int)
            user-factors (make-array Float/TYPE (read-int))
            user-int-map (make-array Integer/TYPE (read-int))]
        (.readArray reader user-int-map)
        (.readArray reader user-factors)
        (aqua.recommend.LatentFactorDecompositionUsers. lfd
                                                        rank
                                                        (into-array aqua.recommend.CFUser (map user-map user-int-map))
                                                        user-factors
                                                        (into-array Integer/TYPE (filter some? (map-indexed #(if (get user-map %2) %1 nil) user-int-map))))))))

(defn get-recommendations [user lfd remove-known-anime]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))

(defn get-all-recommendations [user lfd lfd-airing remove-known-anime]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)
        ranked-airing-anime (.computeUserAnimeScores lfd-airing user-vector)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    (.sort ranked-airing-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [(take 100 (remove-known-anime ranked-anime))
     (take 100 (remove-known-anime ranked-airing-anime))]))

(defn anime-score-map [user lfd]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)]
    (into {} (for [anime ranked-anime]
               [(.animedbId anime) (- (.score anime))]))))
