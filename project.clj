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
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [http-kit "2.2.0"]
                 [ring "1.5.0"]
                 [compojure "1.5.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.1"]]
  :source-paths ["src/main/clojure"]
  :resource-paths ["src/main/resource"]
  :java-source-paths ["src/main/java"]
  :test-paths ["test" "src/test/clojure"]
  :target-path "target/%s"
  :jvm-opts ["-Xmx400M"]
  :profiles {:uberjar {:aot  :all
                       :main aqua.web.serve}})
