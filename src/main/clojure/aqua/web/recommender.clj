(ns aqua.web.recommender
  (:require aqua.mal-local
            aqua.web.render
            [aqua.web.globals :refer [*maldump-directory *data-source-ro *users *anime]]
            aqua.recommend.cosine
            aqua.misc
            [clojure.tools.logging :as log]))

(def ^:private user-count 20000)

(defn- load-users []
  (log/info "Start loading users")
  (let [maldump-directory @*maldump-directory
        data-source @*data-source-ro
        users (aqua.mal-local/load-filtered-cf-users maldump-directory data-source aqua.web.globals/cf-parameters user-count)]
    (reset! *users users))
  (log/info "Done loading users"))

(defn- cf-rated-cache [users]
  (let [cache (java.util.HashMap.)]
    (doseq [^aqua.recommend.CFUser user users]
      (doseq [rated (.animeList user)]
        (.putIfAbsent cache rated rated)))
    cache))

(defn- reload-users []
  (log/info "Start reloading users")
  (let [maldump-directory @*maldump-directory
        data-source @*data-source-ro
        cache (cf-rated-cache @*users)
        target @*users]
    (aqua.mal-local/load-filtered-cf-users-into maldump-directory data-source aqua.web.globals/cf-parameters cache target))
  (log/info "Done reloading users"))

(defn init []
  (load-users))

(defn reload []
  (reload-users))

(defn- make-list [lookup-anime recommended]
  (for [^aqua.recommend.ScoredAnime scored-anime recommended]
    (aqua.web.render/render-anime (lookup-anime (.animedbId scored-anime)) (.tags scored-anime))))

(defn recommend [user]
  (let [users @*users
        lookup-anime @*anime]
    (let [known-anime-filter (aqua.misc/make-filter user lookup-anime)
          airing-anime-filter (aqua.misc/make-airing-filter user lookup-anime)
          known-anime-tagger (aqua.misc/make-tagger user lookup-anime)
          [recommended-completed recommended-airing]
            (aqua.recommend.cosine/get-all-recommendations user users known-anime-filter airing-anime-filter known-anime-tagger)]
      {:airing (make-list lookup-anime (take 10 recommended-airing))
       :completed (make-list lookup-anime recommended-completed)})))
