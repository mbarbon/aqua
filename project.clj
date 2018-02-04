(defproject aqua-recommend "0.0.1-SNAPSHOT"
  :description "Aqua: simplistic anime recommendations"
  :url "https://github.com/mbarbon/aqua"
  :license {:name "3-Clause BSD License"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.xerial/sqlite-jdbc "3.16.1"]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.7"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-xml "2.8.7"]
                 [io.protostuff/protostuff-core "1.6.0"]
                 [io.protostuff/protostuff-runtime "1.6.0"]
                 [com.carrotsearch/hppc "0.7.3"]
                 [com.google.guava/guava "21.0"]
                 [com.googlecode.matrix-toolkits-java/mtj "1.0.2"]
                 [org.jsoup/jsoup "1.10.3"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.eclipse.jetty/jetty-server "9.4.8.v20171121"]
                 [javax.servlet/javax.servlet-api "4.0.0"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [org.asynchttpclient/async-http-client "2.0.37"]
                 [org.freemarker/freemarker "2.3.27-incubating"]
                 [ring "1.5.0"]
                 [compojure "1.5.1"]
                 [slester/ring-browser-caching "0.1.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.1"]]
  :source-paths ["src/main/clojure"]
  :resource-paths ["src/main/resources"]
  :java-source-paths ["src/main/java"]
  :test-paths ["test" "src/test/clojure"]
  :target-path "target/%s"
  :pom-addition [:properties
                    ["maven.compiler.source" "1.8"]
                    ["maven.compiler.target" "1.8"]]
  :profiles {:serve-jvm8 {:jvm-opts ["-Xms100M" "-Xmx100M"
                                     "-XX:+PrintGCDateStamps"
                                     "-XX:+PrintGCDetails"]}
             :serve-jvm9 {:jvm-opts ["-Xms100M" "-Xmx100M"
                                     "-Xlog:gc:stdout:time"]}
             :dev {:resource-paths ["src/test/resources"]}
             :repl {:global-vars {*warn-on-reflection* true}}
             :uberjar {:aot  :all
                       :resource-paths ["build"]
                       :main aqua.web.serve}})
