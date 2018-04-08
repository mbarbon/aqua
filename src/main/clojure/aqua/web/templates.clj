(ns aqua.web.templates
  (:require ring.util.response))

(def ^:private fm-configuration
  (doto (freemarker.template.Configuration. freemarker.template.Configuration/VERSION_2_3_27)
    (.setTemplateExceptionHandler freemarker.template.TemplateExceptionHandler/RETHROW_HANDLER)
    (.setDefaultEncoding "UTF-8")
    (.setObjectWrapper (doto (com.makotan.clojure.freemarker.ClojureWrapper.)
                         (.setExposeFields true)))
    (.setClassForTemplateLoading aqua.mal.data.Anime "/templates")))

(defn get-template [name]
  (.getTemplate fm-configuration name))

(defn render-layout-template [template-name meta model]
  (let [template (get-template "aqua/layout.ftlh")
        writer (java.io.StringWriter.)]
    (.process template {"aquaBodyTemplate" template-name "model" model "meta" meta} writer)
    (-> (.toString writer)
        ring.util.response/response
        (ring.util.response/content-type "text/html"))))

(defn render-template [template-name meta model]
  (let [template (get-template template-name)
        writer (java.io.StringWriter.)]
    (.process template {"model" model "meta" meta} writer)
    (-> (.toString writer)
        ring.util.response/response
        (ring.util.response/content-type (get meta :content-type "text/html")))))

(def ^:private dev-react-bundle "/static/js/bundle.js")

(defn- find-react-bundle []
  (if-let [stream (.getResourceAsStream aqua.mal.Serialize "/public/asset-manifest.json")]
    (let [mapper (com.fasterxml.jackson.databind.ObjectMapper.)
          map (.readValue mapper stream java.util.HashMap)]
      (str "/" (get map "main.js")))
    dev-react-bundle))

(def jsBundle (find-react-bundle))
