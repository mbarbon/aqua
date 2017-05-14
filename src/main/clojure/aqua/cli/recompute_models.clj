(ns aqua.cli.recompute-models
  (:require aqua.mal-local
            aqua.misc
            aqua.recommend.rp-similar-anime
            clojure.java.io))

(def user-count 20000)

; around the point where the score from find-rp-size starts stabilizing
; (i.e. multiple random projections start returning more stable results)
(def rp-projection-size 1500)
; arbitrary
(def rp-similar-item-count 30)

(defn- recompute-rp-model [users anime-map]
  (let [rp-similar (aqua.recommend.rp-similar-anime/create-rp-similarity users anime-map rp-projection-size rp-similar-item-count)]
    (with-open [out (clojure.java.io/writer "maldump/rp-model")]
      (aqua.recommend.rp-similar-anime/store-rp-similarity out rp-similar))))

(defn -main [& projection-sizes]
  (let [data-source (aqua.mal-local/open-sqlite-ro "maldump" "maldump.sqlite")
        cf-parameters (aqua.misc/make-cf-parameters 0 0)
        users (aqua.mal-local/load-filtered-cf-users-into data-source cf-parameters (java.util.HashMap.) (java.util.ArrayList. (for [_ (range user-count)] nil)))
        anime (aqua.mal-local/load-anime data-source)]
    (println "Recomputing random projection similarity model")
    (time (recompute-rp-model users anime))))
