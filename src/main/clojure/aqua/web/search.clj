(ns aqua.web.search
  (:require aqua.search.autocomplete
            aqua.web.render
            [clojure.tools.logging :as log]
            [aqua.web.globals :refer [*data-source-ro *suggest *anime]]))

(defn- rebuild-suggester []
  (log/info "Start loading suggester")
  (let [data-source @*data-source-ro
        anime-titles (aqua.mal-local/load-anime-titles data-source)
        anime-rank (aqua.mal-local/load-anime-rank data-source)
        suggest (aqua.search.autocomplete/prepare-suggest anime-titles anime-rank)]
    (log/info "Done loading suggester")
    (reset! *suggest suggest)))

(defn init []
  (rebuild-suggester))

(defn reload []
  (rebuild-suggester))

(defn autocomplete [term]
  (let [suggestions (aqua.search.autocomplete/get-suggestions @*suggest term @*anime)]
    (for [anime suggestions]
      (aqua.web.render/render-anime anime nil))))
