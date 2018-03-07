(ns aqua.web.serve
  (:gen-class)
  (:require aqua.web.recommender
            aqua.web.search
            aqua.web.globals
            aqua.web.globals-init
            aqua.web.mal-proxy
            aqua.web.internal
            aqua.web.background
            aqua.web.dump
            aqua.web.templates
            cheshire.generate
            [compojure.core :refer :all]
            [compojure.route :as route]
            clojure.tools.cli
            ring.adapter.jetty
            ring.util.response
            ring.middleware.json
            ring.middleware.reload
            ring.middleware.stacktrace
            ring.middleware.browser-caching
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defn- encode-binary [buffer ^com.fasterxml.jackson.core.JsonGenerator jsonGenerator]
  (.writeBinary jsonGenerator buffer))

(cheshire.generate/add-encoder (Class/forName "[B") encode-binary)

(defn bad-request [body]
  (-> (ring.util.response/response body)
      (ring.util.response/status 400)))

(defn- parse-header [value]
  (let [parts (clojure.string/split value #",\s*")]
    (for [part parts]
      (let [[name parameter] (clojure.string/split part #";" 2)]
        name))))

(defn- accepts-gzip [headers]
  (if-let [accept-encoding (get headers "accept-encoding")]
    (let [encodings (parse-header accept-encoding)
          has-deflate (.contains encodings "gzip")]
      has-deflate)))

(defn- accepts-protobuf [headers]
  (if-let [accept (get headers "accept")]
    (let [encodings (parse-header accept)
          has-protobuf (.contains encodings "application/x-protobuf")]
      has-protobuf)))

(defroutes raw-routes
  (GET "/list/anime/:username" [username :as {headers :headers}]
    (let [accepts-gzip (accepts-gzip headers)
          accepts-protobuf (accepts-protobuf headers)
          [response-data content-type content-encoding queue-position]
            (aqua.web.mal-proxy/fetch-user username accepts-gzip accepts-protobuf)]
      (if response-data
        {:status 200
         :headers {"Content-Type" content-type
                   "Content-Encoding" content-encoding}
         :body response-data}
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (str "{\"queue-position\":" queue-position "}")}))))

(defroutes maybe-json-routes
  (GET "/" []
    (ring.util.response/content-type
      (ring.util.response/resource-response "index.html"
                                            {:root "public"})
                                            "text/html"))

  (GET "/anime/details/:animedb-id" [animedb-id]
    (if-let [model (aqua.web.recommender/recommend-single-anime (Integer/parseInt animedb-id))]
      (let [anime (:animeDetails model)
            page-title (str "Anime recommendations for " (:title anime))
            page-description (str "If you watched " (:title anime) " you will like the other anime in this page")]
        (aqua.web.templates/render-layout-template "anime-details.ftlh"
                                                  {:title page-title
                                                    :description page-description}
                                                  model))
      (ring.util.response/redirect "/anime/list")))

  (GET "/anime/list/:initial" [initial]
    (aqua.web.templates/render-layout-template "anime-list-initial.ftlh"
                                               {:noindex true}
                                               (aqua.web.mal-proxy/anime-list-detail initial)))

  (GET "/anime/list" []
    (aqua.web.templates/render-layout-template "anime-list.ftlh"
                                               {:noindex true}
                                               (aqua.web.mal-proxy/anime-list-excerpt)))

  (POST "/recommend" {:keys [body headers]}
    (let [user (aqua.mal.Serialize/readPartialCFUser @aqua.web.globals/*cf-parameters body)]
      (ring.util.response/response
        (aqua.web.recommender/recommend user))))

  (GET "/autocomplete" {params :params}
    (ring.util.response/response
      (aqua.web.search/autocomplete (params :term))))

  (GET "/sitemaps/anime.xml" []
    (aqua.web.templates/render-template "aqua/sitemaps/anime.ftlh"
                                        {:content-type "text/xml"}
                                        {:anime (vals @aqua.web.globals/*anime)}))

  (route/resources "/"))

(defroutes app-routes
  (-> maybe-json-routes
    (ring.middleware.json/wrap-json-response))
  raw-routes
  (route/not-found "Not Found"))

(def reload)

(defroutes service-app-json-routes
  (POST "/sync/all-anime-ids" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/all-anime-ids body)))
  (POST "/sync/all-manga-ids" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/all-manga-ids body)))
  (POST "/sync/all-user-ids" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/all-user-ids body)))
  (POST "/sync/changed-users" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/changed-users body)))
  (POST "/sync/changed-anime" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/changed-anime body)))
  (POST "/sync/changed-manga" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/changed-manga body)))
  (POST "/sync/store-users" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/store-users body)))
  (POST "/sync/store-anime" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/store-anime body)))
  (POST "/sync/store-manga" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/store-manga body)))
  (POST "/sync/upload-model/:model-name" {{:keys [model-name]} :params body :body}
    (ring.util.response/response
      (aqua.web.dump/upload-model model-name body)))
  (POST "/sync/commit-models" {:keys [body]}
    (ring.util.response/response
      (aqua.web.dump/commit-models))))

(defroutes service-app-routes
  (GET "/_is_enabled" []
    (ring.util.response/response
      (str (aqua.web.internal/enabled-and-healthy))))
  (GET "/_reload_data" []
    (ring.util.response/response (reload)))
  (ring.middleware.json/wrap-json-body service-app-json-routes)
  (route/not-found "Service endpoint not found"))

(defn- configurator [jetty-server]
  (.setHandler jetty-server
    (doto (io.dropwizard.jetty.BiDiGzipHandler.)
          (.addIncludedMimeTypes (into-array ["text/html"
                                              "text/javascript"
                                              ; this is needed because Jetty GzipHandler checks the content type
                                              ; based on the extension, not the actual content type of the response
                                              "application/javascript"
                                              "text/css"
                                              "application/json"]))
          (.addIncludedMethods (into-array String ["GET" "POST"]))
          (.setMinGzipSize 1024)
          (.setHandler (.getHandler jetty-server))))
  (.setRequestLog jetty-server
    (doto (org.eclipse.jetty.server.Slf4jRequestLog.)
      (.setLoggerName "access-logger"))))

(defn init [options]
  (aqua.web.globals-init/init (:mal-data-directory options)
                              (:state-directory options))
  (aqua.web.search/init)
  (aqua.web.recommender/init)
  (aqua.web.background/schedule reload "Reload user models" 43200 86400)
  (aqua.web.mal-proxy/init options))

(defn reload []
  (aqua.web.globals-init/reload)
  (aqua.web.search/reload)
  (aqua.web.recommender/reload)
  (aqua.web.mal-proxy/reload)
  (str true))

(def app
  (let [security (site-defaults :security)
        no-csrf (assoc security :anti-forgery false)
        modified-site-defaults (dissoc (assoc site-defaults :security no-csrf) :static)]
    (-> app-routes
      (ring.middleware.browser-caching/wrap-browser-caching {"text/javascript" 604800 ; 7 days
                                                             "image/jpeg"      604800
                                                             "text/html"       604800
                                                             "text/css"        604800})
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
   [nil "--slowpoke" "Enable slowpoke"
    :default true
    :id :slowpoke
    :assoc-fn (fn [m k _] (assoc m k true))]
   [nil "--no-slowpoke" "Disable slowpoke"
    :id :slowpoke
    :assoc-fn (fn [m k _] (assoc m k false))]
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
                            :join?        false
                            :configurator configurator})]
      (.join main-server)
      (.join service-server))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (clojure.tools.cli/parse-opts args cli-options)]
    (cond
      (:help options) ;
      errors ;
      :else (run-server options))))
