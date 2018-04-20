(ns aqua.web.search
  (:require aqua.web.render
            [clojure.tools.logging :as log]
            [aqua.web.globals :refer [*data-source-ro *suggest *anime]]))

(defn- rebuild-suggester []
  (log/info "Start loading suggester")
  (let [data-source @*data-source-ro
        ; nothing against hentai, just there is not enough data for
        ; meaningful recommendations
        anime-titles (map #(aqua.search.AnimeTitle. %) (aqua.mal-local/load-anime-titles data-source (set (.keySet @*anime))))
        anime-rank (aqua.mal-local/load-anime-rank data-source)
        suggest (aqua.search.SubstringMatchSuggest. anime-titles anime-rank)]
    (log/info "Done loading suggester")
    (reset! *suggest suggest)))

(defn init []
  (rebuild-suggester))

(defn reload []
  (rebuild-suggester))

(defn autocomplete [term]
  (let [suggestions (map @*anime (.suggest @*suggest term 15))]
    (for [anime suggestions]
      (aqua.web.render/render-anime anime nil))))
