(ns aqua.recommend.lfd
  (:require aqua.recommend.model-files))

(defn prepare-lfd-decompositor [user-list anime-map rank lambda]
  (let [anime-index-map (java.util.HashMap.)
        airing-anime-index-map (java.util.HashMap.)]
    (doseq [^aqua.mal.data.Anime anime (vals anime-map)]
      (cond
        (.isAiring anime) (.put airing-anime-index-map
                                (.animedbId anime)
                                (.size airing-anime-index-map))
        (.isCompleted anime) (.put anime-index-map
                                   (.animedbId anime)
                                   (.size anime-index-map))))
    (let [lfdr (aqua.recommend.ComputeLatentFactorDecomposition. anime-index-map
                                                                 (.size user-list)
                                                                 rank
                                                                 lambda)
          ; shares user weights
          lfdr-airing (.forAiring lfdr
                                  airing-anime-index-map)]
      (dotimes [i (.size user-list)]
        (.addCompletedRatings lfdr i (.get user-list i))
        (.addAiringRatings lfdr-airing i (.get user-list i)))
      (.initializeIteration lfdr)
      ; initializes user weights twice, but it's OK
      (.initializeIteration lfdr-airing)
      [lfdr lfdr-airing])))

(defn run-user-steps
  ([^aqua.recommend.ComputeLatentFactorDecomposition lfdr]
    (run-user-steps lfdr 0 (.userCount lfdr)))
  ([^aqua.recommend.ComputeLatentFactorDecomposition lfdr current-index steps]
    (dotimes [i steps]
      (.userStep lfdr (+ current-index i)))))

(defn run-anime-steps
  ([^aqua.recommend.ComputeLatentFactorDecomposition lfdr]
    (run-anime-steps lfdr 0 (.animeCount lfdr)))
  ([^aqua.recommend.ComputeLatentFactorDecomposition  lfdr current-index steps]
    (dotimes [i steps]
      (.animeStep lfdr (+ current-index i)))))

(defn store-lfd [out ^aqua.recommend.LatentFactorDecomposition lfd]
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

(defn- load-lfd-v1 [in anime-map]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        read-double (fn [] (Double/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)]
    (binding [*in* in]
      (let [lambda (read-double)
            anime-indices (int-array (read-int))
            cols (read-int)
            rows (read-int)
            factors-data (double-array (* rows cols))]
        (.readArray reader anime-indices)
        (if anime-map
          (dotimes [n (alength anime-indices)]
            (if-let [^aqua.mal.data.Anime anime (anime-map (aget anime-indices n))]
              ; mark as Hentai if present
              (if (.isHentai anime)
                (aset-int anime-indices n (- (aget anime-indices n))))
              ; remove from model in case the set of interesting anime is different from model
              ; creation to runtime
              (aset-int anime-indices n 0))))
        (.readArray reader factors-data)
        (aqua.recommend.LatentFactorDecomposition. lambda
                                                   anime-indices
                                                   (no.uib.cipr.matrix.DenseMatrix. rows cols factors-data false))))))

(defn- load-lfd-helper [path anime-map-to-filter-hentai]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-lfd-v1 in anime-map-to-filter-hentai)))

(defn load-lfd
  ([in] (load-lfd in nil))
  ([in anime-map]
     (load-lfd-helper in anime-map)))

(defn store-user-lfd [out ^aqua.recommend.LatentFactorDecompositionUsers user-lfd]
  (let [user-map (map #(.userId %) (.userMap user-lfd))
        writer (no.uib.cipr.matrix.io.MatrixVectorWriter. out)]
    (binding [*out* out]
      (println (.rank user-lfd))
      (println (count (.userFactors user-lfd)))
      (println (count user-map))
      (.printArray writer (into-array Integer/TYPE user-map))
      (.printArray writer (into-array Double/TYPE (.userFactors user-lfd))))))

(defn- load-user-lfd-v1 [in lfd users]
  (let [read-int (fn [] (Integer/valueOf (read-line)))
        reader (no.uib.cipr.matrix.io.MatrixVectorReader. in)
        user-map (into {} (for [^aqua.recommend.CFUser user users]
                            ; Dubious, but sometimes convenient
                            (if user
                              [(.userId user) user]
                              [-1 nil])))]
    (binding [*in* in]
      (let [rank (read-int)
            user-factors (float-array (read-int))
            user-int-map (int-array (read-int))]
        (.readArray reader user-int-map)
        (.readArray reader user-factors)
        (aqua.recommend.LatentFactorDecompositionUsers. lfd
                                                        rank
                                                        (into-array aqua.recommend.CFUser (map user-map user-int-map))
                                                        user-factors
                                                        (into-array Integer/TYPE (filter some? (map-indexed #(if (get user-map %2) %1 nil) user-int-map))))))))

(defn load-user-lfd [path lfd users]
  (aqua.recommend.model-files/with-open-model path 1 in version
    (load-user-lfd-v1 in lfd users)))

(defn get-recommendations [user
                           ^aqua.recommend.LatentFactorDecomposition lfd
                           remove-known-anime]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [[] (take 100 (remove-known-anime ranked-anime))]))

(defn get-all-recommendations [user
                               ^aqua.recommend.LatentFactorDecomposition lfd
                               ^aqua.recommend.LatentFactorDecomposition lfd-airing
                               remove-known-anime keep-airing-anime tagger]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)
        ranked-airing-anime (.computeUserAnimeScores lfd-airing user-vector)]
    (.sort ranked-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    (.sort ranked-airing-anime aqua.recommend.ScoredAnimeId/SORT_SCORE)
    [(tagger (take 100 (remove-known-anime ranked-anime)))
     (tagger (take 100 (keep-airing-anime ranked-airing-anime)))]))

(defn anime-score-map [user lfd]
  (let [user-vector (.computeUserVector lfd user)
        ranked-anime (.computeUserAnimeScores lfd user-vector)]
    (into {} (for [anime ranked-anime]
               [(.animedbId anime) (- (.score anime))]))))
