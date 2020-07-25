(ns aqua.web.recommender
  (:require aqua.mal-local
            aqua.web.render
            [aqua.web.globals :refer [*data-source-ro
                                      *users
                                      *anime
                                      *cf-parameters
                                      *co-occurrency
                                      *lfd-users
                                      *lfd-anime
                                      *lfd-anime-airing]]
            aqua.paths
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

(defn- load-users []
  (log/info "Start loading users")
  (let [data-source @*data-source-ro
        users (aqua.recommend.user-sample/load-filtered-cf-users (aqua.paths/anime-user-sample) data-source @*cf-parameters user-count @*anime)
        co-occurrency (aqua.recommend.co-occurrency/load-co-occurrency (aqua.paths/anime-co-occurrency-model)
                                                                       (aqua.paths/anime-co-occurrency-model-airing))
        lfd (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model) @*anime)
        lfd-airing (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model-airing) @*anime)
        lfd-users (aqua.recommend.lfd/load-user-lfd (aqua.paths/anime-lfd-user-model) lfd users)]
    (reset! *users users)
    (reset! *co-occurrency co-occurrency)
    (reset! *lfd-users lfd-users)
    (reset! *lfd-anime lfd)
    (reset! *lfd-anime-airing lfd-airing))
  (log/info "Done loading users"))

(defn- reload-users []
  (log/info "Start reloading users")
  ; so users are garbage collected
  (reset! *lfd-users nil)
  (let [data-source @*data-source-ro
        target @*users]
    (aqua.recommend.user-sample/load-filtered-cf-users-into (aqua.paths/anime-user-sample) data-source @*cf-parameters target @*anime))
  (let [lfd (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model) @*anime)
        lfd-airing (aqua.recommend.lfd/load-lfd (aqua.paths/anime-lfd-model-airing) @*anime)
        lfd-users (aqua.recommend.lfd/load-user-lfd (aqua.paths/anime-lfd-user-model) lfd @*users)]
    (reset! *lfd-users lfd-users)
    (reset! *lfd-anime lfd)
    (reset! *lfd-anime-airing lfd-airing))
  (let [co-occurrency (aqua.recommend.co-occurrency/load-co-occurrency (aqua.paths/anime-co-occurrency-model)
                                                                       (aqua.paths/anime-co-occurrency-model-airing))]
    (reset! *co-occurrency co-occurrency))
  (log/info "Done reloading users"))

(defn init []
  (load-users))

(defn reload []
  (reload-users))

(defn- make-list [lookup-anime recommended]
  (filter some?
    (for [^aqua.recommend.ScoredAnime scored-anime recommended]
      (if-let [anime (lookup-anime (.animedbId scored-anime))]
        (aqua.web.render/render-anime anime (.tags scored-anime))))))

(defn- call-recommender [recommender user known-anime-filter airing-anime-filter known-anime-tagger]
  (case recommender
    :cf-cosine
      (aqua.recommend.cosine/get-anime-recommendations user
                                                       @*users
                                                       known-anime-filter
                                                       airing-anime-filter
                                                       known-anime-tagger)
    :cf-co-occurrency
      (aqua.recommend.co-occurrency/get-anime-recommendations user
                                                              @*co-occurrency
                                                              known-anime-filter
                                                              airing-anime-filter
                                                              known-anime-tagger)
    :cf-lfd
      ; *lfd-users is unset during loading
      (if-let [lfd-users @*lfd-users]
        (aqua.recommend.lfd-cf/get-anime-recommendations user
                                                         lfd-users
                                                         known-anime-filter
                                                         airing-anime-filter
                                                         known-anime-tagger)
        (aqua.recommend.cosine/get-anime-recommendations user
                                                         @*users
                                                         known-anime-filter
                                                         airing-anime-filter
                                                         known-anime-tagger))
    :lfd
      (aqua.recommend.lfd/get-anime-recommendations user
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

(defn recommend-single-anime [animedb-id]
  (if-let [anime (@*anime animedb-id)]
    (let [lookup-anime @*anime
          co-occurrency @*co-occurrency
          anime (lookup-anime animedb-id)
          completed (.similarAnime (.complete co-occurrency) animedb-id)
          airing (.similarAnime (.airing co-occurrency) animedb-id)]
      (.sort completed aqua.recommend.ScoredAnimeId/SORT_SCORE)
      (.sort airing aqua.recommend.ScoredAnimeId/SORT_SCORE)
      {:animeDetails (aqua.web.render/add-medium-cover anime (aqua.web.render/render-anime anime nil))
        :recommendations {:airing (make-list lookup-anime (take 5 airing))
                          :completed (make-list lookup-anime (take 15 completed))}})))
