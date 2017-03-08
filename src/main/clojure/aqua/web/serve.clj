(ns aqua.web.serve
  (:require aqua.web.recommender
            aqua.web.search
            aqua.web.mal-proxy
            [compojure.core :refer :all]
            [compojure.route :as route]
            ring.util.response
            ring.middleware.json
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defn bad-request [body]
  (-> (ring.util.response/response body)
      (ring.util.response/status 400)))

(defroutes raw-routes
  (GET "/list/anime/:username" [username :as {headers :headers}]
    (let [compressed (if-let [accept-encoding (get headers "accept-encoding")]
                       (let [encodings (clojure.string/split accept-encoding #",\s*")
                             has-deflate (.contains encodings "gzip")]
                         has-deflate))
          [response-data queue-position] (aqua.web.mal-proxy/fetch-user username (not compressed))]
      (if response-data
        {:status 200,
         :headers {"Content-Type" "application/json"
                   "Content-Encoding" (if compressed "gzip" "identity")}
         :body response-data}
        {:status 200,
         :headers {"Content-Type" "application/json"}
         :body (str "{\"queue-position\":" queue-position "}")}))))

(defroutes maybe-json-routes
  (GET "/" []
    (ring.util.response/content-type
      (ring.util.response/resource-response "index.html"
                                            {:root "public"})
                                            "text/html"))

  (POST "/recommend" {:keys [body]}
    (let [user (aqua.mal.data.Json/readUser body)]
      (ring.util.response/response
        (aqua.web.recommender/recommend user))))

  (GET "/autocomplete" {params :params}
    (ring.util.response/response
      (aqua.web.search/autocomplete (params :term))))

  (route/resources "/"))

(defroutes app-routes
  (-> maybe-json-routes
    (ring.middleware.json/wrap-json-response))
  raw-routes
  (route/not-found "Not Found"))

(defn init []
  (aqua.web.globals/init "maldump")
  (aqua.web.mal-proxy/init)
  (aqua.web.search/init)
  (aqua.web.recommender/init))

(def app
  (let [security (site-defaults :security)
        no-csrf (assoc security :anti-forgery false)
        modified-site-defaults (assoc site-defaults :security no-csrf)]
    (-> app-routes
      (wrap-defaults modified-site-defaults))))
