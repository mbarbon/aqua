(ns aqua.web.recommender
  (:require aqua.mal-local
            aqua.web.render
            [aqua.web.globals :refer [*maldump-directory
                                      *data-source-ro
                                      *users
                                      *anime
                                      *cf-parameters
                                      *co-occurrency
                                      *lfd-users
                                      *lfd-anime
                                      *lfd-anime-airing]]
            aqua.recommend.cosine
            aqua.recommend.co-occurrency
            aqua.recommend.lfd
            aqua.recommend.lfd-cf
            aqua.recommend.user-sample
            aqua.misc
            [clojure.tools.logging :as log]))

(def ^:private user-count 15000)
(def ^:private all-recommenders [:cf-cosine
                                 :cf-lfd
                                 :cf-co-occurrency
                                 :lfd])

(defn- model-path [file]
  (str @*maldump-directory "/" file))

(defn- load-users []
  (log/info "Start loading users")
  (let [data-source @*data-source-ro
        users (aqua.recommend.user-sample/load-filtered-cf-users (model-path "user-sample") data-source @*cf-parameters user-count @*anime)
        co-occurrency (aqua.recommend.co-occurrency/load-co-occurrency (model-path "co-occurrency-model")
                                                                       (model-path "co-occurrency-model-airing"))
        lfd (aqua.recommend.lfd/load-lfd (model-path "lfd-model") @*anime)
        lfd-airing (aqua.recommend.lfd/load-lfd (model-path "lfd-model-airing") @*anime)
        lfd-users (aqua.recommend.lfd/load-user-lfd (model-path "lfd-user-model") lfd users)]
    (reset! *users users)
    (reset! *co-occurrency co-occurrency)
    (reset! *lfd-users lfd-users)
    (reset! *lfd-anime lfd)
    (reset! *lfd-anime-airing lfd-airing))
  (log/info "Done loading users"))

(defn- cf-rated-cache [users]
  (let [cache (java.util.HashMap.)]
    (doseq [^aqua.recommend.CFUser user users]
      (doseq [rated (.animeList user)]
        (.putIfAbsent cache rated rated)))
    cache))

(defn- reload-users []
  (log/info "Start reloading users")
  ; so users are garbage collected
  (reset! *lfd-users nil)
  (let [data-source @*data-source-ro
        cache (cf-rated-cache @*users)
        target @*users]
    (aqua.recommend.user-sample/load-filtered-cf-users-into (model-path "user-sample") data-source @*cf-parameters cache target @*anime))
  (let [lfd (aqua.recommend.lfd/load-lfd (model-path "lfd-model") @*anime)
        lfd-airing (aqua.recommend.lfd/load-lfd (model-path "lfd-model-airing") @*anime)
        lfd-users (aqua.recommend.lfd/load-user-lfd (model-path "lfd-user-model") lfd @*users)]
    (reset! *lfd-users lfd-users)
    (reset! *lfd-anime lfd)
    (reset! *lfd-anime-airing lfd-airing))
  (let [co-occurrency (aqua.recommend.co-occurrency/load-co-occurrency (model-path "co-occurrency-model")
                                                                       (model-path "co-occurrency-model-airing"))]
    (reset! *co-occurrency co-occurrency))
  (log/info "Done reloading users"))

(defn init []
  (load-users))

(defn reload []
  (reload-users))

(defn- make-list [lookup-anime recommended]
  (for [^aqua.recommend.ScoredAnime scored-anime recommended]
    (aqua.web.render/render-anime (lookup-anime (.animedbId scored-anime)) (.tags scored-anime))))

(defn- call-recommender [recommender user known-anime-filter airing-anime-filter known-anime-tagger]
  (case recommender
    :cf-cosine
      (aqua.recommend.cosine/get-all-recommendations user
                                                     @*users
                                                     known-anime-filter
                                                     airing-anime-filter
                                                     known-anime-tagger)
    :cf-co-occurrency
      (aqua.recommend.co-occurrency/get-all-recommendations user
                                                            @*co-occurrency
                                                            known-anime-filter
                                                            airing-anime-filter
                                                            known-anime-tagger)
    :cf-lfd
      ; *lfd-users is unset during loading
      (if-let [lfd-users @*lfd-users]
        (aqua.recommend.lfd-cf/get-all-recommendations user
                                                       lfd-users
                                                       known-anime-filter
                                                       airing-anime-filter
                                                       known-anime-tagger)
        (aqua.recommend.cosine/get-all-recommendations user
                                                       @*users
                                                       known-anime-filter
                                                       airing-anime-filter
                                                       known-anime-tagger))
    :lfd
      (aqua.recommend.lfd/get-all-recommendations user
                                                  @*lfd-anime
                                                  @*lfd-anime-airing
                                                  known-anime-filter
                                                  airing-anime-filter
                                                  known-anime-tagger)))

(defn recommend [user]
  (let [lookup-anime @*anime
        known-anime-filter (aqua.misc/make-filter user lookup-anime)
        airing-anime-filter (aqua.misc/make-airing-filter user lookup-anime)
        known-anime-tagger (aqua.misc/make-tagger user lookup-anime)
        recommender (all-recommenders (int (* (Math/random) (count all-recommenders))))
        [recommended-completed recommended-airing]
          (call-recommender recommender user known-anime-filter airing-anime-filter known-anime-tagger)]
    {:airing (make-list lookup-anime (take 10 recommended-airing))
     :completed (make-list lookup-anime recommended-completed)}))
