(ns aqua.recommend.lfd-items
  (:require aqua.recommend.item-item-model))

(defn- create-model [anime lfd factors item-count]
  (let [clfd (aqua.recommend.ComputeLatentFactorDecompositionItems. anime lfd factors item-count)]
    (.findSimilarAnime clfd)
    (.lfdItems clfd)))

(defn create-lfd-items [anime lfd lfd-airing item-count item-count-airing]
  (let [model (create-model anime lfd lfd item-count)
        model-airing (create-model anime lfd lfd-airing item-count-airing)]
    (aqua.recommend.LatentFactorDecompositionItems. model model-airing)))

(defn store-lfd-items-complete [out lfd]
  (aqua.recommend.item-item-model/store-item-item out (.complete lfd)))

(defn store-lfd-items-airing [out lfd]
  (aqua.recommend.item-item-model/store-item-item out (.airing lfd)))

(defn load-lfd-items [path-complete path-airing]
  (let [complete (aqua.recommend.item-item-model/load-item-item path-complete)
        airing (aqua.recommend.item-item-model/load-item-item path-airing)]
    (aqua.recommend.LatentFactorDecompositionItems. complete airing)))

(defn get-recommendations [user
                           ^aqua.recommend.LatentFactorDecompositionItems model
                           remove-known-anime]
  (aqua.recommend.item-item-model/get-recommendations user (.complete model) remove-known-anime))

(defn get-all-recommendations [user
                               ^aqua.recommend.LatentFactorDecompositionItems model
                               remove-known-anime keep-airing-anime
                               tagger]
  (let [[_ recommendations] (aqua.recommend.item-item-model/get-recommendations user (.complete model) remove-known-anime)
        [_ airing] (aqua.recommend.item-item-model/get-recommendations user (.airing model) remove-known-anime)]
    [(tagger recommendations) (tagger airing)]))