(ns aqua.web.templates
  (:require ring.util.response))

(def ^:private fm-configuration
  (doto (freemarker.template.Configuration. freemarker.template.Configuration/VERSION_2_3_27)
    (.setTemplateExceptionHandler freemarker.template.TemplateExceptionHandler/RETHROW_HANDLER)
    (.setDefaultEncoding "UTF-8")
    (.setObjectWrapper (com.makotan.clojure.freemarker.ClojureWrapper.))
    (.setClassForTemplateLoading aqua.mal.data.Anime "/templates")))

(defn get-template [name]
  (.getTemplate fm-configuration name))

(defn render-layout-template [template-name model]
  (let [template (get-template "aqua/layout.ftlh")
        writer (java.io.StringWriter.)]
    (.process template {"aquaBodyTemplate" template-name "model" model} writer)
    (-> (.toString writer)
        ring.util.response/response
        (ring.util.response/content-type "text/html"))))
