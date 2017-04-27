(ns aqua.web.recommender
  (:require aqua.mal-local
            aqua.web.render
            [aqua.web.globals :refer [*data-source-ro *users *anime]]
            aqua.recommend.cosine
            aqua.misc
            [clojure.tools.logging :as log]))

(defn- reload-users []
  (log/info "Start loading users")
  (let [data-source @*data-source-ro
        users (aqua.mal-local/load-users data-source 20000)]
    (aqua.misc/normalize-all-ratings users)
    (reset! *users users))
  (log/info "Done loading users"))

(defn init []
  (reload-users))

(defn- make-list [lookup-anime recommended]
  (for [[anime-id score tags] recommended]
    (aqua.web.render/render-anime (lookup-anime anime-id) tags)))

(defn recommend [user]
  (let [users @*users
        lookup-anime @*anime]
    (aqua.misc/normalize-ratings user)
    (let [known-anime-filter (aqua.misc/make-filter user lookup-anime)
          airing-anime-filter (aqua.misc/make-airing-filter user lookup-anime)
          known-anime-tagger (aqua.misc/make-tagger user lookup-anime)
          [users recommended-completed]
            (known-anime-tagger
              (aqua.recommend.cosine/get-recommendations user users known-anime-filter))
          [_ recommended-airing]
            (known-anime-tagger
              [users (aqua.recommend.collaborative-filter/recommended-airing users airing-anime-filter)])]
      {:airing (make-list lookup-anime (take 10 recommended-airing))
       :completed (make-list lookup-anime recommended-completed)})))
