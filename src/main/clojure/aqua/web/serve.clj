(ns aqua.web.serve
  (:gen-class)
  (:require aqua.web.recommender
            aqua.web.search
            aqua.web.globals
            aqua.web.mal-proxy
            aqua.web.internal
            [compojure.core :refer :all]
            [compojure.route :as route]
            clojure.tools.cli
            ring.adapter.jetty
            ring.util.response
            ring.middleware.json
            ring.middleware.reload
            ring.middleware.stacktrace
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
    (let [user (aqua.mal.Json/readCFUser aqua.web.globals/cf-parameters body)]
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

(defroutes service-app-routes
  (GET "/_is_enabled" []
    (ring.util.response/response
      (str (aqua.web.internal/enabled-and-healthy))))
  (route/not-found "Service endpoint not found"))

(defn- configurator [jetty-server]
  (.setRequestLog jetty-server
    (doto (org.eclipse.jetty.server.Slf4jRequestLog.)
      (.setLoggerName "access-logger"))))

(defn init [options]
  (aqua.web.globals/init (:mal-data-directory options)
                         (:state-directory options))
  (aqua.web.mal-proxy/init)
  (aqua.web.search/init)
  (aqua.web.recommender/init))

(def app
  (let [security (site-defaults :security)
        no-csrf (assoc security :anti-forgery false)
        modified-site-defaults (assoc site-defaults :security no-csrf)]
    (-> app-routes
      (wrap-defaults modified-site-defaults))))

(def service-app
  (let [security (site-defaults :security)
        no-csrf (assoc security :anti-forgery false)
        modified-site-defaults (assoc site-defaults :security no-csrf)]
    (-> service-app-routes
      (ring.middleware.json/wrap-json-response)
      (wrap-defaults modified-site-defaults))))

(def ^:private cli-options
  [["-p" "--port PORT" "Port number"
    :default 9000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   [nil "--host HOST" "Listen address"
    :default "0.0.0.0"]
   [nil "--state-directory DIR" "Runtime state directory"
    :default "/var/tmp"]
   [nil "--mal-data-directory DIR" "MAL dump database directory"
    :default "maldump"]
   [nil "--code-reload" "Enable code reloading"]
   [nil "--stacktraces" "Enable stacktrace middleware"]
   ["-h" "--help"]])

(defn- wrap-handler [handler options]
  (let [maybe-reload (if (:code-reload options)
                       (ring.middleware.reload/wrap-reload
                           handler {:dirs ["src/main/clojure"]})
                       handler)
        maybe-stacktraces (if (:stacktraces options)
                            (ring.middleware.stacktrace/wrap-stacktrace maybe-reload)
                            maybe-reload)
        wrapped-handler maybe-stacktraces]
    wrapped-handler))

(defn- run-server [options]
  (let [main-handler (wrap-handler app options)
        service-handler (wrap-handler service-app options)]
    (init options)

    (let [main-server (ring.adapter.jetty/run-jetty
                        main-handler
                        {:port         (:port options)
                         :host         (:host options)
                         :join?        false
                         :configurator configurator})
          service-server (ring.adapter.jetty/run-jetty
                           service-handler
                           {:port (+ 1 (:port options))
                            :host         "127.0.0.1"
                            :join?        false})]
      (.join main-server)
      (.join service-server))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (clojure.tools.cli/parse-opts args cli-options)]
    (cond
      (:help options) ;
      errors ;
      :else (run-server options))))
