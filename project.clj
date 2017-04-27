(defproject aquq-recommend "0.0.1-SNAPSHOT"
  :description "Aqua: simplistic anime recommendations"
  :url "https://example.com/FIXME"
  :license {:name "3-Clause BSD License"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.xerial/sqlite-jdbc "3.16.1"]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.7"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-xml "2.8.7"]
                 [com.google.guava/guava "21.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [http-kit "2.2.0"]
                 [compojure "1.5.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.1"]]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["test" "src/test/clojure"]
  :target-path "target/%s"
  :jvm-opts ["-Xmx400M"]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler aqua.web.serve/app
         :init    aqua.web.serve/init
         :port    9000}
  :profiles {:uberjar {:aot :all}})
